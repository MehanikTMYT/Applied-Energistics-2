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

package appeng.blockentity.misc;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumLaneId;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumEndpointOnlineTable;
import appeng.me.quantum.QuantumRoutingService;
import appeng.me.quantum.QuantumTopologyRegistry;
import appeng.me.security.SecurityDomainRegistry;

/**
 * Round 11 ("Routing Controller foundation"): the first physical CONTROL-PLANE device for the Quantum subsystem — it
 * observes and administers the topology of one Network Profile's Mk I PAIRs, and deliberately does nothing else.
 * <p/>
 * <b>Control plane, not power-of-existence.</b> This is the single most important property of this block, and it is
 * guaranteed structurally rather than by convention: nothing in this class is referenced by, or reachable from, the
 * transport path. Destroying it, unloading its chunk, or cutting its power does not delete a {@link NetworkProfileId},
 * {@link QuantumLaneId}, {@code QuantumEndpointId}, credential or persisted edge, and does not tear down a single
 * materialized {@code GridConnection} — because this class has no {@code setRemoved}/ {@code onChunkUnloaded} teardown
 * hook at all, and endpoint reconciliation ({@link appeng.blockentity.misc.QuantumEndpointBlockEntity}) never consults
 * a controller. A healthy PAIR keeps working with no Routing Controller anywhere in the world, and a server restart
 * reconstructs it with no controller loaded and no player online. What a controller outage costs is exactly this:
 * player-driven create/delete/repair operations and the status UI. That ordering — transport first, control second — is
 * what makes the controller an additive layer rather than a new single point of failure.
 * <p/>
 * <b>Bound to at most ONE profile, never a browser.</b> {@link #getProfileId()} mirrors
 * {@link QuantumKeyImprinterBlockEntity#getProfileId()} exactly: it resolves its own physically-connected network's
 * live {@link SecurityDomainId} and finds the one {@code NetworkProfile} bound to it. If that cannot be determined
 * unambiguously — a LEGACY (un-secured) network, an unpowered/disconnected node, or no profile bootstrapped for the
 * domain — the controller reports UNBOUND and performs no routing operation whatsoever. It never enumerates every
 * profile on the server, never picks "the first one", never creates one (unlike the Imprinter, it has no bootstrap path
 * at all — creating identity is not a routing operation), and never silently binds to someone else's.
 * <p/>
 * <b>Zero authority of its own.</b> Every method below is a thin, unauthenticated forwarder to
 * {@link QuantumRoutingService}, which performs the actual {@code QUANTUM_VIEW}/{@code QUANTUM_ROUTE} checks
 * server-side on every call. This block entity holds no route state, caches no status, and persists nothing — its NBT
 * is entirely inherited. The one thing it stores is {@link #selectedLaneIndex}, a purely cosmetic "which pair is the
 * screen currently looking at" cursor which is re-clamped against live data on every use and is not authoritative for
 * anything.
 */
public class QuantumRoutingControllerBlockEntity extends AENetworkedBlockEntity {
    /**
     * Cosmetic UI cursor into {@link #listPairs()} — never persisted, never authoritative, always re-clamped against
     * the live lane list before use. Which pair the screen happens to be showing has no effect on any permission check
     * or registry mutation.
     */
    private int selectedLaneIndex = 0;

    public QuantumRoutingControllerBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos,
            BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    // =====================================================================
    // Profile binding - identical shape to the Imprinter, minus any bootstrap.
    // =====================================================================

