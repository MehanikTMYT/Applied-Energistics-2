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

package appeng.me.quantum;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.UUIDUtil;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumLaneId;
import appeng.api.networking.security.SecurityDomainId;

/**
 * The persistent record of one Network Profile: a stable logical identity for a player-facing ME network/backbone
 * context, the {@link SecurityDomainId} it is (optionally) bound to, and the set of {@link QuantumLaneId}s that belong
 * to it.
 * <p/>
 * Mutable; callers go through {@link QuantumTopologyRegistry}, which is responsible for calling
 * {@link QuantumTopologyRegistry#markDirty()} after a mutation so it gets persisted — mirroring exactly how
 * {@link appeng.me.security.SecurityDomain} relates to {@link appeng.me.security.SecurityDomainRegistry}.
 */
public final class NetworkProfile {
    /** Bumped whenever the on-disk schema of {@link Data} changes; see {@link #upgrade(Data)}. */
    public static final int CURRENT_VERSION = 1;

    private final NetworkProfileId id;
    @Nullable
    private SecurityDomainId securityDomainId;
    private final Set<QuantumLaneId> lanes = new HashSet<>();

    public NetworkProfile(NetworkProfileId id) {
        this.id = id;
    }

    public NetworkProfileId id() {
        return id;
    }

    /**
     * @return the {@link SecurityDomainId} this profile is bound to, if any. Deliberately independent storage from the
     *         profile's own {@link NetworkProfileId} — see that class's javadoc for why.
     */
    public Optional<SecurityDomainId> getSecurityDomainId() {
        return Optional.ofNullable(securityDomainId);
    }

    /**
     * Binds (or explicitly rebinds) this profile's security domain. This is always a deliberate, explicit
     * administrative action; nothing in {@link QuantumTopologyRegistry} or {@link QuantumLaneReconciler} ever calls
     * this as a side effect of lane reconstruction/reload — reconstructing a lane's {@link QuantumLane} topology never
     * manufactures, infers, or "infects" a security domain binding on its own (see the registry's class javadoc, and
     * {@code appeng.me.security.SecurityService}, which remains the sole ACL authority).
     */
    public void setSecurityDomainId(@Nullable SecurityDomainId securityDomainId) {
        this.securityDomainId = securityDomainId;
    }

    public Set<QuantumLaneId> getLanes() {
        return Set.copyOf(lanes);
    }

    void addLane(QuantumLaneId laneId) {
        lanes.add(laneId);
    }

    void removeLane(QuantumLaneId laneId) {
        lanes.remove(laneId);
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private record Data(int version, UUID id, Optional<UUID> securityDomainId, List<UUID> lanes) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.INT.fieldOf("version").forGetter(Data::version),
                UUIDUtil.CODEC.fieldOf("id").forGetter(Data::id),
                UUIDUtil.CODEC.optionalFieldOf("security_domain_id").forGetter(Data::securityDomainId),
                UUIDUtil.CODEC.listOf().optionalFieldOf("lanes", List.of()).forGetter(Data::lanes))
                .apply(builder, Data::new));
    }

    public static final Codec<NetworkProfile> CODEC = Data.CODEC.xmap(NetworkProfile::upgrade, NetworkProfile::toData);

    /**
     * Converts a possibly-old-version {@link Data} DTO into a live {@link NetworkProfile}. There is only one version so
     * far; future schema changes should branch on {@code data.version()} here, mirroring
     * {@code SecurityDomain#upgrade}.
     */
    private static NetworkProfile upgrade(Data data) {
        var profile = new NetworkProfile(new NetworkProfileId(data.id()));
        data.securityDomainId().ifPresent(uuid -> profile.securityDomainId = new SecurityDomainId(uuid));
        for (var laneId : data.lanes()) {
            profile.lanes.add(new QuantumLaneId(laneId));
        }
        return profile;
    }

    private static Data toData(NetworkProfile profile) {
        return new Data(CURRENT_VERSION, profile.id.id(),
                Optional.ofNullable(profile.securityDomainId).map(SecurityDomainId::id),
                profile.lanes.stream().map(QuantumLaneId::id).toList());
    }
}
