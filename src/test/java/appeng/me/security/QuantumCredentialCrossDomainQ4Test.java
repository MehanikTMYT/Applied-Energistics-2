package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.quantum.QuantumCredentialState;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumCredentialService;
import appeng.me.quantum.QuantumEndpointOnlineTable;
import appeng.me.quantum.QuantumLaneReconciler;
import appeng.me.quantum.QuantumTopologyRegistry;

/**
 * Phase O — Quantum Q4 — the one Q4 scenario that genuinely needs {@code appeng.me.security}'s own package-private test
 * hook ({@link SecurityService#setRegistryForTesting}), so it cannot live in
 * {@code appeng.me.quantum.QuantumCredentialQ4Test} alongside the rest of the Q4 suite — exactly mirroring why
 * {@code QuantumCrossDomainSecurityQ3Test} itself lives here rather than in {@code appeng.me.quantum}.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumCredentialCrossDomainQ4Test extends AbstractGridNodeTest {

    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    /**
     * Two credential-activated endpoints of the SAME lane/profile - valid at the Q4 registry level - whose PHYSICAL
     * nodes already independently belong to two different, already-SECURED real networks. Proves Q4's credential layer
     * changes nothing about {@code GridConnection}'s existing, unmodified cross-security-domain pre-merge guard: the
     * registry-level activation succeeds for both credentials (the registry has no idea, and does not need to know,
     * about the physical-layer conflict), but route reconstruction still comes back empty.
     */
    @Test
    void gridConnectionsPreMergeGuardStillRejectsACredentialDrivenEdgeAcrossTwoRealSecuredDomains() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var lane = topology.createLane(profile.id());
        var baseRecord = topology.registerEndpoint(lane.id(), "base");
        var mineRecord = topology.registerEndpoint(lane.id(), "mine");
        assertThat(topology.addEdge(lane.id(), baseRecord.id(), mineRecord.id())).isTrue();

        var baseCredential = credentials.issue(profile.id());
        var mineCredential = credentials.issue(profile.id());
        assertThat(QuantumCredentialService.activate(topology, credentials, null, baseCredential.id(), 1,
                baseRecord.id(), IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(QuantumCredentialService.activate(topology, credentials, null, mineCredential.id(), 1,
                mineRecord.id(), IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);

        var securityRegistry = SecurityDomainRegistry.createForTesting();
        var baseNode = new ManagedGridNode(new Object(), listener);
        baseNode.create(level, null);
        var securityOfBase = (SecurityService) baseNode.getGrid().getService(ISecurityService.class);
        securityOfBase.setRegistryForTesting(securityRegistry);
        securityOfBase.bootstrapDomain(UUID.randomUUID());

        var mineNode = new ManagedGridNode(new Object(), listener);
        mineNode.create(level, null);
        var securityOfMine = (SecurityService) mineNode.getGrid().getService(ISecurityService.class);
        securityOfMine.setRegistryForTesting(securityRegistry);
        securityOfMine.bootstrapDomain(UUID.randomUUID());

        var table = new QuantumEndpointOnlineTable();
        table.register(baseRecord.id(), baseNode.getNode());
        table.register(mineRecord.id(), mineNode.getNode());
        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);

        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);

        assertThat(created).isEmpty();
        assertThat(baseNode.getGrid()).isNotSameAs(mineNode.getGrid());
        // Both credentials REMAIN online at the registry level - the registry never knew, and did not need to know,
        // about the physical-layer conflict; GridConnection's own guard is the only thing that stopped the route.
        assertThat(baseCredential.state()).isEqualTo(QuantumCredentialState.ONLINE);
        assertThat(mineCredential.state()).isEqualTo(QuantumCredentialState.ONLINE);
    }
}
