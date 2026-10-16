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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumEndpointLocation;
import appeng.api.networking.quantum.QuantumLaneId;
import appeng.api.networking.security.SecurityDomainId;
import appeng.core.AppEng;

/**
 * World-scoped (overworld-attached), persistent storage for every {@link NetworkProfile}, {@link QuantumLane} and
 * {@link QuantumEndpointRecord} that has ever been bootstrapped on the server — the canonical topology registry for
 * Quantum Q3's "Network Profile / Lane / Endpoint identity" primitive.
 * <p/>
 * Architecturally, this is a direct sibling of {@link appeng.me.security.SecurityDomainRegistry}: same
 * {@code SavedData}/{@code SavedDataType} ownership pattern, same package-private {@code TYPE} field so unit tests can
 * exercise the real on-disk round-trip via {@code SavedDataStorage.computeIfAbsent(TYPE)} without a running server, and
 * the same {@link #createForTesting()} escape hatch for a plain in-memory instance. It is deliberately kept independent
 * from any single {@link appeng.api.networking.IGrid} for the same reason {@code SecurityDomainRegistry} is: a lane's
 * intended topology must keep meaning something while its endpoints are fully unloaded, mid-restart, or split across
 * grids that have not rejoined yet — see {@link QuantumLaneReconciler}, which is the only thing that ever turns this
 * registry's persisted data back into live {@link appeng.api.networking.IGridConnection}s.
 * <p/>
 * <b>What this registry deliberately does NOT do</b> (see the Q3 task's own stop conditions/non-goals):
 * <ul>
 * <li>It never itself calls {@code SecurityService.bootstrapDomain} or otherwise mutates ACL state. A
 * {@link NetworkProfile}'s {@link NetworkProfile#getSecurityDomainId()} is set only by an explicit administrative
 * caller (e.g. a future Security Station / Routing Controller integration); reconstructing a lane after a reload never
 * manufactures or "infects" a security binding — see {@code QuantumTopologyIdentityQ3Test}'s LEGACY-safety tests. The
 * existing {@code GridConnection.securityDomainsAreCompatible()} pre-merge guard remains the actual, unmodified
 * second-line defense against a cross-domain physical merge; this registry's own {@link #addEdge} guard (see below) is
 * the FIRST line — it never lets a topologically-wrong edge exist as data in the first place.</li>
 * <li>It never stores a full pair/mesh matrix. A {@link QuantumLane}'s {@link QuantumLane#getEdges()} is exactly the
 * N-1 intended tree edges for its N endpoints (see {@link QuantumLane}'s own javadoc) — O(N) records, O(N) (or, for
 * reconstruction, O(edges) = O(N)) topology data, never O(N^2). MESH is a future user-facing routing/topology semantic,
 * never a requirement for an O(N^2) {@code GridConnection} or persisted pair graph.</li>
 * <li>It never re-parents an existing {@link QuantumEndpointId} to a different {@link QuantumLaneId} — see
 * {@link #addEdge}'s rejection of an endpoint whose recorded {@code laneId} does not match the lane being edited. This
 * is what makes "cannot silently attach an endpoint to two incompatible profiles" a structural guarantee rather than a
 * convention callers have to remember.</li>
 * </ul>
 */
public final class QuantumTopologyRegistry extends SavedData {
    private static final Identifier ID = AppEng.makeId("quantum_topology");

    private final Map<NetworkProfileId, NetworkProfile> profiles = new HashMap<>();
    private final Map<QuantumLaneId, QuantumLane> lanes = new HashMap<>();
    private final Map<QuantumEndpointId, QuantumEndpointRecord> endpoints = new HashMap<>();

