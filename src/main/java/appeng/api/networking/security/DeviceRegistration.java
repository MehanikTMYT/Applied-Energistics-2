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

package appeng.api.networking.security;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;

/**
 * The supported way for a machine - including one from another mod - to obtain a {@link PrincipalId.Device} identity.
 *
 * <h2>Why this exists</h2>
 *
 * Since Round 31 the device registry, not a machine's own saved UUID, decides who a device is. That closes a family of
 * impersonation problems, but it also means a machine AE2 has never registered simply has no principal: on a
 * {@code SECURED} network it can act only within whatever was granted to {@link PrincipalId#EVERYONE}. Third-party
 * devices therefore need a way in, and this is it - the same one AE2's own machines use.
 *
 * <h2>What the caller may and may not decide</h2>
 *
 * The caller supplies the machine itself and, optionally, the id it had stored from before this system existed. It does
 * <em>not</em> supply the physical key: that is derived here, from where the object actually is in the world, precisely
 * so that a caller cannot present another machine's location and inherit its identity.
 * <p/>
 * The returned id is authoritative. It equals {@code claimedLegacyId} when that id was still free - which is what keeps
 * an existing world's ACL entries working across the upgrade - and is a freshly minted id otherwise, including every
 * case where the claim belonged to somebody else. Cache the returned value if you like; it is a cache, not a
 * credential, and presenting a different one later changes nothing.
 *
 * <h2>When to call it</h2>
 *
 * Once, from the machine's own lifecycle, at the point where it has a level and a position - {@code onReady} for a
 * block entity, {@code addToWorld} for a part. Not from a permission check: authority is looked up during those, never
 * created.
 */
public final class DeviceRegistration {
    private DeviceRegistration() {
    }

    /**
     * Registers {@code machine} with the device registry, or returns the identity it already holds.
     *
     * @param machine         the block entity or part that owns the grid node. Anything AE2 cannot locate in the world
     *                        is rejected.
     * @param claimedLegacyId the id this machine had stored before, or {@code null} for a new one. A migration hint,
     *                        never proof.
     * @return the authoritative device id, or {@code null} if {@code machine} is not a locatable in-world device or is
     *         not on a server. A {@code null} result means the machine has no device principal, which callers must
     *         treat as "no permissions beyond EVERYONE" rather than as an error.
     */
    @Nullable
    public static UUID register(@Nullable Object machine, @Nullable UUID claimedLegacyId) {
        var key = MachineKey.of(machine);
        if (key == null) {
            return null;
        }

        var level = levelOf(machine);
        if (level == null) {
            return null;
        }

        return DevicePrincipalRegistry.get(level).resolveOrRegister(key, claimedLegacyId);
    }

    @Nullable
    private static ServerLevel levelOf(Object machine) {
        if (machine instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
        }
        if (machine instanceof appeng.parts.AEBasePart part) {
            var be = part.getBlockEntity();
            return be != null && be.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
        }
        return null;
    }
}
