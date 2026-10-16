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

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialState;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumLaneId;
import appeng.api.networking.quantum.QuantumRouteReason;
import appeng.api.networking.quantum.QuantumRouteState;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.SecurityPermissions;
import appeng.me.security.SecurityDomainRegistry;

/**
 * <b>Round 11 ("Routing Controller foundation"): the single actor-aware authorization choke point for quantum ROUTE
 * operations</b>, and the only place operational route state is derived.
 * <p/>
 * It stands to routing exactly as {@link QuantumCredentialService} stands to credentials: no menu, no screen, no block
 * entity and no future command re-implements a permission check or edits topology directly. A
 * {@code QuantumRoutingControllerScreen} button press travels Screen → Menu → BlockEntity → <b>this class</b> →
 * registry; the first three of those are pure plumbing with no authority whatsoever, and a client that lies about which
 * button was enabled changes nothing, because every check happens here, server-side, from scratch, on every call.
 * <p/>
 * <b>What this class is NOT.</b> It owns no state and persists nothing — no route registry, no route table, no status
 * cache. It is not an authority over identity ({@link NetworkProfileId}/{@link QuantumLaneId}/{@link QuantumEndpointId}
 * all remain owned by {@link QuantumTopologyRegistry}), over credentials (owned by
 * {@link QuantumCredentialRegistry}/{@link QuantumCredentialService}), or over security (owned by
 * {@link SecurityDomainRegistry}). Topology INTENT continues to live in exactly one place —
 * {@link QuantumTopologyRegistry}'s persisted exact edges — so a "controller says A↔B while the registry says A↔C"
 * split-brain is structurally impossible: there is only one edge store to disagree with.
 * <p/>
 * <b>Desired vs. materialized is the whole point.</b> {@link #computePairStatus} deliberately never treats "a persisted
 * edge exists" as "the route is physically up". It asks the registry what is INTENDED, the
 * {@link QuantumEndpointOnlineTable} what is currently LOADED, {@link QuantumCredentialRegistry} what is currently
 * PERMITTED, and {@link QuantumRouteMaterializer} what is actually REALIZED, then reports the difference as a derived
 * {@link QuantumRouteState} + {@link QuantumRouteReason} pair that is never written back anywhere.
 * <p/>
 * <b>PAIR-ONLY.</b> Round 11 manages exactly the already-proven Mk I shape: one lane, exactly two endpoints, at most
 * one edge. A lane that is not that shape is reported as {@link QuantumRouteReason#UNSUPPORTED_IN_ROUND_11} rather than
 * having a topology guessed for it. There is no path planner, no failover, no alternative-edge selection and no
 * HUB/MESH logic here, by design — those need a logical-routing-policy layer that does not exist yet, and inventing an
 * ad-hoc version of it inside a controller is exactly the mistake this separation is meant to prevent.
 */
public final class QuantumRoutingService {
    private QuantumRoutingService() {
    }

    /**
     * Outcome of any operation attempted through this class. {@link #OK} is the only success; every other value is a
     * distinct, deterministic reason — never a bare boolean.
     */
    public enum Result {
        OK,
        /** The named profile, lane, endpoint, or (for a delete) the route intent itself does not exist. */
        NOT_FOUND,
        /** The actor lacks the required permission on the profile's bound security domain. */
        FORBIDDEN,
        /** No single {@link NetworkProfileId} could be resolved — see {@link QuantumRouteReason#CONTROLLER_UNBOUND}. */
        UNBOUND,
        /** The named lane does not belong to the controller's own bound profile. */
        WRONG_PROFILE,
        /** The two endpoints do not both belong to the named lane. */
        WRONG_LANE,
        /** An endpoint record is missing or permanently retired (tombstoned). */
        INVALID_ENDPOINT,
        /** The intended edge already exists — create is idempotent and reports this rather than duplicating it. */
        ALREADY_EXISTS,
        /** The lane is not a Mk I PAIR; Round 11 refuses to guess a topology for it. */
        UNSUPPORTED_TOPOLOGY
    }