    private record RegistryData(List<NetworkProfile> profiles, List<QuantumLane> lanes,
            List<QuantumEndpointRecord> endpoints) {
        static final Codec<RegistryData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                NetworkProfile.CODEC.listOf().fieldOf("profiles").forGetter(RegistryData::profiles),
                QuantumLane.CODEC.listOf().fieldOf("lanes").forGetter(RegistryData::lanes),
                QuantumEndpointRecord.CODEC.listOf().fieldOf("endpoints").forGetter(RegistryData::endpoints))
                .apply(builder, RegistryData::new));
    }

    /**
     * Package-private (not {@code private}) purely so unit tests can exercise the real on-disk
     * {@code SavedDataStorage}/{@link SavedDataType} round-trip directly, exactly mirroring
     * {@link appeng.me.security.SecurityDomainRegistry#TYPE}'s own documented reason.
     */
    static final SavedDataType<QuantumTopologyRegistry> TYPE = new SavedDataType<>(
            ID,
            level -> new QuantumTopologyRegistry(),
            level -> RegistryData.CODEC.xmap(
                    data -> {
                        var registry = new QuantumTopologyRegistry();
                        for (var profile : data.profiles()) {
                            registry.profiles.put(profile.id(), profile);
                        }
                        for (var lane : data.lanes()) {
                            registry.lanes.put(lane.id(), lane);
                        }
                        for (var endpoint : data.endpoints()) {
                            registry.endpoints.put(endpoint.id(), endpoint);
                        }
                        return registry;
                    },
                    registry -> new RegistryData(List.copyOf(registry.profiles.values()),
                            List.copyOf(registry.lanes.values()), List.copyOf(registry.endpoints.values()))));

    private QuantumTopologyRegistry() {
    }

    /**
     * Creates a plain, in-memory registry that is not attached to any {@link Level}/{@link SavedData} at all — exactly
     * like {@link appeng.me.security.SecurityDomainRegistry#createForTesting()}.
     */
    public static QuantumTopologyRegistry createForTesting() {
        return new QuantumTopologyRegistry();
    }

    public static QuantumTopologyRegistry get(MinecraftServer server) {
        var overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException(
                    "Cannot retrieve quantum topology data for a server that has no overworld.");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    @Nullable
    public static QuantumTopologyRegistry get(Level level) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return get(serverLevel.getServer());
    }

    // =====================================================================
    // Network Profiles
    // =====================================================================

    public NetworkProfile createProfile() {
        var profile = new NetworkProfile(NetworkProfileId.create());
        profiles.put(profile.id(), profile);
        setDirty();
        return profile;
    }

    @Nullable
    public NetworkProfile getProfile(NetworkProfileId id) {
        return profiles.get(id);
    }

    public Collection<NetworkProfile> getAllProfiles() {
        return List.copyOf(profiles.values());
    }

    /**
     * Binds (or explicitly rebinds) a profile's {@link SecurityDomainId}. Always an explicit administrative action —
     * never called internally by {@link QuantumLaneReconciler} or any reload/reconstruction path.
     *
     * @return {@code false} if the profile does not exist.
     */
    public boolean bindSecurityDomain(NetworkProfileId profileId, @Nullable SecurityDomainId domainId) {
        var profile = profiles.get(profileId);
        if (profile == null) {
            return false;
        }
        profile.setSecurityDomainId(domainId);
        setDirty();
        return true;
    }

    // =====================================================================
    // Quantum Lanes
    // =====================================================================

    /**
     * @return the new lane, or {@code null} if {@code profileId} does not name an existing profile.
     */
    @Nullable
    public QuantumLane createLane(NetworkProfileId profileId) {
        var profile = profiles.get(profileId);
        if (profile == null) {
            return null;
        }
        var lane = new QuantumLane(QuantumLaneId.create(), profileId);
        lanes.put(lane.id(), lane);
        profile.addLane(lane.id());
        setDirty();
        return lane;
    }

    @Nullable
    public QuantumLane getLane(QuantumLaneId id) {
        return lanes.get(id);
    }

    public Collection<QuantumLane> getAllLanes() {
        return List.copyOf(lanes.values());
    }

    // =====================================================================
    // Quantum Endpoints
    // =====================================================================

    /**
     * Registers a brand-new endpoint, permanently bound to {@code laneId} for its whole lifetime (see
     * {@link QuantumEndpointRecord}'s class javadoc).
     *
     * @return the new record, or {@code null} if {@code laneId} does not name an existing lane.
     */
    @Nullable
    public QuantumEndpointRecord registerEndpoint(QuantumLaneId laneId, String label) {
        var lane = lanes.get(laneId);
        if (lane == null) {
            return null;
        }
        var record = new QuantumEndpointRecord(QuantumEndpointId.create(), laneId, label);
        endpoints.put(record.id(), record);
        lane.addEndpoint(record.id());
        setDirty();
        return record;
    }

    @Nullable
    public QuantumEndpointRecord getEndpoint(QuantumEndpointId id) {
        return endpoints.get(id);
    }

    public Collection<QuantumEndpointRecord> getAllEndpoints() {
        return List.copyOf(endpoints.values());
    }

    public List<QuantumEndpointRecord> getEndpointsForLane(QuantumLaneId laneId) {
        var lane = lanes.get(laneId);
        if (lane == null) {
            return List.of();
        }
        var result = new ArrayList<QuantumEndpointRecord>();
        for (var endpointId : lane.getEndpoints()) {
            var record = endpoints.get(endpointId);
            if (record != null) {
                result.add(record);
            }
        }
        return result;
    }

    /**
     * Adds an intended edge between two endpoints of {@code laneId} to the lane's persisted topology.
     * <p/>
     * <b>This is the first-line, structural cross-profile/cross-lane guard</b> (the {@code GridConnection}
     * security-domain check is the unrelated, unmodified second line): both {@code a} and {@code b} must already be
     * registered endpoints whose own recorded {@link QuantumEndpointRecord#laneId()} equals {@code laneId} — an
     * endpoint that belongs to a different lane (and therefore a possibly different, incompatible
     * {@link NetworkProfile}) can never be silently wired into this lane's topology data. There is no "move this
     * endpoint to another lane" API at all (see {@link QuantumEndpointRecord}); this check exists so a caller cannot
     * work around that by simply naming a foreign endpoint id in an edge.
     *
     * @return {@code false} if the lane does not exist, either endpoint does not exist, {@code a} and {@code b} are the
     *         same endpoint (a degenerate self-loop is never a valid topology edge), or either endpoint belongs to a
     *         different lane than {@code laneId}.
     */
    public boolean addEdge(QuantumLaneId laneId, QuantumEndpointId a, QuantumEndpointId b) {
        if (a.equals(b)) {
            return false;
        }
        var lane = lanes.get(laneId);
        if (lane == null) {
            return false;
        }
        var recordA = endpoints.get(a);
        var recordB = endpoints.get(b);
        if (recordA == null || recordB == null) {
            return false;
        }
        if (!recordA.laneId().equals(laneId) || !recordB.laneId().equals(laneId)) {
            return false;
        }
        lane.addEdge(a, b);
        setDirty();
        return true;
    }

    /**
     * Round 11 ("Routing Controller foundation"): removes exactly one intended edge from {@code laneId}'s persisted
     * topology — the precise inverse of {@link #addEdge}, and the ONLY way an authorized route deletion ever changes
     * this registry.
     * <p/>
     * <b>This does not touch identity in any way.</b> Both endpoints keep existing, keep belonging to this lane, keep
     * their credentials and their {@link QuantumEndpointLocation}; only the "these two are intended to be connected"
     * fact goes away. That is exactly the Round 11 separation being enforced: a Routing Controller may edit topology
     * INTENT, and may never delete a {@link NetworkProfile}, {@link QuantumLane} or {@link QuantumEndpointRecord}.
     * Destroying the corresponding runtime {@link appeng.api.networking.IGridConnection}, if one currently exists, is a
     * separate and deliberately decoupled step — see {@link QuantumRouteMaterializer#dematerializeEdge}; this registry
     * stays purely structural and never holds a grid reference, exactly as Q3 left it.
     * <p/>
     * Because the edge is gone from the persisted intent, no later reload/restart reconstruction can resurrect the
     * route: {@link QuantumLaneReconciler} only ever iterates the edges that are actually in this list.
     *
     * @return {@code false} if the lane does not exist, or it had no such edge (an idempotent, already-deleted route is
     *         not an error for the caller to distinguish — {@link QuantumRoutingService} maps that to its own explicit
     *         NOT_FOUND result).
     */
    public boolean removeEdge(QuantumLaneId laneId, QuantumEndpointId a, QuantumEndpointId b) {
        var lane = lanes.get(laneId);
        if (lane == null) {
            return false;
        }
        if (!lane.removeEdge(a, b)) {
            return false;
        }
        setDirty();
        return true;
    }

    /**
     * Marks an endpoint online (first appearance, or a reattach/rebind of an already-known identity — see
     * {@link QuantumEndpointRecord#markOnline}), optionally recording its current physical location.
     *
     * @return {@code false} if the endpoint does not exist, or is {@code REMOVED}.
     */
    public boolean setEndpointOnline(QuantumEndpointId id, @Nullable QuantumEndpointLocation location) {
        var record = endpoints.get(id);
        if (record == null) {
            return false;
        }
        var changed = record.markOnline(location);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /**
     * Marks an endpoint offline. Does not remove its record — unloaded is not deleted.
     *
     * @return {@code false} if the endpoint does not exist.
     */
    public boolean setEndpointOffline(QuantumEndpointId id) {
        var record = endpoints.get(id);
        if (record == null) {
            return false;
        }
        record.markOffline();
        setDirty();
        return true;
    }

    /**
     * Explicitly, permanently retires an endpoint. Its {@link QuantumLane} keeps the (now-dangling, but never reused)
     * id in its edge list — {@link QuantumLaneReconciler} simply never finds a live node for it again, since
     * {@link #setEndpointOnline} refuses to resurrect a {@code REMOVED} record.
     *
     * @return {@code false} if the endpoint does not exist.
     */
    public boolean removeEndpoint(QuantumEndpointId id) {
        var record = endpoints.get(id);
        if (record == null) {
            return false;
        }
        record.markRemoved();
        setDirty();
        return true;
    }

    /**
     * Round 10 hardening: undoes a {@link #registerEndpoint} that was never followed by a successful activation — the
     * documented {@code activateFreshPhysicalEndpoint} orphan case, where {@code registerEndpoint} succeeds but the
     * subsequent {@code QuantumCredentialService.activate} call it delegates to then fails (e.g. {@code CONFLICT}).
     * Unlike {@link #removeEndpoint}, which permanently tombstones an endpoint that WAS real (kept for history, forever
     * occupying its slot in the lane's endpoint set), this deletes the record outright and frees its slot on
     * {@code id}'s lane — as if {@code registerEndpoint} had never been called — so a genuine pair can still complete
     * there. Package-private: a narrow undo for a single failed provisioning attempt, not a general-purpose deletion
     * API; only ever safe on an endpoint that has never gone online or been handed to a caller as provisioned.
     *
     * @return {@code false} if the endpoint does not exist.
     */
    boolean discardUnactivatedEndpoint(QuantumEndpointId id) {
        var record = endpoints.remove(id);
        if (record == null) {
            return false;
        }
        var lane = lanes.get(record.laneId());
        if (lane != null) {
            lane.removeEndpoint(id);
        }
        setDirty();
        return true;
    }

    /**
     * Marks this registry as needing to be saved. Callers normally don't need this directly — every mutation method
     * above already calls it — but it's exposed for symmetry with
     * {@link appeng.me.security.SecurityDomainRegistry#markDirty()}.
     */
    public void markDirty() {
        setDirty();
    }
}
