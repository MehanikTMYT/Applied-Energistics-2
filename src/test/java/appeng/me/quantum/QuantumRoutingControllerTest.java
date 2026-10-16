package appeng.me.quantum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumLaneId;
import appeng.api.networking.quantum.QuantumRouteReason;
import appeng.api.networking.quantum.QuantumRouteState;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityPermissions;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.security.SecurityDomainRegistry;

/**
 * Round 11 ("Routing Controller foundation") — the control-plane test matrix.
 * <p/>
 * Every assertion below runs against the real, unmodified production primitives: the real
 * {@link QuantumTopologyRegistry}/{@link QuantumCredentialRegistry}/{@link SecurityDomainRegistry}, the real
 * {@link QuantumLaneReconciler}, the real {@code GridHelper.createConnection}/{@code GridConnection.destroy}, and the
 * real {@link QuantumRoutingService} authorization path. Nothing about the business logic is mocked — only
 * {@link Player} is, exactly as {@code QuantumPairProvisioningTest} already does.
 * <p/>
 * The class deliberately exercises {@link QuantumRoutingService} rather than
 * {@code QuantumRoutingControllerBlockEntity}: the block entity contains no authority of its own (every method is a
 * one-line forward into this service), so testing the service IS testing the controller's semantics, without needing a
 * live world. The physical block's own lifecycle is covered by the Round 11 GameTests instead.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumRoutingControllerTest extends AbstractGridNodeTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    // =====================================================================
    // Fixtures
    // =====================================================================

    private ManagedGridNode freshNode() {
        var node = new ManagedGridNode(new Object(), listener);
        node.create(level, null);
        return node;
    }

    private IActionSource playerSource(UUID playerId) {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(playerId);
        return IActionSource.ofPlayer(player);
    }

    /** A profile whose domain grants exactly {@code permissions} to {@code actorId}, and nothing else. */
    private record Secured(NetworkProfileId profileId, SecurityDomainRegistry security, UUID ownerId, UUID actorId) {
    }

    private Secured securedProfile(QuantumTopologyRegistry topology, SecurityPermissions... permissions) {
        var profile = topology.createProfile();
        var security = SecurityDomainRegistry.createForTesting();
        var ownerId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var domain = security.createDomain(ownerId);
        if (permissions.length > 0) {
            domain.grant(PrincipalId.ofPlayer(actorId), java.util.Set.of(permissions));
        }
        topology.bindSecurityDomain(profile.id(), domain.id());
        return new Secured(profile.id(), security, ownerId, actorId);
    }

    /** A fully provisioned, materialized Mk I PAIR. */
    private record Pair(QuantumLaneId laneId, QuantumEndpointId a, QuantumEndpointId b, IGridNode nodeA,
            IGridNode nodeB, QuantumEndpointOnlineTable table) {
    }

    private Pair materializedPair(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            SecurityDomainRegistry security, NetworkProfileId profileId, IActionSource provisioningActor) {
        var credA = credentials.issue(profileId);
        var credB = credentials.issue(profileId);
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, security, credA.id(),
                1, "A", provisioningActor);
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, security, credB.id(),
                1, "B", provisioningActor);
        assertThat(outA.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(outB.result()).isEqualTo(QuantumCredentialService.Result.OK);

        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        var laneId = topology.getEndpoint(outA.endpointId()).laneId();
        QuantumRouteMaterializer.materializeLane(topology, credentials, table, laneId);
        return new Pair(laneId, outA.endpointId(), outB.endpointId(), nodeA.getNode(), nodeB.getNode(), table);
    }

    private boolean isConnected(IGridNode a, IGridNode b) {
        for (var connection : a.getConnections()) {
            if (connection.getOtherSide(a) == b) {
                return true;
            }
        }
        return false;
    }

    private long connectionsBetween(IGridNode a, IGridNode b) {
        return a.getConnections().stream().filter(c -> c.getOtherSide(a) == b).count();
    }

    // =====================================================================
    // A. ROUTER PROFILE BINDING
    // =====================================================================

    @Test
    void controllerListsOnlyItsOwnBoundProfilesLanesAndNeverAnotherProfilesLane() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var mine = topology.createProfile();
        var theirs = topology.createProfile();
        materializedPair(topology, credentials, null, mine.id(), IActionSource.empty());
        materializedPair(topology, credentials, null, theirs.id(), IActionSource.empty());

        var visible = QuantumRoutingService.listProfileLanes(topology, mine.id());

        assertThat(visible).hasSize(1);
        var theirLanes = QuantumRoutingService.listProfileLanes(topology, theirs.id());
        assertThat(visible).doesNotContainAnyElementsOf(theirLanes);
        // There is deliberately no API at all that returns every profile's lanes at once.
        assertThat(theirLanes).hasSize(1);
    }

    @Test
    void anUnboundControllerCannotViewOrMutateAnything() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        // boundProfileId == null models a LEGACY/unpowered/unprovisioned controller: fails closed, never falls back
        // to "the first profile on the server".
        assertThat(QuantumRoutingService.viewPairStatus(topology, credentials, null, pair.table(), null, pair.laneId(),
                IActionSource.empty()).result()).isEqualTo(QuantumRoutingService.Result.UNBOUND);
        assertThat(QuantumRoutingService.createPairRoute(topology, credentials, null, pair.table(), null,
                pair.laneId(), IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.UNBOUND);
        assertThat(QuantumRoutingService.removePairRoute(topology, null, pair.table(), null, pair.laneId(),
                IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.UNBOUND);
        assertThat(QuantumRoutingService.repairPairRoute(topology, credentials, null, pair.table(), null,
                pair.laneId(), IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.UNBOUND);

        // ...and the pair it could not touch is still perfectly healthy.
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
    }

    @Test
    void aControllerBoundToOneProfileCannotEvenViewADifferentProfilesLane() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var mine = topology.createProfile();
        var theirs = topology.createProfile();
        materializedPair(topology, credentials, null, mine.id(), IActionSource.empty());
        var theirPair = materializedPair(topology, credentials, null, theirs.id(), IActionSource.empty());

        var outcome = QuantumRoutingService.viewPairStatus(topology, credentials, null, theirPair.table(), mine.id(),
                theirPair.laneId(), IActionSource.empty());

        assertThat(outcome.result()).isEqualTo(QuantumRoutingService.Result.WRONG_PROFILE);
        assertThat(outcome.status()).isNull(); // no leakage of the other profile's endpoint ids
    }

    @Test
    void mutatingAnotherProfilesLaneIsRejectedAndLeavesThatLanesIntentUntouched() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var mine = topology.createProfile();
        var theirs = topology.createProfile();
        var theirPair = materializedPair(topology, credentials, null, theirs.id(), IActionSource.empty());

        var result = QuantumRoutingService.removePairRoute(topology, null, theirPair.table(), mine.id(),
                theirPair.laneId(), IActionSource.empty());

        assertThat(result).isEqualTo(QuantumRoutingService.Result.WRONG_PROFILE);
        assertThat(topology.getLane(theirPair.laneId()).getEdges()).hasSize(1);
        assertThat(isConnected(theirPair.nodeA(), theirPair.nodeB())).isTrue();
    }

    // =====================================================================
    // B. VIEW AUTHORIZATION
    // =====================================================================

    @Test
    void quantumViewPermissionIsRequiredAndSufficientToReadRouteStatus() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.BUILD, SecurityPermissions.QUANTUM_VIEW);
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));

        var outcome = QuantumRoutingService.viewPairStatus(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), playerSource(secured.actorId()));

        assertThat(outcome.result()).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(outcome.status()).isNotNull();
        assertThat(outcome.status().state()).isEqualTo(QuantumRouteState.ACTIVE);
    }

    @Test
    void withoutQuantumViewRouteStatusIsForbiddenAndRevealsNothingAtAll() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        // The actor holds BUILD (so it is a legitimate network user) but NOT QUANTUM_VIEW.
        var secured = securedProfile(topology, SecurityPermissions.BUILD);
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));

        var outcome = QuantumRoutingService.viewPairStatus(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), playerSource(secured.actorId()));

        assertThat(outcome.result()).isEqualTo(QuantumRoutingService.Result.FORBIDDEN);
        assertThat(outcome.status()).isNull();
    }

    @Test
    void theOwnerImplicitlyHoldsQuantumViewLikeEveryOtherPermission() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology); // owner granted nothing explicitly
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));

        var outcome = QuantumRoutingService.viewPairStatus(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), playerSource(secured.ownerId()));

        assertThat(outcome.result()).isEqualTo(QuantumRoutingService.Result.OK);
    }

    @Test
    void routeStatusNeverCarriesAnExactRemoteBlockPos() {
        // Structural proof rather than a value check: the DTO the UI/API is built on has no BlockPos component at
        // all, so no amount of future UI work can accidentally start leaking precise coordinates without an
        // explicit, deliberate signature change here.
        var components = QuantumRoutingService.PairStatus.class.getRecordComponents();
        assertThat(components).isNotNull();
        for (var component : components) {
            assertThat(component.getType()).isNotEqualTo(net.minecraft.core.BlockPos.class);
            assertThat(component.getType())
                    .as("no QuantumEndpointLocation either - it transitively carries a BlockPos")
                    .isNotEqualTo(appeng.api.networking.quantum.QuantumEndpointLocation.class);
        }
    }

    // =====================================================================
    // C. ROUTE CREATE
    // =====================================================================

    @Test
    void authorizedQuantumRouteCanRecreateAnIntentThatWasPreviouslyDeleted() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.BUILD, SecurityPermissions.QUANTUM_ROUTE);
        var actor = playerSource(secured.actorId());
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));

        assertThat(QuantumRoutingService.removePairRoute(topology, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), actor)).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(topology.getLane(pair.laneId()).getEdges()).isEmpty();

        var created = QuantumRoutingService.createPairRoute(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), actor);

        assertThat(created).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
    }

    @Test
    void withoutQuantumRouteCreateIsForbiddenAndNoIntentIsWritten() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.BUILD, SecurityPermissions.QUANTUM_VIEW);
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));
        // Remove it as the owner first, so there IS something for the unauthorized actor to try to create.
        QuantumRoutingService.removePairRoute(topology, secured.security(), pair.table(), secured.profileId(),
                pair.laneId(), playerSource(secured.ownerId()));

        var result = QuantumRoutingService.createPairRoute(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), playerSource(secured.actorId()));

        assertThat(result).isEqualTo(QuantumRoutingService.Result.FORBIDDEN);
        assertThat(topology.getLane(pair.laneId()).getEdges()).isEmpty();
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
    }

    @Test
    void duplicateCreateIsExplicitlyAlreadyExistsAndNeverDuplicatesTheEdgeOrTheConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        var result = QuantumRoutingService.createPairRoute(topology, credentials, null, pair.table(), profile.id(),
                pair.laneId(), IActionSource.empty());

        assertThat(result).isEqualTo(QuantumRoutingService.Result.ALREADY_EXISTS);
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(connectionsBetween(pair.nodeA(), pair.nodeB())).isEqualTo(1);
    }

    @Test
    void creatingIntentWhilePeerIsOfflineSucceedsButMaterializesNothingUntilThePeerReturns() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        // B unloads entirely.
        pair.table().unregister(pair.b(), pair.nodeB());

        var created = QuantumRoutingService.createPairRoute(topology, credentials, null, pair.table(), profile.id(),
                pair.laneId(), IActionSource.empty());

        assertThat(created).as("desired topology is a durable decision, not conditional on who is loaded")
                .isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();

        var status = QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId());
        assertThat(status.intentExists()).isTrue();
        assertThat(status.materialized()).isFalse();
        assertThat(status.state()).isEqualTo(QuantumRouteState.DEGRADED);
        assertThat(status.reason()).isEqualTo(QuantumRouteReason.PEER_MISSING);

        // The peer returns: the EXACT persisted intent materializes, with no further player action.
        pair.table().register(pair.b(), pair.nodeB());
        QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());

        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).state())
                .isEqualTo(QuantumRouteState.ACTIVE);
    }

    @Test
    void aLaneThatIsNotAPairIsReportedUnsupportedAndNeverGuessedAt() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var lane = topology.createLane(profile.id());
        topology.registerEndpoint(lane.id(), "solo"); // exactly one endpoint - not a PAIR

        var table = new QuantumEndpointOnlineTable();
        assertThat(QuantumRoutingService.createPairRoute(topology, credentials, null, table, profile.id(), lane.id(),
                IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.UNSUPPORTED_TOPOLOGY);
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, table, lane.id()).reason())
                .isEqualTo(QuantumRouteReason.UNSUPPORTED_IN_ROUND_11);
        assertThat(topology.getLane(lane.id()).getEdges()).isEmpty();
    }

    @Test
    void aRetiredTombstonedEndpointCanNeverHaveIntentCreatedForIt() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());
        topology.removeEndpoint(pair.b()); // permanent retirement

        var result = QuantumRoutingService.createPairRoute(topology, credentials, null, pair.table(), profile.id(),
                pair.laneId(), IActionSource.empty());

        assertThat(result).isEqualTo(QuantumRoutingService.Result.INVALID_ENDPOINT);
        assertThat(topology.getLane(pair.laneId()).getEdges()).isEmpty();
    }

    // =====================================================================
    // D. ROUTE DELETE
    // =====================================================================

    @Test
    void deleteRemovesTheExactPersistedEdgeAndDestroysExactlyTheConnectionRealizingIt() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        assertThat(pair.nodeA().getGrid()).isSameAs(pair.nodeB().getGrid());

        var result = QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(topology.getLane(pair.laneId()).getEdges()).isEmpty();
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
        assertThat(pair.nodeA().getGrid()).as("the two networks must actually split apart again")
                .isNotSameAs(pair.nodeB().getGrid());
        // Identity is untouched - deleting a route is not retiring an endpoint.
        assertThat(topology.getEndpoint(pair.a())).isNotNull();
        assertThat(topology.getEndpoint(pair.b())).isNotNull();
        assertThat(credentials.getCredentialForEndpoint(pair.a())).isNotNull();
        assertThat(topology.getLane(pair.laneId()).getEndpoints()).containsExactlyInAnyOrder(pair.a(), pair.b());
    }

    @Test
    void deleteNeverDestroysAnUnrelatedInWorldConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        // Two unrelated, ordinary in-world neighbours - one on each side of the pair. These are exactly the kind of
        // connection an "approximate" delete (destroy whatever joins these grids / scan and guess) would be at risk
        // of taking out.
        var neighbourA = freshNode();
        var neighbourB = freshNode();
        var physicalA = appeng.me.GridConnection.create(pair.nodeA(), neighbourA.getNode(),
                net.minecraft.core.Direction.NORTH);
        var physicalB = appeng.me.GridConnection.create(pair.nodeB(), neighbourB.getNode(),
                net.minecraft.core.Direction.SOUTH);
        assertThat(physicalA.isInWorld()).isTrue();
        assertThat(physicalB.isInWorld()).isTrue();

        var result = QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).as("only the quantum edge is destroyed").isFalse();
        assertThat(isConnected(pair.nodeA(), neighbourA.getNode()))
                .as("an unrelated in-world connection on A's side must survive").isTrue();
        assertThat(isConnected(pair.nodeB(), neighbourB.getNode()))
                .as("an unrelated in-world connection on B's side must survive").isTrue();
    }

    @Test
    void anInWorldConnectionBetweenTheTwoEndpointsIsNeverMistakenForTheQuantumMaterialization() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        // Start from a clean slate: intent and quantum connection both gone.
        QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        // Now the two endpoint blocks are placed physically touching, producing an ordinary in-world connection
        // between the very same two nodes the quantum edge would use.
        var physical = appeng.me.GridConnection.create(pair.nodeA(), pair.nodeB(),
                net.minecraft.core.Direction.NORTH);
        assertThat(physical.isInWorld()).isTrue();
        assertThat(QuantumRouteMaterializer.findMaterializedConnection(pair.nodeA(), pair.nodeB()))
                .as("an in-world adjacency is NOT a quantum materialization").isNull();
        assertThat(QuantumRouteMaterializer.isMaterialized(pair.table(), pair.a(), pair.b())).isFalse();

        // Re-creating and then deleting the route must leave that physical connection completely untouched.
        assertThat(QuantumRoutingService.createPairRoute(topology, credentials, null, pair.table(), profile.id(),
                pair.laneId(), IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.OK);

        assertThat(isConnected(pair.nodeA(), pair.nodeB()))
                .as("the physical adjacency must survive a full create+delete route cycle").isTrue();
        assertThat(connectionsBetween(pair.nodeA(), pair.nodeB())).isEqualTo(1);
    }

    @Test
    void deletingAMissingEdgeIsAnExplicitNotFoundAndChangesNothing() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        var again = QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        assertThat(again).isEqualTo(QuantumRoutingService.Result.NOT_FOUND);
        assertThat(topology.getLane(pair.laneId()).getEdges()).isEmpty();
        assertThat(topology.getLane(pair.laneId()).getEndpoints()).hasSize(2);
    }

    @Test
    void deletingWhileThePeerIsOfflineWorksAndThePeersReturnDoesNotResurrectTheRoute() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        // B unloads, taking the materialized connection with it (ordinary AE2 node teardown is simulated by an
        // explicit dematerialize + unregister, exactly what QuantumEndpointBlockEntity#goOffline drives).
        QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());
        pair.table().unregister(pair.b(), pair.nodeB());

        var result = QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());
        assertThat(result).isEqualTo(QuantumRoutingService.Result.OK);

        // B returns and a full reconciliation runs, several times: the route must NOT come back.
        pair.table().register(pair.b(), pair.nodeB());
        for (int i = 0; i < 10; i++) {
            QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());
        }

        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
        assertThat(topology.getLane(pair.laneId()).getEdges()).isEmpty();
    }

    // =====================================================================
    // E. ROUTE REPAIR
    // =====================================================================

    @Test
    void authorizedRepairRematerializesAnIntentWhoseConnectionWentMissing() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.BUILD, SecurityPermissions.QUANTUM_ROUTE);
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));

        // Intent stays, realization disappears - the exact case repair exists for.
        QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).state())
                .isEqualTo(QuantumRouteState.DEGRADED);

        var result = QuantumRoutingService.repairPairRoute(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), playerSource(secured.actorId()));

        assertThat(result).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
    }

    @Test
    void unauthorizedRepairIsForbiddenAndLeavesTheRouteUnrealized() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.BUILD, SecurityPermissions.QUANTUM_VIEW);
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(),
                playerSource(secured.ownerId()));
        QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());

        var result = QuantumRoutingService.repairPairRoute(topology, credentials, secured.security(), pair.table(),
                secured.profileId(), pair.laneId(), playerSource(secured.actorId()));

        assertThat(result).isEqualTo(QuantumRoutingService.Result.FORBIDDEN);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
    }

    @Test
    void oneHundredRepeatedRepairsOfAHealthyPairCreateAtMostOneConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        for (int i = 0; i < 100; i++) {
            assertThat(QuantumRoutingService.repairPairRoute(topology, credentials, null, pair.table(), profile.id(),
                    pair.laneId(), IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.OK);
        }

        assertThat(connectionsBetween(pair.nodeA(), pair.nodeB())).isEqualTo(1);
        assertThat(pair.nodeA().getConnections()).hasSize(1);
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
    }

    @Test
    void repairOfALaneWithNoIntentIsNotFoundAndNeverInventsARoute() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        var result = QuantumRoutingService.repairPairRoute(topology, credentials, null, pair.table(), profile.id(),
                pair.laneId(), IActionSource.empty());

        assertThat(result).isEqualTo(QuantumRoutingService.Result.NOT_FOUND);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
    }

    // =====================================================================
    // F. OPERATIONAL STATE (derived, never persisted)
    // =====================================================================

    @Test
    void aHealthyMaterializedPairIsActiveWithNoReason() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        var status = QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId());

        assertThat(status.state()).isEqualTo(QuantumRouteState.ACTIVE);
        assertThat(status.reason()).isEqualTo(QuantumRouteReason.NONE);
        assertThat(status.intentExists()).isTrue();
        assertThat(status.materialized()).isTrue();
        assertThat(status.aOnline()).isTrue();
        assertThat(status.bOnline()).isTrue();
    }

    @Test
    void bothEndpointsUnloadedIsDegradedEndpointUnloadedAndOneUnloadedIsDegradedPeerMissing() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());
        pair.table().unregister(pair.b(), pair.nodeB());
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).reason())
                .isEqualTo(QuantumRouteReason.PEER_MISSING);

        pair.table().unregister(pair.a(), pair.nodeA());
        var both = QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId());
        assertThat(both.state()).isEqualTo(QuantumRouteState.DEGRADED);
        assertThat(both.reason()).isEqualTo(QuantumRouteReason.ENDPOINT_UNLOADED);
        assertThat(both.intentExists()).as("intent is never touched by mere unloading").isTrue();
    }

    @Test
    void aSuspendedCredentialMakesTheRouteBlockedAndARevokedOneTakesPrecedence() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        credentials.suspend(credentials.getCredentialForEndpoint(pair.a()).id());
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).state())
                .isEqualTo(QuantumRouteState.BLOCKED);
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).reason())
                .isEqualTo(QuantumRouteReason.CREDENTIAL_SUSPENDED);

        credentials.revoke(credentials.getCredentialForEndpoint(pair.b()).id());
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).reason())
                .as("REVOKED is terminal and must be reported in preference to SUSPENDED")
                .isEqualTo(QuantumRouteReason.CREDENTIAL_REVOKED);
    }

    @Test
    void twoLiveClaimantsOfOneEndpointIdBlockTheRouteRatherThanPickingAWinner() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        var impostor = freshNode();
        assertThat(pair.table().register(pair.a(), impostor.getNode()))
                .isEqualTo(QuantumEndpointOnlineTable.RegistrationResult.CONFLICT);

        var status = QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId());
        assertThat(status.state()).isEqualTo(QuantumRouteState.BLOCKED);
        assertThat(status.reason()).isEqualTo(QuantumRouteReason.ENDPOINT_CONFLICT);
    }

    @Test
    void noIntentMeansInactiveEvenWhenBothEndpointsAreOnlineAndAdjacentInTheSameGrid() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        QuantumRoutingService.removePairRoute(topology, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());

        // An accidental runtime adjacency appears between the very same two nodes.
        var accidental = appeng.me.GridConnection.create(pair.nodeA(), pair.nodeB(),
                net.minecraft.core.Direction.NORTH);
        assertThat(accidental).isNotNull();

        var status = QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId());

        assertThat(status.state())
                .as("without a persisted edge there is no quantum route, whatever the grid looks like")
                .isEqualTo(QuantumRouteState.INACTIVE);
        assertThat(status.reason()).isEqualTo(QuantumRouteReason.NO_INTENT);
        assertThat(status.intentExists()).isFalse();
        assertThat(status.materialized()).isFalse();
    }

    // =====================================================================
    // G. ROUTER OUTAGE (the control plane is never load-bearing for transport)
    // =====================================================================

    @Test
    void afterAControllerDisappearsEntirelyTheExistingPairAndAllIdentityStillWork() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        var credentialA = credentials.getCredentialForEndpoint(pair.a());

        // There is no controller object to destroy: QuantumRoutingService is stateless and nothing in the transport
        // path references it. "The controller is gone" is therefore modelled by simply never calling it again, and
        // then driving a full endpoint-load reconciliation cycle exactly as the block entities do on their own.
        QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());
        pair.table().unregister(pair.a(), pair.nodeA());
        pair.table().unregister(pair.b(), pair.nodeB());
        pair.table().register(pair.a(), pair.nodeA());
        pair.table().register(pair.b(), pair.nodeB());
        QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());

        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(topology.getEndpoint(pair.a())).isNotNull();
        assertThat(topology.getEndpoint(pair.b())).isNotNull();
        assertThat(credentials.get(credentialA.id())).isNotNull();
        assertThat(credentialA.state().permitsActiveRoute()).isTrue();
    }

    // =====================================================================
    // H. RUNTIME ENDPOINT LOOKUP (reused Q3/Round 10 index - no second index added)
    // =====================================================================

    @Test
    void oneHundredLoadUnloadCyclesLeaveNoStaleEntryAndNoGrowth() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        for (int i = 0; i < 100; i++) {
            QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());
            pair.table().unregister(pair.b(), pair.nodeB());
            assertThat(pair.table().onlineNodes()).hasSize(1);
            pair.table().register(pair.b(), pair.nodeB());
            assertThat(pair.table().onlineNodes()).hasSize(2);
            QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());
        }

        assertThat(pair.table().onlineNodes()).hasSize(2);
        assertThat(connectionsBetween(pair.nodeA(), pair.nodeB())).isEqualTo(1);
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(topology.getAllEndpoints()).hasSize(2); // no duplicate endpoint registration ever
    }

    @Test
    void aConflictedEndpointIdBecomesUsableAgainOnceTheDuplicateClaimantUnregisters() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());

        var impostor = freshNode();
        pair.table().register(pair.a(), impostor.getNode());
        assertThat(pair.table().isConflicted(pair.a())).isTrue();
        assertThat(pair.table().onlineNodes()).doesNotContainKey(pair.a());

        pair.table().unregister(pair.a(), impostor.getNode());
        assertThat(pair.table().register(pair.a(), pair.nodeA()))
                .isEqualTo(QuantumEndpointOnlineTable.RegistrationResult.ACTIVE);
        assertThat(pair.table().onlineNodes()).containsKey(pair.a());
    }

    // =====================================================================
    // I. ACTIVE CREDENTIAL SECURITY - the Round 11 blocking hardening
    // =====================================================================

    @Test
    void suspendingACredentialOfAnActiveMaterializedPairImmediatelyRemovesTheRemoteTransport() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        assertThat(pair.nodeA().getGrid()).isSameAs(pair.nodeB().getGrid());
        var credentialA = credentials.getCredentialForEndpoint(pair.a());

        var result = QuantumCredentialService.suspend(topology, credentials, null, credentialA.id(),
                IActionSource.empty(), pair.table());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(isConnected(pair.nodeA(), pair.nodeB()))
                .as("a suspended credential must not keep providing working remote access")
                .isFalse();
        assertThat(pair.nodeA().getGrid()).isNotSameAs(pair.nodeB().getGrid());
        // Identity and intent are deliberately untouched - suspension is a credential fact, not a topology edit.
        assertThat(topology.getLane(pair.laneId()).getEdges()).hasSize(1);
        assertThat(topology.getEndpoint(pair.a())).isNotNull();
    }

    @Test
    void aSuspendedRouteStaysDownAcrossManyReconciliationAttemptsAndComesBackOnlyOnResume() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        var credentialA = credentials.getCredentialForEndpoint(pair.a());
        QuantumCredentialService.suspend(topology, credentials, null, credentialA.id(), IActionSource.empty(),
                pair.table());

        for (int i = 0; i < 20; i++) {
            QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());
        }
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();

        var resumed = QuantumCredentialService.resume(topology, credentials, null, credentialA.id(),
                IActionSource.empty(), pair.table());

        assertThat(resumed).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(isConnected(pair.nodeA(), pair.nodeB()))
                .as("resume re-materializes the SAME already-persisted intent")
                .isTrue();
        assertThat(connectionsBetween(pair.nodeA(), pair.nodeB())).isEqualTo(1);
    }

    @Test
    void revokingACredentialOfAnActiveMaterializedPairImmediatelyRemovesTheRemoteTransport() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        var credentialB = credentials.getCredentialForEndpoint(pair.b());

        var result = QuantumCredentialService.revoke(topology, credentials, null, credentialB.id(),
                IActionSource.empty(), pair.table());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
        assertThat(pair.nodeA().getGrid()).isNotSameAs(pair.nodeB().getGrid());
        // ...and it can never come back, however many reconciliations run.
        for (int i = 0; i < 20; i++) {
            QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());
        }
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse();
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).state())
                .isEqualTo(QuantumRouteState.BLOCKED);
    }

    @Test
    void revocationSurvivesAFullSavedDataStorageRestartWithNoControllerAndNoActor(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var profile = topology1.createProfile();
        var pair = materializedPair(topology1, credentials1, null, profile.id(), IActionSource.empty());
        var credentialB = credentials1.getCredentialForEndpoint(pair.b());
        QuantumCredentialService.revoke(topology1, credentials1, null, credentialB.id(), IActionSource.empty(),
                pair.table());
        topology1.markDirty();
        credentials1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);

        // Trusted-internal restart reconstruction: no actor, no Routing Controller anywhere.
        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode();
        table.register(pair.a(), nodeA.getNode());
        table.register(pair.b(), nodeB.getNode());
        QuantumRouteMaterializer.materializeLane(topology2, credentials2, table, pair.laneId());

        assertThat(topology2.getLane(pair.laneId()).getEdges())
                .as("intent survives - revocation is not a topology edit")
                .hasSize(1);
        assertThat(isConnected(nodeA.getNode(), nodeB.getNode()))
                .as("a revoked credential must not be resurrected by a restart")
                .isFalse();
    }

    @Test
    void anAuthorizedRebindDoesNotLeaveTheOldEndpointStillCarryingTheRoute() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.BUILD, SecurityPermissions.QUANTUM_REWRITE);
        var owner = playerSource(secured.ownerId());
        var pair = materializedPair(topology, credentials, secured.security(), secured.profileId(), owner);
        var credentialA = credentials.getCredentialForEndpoint(pair.a());

        // A third endpoint of the SAME profile, in its own new lane (Mk I pairs never grow a third member).
        var credC = credentials.issue(secured.profileId());
        var outC = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, secured.security(),
                credC.id(), 1, "C", owner);
        assertThat(outC.result()).isEqualTo(QuantumCredentialService.Result.OK);

        var result = QuantumCredentialService.rebind(topology, credentials, secured.security(), credentialA.id(),
                outC.endpointId(), playerSource(secured.actorId()));

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(credentialA.boundEndpoint()).contains(outC.endpointId());
        assertThat(credentials.getCredentialForEndpoint(pair.a()))
                .as("the OLD endpoint must not still resolve to this credential")
                .isNull();
    }

    // =====================================================================
    // L. PERSISTENCE - operational state is derived again, never restored from disk
    // =====================================================================

    @Test
    void routeIntentSurvivesRestartWhileOperationalStateIsPurelyRederived(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var profile = topology1.createProfile();
        var pair = materializedPair(topology1, credentials1, null, profile.id(), IActionSource.empty());
        assertThat(QuantumRoutingService.computePairStatus(topology1, credentials1, pair.table(), pair.laneId())
                .state()).isEqualTo(QuantumRouteState.ACTIVE);
        topology1.markDirty();
        credentials1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);

        // A fresh server starts with an EMPTY runtime index - so the very same intent derives DEGRADED, not ACTIVE.
        var emptyTable = new QuantumEndpointOnlineTable();
        var beforeLoad = QuantumRoutingService.computePairStatus(topology2, credentials2, emptyTable, pair.laneId());
        assertThat(beforeLoad.intentExists()).isTrue();
        assertThat(beforeLoad.state()).isEqualTo(QuantumRouteState.DEGRADED);
        assertThat(beforeLoad.reason()).isEqualTo(QuantumRouteReason.ENDPOINT_UNLOADED);
        assertThat(beforeLoad.materialized()).isFalse();

        // As the block entities load, the SAME intent derives ACTIVE again - nothing about "ACTIVE" was persisted.
        var nodeA = freshNode();
        var nodeB = freshNode();
        emptyTable.register(pair.a(), nodeA.getNode());
        emptyTable.register(pair.b(), nodeB.getNode());
        QuantumRouteMaterializer.materializeLane(topology2, credentials2, emptyTable, pair.laneId());

        assertThat(QuantumRoutingService.computePairStatus(topology2, credentials2, emptyTable, pair.laneId()).state())
                .isEqualTo(QuantumRouteState.ACTIVE);
    }

    @Test
    void reverseEndpointLoadOrderProducesTheIdenticalPersistedTopologyAndFinalState() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = materializedPair(topology, credentials, null, profile.id(), IActionSource.empty());
        var edgesBefore = topology.getLane(pair.laneId()).getEdges();

        // Full unload, then reload in the OPPOSITE order.
        QuantumRouteMaterializer.dematerializeEdge(pair.table(), pair.a(), pair.b());
        pair.table().unregister(pair.a(), pair.nodeA());
        pair.table().unregister(pair.b(), pair.nodeB());
        pair.table().register(pair.b(), pair.nodeB());
        QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isFalse(); // A still absent - nothing substituted
        pair.table().register(pair.a(), pair.nodeA());
        QuantumRouteMaterializer.materializeLane(topology, credentials, pair.table(), pair.laneId());

        assertThat(topology.getLane(pair.laneId()).getEdges()).isEqualTo(edgesBefore);
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
        assertThat(connectionsBetween(pair.nodeA(), pair.nodeB())).isEqualTo(1);
        assertThat(QuantumRoutingService.computePairStatus(topology, credentials, pair.table(), pair.laneId()).state())
                .isEqualTo(QuantumRouteState.ACTIVE);
    }

    @Test
    void adversarialSequenceCreateIntentWhileOfflineThenRestartThenPeerLoadsConvergesToActive(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var profile = topology1.createProfile();
        var pair = materializedPair(topology1, credentials1, null, profile.id(), IActionSource.empty());
        // Delete the route, unload the peer, then re-create the intent with the peer still offline.
        QuantumRoutingService.removePairRoute(topology1, null, pair.table(), profile.id(), pair.laneId(),
                IActionSource.empty());
        pair.table().unregister(pair.b(), pair.nodeB());
        assertThat(QuantumRoutingService.createPairRoute(topology1, credentials1, null, pair.table(), profile.id(),
                pair.laneId(), IActionSource.empty())).isEqualTo(QuantumRoutingService.Result.OK);
        topology1.markDirty();
        credentials1.markDirty();
        storage1.saveAndJoin();

        // Restart. No controller, no actor.
        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        assertThat(topology2.getLane(pair.laneId()).getEdges()).hasSize(1);

        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode();
        table.register(pair.a(), nodeA.getNode());
        QuantumRouteMaterializer.materializeLane(topology2, credentials2, table, pair.laneId());
        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isFalse();

        table.register(pair.b(), nodeB.getNode());
        QuantumRouteMaterializer.materializeLane(topology2, credentials2, table, pair.laneId());

        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isTrue();
        assertThat(QuantumRoutingService.computePairStatus(topology2, credentials2, table, pair.laneId()).state())
                .isEqualTo(QuantumRouteState.ACTIVE);
    }

    // =====================================================================
    // M. Multiple Routing Controllers bound to the same Profile — no split-brain.
    // =====================================================================

    /**
     * Post-Round-11 hardening: closes the one gap independently raised by all three external reviewers of this round
     * (job 31d3bf98/dd82b8d1) — the report never formalized what happens when two physical Routing Controllers are both
     * bound to the same {@link NetworkProfileId}. Every method on {@link QuantumRoutingService} is a static function of
     * (registries, table, profileId, laneId, actor) with no controller identity parameter and no controller-local cache
     * anywhere — see this class's own javadoc, "the block entity contains no authority of its own". This test proves
     * that as an observable property rather than an architectural assertion: two independent call sequences into the
     * SAME service, standing in for two physical Controllers, converge on identical shared state at every step, and
     * neither creates a second {@code GridConnection} nor a second persisted edge. If any hidden per-caller state ever
     * gets introduced, this is the test that catches it.
     */
    @Test
    void twoRoutingControllersBoundToTheSameProfileShareOneAuthoritativeStateWithNoDuplicateConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology, SecurityPermissions.QUANTUM_ROUTE, SecurityPermissions.QUANTUM_VIEW,
                SecurityPermissions.BUILD);
        var actor = playerSource(secured.actorId());

        var credA = credentials.issue(secured.profileId());
        var credB = credentials.issue(secured.profileId());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, secured.security(),
                credA.id(), 1, "A", actor);
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, secured.security(),
                credB.id(), 1, "B", actor);
        assertThat(outA.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(outB.result()).isEqualTo(QuantumCredentialService.Result.OK);

        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        var laneId = topology.getEndpoint(outA.endpointId()).laneId();

        // Provisioning both endpoints auto-completes the pair (Round 10's maybeCompletePair) - clear that back down to
        // NO intent first, so the two-controller cycle below starts from a clean, known state.
        assertThat(QuantumRoutingService.removePairRoute(topology, secured.security(), table, secured.profileId(),
                laneId, actor)).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isFalse();

        // "Controller A" creates the route.
        assertThat(QuantumRoutingService.createPairRoute(topology, credentials, secured.security(), table,
                secured.profileId(), laneId, actor)).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isTrue();

        // "Controller B" observes the exact same state A just created - not a second, independent route.
        var seenByB = QuantumRoutingService.viewPairStatus(topology, credentials, secured.security(), table,
                secured.profileId(), laneId, actor);
        assertThat(seenByB.result()).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(seenByB.status().state()).isEqualTo(QuantumRouteState.ACTIVE);
        assertThat(seenByB.status().materialized()).isTrue();

        // "Controller B" tries to create the same route - idempotent, never a second edge or a second connection.
        assertThat(QuantumRoutingService.createPairRoute(topology, credentials, secured.security(), table,
                secured.profileId(), laneId, actor)).isEqualTo(QuantumRoutingService.Result.ALREADY_EXISTS);
        assertThat(connectionsBetween(nodeA.getNode(), nodeB.getNode())).isEqualTo(1);
        assertThat(topology.getLane(laneId).getEdges()).hasSize(1);

        // "Controller B" deletes the route.
        assertThat(QuantumRoutingService.removePairRoute(topology, secured.security(), table, secured.profileId(),
                laneId, actor)).isEqualTo(QuantumRoutingService.Result.OK);
        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isFalse();

        // "Controller A" observes B's deletion immediately, with no stale local view of its own earlier create.
        var seenByAAfterDelete = QuantumRoutingService.viewPairStatus(topology, credentials, secured.security(), table,
                secured.profileId(), laneId, actor);
        assertThat(seenByAAfterDelete.status().state()).isEqualTo(QuantumRouteState.INACTIVE);
        assertThat(seenByAAfterDelete.status().intentExists()).isFalse();

        // "Controller A" repeats the delete B already performed - idempotent NOT_FOUND, no error, no side effect.
        assertThat(QuantumRoutingService.removePairRoute(topology, secured.security(), table, secured.profileId(),
                laneId, actor)).isEqualTo(QuantumRoutingService.Result.NOT_FOUND);
        assertThat(connectionsBetween(nodeA.getNode(), nodeB.getNode())).isEqualTo(0);
        assertThat(topology.getLane(laneId).getEdges()).isEmpty();
    }
}