    /**
     * A derived, read-only snapshot of one PAIR's operational state. Carries only short display identity plus dimension
     * — deliberately <b>never</b> an exact remote {@code BlockPos}; see {@link #viewPairStatus} for why that decision
     * is explicitly deferred rather than silently made here.
     */
    public record PairStatus(
            QuantumLaneId laneId,
            @Nullable QuantumEndpointId a,
            @Nullable QuantumEndpointId b,
            boolean aOnline,
            boolean bOnline,
            @Nullable ResourceKey<Level> aDimension,
            @Nullable ResourceKey<Level> bDimension,
            boolean intentExists,
            boolean materialized,
            QuantumRouteState state,
            QuantumRouteReason reason) {
    }

    public record StatusOutcome(Result result, @Nullable PairStatus status) {
        public static StatusOutcome failure(Result result) {
            return new StatusOutcome(result, null);
        }
    }

    /** One endpoint in a derived multipoint lane snapshot. Exact BlockPos is deliberately absent. */
    public record EndpointView(QuantumEndpointId endpointId, boolean online,
            @Nullable ResourceKey<Level> dimension) {
    }

    /** One exact intended edge and its independently-derived runtime state. */
    public record EdgeView(QuantumEndpointId a, QuantumEndpointId b, boolean materialized,
            QuantumRouteState state, QuantumRouteReason reason) {
    }

    /** Runtime-only projection of an arbitrary sparse lane; persists nothing and owns no topology. */
    public record LaneStatus(QuantumLaneId laneId, List<EndpointView> endpoints, List<EdgeView> edges,
            int materializedEdgeCount, QuantumRouteState state, QuantumRouteReason reason) {
        public int intendedEdgeCount() {
            return edges.size();
        }
    }

    public record LaneStatusOutcome(Result result, @Nullable LaneStatus status) {
        public static LaneStatusOutcome failure(Result result) {
            return new LaneStatusOutcome(result, null);
        }
    }

    // =====================================================================
    // READ — QUANTUM_VIEW.
    // =====================================================================

