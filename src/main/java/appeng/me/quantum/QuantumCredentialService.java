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
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumCredentialState;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityPermissions;
import appeng.me.security.SecurityDomainRegistry;

/**
 * <b>The single actor-aware authorization + cross-registry orchestration choke point for Quantum Q4.</b> Every
 * player-facing credential/topology mutation (issue, revoke, suspend, resume, rebind, physical endpoint activation)
 * goes through exactly one of this class's static methods — nowhere else (not the Imprinter's menu, not its screen, not
 * any future command) re-implements or duplicates a permission check. This mirrors the "server-side source of truth"
 * shape already established by {@code SecurityGroupCommand} and {@code SecurityStationMenu} (both resolve a domain,
 * check a permission, THEN mutate — never the other way around), and directly closes the Round 5 DEFERRED SECURITY
 * HARDENING item "actor-aware BUILD authorization for explicit network/lane creation": before this class existed,
 * {@link QuantumTopologyRegistry#addEdge}/{@code createLane}/{@code createProfile} had no actor-aware gate at all (see
 * the Phase E archaeology in the session report for the exact citation).
 * <p/>
 * <b>Neither {@link QuantumTopologyRegistry} nor {@link QuantumCredentialRegistry} perform any ACL check themselves</b>
 * — both remain structural-only primitives (see their own class javadoc). This class is where "Profile exists;
 * credential state valid; revision matches; actor has permission; endpoint identity valid; lane/profile compatible" all
 * get checked, in that order, before either registry is ever mutated — see {@link #activate} for the exact ordering the
 * Q4 task manifest requires.
 * <p/>
 * <b>Deliberately does NOT touch {@link QuantumLaneReconciler}</b> (restart reconstruction stays TRUSTED INTERNAL,
 * unauthenticated, exactly as Q3 left it) or {@code GridConnection}'s own cross-security-domain pre-merge guard (reused
 * completely unmodified as the physical-layer second line of defense — see {@link #activate}'s javadoc). This class's
 * job ends at the registry/data layer; {@link #filterByCredentialState} is the one bridge it offers into the live
 * {@link IGridNode} reconciliation path, and even that is a pure state filter, not an authorization check (see its own
 * javadoc for why).
 */
public final class QuantumCredentialService {
    private QuantumCredentialService() {
    }

    /**
     * Outcome of any mutation attempted through this class. {@link #OK} is the only success value; every other value is
     * a distinct, deterministic failure reason a caller (menu, command, or test) can react to precisely — never a
     * generic boolean "no".
     */
    public enum Result {
        OK,
        /** The named credential, profile, or endpoint does not exist in the relevant registry. */
        NOT_FOUND,
        /** The actor does not hold the required permission on the credential's profile's bound security domain. */
        FORBIDDEN,
        /** The credential is {@link QuantumCredentialState#REVOKED} — terminal, never recoverable. */
        REVOKED,
        /** The credential is {@link QuantumCredentialState#SUSPENDED} — recoverable via an authorized resume. */
        SUSPENDED,
        /**
         * The credential is already bound to a DIFFERENT endpoint than the one requested — the duplicate/cloned-card
         * safety rejection (see {@link QuantumCredential} class javadoc). Never a silent second endpoint.
         */
        CONFLICT,
        /**
         * The caller-supplied card revision does not match the registry's canonical revision for this credential — the
         * stale-copied-card defense (see {@link QuantumCredentialRegistry#supersede}).
         */
        STALE_REVISION,
        /** The named endpoint's lane does not belong to this credential's profile. */
        WRONG_PROFILE
    }

    public record IssueOutcome(Result result, @Nullable QuantumCredential credential) {
        public static IssueOutcome failure(Result result) {
            return new IssueOutcome(result, null);
        }
    }

    // =====================================================================
    // (1) Credential issuance — SECURITY-tier, QUANTUM_ISSUE.
    // =====================================================================

