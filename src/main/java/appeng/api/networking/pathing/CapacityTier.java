/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, AlgorithmX2, All rights reserved.
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

package appeng.api.networking.pathing;

/**
 * The infrastructure tier of a grid node's native cable/controller hardware. This is orthogonal to {@link ChannelMode}
 * (a global, server-wide admin multiplier applied uniformly to every node) and to
 * {@link appeng.api.networking.GridFlags#DENSE_CAPACITY} (whether this particular node is a "dense" carrier, worth 4x a
 * "smart" carrier of the same tier).
 * <p>
 * Capacity tier only ever <b>constrains</b> transport - it never generates channels by itself. A node's actual channel
 * capacity is {@code (DENSE_CAPACITY ? 32 : 8) * tier.getFactor() * channelMode.getCableCapacityFactor()}. Mixing tiers
 * on a single grid is intentional and safe: the existing per-node bottleneck pathing (see
 * {@code GridNode.subtreeMaxChannels} and {@link appeng.me.pathfinding.PathingCalculation}) already resolves the
 * effective capacity of any path to the controller as the minimum along that path, one {@code GridNode} hop at a time;
 * a lower-tier cable anywhere along a branch caps everything downstream of it, and the controller's own tier caps the
 * whole branch at its root (see {@code GridNode#setControllerRoute}).
 */
public enum CapacityTier {
    /**
     * Default infrastructure tier. Smart = 8 channels, Dense Smart = 32 channels (matches AE2's historical capacity
     * numbers exactly, before this tier system existed).
     */
    STANDARD(1),
    /**
     * Smart = 16 channels, Dense Smart = 64 channels.
     */
    QUANTUM(2),
    /**
     * Smart = 32 channels, Dense Smart = 128 channels.
     */
    SINGULARITY(4),
    /**
     * Smart = 64 channels, Dense Smart = 256 channels.
     */
    COSMIC(8);

    private final int factor;

    CapacityTier(int factor) {
        this.factor = factor;
    }

    /**
     * @return Multiplier applied on top of the base Smart(8)/Dense(32) capacity. Must be a power of two.
     */
    public int getFactor() {
        return factor;
    }
}