    /**
     * Lists the lanes of {@code profileId}, and only that profile. O(lanes of this profile) on demand, computed fresh
     * every time — there is deliberately no cached duplicate list kept around for the UI's convenience, because a
     * cached copy of an authoritative list is exactly how a second source of truth gets born.
     * <p/>
     * This is the closest thing Round 11 has to enumeration, and it is intentionally bounded to the ONE profile the
     * caller is already bound to. There is no "list every profile on the server" operation anywhere in this class.
     */
    public static List<QuantumLaneId> listProfileLanes(QuantumTopologyRegistry topology, NetworkProfileId profileId) {
        var result = new ArrayList<QuantumLaneId>();
        for (var lane : topology.getAllLanes()) {
            if (lane.profileId().equals(profileId)) {
                result.add(lane.id());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Read-only permission probe: does {@code actor} hold {@code permission} on {@code profileId}'s bound domain?
     * <p/>
     * Exists so a menu can grey out a button without either (a) growing its own copy of ACL resolution, or (b) calling
     * a mutation just to find out whether it would have been allowed. It performs no mutation and grants nothing: the
     * real check still runs inside every operation below, on every call, so a client that ignores this answer gains
     * exactly nothing.
     */
    public static boolean hasPermission(QuantumTopologyRegistry topology, @Nullable SecurityDomainRegistry security,
            @Nullable NetworkProfileId profileId, SecurityPermissions permission, IActionSource actor) {
        if (profileId == null) {
            return false;
        }
        var profile = topology.getProfile(profileId);
        if (profile == null) {
            return false;
        }
        return QuantumCredentialService.hasProfilePermission(profile, actor, permission, security);
    }

    /**
     * Actor-gated read of a lane's operational status: requires {@link SecurityPermissions#QUANTUM_VIEW} on the
     * profile's bound domain (LEGACY-open and trusted-internal semantics inherited unchanged from
     * {@link QuantumCredentialService#hasProfilePermission}, the single shared implementation).
     * <p/>
     * <b>Coordinate privacy.</b> The returned {@link PairStatus} carries each endpoint's dimension but never its exact
     * {@code BlockPos}, even though {@link QuantumEndpointLocation} has one and internal code may use it freely. Round
     * 11 does not decide that {@code QUANTUM_VIEW} means "may see the precise coordinates of the whole infrastructure"
     * — that is a distinct authorization question which deserves its own explicit decision, so this round deliberately
     * declines to widen the exposed surface by default.
     */
    public static StatusOutcome viewPairStatus(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumEndpointOnlineTable table, @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId,
            IActionSource actor) {
        if (boundProfileId == null) {
            return StatusOutcome.failure(Result.UNBOUND);
        }
        var profile = topology.getProfile(boundProfileId);
        if (profile == null) {
            return StatusOutcome.failure(Result.NOT_FOUND);
        }
        if (!QuantumCredentialService.hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_VIEW,
                security)) {
            return StatusOutcome.failure(Result.FORBIDDEN);
        }
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return StatusOutcome.failure(Result.NOT_FOUND);
        }
        if (!lane.profileId().equals(boundProfileId)) {
            // A lane of some other profile is not merely unauthorized to act on - it is not visible at all.
            return StatusOutcome.failure(Result.WRONG_PROFILE);
        }
        return new StatusOutcome(Result.OK, computePairStatus(topology, credentials, table, laneId));
    }

    /** Actor-gated multipoint status projection for one exact sparse lane. */
    public static LaneStatusOutcome viewLaneStatus(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumEndpointOnlineTable table, @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId,
            IActionSource actor) {
        if (boundProfileId == null) {
            return LaneStatusOutcome.failure(Result.UNBOUND);
        }
        var profile = topology.getProfile(boundProfileId);
        if (profile == null) {
            return LaneStatusOutcome.failure(Result.NOT_FOUND);
        }
        if (!QuantumCredentialService.hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_VIEW,
                security)) {
            return LaneStatusOutcome.failure(Result.FORBIDDEN);
        }
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return LaneStatusOutcome.failure(Result.NOT_FOUND);
        }
        if (!lane.profileId().equals(boundProfileId)) {
            return LaneStatusOutcome.failure(Result.WRONG_PROFILE);
        }
        return new LaneStatusOutcome(Result.OK, computeLaneStatus(topology, credentials, table, laneId));
    }

    public static LaneStatus computeLaneStatus(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, QuantumEndpointOnlineTable table, QuantumLaneId laneId) {
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return new LaneStatus(laneId, List.of(), List.of(), 0, QuantumRouteState.INACTIVE,
                    QuantumRouteReason.NO_INTENT);
        }
        var endpointViews = new ArrayList<EndpointView>();
        for (var endpointId : lane.getEndpoints()) {
            endpointViews.add(new EndpointView(endpointId, isOnline(table, endpointId),
                    dimensionOf(topology, endpointId)));
        }
        var edgeViews = new ArrayList<EdgeView>();
        var materializedCount = 0;
        for (var edge : lane.getEdges()) {
            var materialized = QuantumRouteMaterializer.isMaterialized(table, edge.a(), edge.b());
            if (materialized) {
                materializedCount++;
            }
            var edgeState = QuantumRouteState.DEGRADED;
            var edgeReason = QuantumRouteReason.CONNECTION_MISSING;
            var credentialBlock = credentialBlockReason(credentials, edge.a(), edge.b());
            if (credentialBlock != null) {
                edgeState = QuantumRouteState.BLOCKED;
                edgeReason = credentialBlock;
            } else if (table.isConflicted(edge.a()) || table.isConflicted(edge.b())) {
                edgeState = QuantumRouteState.BLOCKED;
                edgeReason = QuantumRouteReason.ENDPOINT_CONFLICT;
            } else if (!isOnline(table, edge.a()) || !isOnline(table, edge.b())) {
                edgeReason = QuantumRouteReason.PEER_MISSING;
            } else if (materialized) {
                edgeState = QuantumRouteState.ACTIVE;
                edgeReason = QuantumRouteReason.NONE;
            } else if (!securityDomainsLookCompatible(table, edge.a(), edge.b())) {
                edgeState = QuantumRouteState.BLOCKED;
                edgeReason = QuantumRouteReason.SECURITY_DOMAIN_MISMATCH;
            }
            edgeViews.add(new EdgeView(edge.a(), edge.b(), materialized, edgeState, edgeReason));
        }

        QuantumRouteState laneState;
        QuantumRouteReason laneReason;
        if (edgeViews.isEmpty()) {
            laneState = QuantumRouteState.INACTIVE;
            laneReason = QuantumRouteReason.NO_INTENT;
        } else if (materializedCount == edgeViews.size()) {
            laneState = QuantumRouteState.ACTIVE;
            laneReason = QuantumRouteReason.NONE;
        } else if (materializedCount > 0) {
            laneState = QuantumRouteState.DEGRADED;
            laneReason = edgeViews.stream().filter(e -> !e.materialized()).findFirst().orElseThrow().reason();
        } else {
            var blocked = edgeViews.stream().filter(e -> e.state() == QuantumRouteState.BLOCKED).findFirst();
            laneState = blocked.isPresent() ? QuantumRouteState.BLOCKED : QuantumRouteState.DEGRADED;
            laneReason = blocked.map(EdgeView::reason).orElse(edgeViews.getFirst().reason());
        }
        return new LaneStatus(laneId, List.copyOf(endpointViews), List.copyOf(edgeViews), materializedCount,
                laneState, laneReason);
    }

    /**
     * Trusted-internal, un-gated derivation of a lane's operational state. Never exposed directly to a player-facing
     * caller — {@link #viewPairStatus} is the actor-aware door. Pure function of (intent, online table, credential
     * state, live connections); mutates nothing and persists nothing.
     * <p/>
     * The ordering of the checks below IS the semantic model: identity/topology validity first, then loaded-ness, then
     * credential authority, then physical realization. Security/credential blocks always win over "just not connected",
     * so a revoked route can never be reported as a merely-degraded one.
     */
    public static PairStatus computePairStatus(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, QuantumEndpointOnlineTable table, QuantumLaneId laneId) {
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return new PairStatus(laneId, null, null, false, false, null, null, false, false,
                    QuantumRouteState.INACTIVE, QuantumRouteReason.NO_INTENT);
        }
        var endpoints = new ArrayList<>(lane.getEndpoints());
        if (endpoints.size() != 2) {
            var only = endpoints.size() == 1 ? endpoints.get(0) : null;
            return new PairStatus(laneId, only, null, only != null && isOnline(table, only), false,
                    dimensionOf(topology, only), null, !lane.getEdges().isEmpty(), false,
                    QuantumRouteState.INACTIVE, QuantumRouteReason.UNSUPPORTED_IN_ROUND_11);
        }
        var a = endpoints.get(0);
        var b = endpoints.get(1);
        var intentExists = lane.hasEdge(a, b);
        var aOnline = isOnline(table, a);
        var bOnline = isOnline(table, b);
        var dimA = dimensionOf(topology, a);
        var dimB = dimensionOf(topology, b);
        var materialized = QuantumRouteMaterializer.isMaterialized(table, a, b);

        var state = QuantumRouteState.INACTIVE;
        var reason = QuantumRouteReason.NO_INTENT;

        if (intentExists) {
            var credentialBlock = credentialBlockReason(credentials, a, b);
            if (credentialBlock != null) {
                state = QuantumRouteState.BLOCKED;
                reason = credentialBlock;
            } else if (table.isConflicted(a) || table.isConflicted(b)) {
                state = QuantumRouteState.BLOCKED;
                reason = QuantumRouteReason.ENDPOINT_CONFLICT;
            } else if (!aOnline && !bOnline) {
                state = QuantumRouteState.DEGRADED;
                reason = QuantumRouteReason.ENDPOINT_UNLOADED;
            } else if (!aOnline || !bOnline) {
                state = QuantumRouteState.DEGRADED;
                reason = QuantumRouteReason.PEER_MISSING;
            } else if (materialized) {
                state = QuantumRouteState.ACTIVE;
                reason = QuantumRouteReason.NONE;
            } else if (!securityDomainsLookCompatible(table, a, b)) {
                state = QuantumRouteState.BLOCKED;
                reason = QuantumRouteReason.SECURITY_DOMAIN_MISMATCH;
            } else {
                state = QuantumRouteState.DEGRADED;
                reason = QuantumRouteReason.CONNECTION_MISSING;
            }
        }

        return new PairStatus(laneId, a, b, aOnline, bOnline, dimA, dimB, intentExists, materialized, state, reason);
    }

    // =====================================================================
    // MUTATION — QUANTUM_ROUTE.
    // =====================================================================

    /**
     * Creates the ONE intended edge of a Mk I PAIR lane, after validating — in this order — bound profile, actor
     * permission, lane existence, lane↔profile ownership, PAIR shape, and both endpoint records.
     * <p/>
     * <b>An offline peer does not block intent creation.</b> If every identity/structural check passes, the persisted
     * edge is written even when one or both endpoints are currently unloaded; materialization is simply deferred to the
     * existing trusted-internal reconciliation that runs when they come back. This is the clearest demonstration of the
     * round's central distinction: desired topology is a durable decision, realized topology is a transient consequence
     * of it.
     * <p/>
     * What is NOT allowed to create intent, ever: a wrong profile, a wrong lane, a missing/retired endpoint, or a
     * non-PAIR lane. And a cross-domain physical incompatibility can at most leave the route BLOCKED — it never causes
     * a merge, because this method does not form connections itself; it delegates to the unmodified reconciler, which
     * remains subject to {@code GridConnection}'s own pre-merge guard.
     */
    static Result createPairRoute(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumEndpointOnlineTable table, @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId,
            IActionSource actor) {
        var validation = validateRouteMutation(topology, security, boundProfileId, laneId, actor);
        if (validation.result() != Result.OK) {
            return validation.result();
        }
        var lane = validation.lane();
        if (lane.hasEdge(validation.a(), validation.b())) {
            return Result.ALREADY_EXISTS;
        }
        if (!topology.addEdge(laneId, validation.a(), validation.b())) {
            // The registry's own first-line structural guard refused (cross-lane/self-loop) - never worked around.
            return Result.WRONG_LANE;
        }
        QuantumRouteMaterializer.materializeLane(topology, credentials, table, laneId);
        return Result.OK;
    }

    /**
     * Deletes the exact intended edge and, if it is currently realized, destroys exactly the one connection realizing
     * it — see {@link QuantumRouteMaterializer} for why that match is exact rather than heuristic, and can never take
     * down an unrelated physical connection.
     * <p/>
     * Intent is removed FIRST and unconditionally, so the operation is correct even when the peer is offline and there
     * is nothing to dematerialize: the route is gone from persisted intent, therefore no later reload or restart can
     * resurrect it. Deleting an edge that does not exist returns {@link Result#NOT_FOUND} rather than pretending to
     * succeed. Endpoint identity, credentials and lane membership are never touched.
     */
    static Result removePairRoute(QuantumTopologyRegistry topology,
            @Nullable SecurityDomainRegistry security, QuantumEndpointOnlineTable table,
            @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId, IActionSource actor) {
        var validation = validateRouteMutation(topology, security, boundProfileId, laneId, actor);
        if (validation.result() != Result.OK) {
            return validation.result();
        }
        if (!topology.removeEdge(laneId, validation.a(), validation.b())) {
            return Result.NOT_FOUND;
        }
        QuantumRouteMaterializer.dematerializeEdge(table, validation.a(), validation.b());
        return Result.OK;
    }

    /**
     * Asks the existing trusted reconciler to materialize the ALREADY-persisted exact intent once more.
     * <p/>
     * Repair deliberately has no policy of its own: it creates no new intent, selects no alternative path, performs no
     * failover, and rewrites no edges. It exists solely for the "intent exists, connection does not" case — e.g. after
     * a transient failure — and is fully idempotent, because the reconciler skips edges that are already connected. A
     * hundred consecutive repairs of a healthy pair produce zero additional connections.
     */
    public static Result repairPairRoute(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumEndpointOnlineTable table, @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId,
            IActionSource actor) {
        var validation = validateRouteMutation(topology, security, boundProfileId, laneId, actor);
        if (validation.result() != Result.OK) {
            return validation.result();
        }
        if (!validation.lane().hasEdge(validation.a(), validation.b())) {
            return Result.NOT_FOUND;
        }
        QuantumRouteMaterializer.materializeLane(topology, credentials, table, laneId);
        return Result.OK;
    }

    /** Repairs every already-persisted exact edge of an arbitrary sparse lane; creates no intent. */
    public static Result repairLane(QuantumTopologyRegistry topology,
            @Nullable QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumEndpointOnlineTable table, @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId,
            IActionSource actor) {
        if (boundProfileId == null) {
            return Result.UNBOUND;
        }
        var profile = topology.getProfile(boundProfileId);
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        if (!QuantumCredentialService.hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_ROUTE,
                security)) {
            return Result.FORBIDDEN;
        }
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return Result.NOT_FOUND;
        }
        if (!lane.profileId().equals(boundProfileId)) {
            return Result.WRONG_PROFILE;
        }
        if (lane.getEdges().isEmpty()) {
            return Result.NOT_FOUND;
        }
        QuantumRouteMaterializer.materializeLane(topology, credentials, table, laneId);
        return Result.OK;
    }

    // =====================================================================
    // Shared validation — one implementation for all three mutations.
    // =====================================================================

    private record Validation(Result result, @Nullable QuantumLane lane, @Nullable QuantumEndpointId a,
            @Nullable QuantumEndpointId b) {
        static Validation failure(Result result) {
            return new Validation(result, null, null, null);
        }
    }

    /**
     * The identical, ordered gate every {@code QUANTUM_ROUTE} mutation passes through: bound profile → actor permission
     * → lane exists → lane belongs to THIS profile → lane is a Mk I PAIR → both endpoint records are valid and not
     * retired. Having exactly one implementation is the point; three copies would be three chances to drift.
     */
    private static Validation validateRouteMutation(QuantumTopologyRegistry topology,
            @Nullable SecurityDomainRegistry security, @Nullable NetworkProfileId boundProfileId, QuantumLaneId laneId,
            IActionSource actor) {
        if (boundProfileId == null) {
            return Validation.failure(Result.UNBOUND);
        }
        var profile = topology.getProfile(boundProfileId);
        if (profile == null) {
            return Validation.failure(Result.NOT_FOUND);
        }
        if (!QuantumCredentialService.hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_ROUTE,
                security)) {
            return Validation.failure(Result.FORBIDDEN);
        }
        var lane = topology.getLane(laneId);
        if (lane == null) {
            return Validation.failure(Result.NOT_FOUND);
        }
        if (!lane.profileId().equals(boundProfileId)) {
            return Validation.failure(Result.WRONG_PROFILE);
        }
        var endpoints = new ArrayList<>(lane.getEndpoints());
        if (endpoints.size() != 2) {
            return Validation.failure(Result.UNSUPPORTED_TOPOLOGY);
        }
        var a = endpoints.get(0);
        var b = endpoints.get(1);
        var recordA = topology.getEndpoint(a);
        var recordB = topology.getEndpoint(b);
        if (recordA == null || recordB == null) {
            return Validation.failure(Result.INVALID_ENDPOINT);
        }
        if (recordA.status() == appeng.api.networking.quantum.QuantumEndpointStatus.REMOVED
                || recordB.status() == appeng.api.networking.quantum.QuantumEndpointStatus.REMOVED) {
            return Validation.failure(Result.INVALID_ENDPOINT);
        }
        if (!recordA.laneId().equals(laneId) || !recordB.laneId().equals(laneId)) {
            return Validation.failure(Result.WRONG_LANE);
        }
        return new Validation(Result.OK, lane, a, b);
    }

    // =====================================================================
    // Derivation helpers.
    // =====================================================================

    private static boolean isOnline(QuantumEndpointOnlineTable table, QuantumEndpointId id) {
        return table.onlineNodes().containsKey(id);
    }

    @Nullable
    private static ResourceKey<Level> dimensionOf(QuantumTopologyRegistry topology, @Nullable QuantumEndpointId id) {
        if (id == null) {
            return null;
        }
        var record = topology.getEndpoint(id);
        if (record == null) {
            return null;
        }
        return record.location().map(appeng.api.networking.quantum.QuantumEndpointLocation::dimension).orElse(null);
    }

    /**
     * @return the blocking credential reason, or {@code null} if neither endpoint's credential forbids an active route.
     *         REVOKED is reported in preference to SUSPENDED, being the terminal condition.
     */
    @Nullable
    private static QuantumRouteReason credentialBlockReason(@Nullable QuantumCredentialRegistry credentials,
            QuantumEndpointId a, QuantumEndpointId b) {
        if (credentials == null) {
            return null;
        }
        var stateA = credentialStateOf(credentials, a);
        var stateB = credentialStateOf(credentials, b);
        if (stateA == QuantumCredentialState.REVOKED || stateB == QuantumCredentialState.REVOKED) {
            return QuantumRouteReason.CREDENTIAL_REVOKED;
        }
        if (stateA == QuantumCredentialState.SUSPENDED || stateB == QuantumCredentialState.SUSPENDED) {
            return QuantumRouteReason.CREDENTIAL_SUSPENDED;
        }
        return null;
    }

    @Nullable
    private static QuantumCredentialState credentialStateOf(QuantumCredentialRegistry credentials,
            QuantumEndpointId id) {
        var credential = credentials.getCredentialForEndpoint(id);
        return credential == null ? null : credential.state();
    }

    /**
     * A READ-ONLY observation of whether {@code GridConnection}'s own cross-security-domain pre-merge guard would
     * currently refuse to merge these two endpoints' grids — used purely to turn "both online but not connected" into a
     * specific, actionable {@link QuantumRouteReason#SECURITY_DOMAIN_MISMATCH} for the player, instead of a vague
     * "connection missing".
     * <p/>
     * <b>This is diagnosis, never enforcement.</b> The actual, unmodified enforcement remains entirely inside
     * {@code GridConnection.create}, which this class never bypasses and never reimplements: even if this observation
     * were somehow wrong, no merge could occur, because the only way this service ever forms a connection is by
     * delegating to the reconciler, which delegates to {@code GridHelper.createConnection}, which is gated by that same
     * guard. It mirrors the guard's own conditions (CONFLICT state, or two different non-empty domains) so the reported
     * reason stays truthful.
     */
    private static boolean securityDomainsLookCompatible(QuantumEndpointOnlineTable table, QuantumEndpointId a,
            QuantumEndpointId b) {
        var nodes = table.onlineNodes();
        var nodeA = nodes.get(a);
        var nodeB = nodes.get(b);
        if (nodeA == null || nodeB == null) {
            return true;
        }
        var gridA = nodeA.getGrid();
        var gridB = nodeB.getGrid();
        if (gridA == null || gridB == null || gridA == gridB) {
            return true;
        }
        var securityA = gridA.getService(appeng.api.networking.security.ISecurityService.class);
        var securityB = gridB.getService(appeng.api.networking.security.ISecurityService.class);
        if (securityA.getState() == appeng.api.networking.security.SecurityDomainState.CONFLICT
                || securityB.getState() == appeng.api.networking.security.SecurityDomainState.CONFLICT) {
            return false;
        }
        var domainA = securityA.getDomainId();
        var domainB = securityB.getDomainId();
        return domainA.isEmpty() || domainB.isEmpty() || domainA.equals(domainB);
    }
}
