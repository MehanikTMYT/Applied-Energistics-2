package appeng.me.quantum;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumEndpointStatus;
import appeng.api.networking.quantum.QuantumLaneId;
import appeng.me.AbstractGridNodeTest;
import appeng.me.GridConnection;
import appeng.me.ManagedGridNode;
import appeng.me.service.PathingService;

/**
 * Phase J — Quantum Q3 (part 2 of 3) — reconstruction, load-order independence, partial availability, endpoint
 * lifecycle, duplicate-identity conflict, channel equivalence, and P2P interop, all driven through the REAL production
 * {@link QuantumLaneReconciler}/{@link QuantumEndpointOnlineTable} — not by manually calling
 * {@link GridHelper#createConnection} directly the way the Q1/Q2 prototypes did.
 * <p/>
 * Lives in {@code appeng.me.quantum} (not {@code appeng.me.security}) so it can use
 * {@link QuantumTopologyRegistry#TYPE} for a real on-disk {@link SavedDataStorage} round-trip, in the same style as
 * {@link QuantumTopologyRegistryPersistenceTest}. The two scenarios that genuinely need {@code appeng.me.security}'s
 * OWN package-private hook ({@code SecurityService#setRegistryForTesting}) live in
 * {@code appeng.me.security.QuantumCrossDomainSecurityQ3Test} instead (see that file, part 3 of 3).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumTopologyIdentityQ3Test extends AbstractGridNodeTest {

    @TempDir
    Path tempDir;

    /**
     * Same lazy-bootstrap-ordering hazard the Q1/Q2/Recursive-P2P rounds already found and fixed — forcing the
     * ephemeral server bootstrap here, unconditionally, up front, makes every test in this class deterministic
     * regardless of JUnit's chosen method order.
     */
    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    // =====================================================================
    // Fixtures / helpers
    // =====================================================================

    private record ChainIds(QuantumLaneId laneId, QuantumEndpointId base, QuantumEndpointId factory,
            QuantumEndpointId mine) {
    }

    /** Builds a Base-Factory-Mine lane (2 persisted edges, never a 3rd) in the given registry/profile. */
    private ChainIds buildChainLane(QuantumTopologyRegistry registry, NetworkProfileId profileId) {
        var lane = registry.createLane(profileId);
        var base = registry.registerEndpoint(lane.id(), "base");
        var factory = registry.registerEndpoint(lane.id(), "factory");
        var mine = registry.registerEndpoint(lane.id(), "mine");
        assertThat(registry.addEdge(lane.id(), base.id(), factory.id())).isTrue();
        assertThat(registry.addEdge(lane.id(), factory.id(), mine.id())).isTrue();
        return new ChainIds(lane.id(), base.id(), factory.id(), mine.id());
    }

    private ManagedGridNode freshNode() {
        var node = new ManagedGridNode(new Object(), listener);
        node.create(level, null);
        return node;
    }

    private boolean isConnected(IGridNode a, IGridNode b) {
        for (var connection : a.getConnections()) {
            if (connection.getOtherSide(a) == b) {
                return true;
            }
        }
        return false;
    }

    /** One simulated ME-P2P tunnel device: a main node and its own outer node, sharing one owner object. */
    private record SimTunnel(ManagedGridNode main, ManagedGridNode outer) {
    }

    private SimTunnel makeTunnel() {
        var owner = new Object();
        var main = new ManagedGridNode(owner, listener);
        main.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
        main.create(level, null);
        var outer = new ManagedGridNode(owner, listener);
        outer.setFlags(GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
        outer.create(level, null);
        return new SimTunnel(main, outer);
    }

    // =====================================================================
    // B. CREATE LANE (matrix items 6-9)
    // =====================================================================

    @Test
    void laneAndThreeEndpointsAllBelongToTheSameLaneAndProfile() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());

        assertThat(profile.getLanes()).containsExactly(chain.laneId());
        assertThat(registry.getEndpoint(chain.base()).laneId()).isEqualTo(chain.laneId());
        assertThat(registry.getEndpoint(chain.factory()).laneId()).isEqualTo(chain.laneId());
        assertThat(registry.getEndpoint(chain.mine()).laneId()).isEqualTo(chain.laneId());
        assertThat(registry.getLane(chain.laneId()).profileId()).isEqualTo(profile.id());
        assertThat(registry.getEndpointsForLane(chain.laneId()))
                .extracting(QuantumEndpointRecord::id)
                .containsExactlyInAnyOrder(chain.base(), chain.factory(), chain.mine());
    }

    @Test
    void anEndpointAlreadyBoundToOneLaneCannotBeSilentlyAttachedToADifferentLanesEdge() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profileA = registry.createProfile();
        var chainA = buildChainLane(registry, profileA.id());

        var profileB = registry.createProfile();
        var laneB = registry.createLane(profileB.id());
        var outpost = registry.registerEndpoint(laneB.id(), "outpost");

        // Attempt to wire chain A's "mine" endpoint (belongs to laneA/profileA) into laneB's own topology data.
        var rejected = registry.addEdge(laneB.id(), chainA.mine(), outpost.id());

        assertThat(rejected).isFalse();
        // Nothing changed: laneB still has zero edges, laneA is untouched, "mine"'s own laneId is still laneA.
        assertThat(registry.getLane(laneB.id()).getEdges()).isEmpty();
        assertThat(registry.getEndpoint(chainA.mine()).laneId()).isEqualTo(chainA.laneId());
        assertThat(registry.getLane(chainA.laneId()).getEdges()).hasSize(2);
    }

    // =====================================================================
    // C. RECONSTRUCTION (matrix items 10-16) — real on-disk restart AND fresh node instances in a scrambled order.
    // =====================================================================

    @Test
    void chainTopologySerializesToDiskAndReconstructsToExactlyTwoEdgesAndOneGridWithNoThirdEdge(
            MinecraftServer server) {
        // --- BEFORE "restart": persist the topology and mark all three endpoints online. ---
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var profile = registry1.createProfile();
        var chain = buildChainLane(registry1, profile.id());
        registry1.setEndpointOnline(chain.base(), null);
        registry1.setEndpointOnline(chain.factory(), null);
        registry1.setEndpointOnline(chain.mine(), null);
        registry1.markDirty();
        storage1.saveAndJoin();

        // --- "restart": a brand-new SavedDataStorage instance, no in-memory cache. ---
        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var lane = registry2.getLane(chain.laneId());
        assertThat(lane).isNotNull();
        assertThat(lane.getEdges()).hasSize(2);

        // Brand-new IGridNode instances, constructed in a DIFFERENT order (mine, base, factory) than the roles were
        // originally registered in — modeling real chunk load order being unrelated to original placement order.
        var table = new QuantumEndpointOnlineTable();
        var mineNode = freshNode();
        table.register(chain.mine(), mineNode.getNode());
        var baseNode = freshNode();
        table.register(chain.base(), baseNode.getNode());
        var factoryNode = freshNode();
        table.register(chain.factory(), factoryNode.getNode());

        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(created).hasSize(2);
        assertThat(baseNode.getGrid()).isSameAs(factoryNode.getGrid());
        assertThat(baseNode.getGrid()).isSameAs(mineNode.getGrid());
        var nodesInGrid = new ArrayList<IGridNode>();
        baseNode.getGrid().getNodes().forEach(nodesInGrid::add);
        assertThat(nodesInGrid).containsExactlyInAnyOrder(baseNode.getNode(), factoryNode.getNode(),
                mineNode.getNode());

        // Exactly 2 GridConnections — no 3rd edge, no direct base<->mine.
        assertThat(factoryNode.getNode().getConnections()).hasSize(2);
        assertThat(baseNode.getNode().getConnections()).hasSize(1);
        assertThat(mineNode.getNode().getConnections()).hasSize(1);
        assertThat(isConnected(baseNode.getNode(), mineNode.getNode())).isFalse();
    }

    // =====================================================================
    // D. DIFFERENT LOAD ORDER (matrix items 17-20) — 4 orders, all converge to the same final topology.
    // =====================================================================

    @Test
    void reconstructionConvergesForOrderBaseFactoryMine() {
        assertReconstructionConverges("base", "factory", "mine");
    }

    @Test
    void reconstructionConvergesForOrderMineFactoryBase() {
        assertReconstructionConverges("mine", "factory", "base");
    }

    @Test
    void reconstructionConvergesForOrderFactoryBaseMine() {
        assertReconstructionConverges("factory", "base", "mine");
    }

    @Test
    void reconstructionConvergesForOrderMineBaseFactory() {
        assertReconstructionConverges("mine", "base", "factory");
    }

    private void assertReconstructionConverges(String... order) {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());
        var lane = registry.getLane(chain.laneId());
        var idsByRole = Map.of("base", chain.base(), "factory", chain.factory(), "mine", chain.mine());

        var table = new QuantumEndpointOnlineTable();
        var nodesByRole = new HashMap<String, ManagedGridNode>();
        for (var role : order) {
            var node = freshNode();
            nodesByRole.put(role, node);
            table.register(idsByRole.get(role), node.getNode());
            registry.setEndpointOnline(idsByRole.get(role), null);
            // Reconstruction is attempted incrementally, exactly as it would be each time a real endpoint
            // announces itself online during chunk loading — not just once at the very end.
            QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        }

        var base = nodesByRole.get("base");
        var factory = nodesByRole.get("factory");
        var mine = nodesByRole.get("mine");
        assertThat(base.getGrid()).isSameAs(factory.getGrid());
        assertThat(base.getGrid()).isSameAs(mine.getGrid());
        assertThat(factory.getNode().getConnections()).hasSize(2);
        assertThat(base.getNode().getConnections()).hasSize(1);
        assertThat(mine.getNode().getConnections()).hasSize(1);
        assertThat(isConnected(base.getNode(), mine.getNode())).isFalse();
    }

    // =====================================================================
    // E. PARTIAL AVAILABILITY (matrix items 21-23)
    // =====================================================================

    @Test
    void partialAvailabilityNeverCreatesADirectBaseMineEdgeAndFactoryArrivingLaterCompletesTheChain() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());
        var lane = registry.getLane(chain.laneId());

        var table = new QuantumEndpointOnlineTable();
        var baseNode = freshNode();
        table.register(chain.base(), baseNode.getNode());
        var mineNode = freshNode();
        table.register(chain.mine(), mineNode.getNode());
        // Factory stays offline.

        var createdWhilePartial = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(createdWhilePartial).isEmpty();
        assertThat(baseNode.getNode().getConnections()).isEmpty();
        assertThat(mineNode.getNode().getConnections()).isEmpty();
        assertThat(baseNode.getGrid()).isNotSameAs(mineNode.getGrid()); // definitely not a direct base<->mine edge

        // Factory arrives later.
        var factoryNode = freshNode();
        table.register(chain.factory(), factoryNode.getNode());
        var createdAfterFactory = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(createdAfterFactory).hasSize(2);
        assertThat(baseNode.getGrid()).isSameAs(factoryNode.getGrid());
        assertThat(baseNode.getGrid()).isSameAs(mineNode.getGrid());
        assertThat(isConnected(baseNode.getNode(), mineNode.getNode())).isFalse();
    }

    // =====================================================================
    // F. ENDPOINT UNLOAD (matrix items 24-27)
    // =====================================================================

    @Test
    void unloadingMineKeepsItsRecordAndReloadingRestoresTheIntendedEdge() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());
        var lane = registry.getLane(chain.laneId());

        var table = new QuantumEndpointOnlineTable();
        var baseNode = freshNode();
        table.register(chain.base(), baseNode.getNode());
        var factoryNode = freshNode();
        table.register(chain.factory(), factoryNode.getNode());
        var mineNode = freshNode();
        table.register(chain.mine(), mineNode.getNode());
        QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        assertThat(baseNode.getGrid()).isSameAs(mineNode.getGrid());

        // Mine unloads: its node is destroyed/discarded, exactly like an ordinary chunk unload would do.
        // (Capture the IGridNode reference BEFORE destroy() - ManagedGridNode#destroy() nulls out getNode().)
        var mineGridNode = mineNode.getNode();
        mineNode.destroy();
        table.unregister(chain.mine(), mineGridNode);
        registry.setEndpointOffline(chain.mine());

        assertThat(registry.getEndpoint(chain.mine())).isNotNull(); // the record survives the unload
        assertThat(registry.getEndpoint(chain.mine()).status()).isEqualTo(QuantumEndpointStatus.OFFLINE);
        assertThat(factoryNode.getNode().getConnections()).hasSize(1); // only base<->factory remains

        // Mine reloads with a brand-new IGridNode instance, exactly as a real chunk reload would produce.
        var reloadedMineNode = freshNode();
        table.register(chain.mine(), reloadedMineNode.getNode());
        registry.setEndpointOnline(chain.mine(), null);
        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(created).hasSize(1);
        assertThat(baseNode.getGrid()).isSameAs(reloadedMineNode.getGrid());
        assertThat(factoryNode.getNode().getConnections()).hasSize(2);
    }

    @Test
    void unloadingFactoryPartitionsBaseAndMineAndReloadingFactoryRestoresTheChain() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());
        var lane = registry.getLane(chain.laneId());

        var table = new QuantumEndpointOnlineTable();
        var baseNode = freshNode();
        table.register(chain.base(), baseNode.getNode());
        var factoryNode = freshNode();
        table.register(chain.factory(), factoryNode.getNode());
        var mineNode = freshNode();
        table.register(chain.mine(), mineNode.getNode());
        QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        // (Capture the IGridNode reference BEFORE destroy() - ManagedGridNode#destroy() nulls out getNode().)
        var factoryGridNode = factoryNode.getNode();
        factoryNode.destroy();
        table.unregister(chain.factory(), factoryGridNode);
        registry.setEndpointOffline(chain.factory());

        // The relay's removal partitions Base and Mine — this is correct/expected (mirrors Q2's own
        // "removingTheRelayFactoryCorrectlyPartitionsBaseAndMineIntoTwoSeparateGrids"), not auto-healed into a
        // direct edge that was never persisted.
        assertThat(baseNode.getGrid()).isNotSameAs(mineNode.getGrid());
        assertThat(baseNode.getNode().getConnections()).isEmpty();
        assertThat(mineNode.getNode().getConnections()).isEmpty();

        var reloadedFactoryNode = freshNode();
        table.register(chain.factory(), reloadedFactoryNode.getNode());
        registry.setEndpointOnline(chain.factory(), null);
        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(created).hasSize(2);
        assertThat(baseNode.getGrid()).isSameAs(mineNode.getGrid());
        assertThat(baseNode.getGrid()).isSameAs(reloadedFactoryNode.getGrid());
        assertThat(isConnected(baseNode.getNode(), mineNode.getNode())).isFalse();
    }

    // =====================================================================
    // G (partial: item 32) — repeated reconstruction never creates a duplicate connection.
    // =====================================================================

    @Test
    void repeatedReconstructionAfterEdgesAlreadyExistCreatesNoDuplicateConnections() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());
        var lane = registry.getLane(chain.laneId());

        var table = new QuantumEndpointOnlineTable();
        var baseNode = freshNode();
        table.register(chain.base(), baseNode.getNode());
        var factoryNode = freshNode();
        table.register(chain.factory(), factoryNode.getNode());
        var mineNode = freshNode();
        table.register(chain.mine(), mineNode.getNode());

        var firstPass = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        assertThat(firstPass).hasSize(2);

        // Calling reconstruct again (as would happen on a subsequent repath/tick/reload attempt) must not throw —
        // GridConnection.create() throws IllegalStateException for an already-connected pair — and must not create
        // any new connections.
        var secondPass = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        var thirdPass = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(secondPass).isEmpty();
        assertThat(thirdPass).isEmpty();
        assertThat(factoryNode.getNode().getConnections()).hasSize(2);
        assertThat(baseNode.getNode().getConnections()).hasSize(1);
        assertThat(mineNode.getNode().getConnections()).hasSize(1);
    }

    // =====================================================================
    // J. DUPLICATE ID (matrix item 37)
    // =====================================================================

    @Test
    void duplicatePhysicalClaimsOfOneEndpointIdBecomeConflictedNotSilentlyDuplicatedActive() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var lane = registry.createLane(profile.id());
        var endpoint = registry.registerEndpoint(lane.id(), "duplicated");

        var table = new QuantumEndpointOnlineTable();
        var nodeA = freshNode();
        var nodeB = freshNode(); // a second, genuinely distinct physical node somehow claiming the SAME endpoint id

        assertThat(table.register(endpoint.id(), nodeA.getNode()))
                .isEqualTo(QuantumEndpointOnlineTable.RegistrationResult.ACTIVE);
        // Re-registering the SAME (id, node) pair is idempotent — still ACTIVE (e.g. a repeated repath
        // re-announcing itself is not treated as a conflict).
        assertThat(table.register(endpoint.id(), nodeA.getNode()))
                .isEqualTo(QuantumEndpointOnlineTable.RegistrationResult.ACTIVE);

        var result = table.register(endpoint.id(), nodeB.getNode());
        assertThat(result).isEqualTo(QuantumEndpointOnlineTable.RegistrationResult.CONFLICT);
        assertThat(table.isConflicted(endpoint.id())).isTrue();
        // Neither claimant is exposed as the "one true" active node — not a silent overwrite/duplication.
        assertThat(table.onlineNodes()).doesNotContainKey(endpoint.id());

        // Resolving the conflict (one claimant goes away) makes the id registrable again.
        table.unregister(endpoint.id(), nodeB.getNode());
        assertThat(table.isConflicted(endpoint.id())).isFalse();
        assertThat(table.register(endpoint.id(), nodeA.getNode()))
                .isEqualTo(QuantumEndpointOnlineTable.RegistrationResult.ACTIVE);
        assertThat(table.onlineNodes()).containsEntry(endpoint.id(), nodeA.getNode());
    }

    // =====================================================================
    // Channel equivalence (required by the manifest, not a lettered category of its own)
    // =====================================================================

    @Test
    void reconstructedTopologyHasEquivalentChannelUsageToThePreSerializationTopology() {
        // --- BEFORE: build the live topology directly (Q2 style) and record its channel behavior. ---
        var baseBefore = freshNode();
        var factoryBefore = freshNode();
        var mineBefore = freshNode();
        var conn1Before = (GridConnection) GridHelper.createConnection(baseBefore.getNode(), factoryBefore.getNode());
        GridHelper.createConnection(factoryBefore.getNode(), mineBefore.getNode());
        var leafBefore = new ManagedGridNode(new Object(), listener);
        leafBefore.setFlags(GridFlags.REQUIRE_CHANNEL);
        leafBefore.create(level, null);
        assertThat(GridHelper.createConnection(mineBefore.getNode(), leafBefore.getNode())).isNotNull();
        runTick(baseBefore.getGrid(), 2);

        var maxChannelsBefore = conn1Before.getMaxChannels();
        var usedChannelsBefore = baseBefore.getGrid().getPathingService().getUsedChannels();

        // --- Persist this same topology's identity and reconstruct it fresh, exactly like the C-category test. ---
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var chain = buildChainLane(registry, profile.id());
        var lane = registry.getLane(chain.laneId());

        var table = new QuantumEndpointOnlineTable();
        var baseAfter = freshNode();
        table.register(chain.base(), baseAfter.getNode());
        var factoryAfter = freshNode();
        table.register(chain.factory(), factoryAfter.getNode());
        var mineAfter = freshNode();
        table.register(chain.mine(), mineAfter.getNode());
        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        assertThat(created).hasSize(2); // same edge count — no extra controller branch conjured up

        var leafAfter = new ManagedGridNode(new Object(), listener);
        leafAfter.setFlags(GridFlags.REQUIRE_CHANNEL);
        leafAfter.create(level, null);
        assertThat(GridHelper.createConnection(mineAfter.getNode(), leafAfter.getNode())).isNotNull();
        runTick(baseAfter.getGrid(), 2);

        var conn1After = (GridConnection) factoryAfter.getNode().getConnections().stream()
                .filter(c -> c.getOtherSide(factoryAfter.getNode()) == baseAfter.getNode())
                .findFirst().orElseThrow();
        var maxChannelsAfter = conn1After.getMaxChannels();
        var usedChannelsAfter = baseAfter.getGrid().getPathingService().getUsedChannels();

        assertThat(maxChannelsAfter).isEqualTo(maxChannelsBefore); // no widened capacity
        assertThat(usedChannelsAfter).isEqualTo(usedChannelsBefore); // equivalent usage — not doubled/cloned
        assertThat(factoryAfter.getNode().getConnections()).hasSize(2); // same shape — no extra controller branch
        assertThat(baseAfter.getNode().getConnections()).hasSize(1);
        assertThat(mineAfter.getNode().getConnections()).hasSize(2); // chain edge + its own channel-consuming leaf
    }

    // =====================================================================
    // P2P + Q3 integration (required by the manifest)
    // =====================================================================

    /**
     * {@code P2P -> reconstructed Quantum lane -> P2P -> consumer}, mirroring
     * {@code appeng.me.RecursiveP2PTest#p2pThenQuantumThenP2pChainWorksWithoutGeneratingChannels} exactly, except the
     * "lane" hop is a REAL {@link QuantumLaneReconciler#reconstruct} result instead of one raw
     * {@code GridHelper.createConnection} call — proving persistent Quantum topology identity does not disturb
     * Recursive P2P's already-proven nesting semantics. The P2P route itself is never touched by
     * {@link QuantumTopologyRegistry} — the tunnels below are wired with plain {@link GridHelper#createConnection}
     * calls, exactly like ordinary AE2 P2P, entirely outside the registry.
     */
    @Test
    void p2pThenReconstructedQuantumLaneThenP2pThenConsumerWorksAfterReload() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var lane = registry.createLane(profile.id());
        var middleEndpoint = registry.registerEndpoint(lane.id(), "middle");
        var remoteEndpoint = registry.registerEndpoint(lane.id(), "remote");
        assertThat(registry.addEdge(lane.id(), middleEndpoint.id(), remoteEndpoint.id())).isTrue();

        var table = new QuantumEndpointOnlineTable();
        var middleNode = freshNode();
        table.register(middleEndpoint.id(), middleNode.getNode());
        var remoteNode = freshNode();
        table.register(remoteEndpoint.id(), remoteNode.getNode());
        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        assertThat(created).hasSize(1);

        // P2P tunnel A: input wired into an ordinary "home" network; output wired into "middle" — one of the
        // lane's own reconstructed endpoints. P2P feeding INTO the lane.
        var home = freshNode();
        var a = makeTunnel();
        assertThat(GridHelper.createConnection(home.getNode(), a.main().getNode())).isNotNull();
        var aOut = makeTunnel();
        assertThat(GridHelper.createConnection(middleNode.getNode(), aOut.main().getNode())).isNotNull();
        assertThat(GridHelper.createConnection(a.outer().getNode(), aOut.outer().getNode())).isNotNull();

        // P2P tunnel B: input wired into "remote" — the lane's other reconstructed endpoint. P2P nested AFTER the
        // lane.
        var b = makeTunnel();
        assertThat(GridHelper.createConnection(remoteNode.getNode(), b.main().getNode())).isNotNull();
        var far = freshNode();
        var bOut = makeTunnel();
        assertThat(GridHelper.createConnection(far.getNode(), bOut.main().getNode())).isNotNull();
        assertThat(GridHelper.createConnection(b.outer().getNode(), bOut.outer().getNode())).isNotNull();

        var consumer = new ManagedGridNode(new Object(), listener);
        consumer.setFlags(GridFlags.REQUIRE_CHANNEL);
        consumer.create(level, null);
        assertThat(GridHelper.createConnection(far.getNode(), consumer.getNode())).isNotNull();

        runTick(middleNode.getGrid(), 3);
        runTick(far.getGrid(), 3);

        var middlePathing = (PathingService) middleNode.getGrid().getPathingService();
        var farPathing = (PathingService) far.getGrid().getPathingService();
        assertThat(middlePathing.getAdHocNetworkError())
                .as("no false NESTED_P2P_TUNNEL trip through a reconstructed lane").isNull();
        assertThat(farPathing.getAdHocNetworkError()).isNull();
        // middle's own grid: aOut's own main node (1) + b's own main node (1) — b's main is wired to remoteNode,
        // which the reconstructed Quantum lane already merged into middle's own grid.
        assertThat(middlePathing.getUsedChannels()).isEqualTo(2);
        // far's own grid: bOut's own main node (1) + the consumer (1).
        assertThat(farPathing.getUsedChannels()).isEqualTo(2);
        assertThat(middleNode.getGrid()).isSameAs(remoteNode.getGrid()); // still one shared grid via the lane
        assertThat(middleNode.getGrid()).isNotSameAs(far.getGrid()); // tunnel B's far side is its own ad-hoc grid
    }
}
