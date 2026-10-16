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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import appeng.core.AppEng;

/**
 * The authority for {@code PrincipalId.Device}.
 * <p/>
 * <b>What changed in Round 29.</b> Until now a device's identity was whatever its host reported. Round 26 proved that
 * forgeable, Round 27 raised the cost by demanding a real grid node, and Round 28 proved why no amount of node-local
 * work can finish the job: every byte a host persists - including the per-node blobs AE2's own services write - travels
 * back through {@code ManagedGridNode.deserialize(ValueInput)}, whose tag the host supplies. A host can therefore
 * always present someone else's id on load. Authority has to live somewhere the host cannot write, and this is that
 * place.
 * <p/>
 * From here on the host's stored id is a <em>claim</em>; the binding kept here is the <em>authority</em>.
 *
 * <h2>Scope: server, not level</h2>
 *
 * Attached to the overworld's data storage, which makes it server-global - the same shape
 * {@link SecurityDomainRegistry} already uses. That is not imitation: device grants live inside security domains that
 * are themselves server-global, and this fork's grids can span dimensions, so a level-scoped registry would let the
 * same id authenticate twice in two dimensions.
 *
 * <h2>The rules, and why each one is what it is</h2>
 *
 * <ul>
 * <li><b>A key keeps its id.</b> Look-up is by {@link MachineKey}, so an ordinary reload finds the existing binding and
 * returns the same id. Nothing about activity is consulted, which is what makes chunk unload a non-event (brief
 * §22).</li>
 * <li><b>A legacy claim is honoured only if free.</b> Upgrading an existing world must not orphan grants, so a device
 * arriving with a stored id and no binding adopts it. If that id is already bound to a different key, the newcomer is
 * minted a fresh one instead.</li>
 * <li><b>An id is never held by two keys.</b> This is the invariant everything else exists to protect, and it is what
 * closes both the clone (a copy sits at a different position, so a different key) and the malicious real node (its own
 * position, so its own key).</li>
 * <li><b>Retired ids are not reused.</b> Removal tombstones the id rather than freeing it, so an old copy of a
 * destroyed machine's NBT cannot resurrect its authority later (brief §20). UUID space makes this free.</li>
 * </ul>
 *
 * <h2>The limitation this design accepts, stated plainly</h2>
 *
 * On the first upgrade of a world that already contains two machines sharing one id - which Round 28 proved is
 * reachable through blueprints - there is no evidence left about which was the original. Whichever loads first keeps
 * the id; the other is reminted and needs a new grant. Security is preserved (never two authentic holders), historical
 * attribution is not. Brief §36/§37 call this the right trade, and it is made deliberately rather than hidden behind
 * "trust on first use".
 */
