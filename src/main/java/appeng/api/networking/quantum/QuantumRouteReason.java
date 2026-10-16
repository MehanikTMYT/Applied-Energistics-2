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
 * Round 11 ("Routing Controller foundation"): why a route is in its current {@link QuantumRouteState}.
 * <p/>
 * Kept as a separate axis from the state itself — see {@link QuantumRouteState}'s own javadoc for why. Like the state,
 * this is a derived projection and is never persisted.
 */
public enum QuantumRouteReason {
    /** Nothing to explain — the route is ACTIVE. */
    NONE,
    /** No persisted edge exists between the pair's endpoints. */
    NO_INTENT,
    /** This endpoint itself is not currently online (chunk unloaded, block removed, never activated). */
    ENDPOINT_UNLOADED,
    /** This endpoint is online but its intended peer is not. */
    PEER_MISSING,
    /** The credential bound to one of the endpoints is SUSPENDED — recoverable by an authorized resume. */
    CREDENTIAL_SUSPENDED,
    /** The credential bound to one of the endpoints is REVOKED — terminal. */
    CREDENTIAL_REVOKED,
    /**
     * Both endpoints are online and permitted, but the connection does not exist — most commonly because
     * {@code GridConnection}'s own unmodified cross-security-domain pre-merge guard refused to merge the two networks.
     * Never worked around; a repair will keep failing until the domains are made compatible by a human.
     */
    SECURITY_DOMAIN_MISMATCH,
    /**
     * Both endpoints are online and permitted and no security guard applies, yet the realization is missing — the
     * genuine "intent exists, materialization does not" case an authorized repair is meant to fix.
     */
    CONNECTION_MISSING,
    /**
     * Two simultaneously-live physical blocks claim the same {@link QuantumEndpointId}; the runtime index fails closed
     * and neither is treated as the authoritative node.
     */
    ENDPOINT_CONFLICT,
    /**
     * The Routing Controller could not deterministically resolve exactly one {@link NetworkProfileId} — e.g. its
     * network is LEGACY (un-secured), unpowered, or no profile has been bootstrapped for its security domain. No
     * routing operation is possible; the controller never falls back to guessing or browsing other profiles.
     */
    CONTROLLER_UNBOUND,
    /**
     * The lane is not a Mk I PAIR (it does not have exactly two endpoints). Round 11 is deliberately PAIR-only and
     * reports this rather than attempting to infer a HUB/MESH topology.
     */
    UNSUPPORTED_IN_ROUND_11
}
