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

/**
 * Round 11 ("Routing Controller foundation"): the OPERATIONAL state of one quantum route, as observed right now.
 * <p/>
 * <b>This is a derived projection, never persisted and never authoritative.</b> It is recomputed on demand from three
 * independent, already-authoritative sources — the persisted topology intent
 * ({@code QuantumTopologyRegistry}/{@code QuantumLane} edges), the transient runtime endpoint index
 * ({@code QuantumEndpointOnlineTable}), and credential state ({@code QuantumCredentialRegistry}). Nothing ever writes a
 * {@code QuantumRouteState} to disk; there is deliberately no second source of truth for "is this route up".
 * <p/>
 * <b>Why state and reason are separate types.</b> Collapsing them would produce one sprawling enum mixing endpoint
 * identity lifecycle, credential lifecycle and route lifecycle together (ONLINE, SUSPENDED, PEER_MISSING, …), which is
 * precisely the design this architecture avoids: those are three genuinely independent axes owned by three different
 * authorities. A caller that only needs "can I use this route" reads this enum; a caller that needs to tell the player
 * WHY reads {@link QuantumRouteReason} alongside it.
 */
public enum QuantumRouteState {
    /** Intent exists, both endpoints are online, and the connection realizing the edge currently exists. */
    ACTIVE,
    /**
     * Intent exists and is structurally valid, but it is not realized right now for a recoverable, non-security reason
     * — typically an endpoint or its peer being unloaded. It will materialize by itself, with no player action at all,
     * as soon as the missing side comes back (trusted-internal reconstruction).
     */
    DEGRADED,
    /**
     * Intent exists but must NOT be realized: a security-domain incompatibility, or a suspended/revoked credential.
     * Never resolves on its own — a human has to fix the underlying authority problem first.
     */
    BLOCKED,
    /**
     * There is no route intent at all (and therefore nothing to be up or down). Also the state reported when the
     * Routing Controller cannot resolve a bound profile. Note that an accidental runtime adjacency between two
     * endpoints never turns this into {@link #ACTIVE} — without a persisted edge there is no quantum route, by
     * definition.
     */
    INACTIVE
}
