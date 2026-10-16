/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, AlgorithmX2, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.me.security;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.networking.security.IDeviceIdentity;

/**
 * Reusable persistent {@link IDeviceIdentity} state for a machine that wants to be its own
 * {@link appeng.api.networking.security.PrincipalId.Device} principal.
 * <p/>
 * <b>Identity lifecycle.</b> The {@link UUID} is generated <em>eagerly, exactly once</em>, when the owning object is
 * constructed, and is thereafter only ever replaced by a value read back from disk. It is deliberately <em>not</em>
 * generated lazily inside the getter: a getter that mints a fresh id on every call (or on every load) would hand out a
 * different {@code Device} principal after each restart and would silently invalidate every ACL entry granted to that
 * device.
 * <p/>
 * <b>Migration (brief §32).</b> A machine that was placed before device identities existed has no saved id. Such a
 * machine keeps the id generated in its constructor, and {@link #read(ValueInput)} reports that the id was absent so
 * the host can persist it immediately (see {@link #read(ValueInput)}'s return value). The id is therefore minted once
 * per pre-existing machine and is stable from then on. It is <em>never</em> derived from a player UUID and confers no
 * permissions by itself: on a {@code SECURED} network a brand-new device principal starts with no grants at all, which
 * is the intended fail-closed default (brief §33).
 * <p/>
 * <b>Scope.</b> One instance belongs to exactly one machine. Two Pattern Providers - two blocks, two parts, or a block
 * and a part - each own their own instance and therefore have different identities, even when two parts share a single
 * {@code CableBus} host, because each part owns its own grid node (see {@code SecurityService#resolveDeviceId}, which
 * resolves through {@code IGridNode#getOwner()}).
 */
public final class PersistentDeviceIdentity {
    /**
     * NBT key. Deliberately distinct from any key {@code PatternProviderLogic} writes, so that this state is saved by
     * the block entity / part itself and never through {@code PatternProviderLogic#writeToNBT}/{@code readFromNBT} -
     * those two methods carry required ExpandedAE mixins and are intentionally left untouched (brief §24).
     */
    private static final String TAG_DEVICE_ID = "aeDeviceId";

    private UUID deviceId = UUID.randomUUID();

    /**
     * @return this machine's stable device id. Never {@code null}, never regenerated per call.
     */
    public UUID get() {
        return deviceId;
    }

    public void write(ValueOutput data) {
        data.store(TAG_DEVICE_ID, UUIDUtil.CODEC, deviceId);
    }

    /**
     * Caches the identity {@link DevicePrincipalRegistry} issued to this machine.
     * <p/>
     * Round 29 moved authority out of this class: what is stored here is now a <em>claim</em> the registry either
     * confirms or replaces, and this method exists so a machine that was reminted (because the id it had was already
     * held by somebody else) writes the new one back instead of re-colliding on every load.
     * <p/>
     * Deliberately <em>not</em> a general {@code setDeviceId(UUID)}: the only value a caller should ever pass here is
     * one that {@link DevicePrincipalRegistry#resolveOrRegister} just returned for this exact machine. Passing an
     * arbitrary id changes nothing about authority - the registry still answers with the binding - it would only
     * desynchronise this cache from it (brief §31).
     */
    public void adoptAuthenticatedId(UUID issuedByRegistry) {
        this.deviceId = issuedByRegistry;
    }

    /**
     * Restores the persisted identity.
     *
     * @return {@code true} if this machine had no persisted identity (a pre-existing machine from a world saved before
     *         device identities existed, or a freshly placed one), in which case the caller should mark itself dirty so
     *         the newly minted id is written out and stays stable from now on.
     */
    public boolean read(ValueInput data) {
        var stored = data.read(TAG_DEVICE_ID, UUIDUtil.CODEC).orElse(null);
        if (stored == null) {
            return true;
        }
        deviceId = stored;
        return false;
    }
}
