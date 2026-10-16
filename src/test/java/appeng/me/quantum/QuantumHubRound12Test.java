package appeng.me.quantum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.player.Player;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.security.IActionSource;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.security.SecurityDomainRegistry;

class QuantumHubRound12Test extends AbstractGridNodeTest {
    private record Fixture(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            SecurityDomainRegistry security, NetworkProfileId profileId, IActionSource actor,
            QuantumCredentialId anchorCredential, QuantumEndpointId anchorEndpoint) {
    }

    private IActionSource actor(UUID id) {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(id);
        return IActionSource.ofPlayer(player);
    }

    private Fixture fixture() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var security = SecurityDomainRegistry.createForTesting();
        var owner = UUID.randomUUID();
        var domain = security.createDomain(owner);
        var profile = topology.createProfile();
        topology.bindSecurityDomain(profile.id(), domain.id());
        var anchorCredential = credentials.issue(profile.id());
        var anchor = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, security,
                anchorCredential.id(), 1, "explicit-anchor", actor(owner));
        assertThat(anchor.result()).isEqualTo(QuantumCredentialService.Result.OK);
        return new Fixture(topology, credentials, security, profile.id(), actor(owner), anchorCredential.id(),
                anchor.endpointId());
    }

    private QuantumEndpointId join(Fixture f, String label) {
        var credential = f.credentials().issue(f.profileId());
        var outcome = QuantumCredentialService.activateFreshHubMember(f.topology(), f.credentials(), f.security(),
                credential.id(), 1, f.anchorCredential(), 1, label, f.actor());
        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.OK);
        return outcome.endpointId();
    }

    private ManagedGridNode node() {
        var node = new ManagedGridNode(new Object(), listener);
        node.create(level, null);
        return node;
    }

    @Test
    void threeEndpointsProduceExactlyTwoExplicitAnchorEdgesAndNoMeshEdge() {
        var f = fixture();
        var b = join(f, "B");
        var c = join(f, "C");
        var lane = f.topology().getLane(f.topology().getEndpoint(f.anchorEndpoint()).laneId());

        assertThat(lane.getEndpoints()).containsExactlyInAnyOrder(f.anchorEndpoint(), b, c);
        assertThat(lane.getEdges()).hasSize(2);
        assertThat(lane.hasEdge(f.anchorEndpoint(), b)).isTrue();
        assertThat(lane.hasEdge(f.anchorEndpoint(), c)).isTrue();
        assertThat(lane.hasEdge(b, c)).isFalse();
    }

    @Test
    void oneHundredEndpointsProduceNinetyNineEdgesNotAFullMesh() {
        var f = fixture();
        for (int i = 1; i < 100; i++) {
            join(f, "member-" + i);
        }
        var lane = f.topology().getLane(f.topology().getEndpoint(f.anchorEndpoint()).laneId());
        assertThat(lane.getEndpoints()).hasSize(100);
        assertThat(lane.getEdges()).hasSize(99);
    }

    @Test
    void wrongProfileMemberFailsBeforeMutationAndLeavesNoOrphan() {
        var f = fixture();
        var otherProfile = f.topology().createProfile();
        var otherCredential = f.credentials().issue(otherProfile.id());
        var beforeEndpoints = f.topology().getAllEndpoints().size();
        var lane = f.topology().getLane(f.topology().getEndpoint(f.anchorEndpoint()).laneId());

        var outcome = QuantumCredentialService.activateFreshHubMember(f.topology(), f.credentials(), f.security(),
                otherCredential.id(), 1, f.anchorCredential(), 1, "wrong-profile", f.actor());

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.WRONG_PROFILE);
        assertThat(f.topology().getAllEndpoints()).hasSize(beforeEndpoints);
        assertThat(lane.getEdges()).isEmpty();
    }

    @Test
    void clonedAlreadyBoundMemberFailsWithoutEdgeOrEndpointResidue() {
        var f = fixture();
        var memberCredential = f.credentials().issue(f.profileId());
        var first = QuantumCredentialService.activateFreshHubMember(f.topology(), f.credentials(), f.security(),
                memberCredential.id(), 1, f.anchorCredential(), 1, "first", f.actor());
        assertThat(first.result()).isEqualTo(QuantumCredentialService.Result.OK);
        var beforeEndpoints = f.topology().getAllEndpoints().size();
        var lane = f.topology().getLane(f.topology().getEndpoint(f.anchorEndpoint()).laneId());

        var clone = QuantumCredentialService.activateFreshHubMember(f.topology(), f.credentials(), f.security(),
                memberCredential.id(), 1, f.anchorCredential(), 1, "clone", f.actor());

        assertThat(clone.result()).isEqualTo(QuantumCredentialService.Result.CONFLICT);
        assertThat(f.topology().getAllEndpoints()).hasSize(beforeEndpoints);
        assertThat(lane.getEdges()).hasSize(1);
    }

    @Test
    void arbitraryLoadOrderMaterializesOnlyTheTwoExactEdgesAndRepeatedRepairIsIdempotent() {
        var f = fixture();
        var b = join(f, "B");
        var c = join(f, "C");
        var laneId = f.topology().getEndpoint(f.anchorEndpoint()).laneId();
        var table = new QuantumEndpointOnlineTable();
        var anchorNode = node();
        var bNode = node();
        var cNode = node();
        table.register(c, cNode.getNode());
        table.register(b, bNode.getNode());
        table.register(f.anchorEndpoint(), anchorNode.getNode());

        for (int i = 0; i < 100; i++) {
            QuantumRouteMaterializer.materializeLane(f.topology(), f.credentials(), table, laneId);
        }

        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), b)).isTrue();
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), c)).isTrue();
        assertThat(QuantumRouteMaterializer.isMaterialized(table, b, c)).isFalse();
        assertThat(anchorNode.getNode().getConnections().stream().filter(connection -> !connection.isInWorld()))
                .hasSize(2);
    }

    @Test
    void oneMissingMemberDegradesOnlyItsEdgeAndStatusCountsRemainAccurate() {
        var f = fixture();
        var b = join(f, "B");
        var c = join(f, "C");
        var laneId = f.topology().getEndpoint(f.anchorEndpoint()).laneId();
        var table = new QuantumEndpointOnlineTable();
        var anchorNode = node();
        var bNode = node();
        table.register(f.anchorEndpoint(), anchorNode.getNode());
        table.register(b, bNode.getNode());
        QuantumRouteMaterializer.materializeLane(f.topology(), f.credentials(), table, laneId);

        var status = QuantumRoutingService.computeLaneStatus(f.topology(), f.credentials(), table, laneId);

        assertThat(status.endpoints()).hasSize(3);
        assertThat(status.intendedEdgeCount()).isEqualTo(2);
        assertThat(status.materializedEdgeCount()).isEqualTo(1);
        assertThat(status.state()).isEqualTo(appeng.api.networking.quantum.QuantumRouteState.DEGRADED);
        assertThat(status.edges()).filteredOn(edge -> edge.b().equals(c) || edge.a().equals(c)).singleElement()
                .satisfies(edge -> assertThat(edge.materialized()).isFalse());
    }

    @Test
    void revokedMemberLosesOnlyItsArmWhileOtherArmRemainsMaterialized() {
        var f = fixture();
        var b = join(f, "B");
        var c = join(f, "C");
        var laneId = f.topology().getEndpoint(f.anchorEndpoint()).laneId();
        var table = new QuantumEndpointOnlineTable();
        var ids = new ArrayList<>(java.util.List.of(f.anchorEndpoint(), b, c));
        for (var id : ids) {
            table.register(id, node().getNode());
        }
        QuantumRouteMaterializer.materializeLane(f.topology(), f.credentials(), table, laneId);

        var revokedCredential = f.credentials().getCredentialForEndpoint(c);
        assertThat(QuantumCredentialService.revoke(f.topology(), f.credentials(), f.security(),
                revokedCredential.id(), f.actor(), table)).isEqualTo(QuantumCredentialService.Result.OK);

        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), c)).isFalse();
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), b)).isTrue();
        assertThat(f.topology().getLane(laneId).getEdges()).hasSize(2);
    }

    @Test
    void suspendedMemberLosesOnlyItsArmAndResumeRestoresTheSameIntent() {
        var f = fixture();
        var b = join(f, "B");
        var c = join(f, "C");
        var laneId = f.topology().getEndpoint(f.anchorEndpoint()).laneId();
        var table = new QuantumEndpointOnlineTable();
        for (var id : java.util.List.of(f.anchorEndpoint(), b, c)) {
            table.register(id, node().getNode());
        }
        QuantumRouteMaterializer.materializeLane(f.topology(), f.credentials(), table, laneId);
        var credential = f.credentials().getCredentialForEndpoint(c);

        assertThat(QuantumCredentialService.suspend(f.topology(), f.credentials(), f.security(), credential.id(),
                f.actor(), table)).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), c)).isFalse();
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), b)).isTrue();
        assertThat(f.topology().getLane(laneId).getEdges()).hasSize(2);

        assertThat(QuantumCredentialService.resume(f.topology(), f.credentials(), f.security(), credential.id(),
                f.actor(), table)).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), c)).isTrue();
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), b)).isTrue();
        assertThat(f.topology().getLane(laneId).getEdges()).hasSize(2);
    }

    @Test
    void revokedAnchorTearsDownEveryIncidentArmWithoutDeletingMembersOrIntent() {
        var f = fixture();
        var b = join(f, "B");
        var c = join(f, "C");
        var laneId = f.topology().getEndpoint(f.anchorEndpoint()).laneId();
        var table = new QuantumEndpointOnlineTable();
        for (var id : java.util.List.of(f.anchorEndpoint(), b, c)) {
            table.register(id, node().getNode());
        }
        QuantumRouteMaterializer.materializeLane(f.topology(), f.credentials(), table, laneId);

        assertThat(QuantumCredentialService.revoke(f.topology(), f.credentials(), f.security(),
                f.anchorCredential(), f.actor(), table)).isEqualTo(QuantumCredentialService.Result.OK);

        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), b)).isFalse();
        assertThat(QuantumRouteMaterializer.isMaterialized(table, f.anchorEndpoint(), c)).isFalse();
        assertThat(f.topology().getLane(laneId).getEdges()).hasSize(2);
        assertThat(f.topology().getLane(laneId).getEndpoints()).containsExactlyInAnyOrder(f.anchorEndpoint(), b, c);
        assertThat(f.credentials().getCredentialForEndpoint(b).state())
                .isEqualTo(appeng.api.networking.quantum.QuantumCredentialState.ONLINE);
        assertThat(f.credentials().getCredentialForEndpoint(c).state())
                .isEqualTo(appeng.api.networking.quantum.QuantumCredentialState.ONLINE);
    }
}
