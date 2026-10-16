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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;

import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.QuantumEndpointId;

/**
 * Transient (never persisted — this is live-session bookkeeping, not registry data), conflict-safe table mapping
 * currently-online {@link QuantumEndpointId}s to the live {@link IGridNode} representing them, feeding
 * {@link QuantumLaneReconciler#reconstruct}.
 * <p/>
 * <b>Why this exists.</b> A plain {@code Map<QuantumEndpointId, IGridNode>} built by simply calling {@code put} for
 * every physical endpoint that announces itself online would silently let a second, distinct physical node claiming the
 * same {@link QuantumEndpointId} overwrite the first — indistinguishable from an ordinary reattach/rebind (see
 * {@link QuantumEndpointRecord#markOnline}), even though it is a fundamentally different, unsafe situation: two
 * simultaneously-live copies of what is supposed to be one stable identity. This class makes that distinction explicit
 * and deterministic instead: the first registrant becomes the sole active node for that id; a second, genuinely
 * different node registering the same id while the first is still registered flips BOTH into
 * {@link RegistrationResult#CONFLICT} and neither is included in {@link #onlineNodes()} (so
 * {@link QuantumLaneReconciler} simply never forms a connection through either) until the conflict is resolved by one
 * of them unregistering. This is deliberately not a full credential/ownership-arbitration system — just the minimal
 * invariant this identity model needs: never treat two simultaneously-live claimants of one id as if they were a single
 * valid endpoint.
 */
public final class QuantumEndpointOnlineTable {
    public enum RegistrationResult {
        /** This node is now (or remains) the sole active node for this id. */
        ACTIVE,
        /**
         * A different node already claims this id (or now does, as of this call) — this id is excluded from
         * {@link #onlineNodes()} until one of the claimants unregisters.
         */
        CONFLICT
    }

    /**
     * Round 10 ("Mk I PHYSICAL QUANTUM PAIR"): one transient table per live {@link MinecraftServer}, exactly mirroring
     * the "world-scoped singleton, memory-only, never persisted" shape {@link appeng.api.features.Locatables} already
     * uses for the vanilla QNB's own frequency table — the only difference is a {@link WeakHashMap} keyed by server
     * instance instead of one static field, so a second/embedded server (integrated-server restarts, unit tests that
     * spin up more than one {@code MinecraftServer}) never shares state with a previous one. Before this round, this
     * class was only ever constructed directly (unit tests only) — this is the first production wiring point.
     */
    private static final Map<MinecraftServer, QuantumEndpointOnlineTable> BY_SERVER = new WeakHashMap<>();

    /**
     * @return this server's single live table, created on first use. Never persisted, never shared across servers.
     */
    public static QuantumEndpointOnlineTable get(MinecraftServer server) {
        return BY_SERVER.computeIfAbsent(server, s -> new QuantumEndpointOnlineTable());
    }

    private final Map<QuantumEndpointId, IGridNode> active = new HashMap<>();
    private final Set<QuantumEndpointId> conflicted = new HashSet<>();

    /**
     * Registers {@code node} as the live representative of {@code id}. Idempotent for the same {@code (id, node)} pair
     * (e.g. calling it again on every repath is harmless).
     */
    public RegistrationResult register(QuantumEndpointId id, IGridNode node) {
        if (conflicted.contains(id)) {
            return RegistrationResult.CONFLICT;
        }
        var existing = active.get(id);
        if (existing == null) {
            active.put(id, node);
            return RegistrationResult.ACTIVE;
        }
        if (existing == node) {
            return RegistrationResult.ACTIVE;
        }
        // A second, distinct physical node claiming the same identity while the first is still registered: neither
        // is treated as the sole authority - both become CONFLICT rather than one silently winning by overwrite.
        active.remove(id);
        conflicted.add(id);
        return RegistrationResult.CONFLICT;
    }

    /**
     * Unregisters {@code node} from {@code id} (e.g. its chunk unloaded). If {@code id} was conflicted, this clears the
     * conflict too — a stale second claimant going away lets the id become registrable again.
     */
    public void unregister(QuantumEndpointId id, IGridNode node) {
        active.remove(id, node);
        conflicted.remove(id);
    }

    public boolean isConflicted(QuantumEndpointId id) {
        return conflicted.contains(id);
    }

    /**
     * @return an immutable snapshot of every id currently mapped to exactly one active node — safe to pass directly to
     *         {@link QuantumLaneReconciler#reconstruct}.
     */
    public Map<QuantumEndpointId, IGridNode> onlineNodes() {
        return Map.copyOf(active);
    }
}
