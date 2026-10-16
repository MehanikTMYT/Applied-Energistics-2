package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainState;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumCredentialService;
import appeng.me.quantum.QuantumEndpointOnlineTable;
import appeng.me.quantum.QuantumLaneReconciler;
import appeng.me.quantum.QuantumTopologyRegistry;

/**
 * Round 10 ("Mk I PHYSICAL QUANTUM PAIR") — Phase I.D and the task manifest's explicit "prove it with a live test"
 * requirement: a SECURED Domain A endpoint paired (at the Q3/Q4 registry level, via the SAME
 * {@link QuantumCredentialService#activateFreshPhysicalEndpoint} physical-activation path Round 10 adds) with a SECURED
 * Domain B endpoint must never materialize a {@code GridConnection} — persisted topology intent grants no right to
 * bypass the existing, completely unmodified {@code GridConnection} cross-security-domain pre-merge guard. Lives here
 * (not in {@code appeng.me.quantum}) purely for {@link SecurityService}'s package-private {@code setRegistryForTesting}
 * test hook, exactly mirroring why {@code QuantumCredentialCrossDomainQ4Test} and
 * {@code QuantumCrossDomainSecurityQ3Test} themselves live in this package instead of {@code appeng.me.quantum}.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumPairCrossDomainSecurityTest extends AbstractGridNodeTest {

    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    private ManagedGridNode freshNode() {
        var node = new ManagedGridNode(new Object(), listener);
        node.create(level, null);
        return node;
    }

    private SecurityService securityOf(ManagedGridNode mgn, SecurityDomainRegistry registry) {
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(registry);
        return security;
    }

    @Test
    void twoIndependentlySecuredEndpointsPairedByCredentialFreshPhysicalActivationNeverMaterializeAConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        // Registry-level: one profile/lane, both endpoints activated through the SAME Round 10 physical-activation
        // path a real Quantum Endpoint block uses. The registry has no idea about physical security domains at all.
        var profile = topology.createProfile();
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1,
                "B", IActionSource.empty());
        assertThat(outA.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(outB.result()).isEqualTo(QuantumCredentialService.Result.OK);
        var laneId = topology.getEndpoint(outA.endpointId()).laneId();
        assertThat(topology.getLane(laneId).getEdges()).hasSize(1); // the persisted intent DOES exist

        // Physical layer: each endpoint's real GridNode independently already belongs to its own SECURED domain.
        var nodeA = freshNode();
        var nodeB = freshNode();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        securityOf(nodeA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(nodeB, registryB).bootstrapDomain(UUID.randomUUID());

        var table = new QuantumEndpointOnlineTable();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        var filtered = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(laneId), filtered);

        assertThat(created).isEmpty(); // GridConnection's pre-merge guard rejected it, completely unmodified
        assertThat(nodeA.getGrid()).isNotSameAs(nodeB.getGrid());
        assertThat(securityOf(nodeA, registryA).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(nodeB, registryB).getState()).isEqualTo(SecurityDomainState.SECURED);
        // Persisted intent still exists - unaffected by the physical-layer rejection (intent vs. materialization).
        assertThat(topology.getLane(laneId).getEdges()).hasSize(1);
    }
}
