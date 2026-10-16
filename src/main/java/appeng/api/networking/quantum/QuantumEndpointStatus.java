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

/**
 * Lifecycle status of one persisted {@link appeng.me.quantum.QuantumEndpointRecord}.
 * <p/>
 * Deliberately does <strong>not</strong> include a "deleted purely because its chunk unloaded" concept — unloaded is
 * {@link #OFFLINE}, never a reason to drop the record from {@code appeng.me.quantum.QuantumTopologyRegistry}. Only
 * {@link #REMOVED} is a deliberate, explicit retirement.
 */
public enum QuantumEndpointStatus {
    /** Known to the registry (created and bound to a lane) but has never actually come online this session. */
    REGISTERED,
    /** Currently backed by a live, in-memory {@code IGridNode} — see {@code QuantumEndpointOnlineTable}. */
    ONLINE,
    /**
     * Was online at some point; its chunk unloaded, its block entity is not currently present, or the server is not
     * running. Not deleted — reconstructing the lane again once this endpoint (or its replacement, on reattach) comes
     * back online restores its intended edges without recreating the record.
     */
    OFFLINE,
    /**
     * Explicitly, permanently retired — e.g. a player broke the endpoint block with no intention of rebuilding it.
     * Excluded from reconstruction. The record itself is kept (not physically erased) for audit purposes and so a stale
     * reference from a lane's edge list fails safe/observably rather than resurrecting a dangling identity.
     */
    REMOVED;

    public static final Codec<QuantumEndpointStatus> CODEC = Codec.STRING.xmap(
            QuantumEndpointStatus::valueOf, Enum::name);
}