public final class DevicePrincipalRegistry extends SavedData {
    /**
     * Serialized form: the live bindings plus the set of retired ids. Kept as two flat collections rather than one
     * map-with-optional, because a tombstone is genuinely a different thing from a binding and reads better as such.
     */
    private record RegistryData(Map<UUID, MachineKey> bindings, List<UUID> tombstones) {
        static final Codec<RegistryData> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(
                        Codec.unboundedMap(UUIDUtil.STRING_CODEC, MachineKey.CODEC).fieldOf("bindings")
                                .forGetter(RegistryData::bindings),
                        UUIDUtil.STRING_CODEC.listOf().fieldOf("tombstones").forGetter(RegistryData::tombstones))
                .apply(builder, RegistryData::new));
    }

    private static final Identifier ID = AppEng.makeId("device_principals");

    static final SavedDataType<DevicePrincipalRegistry> TYPE = new SavedDataType<>(
            ID,
            level -> new DevicePrincipalRegistry(),
            level -> RegistryData.CODEC.xmap(
                    data -> {
                        var registry = new DevicePrincipalRegistry();
                        registry.bindings.putAll(data.bindings());
                        registry.tombstones.addAll(data.tombstones());
                        registry.reindex();
                        return registry;
                    },
                    registry -> new RegistryData(Map.copyOf(registry.bindings), List.copyOf(registry.tombstones))));

    /** Authoritative direction: which machine currently holds each live id. */
    private final Map<UUID, MachineKey> bindings = new HashMap<>();
    /** Ids that once belonged to a machine and must never be handed out again. */
    private final Set<UUID> tombstones = new HashSet<>();
    /** Derived reverse index, rebuilt on load; never serialized. */
    private final Map<MachineKey, UUID> byMachine = new HashMap<>();

    private DevicePrincipalRegistry() {
    }

    private void reindex() {
        byMachine.clear();
        bindings.forEach((id, key) -> byMachine.put(key, id));
    }

    public static DevicePrincipalRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    public static DevicePrincipalRegistry get(MinecraftServer server) {
        var overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot retrieve device principals for a server that has no overworld.");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /** An in-memory registry with no world attached, so the state machine can be unit-tested on its own. */
    public static DevicePrincipalRegistry createForTesting() {
        return new DevicePrincipalRegistry();
    }

    /**
     * The only entry point that establishes authority, called once per device from its lifecycle - never from a
     * permission check, which stays a pure lookup (brief §46).
     *
     * @param key           where the device physically is.
     * @param claimedLegacy the id the host had stored, or {@code null} for a device that has none. Treated as a hint
     *                      for migrating an existing world, never as proof.
     * @return the id this machine is authorized to act as. Equal to {@code claimedLegacy} when that claim was free, and
     *         a freshly minted id otherwise - including every case where the claim belonged to somebody else.
     */
    public UUID resolveOrRegister(MachineKey key, @Nullable UUID claimedLegacy) {
        var existing = byMachine.get(key);
        if (existing != null) {
            // Ordinary reload. The host's claim is irrelevant here: if it disagrees, the binding wins, which is
            // precisely what makes a runtime-mutated or NBT-edited claim powerless.
            return existing;
        }

        if (claimedLegacy != null && !isTaken(claimedLegacy)) {
            bind(claimedLegacy, key);
            return claimedLegacy;
        }

        var minted = UUID.randomUUID();
        while (isTaken(minted)) {
            minted = UUID.randomUUID();
        }
        bind(minted, key);
        return minted;
    }

    /** An id is unavailable whether it is currently held or was retired earlier. */
    private boolean isTaken(UUID id) {
        return bindings.containsKey(id) || tombstones.contains(id);
    }

    /**
     * The pure lookup a permission check uses: what is this machine authenticated as, if anything? Returns {@code null}
     * for a machine that was never registered, which callers must treat as "not a device".
     */
    @Nullable
    public UUID getAuthenticated(MachineKey key) {
        return byMachine.get(key);
    }

    /** Whether an id is currently held by a live machine (as opposed to unbound or tombstoned). */
    public boolean isBound(UUID deviceId) {
        return bindings.containsKey(deviceId);
    }

    public boolean isTombstoned(UUID deviceId) {
        return tombstones.contains(deviceId);
    }

    /**
     * Permanent removal: the machine is gone for good (broken, not merely unloaded). The id is tombstoned rather than
     * freed, so a stale copy of the old NBT cannot bring the authority back months later.
     * <p/>
     * Chunk unload must <em>not</em> call this. AE2 cannot always distinguish unload from destruction, and the safe
     * side of that ambiguity is to keep the binding: an identity that outlives its machine costs a UUID, whereas one
     * that is released too early is an ACL leak.
     */
    public void retire(MachineKey key) {
        var id = byMachine.remove(key);
        if (id != null) {
            bindings.remove(id);
            tombstones.add(id);
            setDirty();
        }
    }

    private void bind(UUID id, MachineKey key) {
        bindings.put(id, key);
        byMachine.put(key, id);
        setDirty();
    }

    /** Number of records, for the storage-cost figures in the round report. */
    public int size() {
        return bindings.size() + tombstones.size();
    }
}
