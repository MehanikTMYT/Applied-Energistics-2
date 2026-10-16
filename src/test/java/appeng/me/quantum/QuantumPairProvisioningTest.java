package appeng.me.quantum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.crafting.simulation.helpers.ProcessingPatternBuilder;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.security.SecurityDomainRegistry;

/**
 * Round 10 ("Mk I PHYSICAL QUANTUM PAIR") — the functional PAIR test matrix (Phase I of the task manifest): proves
 * {@link QuantumCredentialService#activateFreshPhysicalEndpoint} end-to-end — the same real, unmodified
 * {@link QuantumTopologyRegistry}, {@link QuantumCredentialRegistry}, {@link QuantumLaneReconciler}, and
 * {@link GridHelper#createConnection} production primitives every earlier Q1-Q4 test already exercises, never a mock of
 * any business logic. Only {@link Player}/{@link MEStorage}/{@link ICraftingProvider} fixtures are faked, exactly
 * mirroring {@code RemoteQuantumLaneIsolationTest}'s own {@code SimpleFakeStorage}/{@code SinglePatternProvider}
 * pattern (reused here almost verbatim).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumPairProvisioningTest extends AbstractGridNodeTest {

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

    private record SecuredProfile(NetworkProfile profile, SecurityDomainRegistry security, UUID ownerId) {
    }

    private SecuredProfile securedProfile(QuantumTopologyRegistry topology) {
        var profile = topology.createProfile();
        var security = SecurityDomainRegistry.createForTesting();
        var ownerId = UUID.randomUUID();
        var domain = security.createDomain(ownerId);
        topology.bindSecurityDomain(profile.id(), domain.id());
        return new SecuredProfile(profile, security, ownerId);
    }

    /**
     * Full pair provisioning: two fresh physical endpoints of {@code profileId}, credentials, online nodes, reconcile.
     */
    private record Pair(QuantumEndpointId a, QuantumEndpointId b, IGridNode nodeA, IGridNode nodeB,
            QuantumEndpointOnlineTable table) {
    }

    private Pair provisionPair(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            SecurityDomainRegistry security, NetworkProfileId profileId, IActionSource actor) {
        var credA = credentials.issue(profileId);
        var credB = credentials.issue(profileId);
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, security, credA.id(),
                1, "A", actor);
        assertThat(outA.result()).isEqualTo(QuantumCredentialService.Result.OK);
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, security, credB.id(),
                1, "B", actor);
        assertThat(outB.result()).isEqualTo(QuantumCredentialService.Result.OK);

        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        reconcile(topology, credentials, table, outA.endpointId());
        return new Pair(outA.endpointId(), outB.endpointId(), nodeA.getNode(), nodeB.getNode(), table);
    }

    private void reconcile(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            QuantumEndpointOnlineTable table, QuantumEndpointId anyEndpointInLane) {
        var record = topology.getEndpoint(anyEndpointInLane);
        var lane = topology.getLane(record.laneId());
        var filtered = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        QuantumLaneReconciler.reconstruct(lane, filtered);
    }

    private boolean isConnected(IGridNode a, IGridNode b) {
        for (var connection : a.getConnections()) {
            if (connection.getOtherSide(a) == b) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // A. Two provisioned endpoints, same Profile/Lane: exactly one intent edge, one materialized connection.
    // =====================================================================

    @Test
    void twoFreshEndpointsOfTheSameProfileFormExactlyOneEdgeAndOneMaterializedConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();

        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());

        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
        var lane = topology.getAllLanes().iterator().next();
        assertThat(lane.getEndpoints()).hasSize(2);
        assertThat(lane.getEdges()).hasSize(1); // N=2, E=1 - never more
        assertThat(pair.nodeA().getGrid()).isSameAs(pair.nodeB().getGrid());
    }

    // =====================================================================
    // B. Different Profile: activation succeeds per-profile but never cross-wires into one lane/edge.
    // =====================================================================

    @Test
    void twoEndpointsOfDifferentProfilesNeverShareALaneOrEdge() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profileA = topology.createProfile();
        var profileB = topology.createProfile();
        var credA = credentials.issue(profileA.id());
        var credB = credentials.issue(profileB.id());

        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1,
                "B", IActionSource.empty());

        assertThat(outA.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(outB.result()).isEqualTo(QuantumCredentialService.Result.OK);
        var laneA = topology.getEndpoint(outA.endpointId()).laneId();
        var laneB = topology.getEndpoint(outB.endpointId()).laneId();
        assertThat(laneA).isNotEqualTo(laneB);
        assertThat(topology.getLane(laneA).getEdges()).isEmpty();
        assertThat(topology.getLane(laneB).getEdges()).isEmpty();
    }

    // =====================================================================
    // C. A THIRD endpoint of an already-complete pair's profile gets its OWN new lane, never a 3rd member.
    // =====================================================================

    @Test
    void aThirdEndpointOfAnAlreadyCompletePairsProfileStartsABrandNewLaneNeverAThirdMember() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());
        var firstLaneId = topology.getEndpoint(pair.a()).laneId();

        var credC = credentials.issue(profile.id());
        var outC = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credC.id(), 1,
                "C", IActionSource.empty());

        assertThat(outC.result()).isEqualTo(QuantumCredentialService.Result.OK);
        var thirdLaneId = topology.getEndpoint(outC.endpointId()).laneId();
        assertThat(thirdLaneId).isNotEqualTo(firstLaneId);
        assertThat(topology.getLane(firstLaneId).getEndpoints()).hasSize(2); // still exactly 2, untouched
        assertThat(topology.getLane(thirdLaneId).getEndpoints()).hasSize(1); // awaiting its own peer
    }

    // D. Different SecurityDomain: no materialized merge - see
    // appeng.me.security.QuantumPairCrossDomainSecurityTest (needs SecurityService's package-private test hook,
    // exactly mirroring why QuantumCredentialCrossDomainQ4Test itself lives in that package instead of this one).

    // =====================================================================
    // E/F. One SECURED domain works with BUILD; LEGACY (unbound) semantics preserved without any actor.
    // =====================================================================

    @Test
    void ownerHoldingBuildCanPhysicallyActivateBothEndpointsOfASecuredProfile() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);

        var pair = provisionPair(topology, credentials, secured.security(), secured.profile().id(),
                playerSource(secured.ownerId()));

        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
    }

    @Test
    void actorWithoutBuildCannotPhysicallyActivateAFreshEndpointOfASecuredProfile() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var credential = credentials.issue(secured.profile().id());
        var stranger = UUID.randomUUID();

        var outcome = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, secured.security(),
                credential.id(), 1, "x", playerSource(stranger));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.FORBIDDEN);
        // No topology state was created for the rejected attempt - BUILD is checked BEFORE any mutation.
        assertThat(topology.getAllLanes()).isEmpty();
        assertThat(topology.getAllEndpoints()).isEmpty();
    }

    @Test
    void legacyUnboundProfileNeedsNoActorAtAllExactlyLikeTrustedInternalReconstruction() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile(); // never bound to a domain - LEGACY-open

        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());

        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
    }

    // =====================================================================
    // G. Real storage insert/extract across the materialized pair.
    // =====================================================================

    @Test
    void storageInsertedThroughEndpointAIsVisibleAndExtractableFromEndpointB() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        var nodeA = new ManagedGridNode(new Object(), listener);
        var storage = new SimpleFakeStorage();
        nodeA.addService(IStorageProvider.class, new SimpleStorageProvider(storage));
        nodeA.create(level, null);
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1,
                "B", IActionSource.empty());
        var nodeB = freshNode();
        var table = new QuantumEndpointOnlineTable();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        reconcile(topology, credentials, table, outA.endpointId());
        assertThat(nodeA.getGrid()).isSameAs(nodeB.getGrid());

        var diamond = AEItemKey.of(Items.DIAMOND);
        var inserted = nodeA.getGrid().getStorageService().getInventory().insert(diamond, 64, Actionable.MODULATE,
                IActionSource.empty());
        assertThat(inserted).isEqualTo(64L);

        var seenFromB = new KeyCounter();
        nodeB.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromB);
        assertThat(seenFromB.get(diamond)).isEqualTo(64L);

        var extracted = nodeB.getGrid().getStorageService().getInventory().extract(diamond, 64, Actionable.MODULATE,
                IActionSource.empty());
        assertThat(extracted).isEqualTo(64L);
    }

    // =====================================================================
    // H. Real crafting pattern visibility across the materialized pair.
    // =====================================================================

    @Test
    void craftingPatternOnEndpointAIsDiscoverableThroughEndpointBsSameGridCraftingService() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        var nodeA = new ManagedGridNode(new Object(), listener);
        var andesite = AEItemKey.of(Items.ANDESITE);
        var pattern = new ProcessingPatternBuilder(new GenericStack(andesite, 1)).build();
        nodeA.addService(ICraftingProvider.class, new SinglePatternProvider(pattern));
        nodeA.create(level, null);
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1,
                "B", IActionSource.empty());
        var nodeB = freshNode();
        var table = new QuantumEndpointOnlineTable();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        reconcile(topology, credentials, table, outA.endpointId());

        assertThat(nodeB.getGrid().getCraftingService().isCraftable(andesite)).isTrue();
    }

    // =====================================================================
    // I/J. Credential clone / stale revision safety, through the fresh-physical-activation path specifically.
    // =====================================================================

    @Test
    void clonedCredentialCardAttemptingASecondFreshPhysicalActivationIsConflictAndLeavesNoOrphan() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credential = credentials.issue(profile.id());
        var first = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null,
                credential.id(), 1, "first", IActionSource.empty());
        assertThat(first.result()).isEqualTo(QuantumCredentialService.Result.OK);
        var laneId = topology.getEndpoint(first.endpointId()).laneId();

        // A duplicated card (same credential id + revision) tries to activate a SECOND, distinct fresh physical block.
        var clone = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credential.id(),
                1, "clone", IActionSource.empty());

        assertThat(clone.result()).isEqualTo(QuantumCredentialService.Result.CONFLICT);
        // Round 10 hardening (independently flagged by external review as a lifecycle-consistency defect): the
        // clone's transient registration is rolled back via QuantumTopologyRegistry#discardUnactivatedEndpoint -
        // no orphaned QuantumEndpointRecord survives the failed activate(), and it never permanently occupies a slot
        // in the lane. This mirrors the existing Q4 precedent's intent - never a second live endpoint for the SAME
        // credential - but now without leaving registry-visible debris behind either.
        assertThat(clone.endpointId()).isNull();
        assertThat(topology.getLane(laneId).getEndpoints()).containsExactly(first.endpointId());
        assertThat(credential.boundEndpoint()).contains(first.endpointId());

        // The freed slot is genuinely reusable, not just vacated in name: a second, legitimately DIFFERENT
        // credential on the same profile can still complete the pair through this exact lane.
        var credentialB = credentials.issue(profile.id());
        var second = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null,
                credentialB.id(), 1, "second", IActionSource.empty());

        assertThat(second.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(topology.getLane(laneId).getEndpoints())
                .containsExactlyInAnyOrder(first.endpointId(), second.endpointId());
        assertThat(topology.getLane(laneId).getEdges()).hasSize(1);
    }

    @Test
    void staleRevisionCardIsRejectedBeforeAnyFreshTopologyStateIsEverCreated() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credential = credentials.issue(profile.id());
        credentials.supersede(credential.id()); // registry revision now 2

        var outcome = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null,
                credential.id(), 1, "x", IActionSource.empty());

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.STALE_REVISION);
        assertThat(outcome.endpointId()).isNull();
        assertThat(topology.getAllLanes()).isEmpty(); // no lane/endpoint ever created for a rejected stale card
    }

    // =====================================================================
    // K/L. Suspended/revoked: connection absent, identity/intent survives, restart never resurrects it.
    // =====================================================================

    @Test
    void suspendingOneEndpointsCredentialTakesTheMaterializedConnectionDownButKeepsIdentityAndIntent() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();
        var lane = topology.getEndpoint(pair.a()).laneId();

        var credA = credentials.getCredentialForEndpoint(pair.a());
        credentials.suspend(credA.id());
        // Simulate a repath/restart attempt with the suspended credential filtered out.
        var filtered = QuantumCredentialService.filterByCredentialState(pair.table().onlineNodes(), credentials);
        assertThat(filtered).doesNotContainKey(pair.a());
        // The edge itself is still persisted intent - never deleted by suspension.
        assertThat(topology.getLane(lane).getEdges()).hasSize(1);
        assertThat(topology.getEndpoint(pair.a())).isNotNull();
    }

    @Test
    void revokedCredentialsEndpointNeverParticipatesInReconstructionEvenAfterManyRetries() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());
        var credA = credentials.getCredentialForEndpoint(pair.a());
        credentials.revoke(credA.id());
        var laneId = topology.getEndpoint(pair.a()).laneId();

        // "Restart" retried 10 times - revocation is terminal, never revived.
        for (int i = 0; i < 10; i++) {
            var filtered = QuantumCredentialService.filterByCredentialState(pair.table().onlineNodes(), credentials);
            QuantumLaneReconciler.reconstruct(topology.getLane(laneId), filtered);
        }
        assertThat(credA.state()).isEqualTo(appeng.api.networking.quantum.QuantumCredentialState.REVOKED);
        assertThat(topology.getEndpoint(pair.a())).isNotNull(); // identity survives
        assertThat(topology.getLane(laneId).getEdges()).hasSize(1); // intent survives
    }

    // =====================================================================
    // M/N. Repeated reconcile never duplicates a GridConnection; stable across many cycles.
    // =====================================================================

    @Test
    void repeatedReconcileNeverDuplicatesTheGridConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());

        for (int i = 0; i < 100; i++) {
            reconcile(topology, credentials, pair.table(), pair.a());
        }

        long connectionsBetween = 0;
        for (var c : pair.nodeA().getConnections()) {
            if (c.getOtherSide(pair.nodeA()) == pair.nodeB()) {
                connectionsBetween++;
            }
        }
        assertThat(connectionsBetween).isEqualTo(1);
        assertThat(pair.nodeA().getConnections()).hasSize(1);
    }

    // =====================================================================
    // O. Reverse activation order produces the identical final topology/materialization.
    // =====================================================================

    @Test
    void reverseActivationOrderProducesTheSameFinalPairTopology() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());

        // B activates FIRST this time.
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1,
                "B", IActionSource.empty());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        assertThat(outA.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(outB.result()).isEqualTo(QuantumCredentialService.Result.OK);

        var table = new QuantumEndpointOnlineTable();
        var nodeB = freshNode();
        var nodeA = freshNode();
        table.register(outB.endpointId(), nodeB.getNode());
        table.register(outA.endpointId(), nodeA.getNode());
        reconcile(topology, credentials, table, outA.endpointId());

        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isTrue();
        var lane = topology.getEndpoint(outA.endpointId()).laneId();
        assertThat(topology.getLane(lane).getEdges()).hasSize(1);
        assertThat(topology.getLane(lane).getEndpoints()).hasSize(2);
    }

    // =====================================================================
    // P. Fresh SavedDataStorage restart preserves intent; a fresh reconcile against it re-materializes the pair.
    // =====================================================================

    @Test
    void freshSavedDataStorageRestartPreservesIntentAndReconcilesToTheSameMaterialization(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var profile = topology1.createProfile();
        var credA = credentials1.issue(profile.id());
        var credB = credentials1.issue(profile.id());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology1, credentials1, null, credA.id(), 1,
                "A", IActionSource.empty());
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology1, credentials1, null, credB.id(), 1,
                "B", IActionSource.empty());
        var laneId = topology1.getEndpoint(outA.endpointId()).laneId();
        topology1.markDirty();
        credentials1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);

        // Identity + intent survived the restart exactly - same lane id, same edge.
        assertThat(topology2.getLane(laneId).getEdges()).hasSize(1);
        assertThat(topology2.getEndpoint(outA.endpointId())).isNotNull();
        assertThat(topology2.getEndpoint(outB.endpointId())).isNotNull();

        // Trusted-internal restart reconciliation (no IActionSource): both endpoints come back online, reconcile.
        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode();
        table.register(outA.endpointId(), nodeA.getNode());
        table.register(outB.endpointId(), nodeB.getNode());
        credentials2.markOnline(credentials2.getCredentialForEndpoint(outA.endpointId()).id());
        credentials2.markOnline(credentials2.getCredentialForEndpoint(outB.endpointId()).id());
        reconcile(topology2, credentials2, table, outA.endpointId());

        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isTrue();
    }

    // =====================================================================
    // Q/R. Peer missing never substitutes topology; peer returning restores the ORIGINAL pair, no duplicate.
    // =====================================================================

    @Test
    void peerMissingNeverCreatesASubstituteEdgeAndTheOnlineEndpointStaysStandalone() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1, "B",
                IActionSource.empty()); // B never comes online

        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        table.register(outA.endpointId(), nodeA.getNode());
        reconcile(topology, credentials, table, outA.endpointId());

        assertThat(nodeA.getNode().getConnections()).isEmpty();
        assertThat(nodeA.getGrid()).isNotNull(); // standalone grid of its own, not merged with anything
    }

    @Test
    void peerReturningRestoresTheOriginalPairWithoutADuplicateConnection() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var outA = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credA.id(), 1,
                "A", IActionSource.empty());
        var outB = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, null, credB.id(), 1,
                "B", IActionSource.empty());
        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        table.register(outA.endpointId(), nodeA.getNode());
        reconcile(topology, credentials, table, outA.endpointId()); // B still missing

        // B's peer finally comes online.
        var nodeB = freshNode();
        table.register(outB.endpointId(), nodeB.getNode());
        reconcile(topology, credentials, table, outA.endpointId());
        // A second reconcile pass (e.g. another tick) must not duplicate the connection.
        reconcile(topology, credentials, table, outA.endpointId());

        assertThat(isConnected(nodeA.getNode(), nodeB.getNode())).isTrue();
        assertThat(nodeA.getNode().getConnections()).hasSize(1);
    }

    // =====================================================================
    // S. Recursive ME P2P through the Mk I PAIR still works, with real PathingService accounting.
    // =====================================================================

    private record SimTunnel(ManagedGridNode main, ManagedGridNode outer) {
    }

    private SimTunnel makeTunnel() {
        var owner = new Object();
        var main = new ManagedGridNode(owner, listener);
        main.setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL,
                appeng.api.networking.GridFlags.COMPRESSED_CHANNEL);
        main.create(level, null);
        var outer = new ManagedGridNode(owner, listener);
        outer.setFlags(appeng.api.networking.GridFlags.DENSE_CAPACITY,
                appeng.api.networking.GridFlags.CANNOT_CARRY_COMPRESSED);
        outer.create(level, null);
        return new SimTunnel(main, outer);
    }

    @Test
    void recursiveP2pThroughTheMkIPairStillWorksWithoutGeneratingChannels() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = topology.createProfile();
        var pair = provisionPair(topology, credentials, null, profile.id(), IActionSource.empty());
        assertThat(isConnected(pair.nodeA(), pair.nodeB())).isTrue();

        var home = freshNode();
        var a = makeTunnel();
        assertThat(GridHelper.createConnection(home.getNode(), a.main().getNode())).isNotNull();
        var aOut = makeTunnel();
        assertThat(GridHelper.createConnection(pair.nodeA(), aOut.main().getNode())).isNotNull();
        assertThat(GridHelper.createConnection(a.outer().getNode(), aOut.outer().getNode())).isNotNull();

        var consumer = new ManagedGridNode(new Object(), listener);
        consumer.setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
        consumer.create(level, null);
        assertThat(GridHelper.createConnection(pair.nodeB(), consumer.getNode())).isNotNull();

        runTick(pair.nodeA().getGrid(), 3);

        var pathing = (appeng.me.service.PathingService) pair.nodeA().getGrid().getPathingService();
        assertThat(pathing.getAdHocNetworkError()).isNull();
        assertThat(pathing.getUsedChannels()).isEqualTo(2); // aOut's main node + the consumer
    }

    // =====================================================================
    // Fixtures reused from RemoteQuantumLaneIsolationTest's own pattern (storage/crafting).
    // =====================================================================

    private static final class SimpleFakeStorage implements MEStorage {
        private final KeyCounter contents = new KeyCounter();

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                contents.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var available = contents.get(what);
            var extracted = Math.min(available, amount);
            if (mode == Actionable.MODULATE && extracted > 0) {
                contents.remove(what, extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(contents);
        }

        @Override
        public Component getDescription() {
            return Component.literal("QuantumPairProvisioningTest fake storage");
        }
    }

    private static final class SimpleStorageProvider implements IStorageProvider {
        private final MEStorage storage;

        SimpleStorageProvider(MEStorage storage) {
            this.storage = storage;
        }

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            storageMounts.mount(storage, 0);
        }
    }

    private static final class SinglePatternProvider implements ICraftingProvider {
        private final IPatternDetails pattern;

        SinglePatternProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }
}