    @Nullable
    public SecurityDomainState getDomainState() {
        if (!getMainNode().isReady()) {
            return null;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        return grid.getService(ISecurityService.class).getState();
    }

    @Nullable
    public SecurityDomainId getDomainId() {
        if (!getMainNode().isReady()) {
            return null;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        return grid.getService(ISecurityService.class).getDomainId().orElse(null);
    }

    /**
     * @return the ONE profile bound to this controller's own network's security domain, or {@code null} (UNBOUND) if
     *         that cannot be determined deterministically. Never a list, never another network's profile, never a newly
     *         created one.
     */
    @Nullable
    public NetworkProfileId getProfileId() {
        var domainId = getDomainId();
        if (domainId == null) {
            return null;
        }
        var topology = topology();
        if (topology == null) {
            return null;
        }
        for (var profile : topology.getAllProfiles()) {
            if (profile.getSecurityDomainId().isPresent() && profile.getSecurityDomainId().get().equals(domainId)) {
                return profile.id();
            }
        }
        return null;
    }

    // =====================================================================
    // Read - forwarded to QuantumRoutingService, which enforces QUANTUM_VIEW.
    // =====================================================================

    /**
     * @return the lanes of this controller's bound profile only — O(lanes of that profile), computed on demand, never
     *         cached. Empty when UNBOUND.
     */
    public List<QuantumLaneId> listPairs() {
        var topology = topology();
        var profileId = getProfileId();
        if (topology == null || profileId == null) {
            return List.of();
        }
        return QuantumRoutingService.listProfileLanes(topology, profileId);
    }

    public int getSelectedLaneIndex() {
        return selectedLaneIndex;
    }

    /** Advances the cosmetic display cursor, wrapping around. Pure UI state; grants nothing. */
    public void cycleSelection() {
        var pairs = listPairs();
        if (pairs.isEmpty()) {
            selectedLaneIndex = 0;
            return;
        }
        selectedLaneIndex = (selectedLaneIndex + 1) % pairs.size();
    }

    @Nullable
    public QuantumLaneId getSelectedLane() {
        var pairs = listPairs();
        if (pairs.isEmpty()) {
            return null;
        }
        if (selectedLaneIndex >= pairs.size()) {
            selectedLaneIndex = 0;
        }
        return pairs.get(selectedLaneIndex);
    }

    /**
     * Actor-gated status read of the currently selected pair. Returns {@link QuantumRoutingService.Result#UNBOUND} when
     * no profile/lane can be resolved, and {@code FORBIDDEN} when the actor lacks {@code QUANTUM_VIEW} — the check
     * happens inside the service, never here.
     */
    public QuantumRoutingService.StatusOutcome viewSelectedPair(IActionSource actor) {
        var topology = topology();
        var laneId = getSelectedLane();
        var table = onlineTable();
        if (topology == null || table == null) {
            return QuantumRoutingService.StatusOutcome.failure(QuantumRoutingService.Result.NOT_FOUND);
        }
        if (laneId == null) {
            return QuantumRoutingService.StatusOutcome.failure(QuantumRoutingService.Result.UNBOUND);
        }
        return QuantumRoutingService.viewPairStatus(topology, credentials(), security(), table, getProfileId(), laneId,
                actor);
    }

    public QuantumRoutingService.LaneStatusOutcome viewSelectedLane(IActionSource actor) {
        var topology = topology();
        var laneId = getSelectedLane();
        var table = onlineTable();
        if (topology == null || table == null) {
            return QuantumRoutingService.LaneStatusOutcome.failure(QuantumRoutingService.Result.NOT_FOUND);
        }
        if (laneId == null) {
            return QuantumRoutingService.LaneStatusOutcome.failure(QuantumRoutingService.Result.UNBOUND);
        }
        return QuantumRoutingService.viewLaneStatus(topology, credentials(), security(), table, getProfileId(), laneId,
                actor);
    }

    /**
     * Read-only probe used solely so the screen can grey out buttons. Grants nothing — every mutation re-checks
     * server-side regardless of what this returned.
     */
    public boolean hasRoutePermission(IActionSource actor) {
        var topology = topology();
        if (topology == null) {
            return false;
        }
        return QuantumRoutingService.hasPermission(topology, security(), getProfileId(),
                appeng.api.networking.security.SecurityPermissions.QUANTUM_ROUTE, actor);
    }

    // =====================================================================
    // Mutation - forwarded to QuantumRoutingService, which enforces QUANTUM_ROUTE.
    // =====================================================================

    public QuantumRoutingService.Result repairSelectedRoute(IActionSource actor) {
        var topology = topology();
        var table = onlineTable();
        var laneId = getSelectedLane();
        if (topology == null || table == null) {
            return QuantumRoutingService.Result.NOT_FOUND;
        }
        if (laneId == null) {
            return QuantumRoutingService.Result.UNBOUND;
        }
        return QuantumRoutingService.repairLane(topology, credentials(), security(), table, getProfileId(),
                laneId, actor);
    }

    // =====================================================================
    // Registry accessors.
    // =====================================================================

    @Nullable
    private QuantumTopologyRegistry topology() {
        return QuantumTopologyRegistry.get(getLevel());
    }

    @Nullable
    private QuantumCredentialRegistry credentials() {
        return QuantumCredentialRegistry.get(getLevel());
    }

    @Nullable
    private SecurityDomainRegistry security() {
        return SecurityDomainRegistry.get(getLevel());
    }

    @Nullable
    private QuantumEndpointOnlineTable onlineTable() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return QuantumEndpointOnlineTable.get(serverLevel.getServer());
    }

    /**
     * Deliberately connectable on ALL sides (the inherited default), unlike the Quantum Key Imprinter's single BACK
     * face. The Imprinter's restriction is a flavour choice for a provisioning device with a clear "front"; a Routing
     * Controller has no directional function at all, and forcing players to orient a purely observational control block
     * correctly would be friction with no gameplay meaning behind it.
     */
    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }
}
