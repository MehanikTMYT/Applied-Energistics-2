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

package appeng.api.networking.quantum;

import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;

/**
 * Stable, persistent identity for one {@link appeng.me.quantum.QuantumLane} — a topology grouping of
 * {@link QuantumEndpointId}s that belong to the same {@link appeng.me.quantum.NetworkProfile} and are meant to be
 * reconnected into a single connected component (one {@link appeng.api.networking.IGrid}) whenever their endpoints are
 * simultaneously available, following the lane's own persisted topology (see
 * {@link appeng.me.quantum.QuantumLane#getEdges()}).
 * <p/>
 * A lane is a grouping/topology identity, not a physical object and not a security boundary in its own right — ACL
 * enforcement always stays with {@link appeng.api.networking.security.SecurityDomainId} /
 * {@link appeng.me.security.SecurityService}, which the Quantum registry never bypasses or duplicates (see
 * {@code appeng.me.quantum.QuantumTopologyRegistry}'s class javadoc).
 */
public record QuantumLaneId(UUID id) {
    public static final Codec<QuantumLaneId> CODEC = UUIDUtil.CODEC.xmap(QuantumLaneId::new, QuantumLaneId::id);

    public static QuantumLaneId create() {
        return new QuantumLaneId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
