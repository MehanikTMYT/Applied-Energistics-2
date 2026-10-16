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
 * Stable, persistent identity for one physical Quantum Lane attachment point (a "Base"/"Factory"/"Mine"-style endpoint
 * in the Q2 prototype's terminology).
 * <p/>
 * <b>Hard invariant: this is an identity, never a coordinate.</b> {@code dimension + BlockPos} is deliberately NOT used
 * as (or derived into) an endpoint's identity anywhere in this subsystem — see
 * {@link appeng.me.quantum.QuantumEndpointRecord}, which keeps a {@code QuantumEndpointId} separate from its
 * {@link QuantumEndpointLocation}. Coordinate-as-identity would break under block move/replacement/rebind/recovery, a
 * dimensional endpoint being relocated, or a future Routing Controller re-siting an endpoint — all ordinary lifecycle
 * events that must never invalidate what a lane's persisted topology (its {@code List<Edge>}) refers to.
 */
public record QuantumEndpointId(UUID id) {
    public static final Codec<QuantumEndpointId> CODEC = UUIDUtil.CODEC.xmap(QuantumEndpointId::new,
            QuantumEndpointId::id);

    public static QuantumEndpointId create() {
        return new QuantumEndpointId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
