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
 * Lifecycle state of one persisted {@link appeng.me.quantum.QuantumCredential}.
 * <p/>
 * Linear happy path: {@link #ISSUED} &rarr; {@link #ACTIVATED} &rarr; {@link #ONLINE} &harr; {@link #OFFLINE}, with
 * {@link #SUSPENDED} and {@link #REVOKED} reachable as explicit administrative actions from anywhere in that path (see
 * {@code QuantumCredentialService} for exactly which transitions are legal). {@link #SUPERSEDED} and {@link #RETUNED}
 * are reserved for a future full reissue/migration workflow — see {@code QuantumCredentialRegistry#supersede}, which
 * today only bumps the credential's revision counter (the stale-card defense) without a full state-machine around it;
 * nothing in Q4 transitions a credential into either of those two states yet.
 */
public enum QuantumCredentialState {
    /** Minted, but never yet bound to any {@link appeng.api.networking.quantum.QuantumEndpointId}. */
    ISSUED,
    /**
     * Bound to exactly one endpoint (see {@link appeng.me.quantum.QuantumCredential#boundEndpoint()}), but that
     * endpoint has not (yet, or not currently this session) reported itself online.
     */
    ACTIVATED,
    /** Bound to an endpoint that is currently online. */
    ONLINE,
    /** Bound to an endpoint that was online at some point but currently is not. Not deleted — see {@link #REVOKED}. */
    OFFLINE,
    /**
     * Explicitly suspended by an administrator (holding
     * {@link appeng.api.networking.security.SecurityPermissions#QUANTUM_REVOKE}): the binding and revision are kept,
     * but the credential is refused by every activation/route-reconstruction check until explicitly resumed. Not
     * terminal.
     */
    SUSPENDED,
    /**
     * Explicitly, permanently revoked. Terminal — refused by every activation check forever after. The persisted
     * {@link appeng.me.quantum.QuantumCredential} record itself is <strong>not</strong> deleted (audit/observability —
     * a stale reference fails safe/observably rather than the id becoming reusable).
     */
    REVOKED,
    /** Reserved for a future full reissue workflow. Not produced or consumed by any Q4 code path yet. */
    SUPERSEDED,
    /** Reserved for a future full endpoint-retune workflow. Not produced or consumed by any Q4 code path yet. */
    RETUNED;

    public static final Codec<QuantumCredentialState> CODEC = Codec.STRING.xmap(
            QuantumCredentialState::valueOf, Enum::name);

    /**
     * @return {@code true} if a credential in this state is permitted to back an active route (i.e. neither suspended
     *         nor revoked). Used by {@code QuantumCredentialService}/route-reconstruction integration to decide whether
     *         a bound endpoint should be allowed to participate in {@code QuantumLaneReconciler.reconstruct}.
     */
    public boolean permitsActiveRoute() {
        return this != SUSPENDED && this != REVOKED;
    }
}
