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

import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.QuantumEndpointId;

/**
 * Round 11 ("Routing Controller foundation"): the single, exact, non-heuristic bridge between a persisted
 * {@link QuantumLane.Edge} (topology INTENT) and the live {@link IGridConnection} that currently realizes it
 * (MATERIALIZED state) — in both directions.
 * <p/>
 * <b>Why this class exists at all.</b> Q3 gave us {@link QuantumLaneReconciler}, which only ever goes one way: intent →
 * connection. Nothing in Q1-Q4/Round 10 could ever go the other way, because nothing needed to: a quantum connection
 * only ever disappeared as a side effect of a node itself being destroyed (chunk unload, block break). Round 11
 * introduces two operations that genuinely need the reverse direction while both nodes stay perfectly alive: an
 * authorized route DELETE, and the credential SUSPEND/REVOKE runtime invalidation this round closes (see
 * {@link QuantumCredentialService#suspend}/{@link QuantumCredentialService#revoke}). Both must destroy exactly one
 * connection and provably nothing else.
 * <p/>
 * <b>The exactness argument — why this is not "approximate delete".</b> A quantum edge is always materialized through
 * {@link appeng.api.networking.GridHelper#createConnection(IGridNode, IGridNode)}, which is
 * {@code GridConnection.create(a, b, null)} — a {@code null} {@code fromAtoB} Direction, and therefore
 * {@link IGridConnection#isInWorld()} {@code == false}. Every ordinary, physically-adjacent in-world connection
 * (cable-to-machine, cable-to-cable) is created WITH a Direction and reports {@code isInWorld() == true}. So the
 * realization of intended edge {@code (a, b)} is identified by two simultaneous, exact facts:
 * <ol>
 * <li>the connection's two sides are precisely the two {@link IGridNode}s currently registered in
 * {@link QuantumEndpointOnlineTable} for endpoint ids {@code a} and {@code b} — an identity match on the registry's own
 * runtime index, never a search over the grid; and</li>
 * <li>{@code !isInWorld()} — so even two Quantum Endpoint blocks placed physically touching each other, which would
 * ALSO have an ordinary in-world face connection between the very same two nodes, can never have that unrelated
 * physical connection destroyed by a route deletion.</li>
 * </ol>
 * There is no scanning, no nearest-match, no "destroy whatever connects these grids". If the exact connection is not
 * found, nothing is destroyed and the caller is told so.
 * <p/>
 * <b>The exactness argument rests on an ownership invariant, not on {@code isInWorld()} alone — document this
 * explicitly, because it is not enforced by the type system.</b> {@code isInWorld() == false} is not unique to Quantum:
 * vanilla {@code MEP2PTunnelPart}, the vanilla Quantum Network Bridge's {@code QuantumCluster}, and
 * {@code ToggleBusPart} all materialize their own virtual links through the exact same
 * {@code GridHelper.createConnection(a, b)} helper (independently confirmed by three external adversarial reviewers of
 * Round 11, jobs 31d3bf98/dd82b8d1). What makes {@link #findMaterializedConnection} exact regardless is that it never
 * scans the grid at large — it walks only {@code nodeA.getConnections()} for the SPECIFIC {@link IGridNode} instances
 * {@code a}/{@code b} that {@link QuantumEndpointOnlineTable#register} holds for these two endpoint ids. Those
 * instances are always a {@code QuantumEndpointBlockEntity}'s own {@code getMainNode().getNode()} — never an exposed,
 * proxied, or position-derived lookup (see {@code QuantumEndpointBlockEntity#goOnlineIfProvisioned}) — and no vanilla
 * P2P/ToggleBus/QNB mechanism ever attaches ITS OWN virtual connection to a node it does not itself own. Break either
 * half of that (this table ever registering something other than the endpoint block's own node, or some future
 * mechanism creating a virtual link to a foreign node) and this discriminator stops being exact. If Round 12+ ever
 * needs a stronger guarantee than this ownership invariant (e.g. the endpoint block becoming part-host-compatible),
 * replace this with an explicit lane handle/identity map rather than widening the search.
 * <p/>
 * This class holds no state, persists nothing, and is not an authority: it never checks a permission, never decides
 * whether a route SHOULD exist, and never edits {@link QuantumTopologyRegistry}. Callers
 * ({@link QuantumRoutingService}, {@link QuantumCredentialService}) own those decisions.
 */
public final class QuantumRouteMaterializer {
    private QuantumRouteMaterializer() {
    }

