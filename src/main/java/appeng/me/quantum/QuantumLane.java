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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumLaneId;

/**
 * The persistent record of one Quantum Lane: which {@link QuantumEndpointId}s belong to it, and the exact set of
 * intended edges between them.
 * <p/>
 * <b>Reconstruction strategy — persist exact minimal-tree edges (approach "A" of the two considered).</b> The
 * alternative ("B": persist only endpoint membership plus a deterministic derivation rule, e.g. "sort by registration
 * time and chain them") was rejected: it only ever produces a chain, silently forecloses any other intended tree shape
 * (e.g. a future star/hub arrangement), and re-derives the "which N-1 pairs" decision from incidental metadata
 * (registration order) instead of treating it as the actual fact being persisted. Persisting the literal {@link Edge}
 * list instead means:
 * <ul>
 * <li>reconstruction is trivially deterministic — the edges as data ARE the intended topology, not something recomputed
 * from a rule that could drift or be reinterpreted differently after a future code change;</li>
 * <li>it is load-order-independent by construction (see {@link QuantumLaneReconciler}): each edge is attempted
 * independently, whenever both of its endpoints happen to have a live node, regardless of what order endpoints became
 * available in;</li>
 * <li>it stays O(N): a tree on N endpoints has exactly N-1 edges by definition, so the edge list is never a full pair
 * matrix — see {@link #addEdge} which does not create an edge for every endpoint pair, only the ones a caller
 * explicitly names (mirroring exactly how {@code MultipointSingleLaneQ2Test} built its Base-Factory-Mine chain with 2
 * {@code GridConnection}s, never 3).</li>
 * </ul>
 */
public final class QuantumLane {
    public static final int CURRENT_PROTOCOL_VERSION = 1;

    /** One intended, persisted connection between two endpoints of this lane. The order of a/b is not meaningful. */
    public record Edge(QuantumEndpointId a, QuantumEndpointId b) {
        public static final Codec<Edge> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                QuantumEndpointId.CODEC.fieldOf("a").forGetter(Edge::a),
                QuantumEndpointId.CODEC.fieldOf("b").forGetter(Edge::b))
                .apply(builder, Edge::new));

        /** True if this edge connects the same unordered pair of endpoints as {@code (x, y)}. */
        public boolean sameUnorderedPair(QuantumEndpointId x, QuantumEndpointId y) {
            return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
        }
    }

    private final QuantumLaneId id;
    private final NetworkProfileId profileId;
    private final Set<QuantumEndpointId> endpoints = new LinkedHashSet<>();
    private final List<Edge> edges = new ArrayList<>();
    private int protocolVersion = CURRENT_PROTOCOL_VERSION;

    public QuantumLane(QuantumLaneId id, NetworkProfileId profileId) {
        this.id = id;
        this.profileId = profileId;
    }

    public QuantumLaneId id() {
        return id;
    }

    public NetworkProfileId profileId() {
        return profileId;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public Set<QuantumEndpointId> getEndpoints() {
        return Set.copyOf(endpoints);
    }

    public List<Edge> getEdges() {
        return List.copyOf(edges);
    }

    void addEndpoint(QuantumEndpointId endpointId) {
        endpoints.add(endpointId);
    }

    void removeEndpoint(QuantumEndpointId endpointId) {
        endpoints.remove(endpointId);
        edges.removeIf(e -> e.a().equals(endpointId) || e.b().equals(endpointId));
    }

    public boolean hasEdge(QuantumEndpointId a, QuantumEndpointId b) {
        for (var e : edges) {
            if (e.sameUnorderedPair(a, b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the given edge if it is not already present (idempotent — calling this twice for the same unordered pair
     * never creates a duplicate/parallel edge). Both endpoints are implicitly added to this lane's membership set.
     */
    void addEdge(QuantumEndpointId a, QuantumEndpointId b) {
        if (!hasEdge(a, b)) {
            edges.add(new Edge(a, b));
        }
        endpoints.add(a);
        endpoints.add(b);
    }

    /**
     * Round 11 ("Routing Controller foundation"): removes exactly the one intended edge between {@code a} and
     * {@code b}, and nothing else — the precise inverse of {@link #addEdge}.
     * <p/>
     * Deliberately distinct from {@link #removeEndpoint}, which is the blunt cascade used when an endpoint identity
     * itself goes away (it drops the endpoint from this lane's membership AND every edge touching it). This one never
     * touches lane membership: both endpoints keep belonging to this lane, keep their identities, and can have the
     * exact same edge re-created later by an authorized Routing Controller operation. That distinction is what lets
     * "delete this route" mean "delete this route" rather than "retire these endpoints".
     *
     * @return {@code true} if an edge for that unordered pair existed and was removed.
     */
    boolean removeEdge(QuantumEndpointId a, QuantumEndpointId b) {
        return edges.removeIf(e -> e.sameUnorderedPair(a, b));
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private record Data(int version, UUID id, UUID profileId, List<UUID> endpoints, List<Edge> edges,
            int protocolVersion) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.INT.fieldOf("version").forGetter(Data::version),
                UUIDUtil.CODEC.fieldOf("id").forGetter(Data::id),
                UUIDUtil.CODEC.fieldOf("profile_id").forGetter(Data::profileId),
                UUIDUtil.CODEC.listOf().optionalFieldOf("endpoints", List.of()).forGetter(Data::endpoints),
                Edge.CODEC.listOf().optionalFieldOf("edges", List.of()).forGetter(Data::edges),
                Codec.INT.fieldOf("protocol_version").forGetter(Data::protocolVersion))
                .apply(builder, Data::new));
    }

    public static final Codec<QuantumLane> CODEC = Data.CODEC.xmap(QuantumLane::upgrade, QuantumLane::toData);

    private static QuantumLane upgrade(Data data) {
        var lane = new QuantumLane(new QuantumLaneId(data.id()), new NetworkProfileId(data.profileId()));
        for (var e : data.endpoints()) {
            lane.endpoints.add(new QuantumEndpointId(e));
        }
        lane.edges.addAll(data.edges());
        lane.protocolVersion = data.protocolVersion();
        return lane;
    }

    private static Data toData(QuantumLane lane) {
        return new Data(CURRENT_PROTOCOL_VERSION, lane.id.id(), lane.profileId.id(),
                lane.endpoints.stream().map(QuantumEndpointId::id).toList(), List.copyOf(lane.edges),
                lane.protocolVersion);
    }
}
