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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a {@link appeng.me.quantum.QuantumEndpointRecord} is <em>currently</em> physically located, if known — a
 * location/status attribute of an endpoint, never its identity. See {@link QuantumEndpointId}'s class javadoc for why
 * coordinate-as-identity is a hard invariant this subsystem does not violate: a record's {@code QuantumEndpointId}
 * never changes across a move/rebind, while its {@code QuantumEndpointLocation} is simply overwritten to the new
 * coordinate. This deliberately does not keep a movement history — only the current location (or none, if
 * {@link appeng.api.networking.quantum.QuantumEndpointStatus#OFFLINE}/newly
 * {@link appeng.api.networking.quantum.QuantumEndpointStatus#REGISTERED} and never yet placed).
 */
public record QuantumEndpointLocation(ResourceKey<Level> dimension, BlockPos pos) {
    public static final Codec<QuantumEndpointLocation> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(QuantumEndpointLocation::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(QuantumEndpointLocation::pos))
            .apply(builder, QuantumEndpointLocation::new));
}