    /**
     * @return the live connection realizing the quantum edge between {@code nodeA} and {@code nodeB} — i.e. the
     *         connection joining exactly those two nodes that is NOT an in-world face adjacency — or {@code null} if
     *         the edge is currently unrealized. O(degree of {@code nodeA}), never a grid-wide scan.
     */
    @Nullable
    public static IGridConnection findMaterializedConnection(IGridNode nodeA, IGridNode nodeB) {
        for (var connection : nodeA.getConnections()) {
            if (connection.getOtherSide(nodeA) == nodeB && !connection.isInWorld()) {
                return connection;
            }
        }
        return null;
    }

    /**
     * @return {@code true} if the quantum edge {@code (a, b)} is currently materialized. Both endpoints must be online
     *         and unconflicted in {@code table} — a conflicted id (two live claimants) is deliberately treated as not
     *         materialized, mirroring {@link QuantumEndpointOnlineTable#onlineNodes()}'s own exclusion of it.
     */
    public static boolean isMaterialized(QuantumEndpointOnlineTable table, QuantumEndpointId a, QuantumEndpointId b) {
        var nodes = table.onlineNodes();
        var nodeA = nodes.get(a);
        var nodeB = nodes.get(b);
        if (nodeA == null || nodeB == null) {
            return false;
        }
        return findMaterializedConnection(nodeA, nodeB) != null;
    }

    /**
     * Destroys exactly the connection realizing quantum edge {@code (a, b)}, if it is currently materialized.
     * <p/>
     * Safe and idempotent when the edge is already unrealized (peer offline, never connected, or already
     * dematerialized) — that is a normal, correct outcome, not an error. Never destroys an in-world connection; see
     * class javadoc for the exactness argument.
     *
     * @return {@code true} if a connection was actually destroyed by this call.
     */
    public static boolean dematerializeEdge(QuantumEndpointOnlineTable table, QuantumEndpointId a,
            QuantumEndpointId b) {
        var nodes = table.onlineNodes();
        var nodeA = nodes.get(a);
        var nodeB = nodes.get(b);
        if (nodeA == null || nodeB == null) {
            return false;
        }
        var connection = findMaterializedConnection(nodeA, nodeB);
        if (connection == null) {
            return false;
        }
        // The unmodified, existing production primitive - the exact counterpart of GridHelper.createConnection, which
        // is what formed this connection in the first place. Splitting the grid back apart (and the resulting repath)
        // is entirely AE2's own, unchanged behaviour.
        connection.destroy();
        return true;
    }

    /**
     * Destroys every currently-materialized connection realizing an intended edge that TOUCHES {@code endpointId}, on
     * that endpoint's own lane. This is the runtime-invalidation primitive used when an endpoint must stop providing
     * remote transport while its identity, its credential binding and the lane's edge INTENT all deliberately survive
     * (credential suspend/revoke — see {@link QuantumCredentialService}).
     * <p/>
     * O(edges of the lane), which for a Mk I PAIR is at most 1. Never iterates other lanes, other profiles, or the grid
     * at large.
     *
     * @return the number of connections actually destroyed.
     */
    public static int dematerializeEndpoint(QuantumTopologyRegistry topology, QuantumEndpointOnlineTable table,
            QuantumEndpointId endpointId) {
        var record = topology.getEndpoint(endpointId);
        if (record == null) {
            return 0;
        }
        var lane = topology.getLane(record.laneId());
        if (lane == null) {
            return 0;
        }
        var destroyed = 0;
        for (var edge : lane.getEdges()) {
            if (!edge.a().equals(endpointId) && !edge.b().equals(endpointId)) {
                continue;
            }
            if (dematerializeEdge(table, edge.a(), edge.b())) {
                destroyed++;
            }
        }
        return destroyed;
    }

    /**
     * Re-runs the UNMODIFIED trusted-internal reconciliation for {@code laneId}: asks
     * {@link QuantumLaneReconciler#reconstruct} to materialize the lane's already-persisted exact intent, with
     * suspended/revoked endpoints filtered out by {@link QuantumCredentialService#filterByCredentialState}.
     * <p/>
     * This is the ONE materialization entry point Round 11 uses everywhere — endpoint load, authorized route create,
     * authorized route repair, and credential resume all funnel through it, so there is exactly one behaviour to reason
     * about. It creates no new topology policy, chooses no alternative path, and is idempotent: an already-materialized
     * edge is skipped by the reconciler's own {@code isAlreadyConnected} check, so calling this a hundred times can
     * never produce a duplicate {@link IGridConnection}.
     *
     * @return the connections newly created by this call (empty if everything intended was already realized).
     */
    public static List<IGridConnection> materializeLane(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, QuantumEndpointOnlineTable table,
            appeng.api.networking.quantum.QuantumLaneId laneId) {
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return List.of();
        }
        var filtered = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        return QuantumLaneReconciler.reconstruct(lane, filtered);
    }
}
