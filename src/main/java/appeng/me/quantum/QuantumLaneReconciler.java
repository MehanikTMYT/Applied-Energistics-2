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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.QuantumEndpointId;

/**
 * Turns a {@link QuantumLane}'s persisted {@link QuantumLane.Edge} list back into live
 * {@link appeng.api.networking.IGridConnection}s, using the exact same production primitive
 * ({@link GridHelper#createConnection}) the real {@code QuantumCluster} and the Q1/Q2 prototypes already use — this
 * class adds no new grid-formation mechanism, it only decides <em>which pairs</em> to call it for.
 * <p/>
 * <b>Deterministic, load-order-independent, O(N).</b> {@link #reconstruct} is a single pass over the lane's edge list
 * (O(N) for a tree on N endpoints — never a pair matrix); for each edge it only attempts a connection if <em>both</em>
 * endpoints already have a live {@link IGridNode} in {@code onlineNodes}. This means:
 * <ul>
 * <li>calling it repeatedly, once per endpoint that comes online (in ANY order — see
 * {@code QuantumTopologyIdentityQ3Test}'s load-order matrix), converges to the exact same final edge set regardless of
 * which endpoint happened to load first;</li>
 * <li>partial availability degrades exactly as intended: if the relay endpoint of a chain is offline, neither of its
 * two edges is attempted, and — critically — no alternate edge between the two endpoints that WOULD have been relayed
 * through it is ever attempted either, because that pair was simply never a persisted edge in the first place (see
 * {@link QuantumLane}'s own javadoc on why edges, not a derivation rule, are persisted);</li>
 * <li>it is safe to call again after some edges already exist: {@link #isAlreadyConnected} checks each endpoint pair's
 * live connections first, so a repeated call never attempts a duplicate {@code GridConnection} for an edge that
 * reconstruction (or ordinary in-world play) already satisfied — {@code GridConnection.create} throws
 * {@code IllegalStateException} for an already-connected pair, so this check is not optional.</li>
 * </ul>
 * This class is intentionally stateless and holds no persisted data of its own — {@code onlineNodes} is supplied by the
 * caller for each call (see {@link QuantumEndpointOnlineTable} for a ready-made, conflict-safe way to build that map
 * from live nodes as they come online).
 */
public final class QuantumLaneReconciler {
    private QuantumLaneReconciler() {
    }

    /**
     * Attempts to create every persisted edge of {@code lane} whose both endpoints currently have a live node in
     * {@code onlineNodes} and are not already connected to each other.
     *
     * @return the connections newly created by this call (never includes edges that were already connected, or that
     *         could not be attempted because one/both endpoints are not currently online, or that were rejected by
     *         {@code GridConnection}'s own cross-security-domain pre-merge guard).
     */
    public static List<IGridConnection> reconstruct(QuantumLane lane, Map<QuantumEndpointId, IGridNode> onlineNodes) {
        var created = new ArrayList<IGridConnection>();
        for (var edge : lane.getEdges()) {
            var nodeA = onlineNodes.get(edge.a());
            var nodeB = onlineNodes.get(edge.b());
            if (nodeA == null || nodeB == null) {
                // Partial availability: this edge simply waits for both ends to be online. No substitute edge is
                // ever attempted - see class javadoc.
                continue;
            }
            if (isAlreadyConnected(nodeA, nodeB)) {
                continue;
            }
            var connection = GridHelper.createConnection(nodeA, nodeB);
            if (connection != null) {
                created.add(connection);
            }
            // A null result means GridConnection's own security-domain pre-merge guard rejected it - the existing,
            // unmodified second-line defense (see QuantumTopologyRegistry's class javadoc). Nothing further to do
            // here; the edge stays unrealized until that underlying incompatibility is resolved by a human.
        }
        return created;
    }

    private static boolean isAlreadyConnected(IGridNode a, IGridNode b) {
        for (var connection : a.getConnections()) {
            if (connection.getOtherSide(a) == b) {
                return true;
            }
        }
        return false;
    }
}