    /**
     * Issues a brand-new credential for {@code profileId}, if {@code profileId} names an existing profile and
     * {@code actor} holds {@link SecurityPermissions#QUANTUM_ISSUE} on that profile's bound security domain (or the
     * profile has no domain bound at all — LEGACY-open, mirroring {@code SecurityService}'s own LEGACY semantics). The
     * new credential starts {@link QuantumCredentialState#ISSUED}, unbound, at revision 1 — it does not occupy any
     * physical endpoint capacity, create any {@code IGridNode}/{@code IGridConnection}, or count as
     * {@link QuantumCredentialState#ONLINE} until a later {@link #activate} call.
     */
    public static IssueOutcome issue(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, NetworkProfileId profileId, IActionSource actor) {
        var profile = topology.getProfile(profileId);
        if (profile == null) {
            return IssueOutcome.failure(Result.NOT_FOUND);
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_ISSUE, security)) {
            return IssueOutcome.failure(Result.FORBIDDEN);
        }
        return new IssueOutcome(Result.OK, credentials.issue(profileId));
    }

    // =====================================================================
    // (3) Physical endpoint activation — BUILD-tier.
    // =====================================================================

    /**
     * Binds {@code credentialId} to {@code endpointId}, if every one of the following holds, checked in exactly this
     * order (per the Q4 task manifest):
     * <ol>
     * <li>the credential's profile exists in {@code topology};</li>
     * <li>the credential is not {@link QuantumCredentialState#REVOKED}/{@link QuantumCredentialState#SUSPENDED};</li>
     * <li>{@code cardRevision} matches the registry's canonical {@link QuantumCredential#revision()} (the caller —
     * normally an Imprinter/endpoint block reading a physical card's own stored revision — passes what the CARD claims;
     * this is deliberately never trusted on its own, see {@link QuantumCredential} class javadoc);</li>
     * <li>{@code actor} holds {@link SecurityPermissions#BUILD} on the profile's bound domain;</li>
     * <li>{@code endpointId} names an existing endpoint in {@code topology};</li>
     * <li>that endpoint's lane belongs to the SAME profile as the credential (cross-profile rejection —
     * {@link Result#WRONG_PROFILE}).</li>
     * </ol>
     * Binding itself is finally attempted via {@link QuantumCredentialRegistry#bind}, which is what actually enforces
     * the duplicate/cloned-card invariant ({@link Result#CONFLICT} if the credential is already bound to a DIFFERENT
     * endpoint) — this is a REGISTRY-level acceptance, not yet a physical {@code IGridConnection}; the physical-layer
     * cross-security-domain merge guard remains entirely {@code GridConnection}'s own, unmodified, pre-merge check (see
     * {@link #filterByCredentialState} for how a credential's state feeds into whether {@code QuantumLaneReconciler}
     * even attempts a connection for its endpoint at all) — this method never forms or bypasses that guard itself.
     * <p/>
     * On success, the endpoint is also marked online in {@code topology} (kept in sync with the credential's own
     * {@link QuantumCredentialState#ONLINE} transition) — see {@link QuantumTopologyRegistry#setEndpointOnline}.
     */
    public static Result activate(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, int cardRevision,
            QuantumEndpointId endpointId, IActionSource actor) {
        var credential = credentials.get(credentialId);
        if (credential == null) {
            return Result.NOT_FOUND;
        }
        var profile = topology.getProfile(credential.profileId());
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        if (credential.state() == QuantumCredentialState.REVOKED) {
            return Result.REVOKED;
        }
        if (credential.state() == QuantumCredentialState.SUSPENDED) {
            return Result.SUSPENDED;
        }
        if (credential.revision() != cardRevision) {
            return Result.STALE_REVISION;
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.BUILD, security)) {
            return Result.FORBIDDEN;
        }
        var endpoint = topology.getEndpoint(endpointId);
        if (endpoint == null) {
            return Result.NOT_FOUND;
        }
        var lane = topology.getLane(endpoint.laneId());
        if (lane == null || !lane.profileId().equals(credential.profileId())) {
            return Result.WRONG_PROFILE;
        }

        if (!credentials.bind(credentialId, endpointId)) {
            return Result.CONFLICT;
        }
        credentials.markOnline(credentialId);
        topology.setEndpointOnline(endpointId, null);
        return Result.OK;
    }

    /**
     * Outcome of {@link #activateFreshPhysicalEndpoint}: the same {@link Result} vocabulary as {@link #activate}, plus
     * the {@link QuantumEndpointId} that ended up being used — either a brand-new one this call registered, or
     * {@code null} if nothing was ever created (e.g. {@link Result#NOT_FOUND}/{@link Result#REVOKED} before any
     * topology mutation was attempted at all).
     */
    public record PhysicalActivationOutcome(Result result, @Nullable QuantumEndpointId endpointId) {
    }

    /**
     * Round 10 ("Mk I PHYSICAL QUANTUM PAIR"): the sanctioned activation path for a brand-new physical endpoint block
     * that does not have a stable {@link QuantumEndpointId} of its own yet — the ordinary {@link #activate} requires an
     * ALREADY-registered {@code endpointId} to bind to (see its own javadoc), which a freshly-placed physical block
     * simply does not have. This method is the minimum needed to bridge that gap without inventing a second
     * authorization implementation:
     * <ol>
     * <li>every pre-mutation check {@link #activate} itself performs — credential exists, not REVOKED/SUSPENDED,
     * revision matches, actor holds {@link SecurityPermissions#BUILD} on the credential's profile — is repeated here
     * FIRST, in the same order, because unlike {@link #activate} this method is about to CREATE topology state
     * ({@link QuantumTopologyRegistry#createLane}/{@link QuantumTopologyRegistry#registerEndpoint}) before an
     * {@code endpointId} even exists to hand to {@link #activate} — and per this class's own core invariant, no
     * registry mutation (including topology creation, not just credential binding) may happen before authorization is
     * confirmed. This is a structurally-forced re-check, not a second, independently-maintained copy of the business
     * rule: it exists purely to move the SAME ordered checks earlier, and the actual state transition (the bind, the
     * ONLINE marking) is still performed by, and ONLY by, the single unmodified call to {@link #activate} at the end of
     * this method — this method adds no new rule {@link #activate} does not already enforce.</li>
     * <li>find-or-create the credential's profile's Mk I PAIR lane: reuses any of the profile's existing
     * {@link QuantumLane}s that does not already have two endpoints (a completed pair never gains a third), or creates
     * a brand-new lane if every existing lane is already full or none exists yet — never a Routing Controller, never a
     * lane-selection UI, never more than one lane "in progress" per profile at a time from this call's own POV (a
     * profile CAN end up with several lanes over time, one per physical pair provisioned against it — Q3's
     * multi-lane-per-profile support was always there, this method just never needs more than the one-not-yet-full lane
     * for a single new physical endpoint).</li>
     * <li>registers a brand-new {@link QuantumEndpointId} in that lane via
     * {@link QuantumTopologyRegistry#registerEndpoint} and immediately hands it to the UNMODIFIED {@link #activate} for
     * the actual credential bind — from here on this is byte-for-byte the same sanctioned path any other activation
     * goes through.</li>
     * <li>if, after a successful {@link #activate}, the lane now has exactly the two endpoints a Mk I PAIR needs and no
     * edge yet, adds the single persisted {@link QuantumTopologyRegistry#addEdge} — this is the "explicit topology
     * intent creation" the Round 10 task manifest requires be {@code BUILD}-gated: it only ever runs as a direct,
     * same-call consequence of the {@code BUILD} check that already gated this entire method, on the SAME lane the SAME
     * already-authorized actor's own endpoint was just registered into. N=2, E=1 — never more, per Mk I PAIR scope; a
     * lane that already has 2 endpoints is never reused by step 2 above, so this branch can only ever add exactly one
     * edge in a lane's whole lifetime under this call path.</li>
     * <li>if {@link #activate} does NOT return {@link Result#OK}, the endpoint just registered in step 3 never
     * legitimately existed from the caller's point of view — it is immediately discarded via
     * {@link QuantumTopologyRegistry#discardUnactivatedEndpoint} rather than left behind as a registry-visible but
     * never-activated record. This closes the Round 10 orphan finding (independently flagged by three external
     * reviewers of job 1f717902 as a lifecycle-consistency defect, not a security issue, but one worth closing before
     * Round 11's Routing Controller/HUB/MESH work starts building on top of this registry): without the rollback, a
     * {@link Result#CONFLICT} (e.g. re-using an already-bound credential on a second physical block) would leave a
     * {@code QuantumEndpointRecord} permanently occupying a slot in its lane while the physical block that requested it
     * stays UNPROVISIONED forever, unable to retry into that same lane.</li>
     * </ol>
     */
    public static PhysicalActivationOutcome activateFreshPhysicalEndpoint(QuantumTopologyRegistry topology,
            QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumCredentialId credentialId, int cardRevision, String label, IActionSource actor) {
        var credential = credentials.get(credentialId);
        if (credential == null) {
            return new PhysicalActivationOutcome(Result.NOT_FOUND, null);
        }
        var profile = topology.getProfile(credential.profileId());
        if (profile == null) {
            return new PhysicalActivationOutcome(Result.NOT_FOUND, null);
        }
        if (credential.state() == QuantumCredentialState.REVOKED) {
            return new PhysicalActivationOutcome(Result.REVOKED, null);
        }
        if (credential.state() == QuantumCredentialState.SUSPENDED) {
            return new PhysicalActivationOutcome(Result.SUSPENDED, null);
        }
        if (credential.revision() != cardRevision) {
            return new PhysicalActivationOutcome(Result.STALE_REVISION, null);
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.BUILD, security)) {
            return new PhysicalActivationOutcome(Result.FORBIDDEN, null);
        }

        var lane = findOrCreatePairLane(topology, profile.id());
        var record = topology.registerEndpoint(lane.id(), label);

        var result = activate(topology, credentials, security, credentialId, cardRevision, record.id(), actor);
        if (result == Result.OK) {
            maybeCompletePair(topology, record.id());
            return new PhysicalActivationOutcome(result, record.id());
        }
        topology.discardUnactivatedEndpoint(record.id());
        return new PhysicalActivationOutcome(result, null);
    }

    /**
     * Round 12 explicit HUB member join. The member credential is activated into the lane of an already-bound anchor
     * credential and exactly one anchor-member edge is persisted. Both cards are merely selectors: every identity,
     * revision, state, profile, SECURED-domain and BUILD fact is re-read here from authoritative registries.
     * <p>
     * One call is one atomic server-thread mutation. Earlier successful member joins are independent committed user
     * operations; failure of this join removes its newly-added edge and endpoint and leaves all prior arms unchanged.
     */
    public static PhysicalActivationOutcome activateFreshHubMember(QuantumTopologyRegistry topology,
            QuantumCredentialRegistry credentials, @Nullable SecurityDomainRegistry security,
            QuantumCredentialId memberCredentialId, int memberRevision, QuantumCredentialId anchorCredentialId,
            int anchorRevision, String label, IActionSource actor) {
        var member = credentials.get(memberCredentialId);
        var anchor = credentials.get(anchorCredentialId);
        if (member == null || anchor == null || anchor.boundEndpoint().isEmpty()) {
            return new PhysicalActivationOutcome(Result.NOT_FOUND, null);
        }
        if (memberCredentialId.equals(anchorCredentialId)) {
            return new PhysicalActivationOutcome(Result.CONFLICT, null);
        }
        if (!member.profileId().equals(anchor.profileId())) {
            return new PhysicalActivationOutcome(Result.WRONG_PROFILE, null);
        }
        if (member.state() == QuantumCredentialState.REVOKED || anchor.state() == QuantumCredentialState.REVOKED) {
            return new PhysicalActivationOutcome(Result.REVOKED, null);
        }
        if (member.state() == QuantumCredentialState.SUSPENDED || anchor.state() == QuantumCredentialState.SUSPENDED) {
            return new PhysicalActivationOutcome(Result.SUSPENDED, null);
        }
        if (member.revision() != memberRevision || anchor.revision() != anchorRevision) {
            return new PhysicalActivationOutcome(Result.STALE_REVISION, null);
        }
        var profile = topology.getProfile(member.profileId());
        if (profile == null || profile.getSecurityDomainId().isEmpty() || security == null) {
            return new PhysicalActivationOutcome(Result.FORBIDDEN, null);
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.BUILD, security)) {
            return new PhysicalActivationOutcome(Result.FORBIDDEN, null);
        }

        var anchorEndpointId = anchor.boundEndpoint().orElseThrow();
        var anchorEndpoint = topology.getEndpoint(anchorEndpointId);
        if (anchorEndpoint == null
                || anchorEndpoint.status() == appeng.api.networking.quantum.QuantumEndpointStatus.REMOVED) {
            return new PhysicalActivationOutcome(Result.NOT_FOUND, null);
        }
        var lane = topology.getLane(anchorEndpoint.laneId());
        if (lane == null || !lane.profileId().equals(member.profileId())) {
            return new PhysicalActivationOutcome(Result.WRONG_PROFILE, null);
        }

        var record = topology.registerEndpoint(lane.id(), label);
        if (record == null) {
            return new PhysicalActivationOutcome(Result.NOT_FOUND, null);
        }
        if (!topology.addEdge(lane.id(), anchorEndpointId, record.id())) {
            topology.discardUnactivatedEndpoint(record.id());
            return new PhysicalActivationOutcome(Result.WRONG_PROFILE, null);
        }

        var result = activate(topology, credentials, security, memberCredentialId, memberRevision, record.id(), actor);
        if (result != Result.OK) {
            topology.removeEdge(lane.id(), anchorEndpointId, record.id());
            topology.discardUnactivatedEndpoint(record.id());
            return new PhysicalActivationOutcome(result, null);
        }
        return new PhysicalActivationOutcome(Result.OK, record.id());
    }

    /**
     * @return an existing lane of {@code profileId} with fewer than two endpoints (a Mk I pair never grows a third), or
     *         a brand-new lane if none qualifies. Never scans any OTHER profile's lanes — O(lanes for this profile),
     *         never O(all profiles).
     */
    private static QuantumLane findOrCreatePairLane(QuantumTopologyRegistry topology, NetworkProfileId profileId) {
        for (var lane : topology.getAllLanes()) {
            if (lane.profileId().equals(profileId) && lane.getEndpoints().size() < 2) {
                return lane;
            }
        }
        return topology.createLane(profileId);
    }

    /**
     * If {@code endpointId}'s lane now has exactly the two endpoints a Mk I PAIR needs and no edge yet, persists the
     * single intended edge between them — see {@link #activateFreshPhysicalEndpoint}'s javadoc point 4 for why this is
     * still inside the same {@code BUILD}-authorized call and never a separate, ungated mutation.
     */
    private static void maybeCompletePair(QuantumTopologyRegistry topology, QuantumEndpointId endpointId) {
        var record = topology.getEndpoint(endpointId);
        if (record == null) {
            return;
        }
        var lane = topology.getLane(record.laneId());
        if (lane == null || lane.getEndpoints().size() != 2 || !lane.getEdges().isEmpty()) {
            return;
        }
        var it = lane.getEndpoints().iterator();
        var a = it.next();
        var b = it.next();
        topology.addEdge(lane.id(), a, b);
    }

    /**
     * Marks the credential bound to {@code endpointId} (if any) offline, keeping the binding intact — mirrors
     * {@link QuantumTopologyRegistry#setEndpointOffline}'s "unloaded is not deleted" semantics. Not an
     * authorization-gated action (mirrors {@code QuantumTopologyRegistry#setEndpointOffline}, which is also never
     * actor-checked — an ordinary chunk unload/server stop is not a deliberate player mutation-intent).
     */
    public static void deactivate(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            QuantumEndpointId endpointId) {
        var credential = credentials.getCredentialForEndpoint(endpointId);
        if (credential != null) {
            credentials.markOffline(credential.id());
        }
        topology.setEndpointOffline(endpointId);
    }

    // =====================================================================
    // (2) Credential revoke/suspend/resume — SECURITY-tier, QUANTUM_REVOKE.
    // =====================================================================

    /**
     * Registry-only overload, kept for callers that genuinely have no live grid to invalidate (unit tests of pure
     * registry semantics, and any offline/administrative context). <b>Production callers must use
     * {@link #revoke(QuantumTopologyRegistry, QuantumCredentialRegistry, SecurityDomainRegistry, QuantumCredentialId, IActionSource, QuantumEndpointOnlineTable)}</b>
     * so an already-materialized route is actually taken down — see that overload's javadoc.
     */
    public static Result revoke(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, IActionSource actor) {
        return revoke(topology, credentials, security, credentialId, actor, null);
    }

    /**
     * Round 11 runtime-invalidation closure. Revoking a credential now also DEMATERIALIZES any live
     * {@link appeng.api.networking.IGridConnection} its endpoint was still providing, via
     * {@link QuantumRouteMaterializer#dematerializeEndpoint}.
     * <p/>
     * <b>Why this was a real hole.</b> Before Round 11, {@link #filterByCredentialState} was the only bridge between
     * credential state and the live grid — and it is purely an INPUT filter for
     * {@link QuantumLaneReconciler#reconstruct}, i.e. it only decides which endpoints may GAIN a connection. An
     * already-materialized PAIR therefore kept its merged grid after a revoke, leaving the remote network's storage and
     * crafting fully visible and usable until some unrelated event (chunk unload, block break, restart) happened to
     * tear the connection down. Revocation must be effective immediately and unconditionally, so this is now done here,
     * at the same single authorization choke point that performs the state transition itself.
     * <p/>
     * Deliberately narrow: identity, credential binding, and the lane's persisted edge INTENT all survive untouched
     * (revocation is a credential fact, not a topology edit). What disappears is only the physical realization — and it
     * stays gone, because {@link #filterByCredentialState} then keeps that endpoint out of every subsequent
     * reconstruction, including after a server restart. Enforcement therefore never depends on a Routing Controller
     * being loaded, or on any player being online.
     *
     * @param table the live per-server endpoint index; {@code null} only in registry-only contexts, in which case no
     *              dematerialization is attempted.
     */
    public static Result revoke(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, IActionSource actor,
            @Nullable QuantumEndpointOnlineTable table) {
        var credential = credentials.get(credentialId);
        if (credential == null) {
            return Result.NOT_FOUND;
        }
        var profile = topology.getProfile(credential.profileId());
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_REVOKE, security)) {
            return Result.FORBIDDEN;
        }
        var boundEndpoint = credential.boundEndpoint().orElse(null);
        if (!credentials.revoke(credentialId)) {
            return Result.REVOKED;
        }
        invalidateRuntimeRoutes(topology, table, boundEndpoint);
        return Result.OK;
    }

    /**
     * Registry-only overload — see
     * {@link #revoke(QuantumTopologyRegistry, QuantumCredentialRegistry, SecurityDomainRegistry, QuantumCredentialId, IActionSource)}.
     */
    public static Result suspend(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, IActionSource actor) {
        return suspend(topology, credentials, security, credentialId, actor, null);
    }

    /**
     * Round 11 runtime-invalidation closure for SUSPEND — identical reasoning to
     * {@link #revoke(QuantumTopologyRegistry, QuantumCredentialRegistry, SecurityDomainRegistry, QuantumCredentialId, IActionSource, QuantumEndpointOnlineTable)},
     * and equally non-negotiable: a suspended credential must not keep handing out working remote access. The only
     * difference is that suspension is recoverable — see
     * {@link #resume(QuantumTopologyRegistry, QuantumCredentialRegistry, SecurityDomainRegistry, QuantumCredentialId, IActionSource, QuantumEndpointOnlineTable)},
     * which re-materializes the SAME already-persisted intent rather than creating anything new.
     */
    public static Result suspend(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, IActionSource actor,
            @Nullable QuantumEndpointOnlineTable table) {
        var credential = credentials.get(credentialId);
        if (credential == null) {
            return Result.NOT_FOUND;
        }
        var profile = topology.getProfile(credential.profileId());
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_REVOKE, security)) {
            return Result.FORBIDDEN;
        }
        if (credential.state() == QuantumCredentialState.REVOKED) {
            return Result.REVOKED;
        }
        var boundEndpoint = credential.boundEndpoint().orElse(null);
        if (!credentials.suspend(credentialId)) {
            return Result.FORBIDDEN;
        }
        invalidateRuntimeRoutes(topology, table, boundEndpoint);
        return Result.OK;
    }

    /**
     * Registry-only overload — see
     * {@link #resume(QuantumTopologyRegistry, QuantumCredentialRegistry, SecurityDomainRegistry, QuantumCredentialId, IActionSource, QuantumEndpointOnlineTable)}.
     */
    public static Result resume(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, IActionSource actor) {
        return resume(topology, credentials, security, credentialId, actor, null);
    }

    /**
     * The exact symmetric counterpart of {@link #suspend}: once the credential is permitted to carry an active route
     * again, the lane's ALREADY-PERSISTED exact intent is re-materialized through the unmodified trusted-internal
     * reconciler ({@link QuantumRouteMaterializer#materializeLane}). No new intent is ever created here, no alternative
     * path is chosen, and the operation is idempotent — resuming an already-connected pair produces zero additional
     * connections.
     */
    public static Result resume(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId, IActionSource actor,
            @Nullable QuantumEndpointOnlineTable table) {
        var credential = credentials.get(credentialId);
        if (credential == null) {
            return Result.NOT_FOUND;
        }
        var profile = topology.getProfile(credential.profileId());
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_REVOKE, security)) {
            return Result.FORBIDDEN;
        }
        if (!credentials.resume(credentialId)) {
            return Result.FORBIDDEN;
        }
        var boundEndpoint = credential.boundEndpoint().orElse(null);
        if (table != null && boundEndpoint != null) {
            var record = topology.getEndpoint(boundEndpoint);
            if (record != null) {
                QuantumRouteMaterializer.materializeLane(topology, credentials, table, record.laneId());
            }
        }
        return Result.OK;
    }

    /**
     * Tears down every live connection the (now suspended/revoked) credential's endpoint was still realizing. A no-op
     * when the credential was never bound to an endpoint, or when there is no live grid context to invalidate.
     */
    private static void invalidateRuntimeRoutes(QuantumTopologyRegistry topology,
            @Nullable QuantumEndpointOnlineTable table, @Nullable QuantumEndpointId boundEndpoint) {
        if (table == null || boundEndpoint == null) {
            return;
        }
        QuantumRouteMaterializer.dematerializeEndpoint(topology, table, boundEndpoint);
    }

    // =====================================================================
    // (2c)/(5) Explicit rebind — SECURITY-tier, QUANTUM_REWRITE.
    // =====================================================================

    /**
     * Explicit, authorized rebind of an already-issued credential onto a DIFFERENT endpoint of the SAME profile —
     * "endpoint destruction/replacement recovery" without losing the credential's identity or issuing a new one.
     * Retires the old binding (if any); never a silent migration (see {@link QuantumCredential#rebind} javadoc).
     */
    public static Result rebind(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            @Nullable SecurityDomainRegistry security, QuantumCredentialId credentialId,
            QuantumEndpointId newEndpointId, IActionSource actor) {
        var credential = credentials.get(credentialId);
        if (credential == null) {
            return Result.NOT_FOUND;
        }
        var profile = topology.getProfile(credential.profileId());
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        if (credential.state() == QuantumCredentialState.REVOKED) {
            return Result.REVOKED;
        }
        if (!hasProfilePermission(profile, actor, SecurityPermissions.QUANTUM_REWRITE, security)) {
            return Result.FORBIDDEN;
        }
        var endpoint = topology.getEndpoint(newEndpointId);
        if (endpoint == null) {
            return Result.NOT_FOUND;
        }
        var lane = topology.getLane(endpoint.laneId());
        if (lane == null || !lane.profileId().equals(credential.profileId())) {
            return Result.WRONG_PROFILE;
        }
        return credentials.rebind(credentialId, newEndpointId) ? Result.OK : Result.FORBIDDEN;
    }

    // =====================================================================
    // Route-reconstruction bridge — a pure credential-STATE filter, NOT an authorization check.
    // =====================================================================

    /**
     * Filters {@code onlineNodes} (normally {@link QuantumEndpointOnlineTable#onlineNodes()}) down to the subset whose
     * bound credential (if any) still {@link QuantumCredentialState#permitsActiveRoute()}. An endpoint with NO bound
     * credential at all passes through unfiltered — Q4's credential provisioning is strictly additive, never
     * retroactively required for the pre-existing Q1/Q2/Q3 topology primitives, which never construct a credential at
     * all (see the session report's Q1/Q2/Q3 integration section for the regression tests proving this).
     * <p/>
     * This is deliberately NOT an actor/permission check — {@link QuantumLaneReconciler#reconstruct} remains TRUSTED
     * INTERNAL (see its own javadoc) and this method is never given an {@link IActionSource}. It exists purely so a
     * {@link QuantumCredentialState#SUSPENDED}/{@link QuantumCredentialState#REVOKED} credential's endpoint is excluded
     * from a restart's reconstructed route, without {@link QuantumLaneReconciler} or {@link QuantumEndpointOnlineTable}
     * needing to know {@link QuantumCredentialRegistry} exists at all — this class is the only place the two subsystems
     * are wired together.
     */
    public static Map<QuantumEndpointId, IGridNode> filterByCredentialState(
            Map<QuantumEndpointId, IGridNode> onlineNodes, @Nullable QuantumCredentialRegistry credentials) {
        if (credentials == null) {
            return onlineNodes;
        }
        var filtered = new HashMap<QuantumEndpointId, IGridNode>();
        for (var entry : onlineNodes.entrySet()) {
            var credential = credentials.getCredentialForEndpoint(entry.getKey());
            if (credential == null || credential.state().permitsActiveRoute()) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(filtered);
    }

    // =====================================================================
    // Domain-anchored permission resolution — mirrors SecurityService.hasPermission's ACL resolution, but
    // anchored to a NetworkProfile's persisted SecurityDomainId instead of a live IGrid's per-node domain state
    // (SecurityService requires a live IGrid to even resolve LEGACY/SECURED/CONFLICT; a Q4 profile must remain
    // administrable — issue/revoke/suspend/resume/rebind — even while its network is fully offline, exactly like
    // its topology/lane/endpoint identity already survives being fully unloaded per Q3).
    // =====================================================================

    /**
     * Package-private (Round 11) rather than {@code private} purely so {@link QuantumRoutingService} can gate its own
     * {@code QUANTUM_VIEW}/{@code QUANTUM_ROUTE} operations on the SAME, single implementation of profile-anchored ACL
     * resolution — including its LEGACY-open and trusted-internal semantics — instead of growing a second, separately
     * maintained copy of it. Widening the visibility is the entire change; the logic is untouched.
     */
    static boolean hasProfilePermission(NetworkProfile profile, IActionSource actor,
            SecurityPermissions permission, @Nullable SecurityDomainRegistry securityRegistry) {
        var domainId = profile.getSecurityDomainId().orElse(null);
        if (domainId == null) {
            // Never bound to a domain: LEGACY-open, mirrors SecurityService's own LEGACY semantics exactly.
            return true;
        }
        if (actor == null) {
            // Defensive fail-closed guard, mirroring SecurityService.hasPermission's own null-source handling —
            // but unlike that method, a bound domainId here means there is no LEGACY fallback to defer to.
            return false;
        }
        if (actor.player().isEmpty() && actor.machine().isEmpty()) {
            // Trusted-internal bypass, mirrors SecurityService.hasPermission exactly.
            return true;
        }
        if (securityRegistry == null) {
            return false;
        }
        var domain = securityRegistry.getDomain(domainId);
        if (domain == null) {
            return false;
        }
        var principal = resolvePrincipal(actor);
        if (principal instanceof PrincipalId.Player player && domain.isOwner(player.id())) {
            return true;
        }
        if (principal == null) {
            return domain.getPermissions(PrincipalId.EVERYONE).contains(permission);
        }
        return domain.getEffectivePermissions(principal).contains(permission);
    }

    /**
     * Mirrors {@code SecurityService#resolvePrincipal} exactly (that method is private and grid-instance-bound, so it
     * cannot be reused directly here) — see that method's own javadoc for the "unidentified machine only gets EVERYONE"
     * rationale, which applies identically here.
     */
    @Nullable
    private static PrincipalId resolvePrincipal(IActionSource source) {
        var player = source.player().orElse(null);
        if (player != null) {
            return PrincipalId.ofPlayer(player.getUUID());
        }

        var machine = source.machine().orElse(null);
        if (machine != null) {
            var deviceId = resolveDeviceId(machine);
            if (deviceId != null) {
                return PrincipalId.ofDevice(deviceId);
            }
        }

        return null;
    }

    @Nullable
    private static java.util.UUID resolveDeviceId(IActionHost machine) {
        if (machine instanceof IDeviceIdentity identity) {
            return identity.getDeviceId();
        }
        var node = machine.getActionableNode();
        if (node != null && node.getOwner() instanceof IDeviceIdentity identity) {
            return identity.getDeviceId();
        }
        return null;
    }
}
