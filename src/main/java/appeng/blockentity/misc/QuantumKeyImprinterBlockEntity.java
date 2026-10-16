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

import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumCredentialService;
import appeng.me.quantum.QuantumTopologyRegistry;
import appeng.me.security.SecurityDomainRegistry;

/**
 * A Quantum Key Imprinter is a provisioning/security device — <strong>not</strong> a generic processor, not a network
 * database, and not a Routing Controller (see the Q4 task's own scope boundaries). It is an editor/issuer over the
 * existing {@link QuantumCredentialRegistry}/{@link QuantumTopologyRegistry}, exactly analogous to how a
 * {@link SecurityStationBlockEntity} is an editor/access point over {@link SecurityDomainRegistry} — see that class's
 * own javadoc, which this class deliberately mirrors: destroying every Imprinter belonging to a network never deletes
 * any {@code NetworkProfile}/{@code QuantumLane}/{@code QuantumEndpointRecord}/credential record; all of that data
 * keeps living in its own registries independent of this block.
 * <p/>
 * <b>Profile discovery — deliberately narrow.</b> This Imprinter can only ever see/administer the ONE
 * {@code NetworkProfile} bound to the SAME {@link SecurityDomainId} as its own physically-connected network (see
 * {@link #getProfileId()}) — never a global list of every profile on the server (that would be an information leak to
 * an ordinary player; see the Q4 task's own explicit non-goal). "Known" is not "authorized" either: every mutation
 * ({@link #issueCredential}/{@link #revokeCredential}/{@link #suspendCredential}/{@link #resumeCredential}) re-checks
 * the actor's permission from scratch via {@link QuantumCredentialService}, regardless of what this block has ever
 * displayed.
 * <p/>
 * <b>Bootstrap semantics</b> mirror {@link SecurityStationBlockEntity#tryBootstrapDomain()} exactly, one level up: when
 * this Imprinter joins a network that is {@link SecurityDomainState#SECURED} but has no {@code NetworkProfile} bound to
 * that domain yet, it creates one (a brand-new profile with zero lanes/endpoints) so there is something to provision
 * credentials for. A {@link SecurityDomainState#LEGACY} (un-secured) network is left alone entirely — there is no
 * principled way to prove which physical Imprinter is authorized to administer an unsecured, un-owned profile, so none
 * is offered (secure the network with a Security Station first). This is a deliberate, narrow limitation, not a
 * shortcut around authorization.
 */
public class QuantumKeyImprinterBlockEntity extends AENetworkedBlockEntity {
    public QuantumKeyImprinterBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    @Override
    public void onReady() {
        super.onReady();
        tryBootstrapProfile();
    }

    private void tryBootstrapProfile() {
        getMainNode().ifPresent((grid, node) -> {
            var security = grid.getService(ISecurityService.class);
            if (security.getState() != SecurityDomainState.SECURED) {
                return;
            }
            var domainId = security.getDomainId().orElse(null);
            if (domainId == null) {
                return;
            }
            var topology = QuantumTopologyRegistry.get(getLevel());
            if (topology == null) {
                return;
            }
            for (var profile : topology.getAllProfiles()) {
                if (profile.getSecurityDomainId().isPresent() && profile.getSecurityDomainId().get().equals(domainId)) {
                    return; // already provisioned for this domain - nothing to bootstrap
                }
            }
            var profile = topology.createProfile();
            topology.bindSecurityDomain(profile.id(), domainId);
        });
    }

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
     * @return the ONE {@link NetworkProfileId} bound to this Imprinter's own network's live security domain, or
     *         {@code null} if the network is not currently {@link SecurityDomainState#SECURED}, or no profile has been
     *         bootstrapped for it yet. Never a list, never any other network's profile — see class javadoc.
     */
    @Nullable
    public NetworkProfileId getProfileId() {
        var domainId = getDomainId();
        if (domainId == null) {
            return null;
        }
        var topology = QuantumTopologyRegistry.get(getLevel());
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

    /**
     * Issues a fresh credential for this Imprinter's own network profile. Server-authoritative — re-checks
     * {@code QUANTUM_ISSUE} via {@link QuantumCredentialService#issue} regardless of anything the client/menu/screen
     * displayed.
     */
    public QuantumCredentialService.IssueOutcome issueCredential(IActionSource actor) {
        var topology = topology();
        var credentials = credentials();
        var profileId = getProfileId();
        if (topology == null || credentials == null || profileId == null) {
            return QuantumCredentialService.IssueOutcome.failure(QuantumCredentialService.Result.NOT_FOUND);
        }
        return QuantumCredentialService.issue(topology, credentials, security(), profileId, actor);
    }

    /**
     * Round 11: passes the live per-server {@link appeng.me.quantum.QuantumEndpointOnlineTable} so a revoke also
     * DEMATERIALIZES any route the credential's endpoint was still providing. Before this, revoking a credential left
     * an already-merged Mk I PAIR fully usable until an unrelated chunk unload/restart happened to break it — see
     * {@code QuantumCredentialService#revoke}'s own javadoc for the full reasoning.
     */
    public QuantumCredentialService.Result revokeCredential(IActionSource actor, QuantumCredentialId credentialId) {
        var topology = topology();
        var credentials = credentials();
        if (topology == null || credentials == null) {
            return QuantumCredentialService.Result.NOT_FOUND;
        }
        return QuantumCredentialService.revoke(topology, credentials, security(), credentialId, actor, onlineTable());
    }

    public QuantumCredentialService.Result suspendCredential(IActionSource actor, QuantumCredentialId credentialId) {
        var topology = topology();
        var credentials = credentials();
        if (topology == null || credentials == null) {
            return QuantumCredentialService.Result.NOT_FOUND;
        }
        return QuantumCredentialService.suspend(topology, credentials, security(), credentialId, actor, onlineTable());
    }

    public QuantumCredentialService.Result resumeCredential(IActionSource actor, QuantumCredentialId credentialId) {
        var topology = topology();
        var credentials = credentials();
        if (topology == null || credentials == null) {
            return QuantumCredentialService.Result.NOT_FOUND;
        }
        return QuantumCredentialService.resume(topology, credentials, security(), credentialId, actor, onlineTable());
    }

    @Nullable
    private appeng.me.quantum.QuantumEndpointOnlineTable onlineTable() {
        if (!(getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        return appeng.me.quantum.QuantumEndpointOnlineTable.get(serverLevel.getServer());
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }
}
