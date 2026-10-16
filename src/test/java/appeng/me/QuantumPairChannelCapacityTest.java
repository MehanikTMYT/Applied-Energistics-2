package appeng.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import net.minecraft.core.BlockPos;

import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.pathing.CapacityTier;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.service.PathingService;

/**
 * Round 10 ("Mk I PHYSICAL QUANTUM PAIR") — Phase G, the channel-capacity implementation gate. Proves the Mk I Quantum
 * PAIR's remote transport edge — modeled here exactly like a real {@code QuantumEndpointBlockEntity}'s own main node
 * ({@link GridFlags#DENSE_CAPACITY} + explicit {@link CapacityTier#STANDARD}, see {@link #makePairNode}) — is a REAL
 * bottleneck node in the SAME {@code GridNode#subtreeMaxChannels}/{@link PathingCalculation} walk
 * {@code CapacityTierTest}/{@code HighTierP2PTest} already validate, through the real {@link PathingService}. No
 * {@code QuantumPathingService}, no second channel calculator, no isolated helper anywhere in this file or in
 * production code — every assertion below reads {@code PathingService#getUsedChannels()} after a real {@code repath()}
 * (via {@code runTick}), exactly like the existing high-tier suites.
 * <p/>
 * The 8 mixed-path scenarios below are literally the Round 10 task manifest's own Phase G matrix, numbered to match.
 */
class QuantumPairChannelCapacityTest extends AbstractGridNodeTest {

    @Mock
    private MockedStatic<IPlayerRegistry> playerRegistry;

    @BeforeEach
    void stubPlayerRegistryForAchievements() {
        playerRegistry.when(() -> IPlayerRegistry.getConnected(any(), anyInt())).thenReturn(null);
    }

    // =====================================================================
    // Fixture helpers (mirrors HighTierP2PTest's own fixture vocabulary).
    // =====================================================================

    private GridNode makeOwnedNode(Object nodeOwner, GridFlags... flags) {
        var node = new GridNode(level, nodeOwner, listener, Set.of(flags));
        node.markReady();
        return node;
    }

    /**
     * One side of the Mk I PAIR's own materialized remote edge — mirrors exactly what
     * {@code QuantumEndpointBlockEntity}'s constructor does: {@code getMainNode().setFlags(GridFlags.DENSE_CAPACITY)} +
     * {@code getMainNode().setCapacityTier(CapacityTier.STANDARD)}.
     */
    private GridNode makePairNode() {
        var node = makeReadyNode(GridFlags.DENSE_CAPACITY);
        node.setCapacityTier(CapacityTier.STANDARD);
        return node;
    }

    /** Both endpoints of one materialized Mk I PAIR remote {@code GridConnection}, already connected. */
    private record PairEdge(GridNode a, GridNode b) {
    }

    private PairEdge makePairEdge() {
        var a = makePairNode();
        var b = makePairNode();
        assertThat(GridHelper.createConnection(a, b)).as("materializing the Mk I PAIR's own remote edge").isNotNull();
        return new PairEdge(a, b);
    }

    private GridNode makeDenseCable(CapacityTier tier) {
        var node = makeReadyNode(GridFlags.DENSE_CAPACITY);
        node.setCapacityTier(tier);
        return node;
    }

    private GridNode makeController(CapacityTier tier) {
        var controllerOwner = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(controllerOwner, GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
        controllerNode.setCapacityTier(tier);
        when(controllerOwner.getGridNode()).thenReturn(controllerNode);
        when(controllerOwner.getBlockPos()).thenReturn(BlockPos.ZERO);
        return controllerNode;
    }

    private GridNode makeConsumer() {
        return makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
    }

    private List<GridNode> attachConsumers(GridNode cable, int count) {
        List<GridNode> consumers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var consumer = makeConsumer();
            assertThat(GridHelper.createConnection(cable, consumer)).isNotNull();
            consumers.add(consumer);
        }
        return consumers;
    }

    /** One simulated ME-P2P tunnel device, mirrors {@code HighTierP2PTest}'s own fixture exactly. */
    private record SimTunnel(GridNode main, GridNode outer) {
    }

    private SimTunnel makeTunnel(CapacityTier outerTier) {
        var owner = new Object();
        var main = makeOwnedNode(owner, GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
        var outer = makeOwnedNode(owner, GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
        outer.setCapacityTier(outerTier);
        return new SimTunnel(main, outer);
    }

    private PathingService pathingServiceOf(GridNode anyMemberNode) {
        return (PathingService) anyMemberNode.getGrid().getPathingService();
    }

    private int usedChannelsOf(GridNode anyMemberNode) {
        return pathingServiceOf(anyMemberNode).getUsedChannels();
    }

    // =====================================================================
    // 1. Standard Controller 32 -> Standard Dense 32 -> Mk I Quantum 32 -> Standard Dense -> consumers = 32.
    // =====================================================================

    @Test
    void scenario1_standardControllerStandardDenseMkIQuantumStandardDenseEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.STANDARD);
        var denseIn = makeDenseCable(CapacityTier.STANDARD);
        var pair = makePairEdge();
        var denseOut = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(controller, denseIn);
        GridHelper.createConnection(denseIn, pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        var consumers = attachConsumers(denseOut, 40);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
        assertThat(consumers.stream().filter(c -> c.getUsedChannels() > 0).count()).isEqualTo(32);
    }

    // =====================================================================
    // 2. Cosmic Controller 256 -> Cosmic Dense 256 -> Mk I Quantum 32 -> Cosmic Dense 256 -> consumers = 32.
    // =====================================================================

    @Test
    void scenario2_cosmicControllerCosmicDenseMkIQuantumCosmicDenseEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.COSMIC);
        var denseIn = makeDenseCable(CapacityTier.COSMIC);
        var pair = makePairEdge();
        var denseOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, denseIn);
        GridHelper.createConnection(denseIn, pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        attachConsumers(denseOut, 40);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // 3. Cosmic Controller 256 -> Cosmic Dense -> Singularity P2P 128 -> Mk I Quantum 32 -> Cosmic Dense = 32.
    // =====================================================================

    @Test
    void scenario3_cosmicControllerCosmicDenseSingularityP2PMkIQuantumCosmicDenseEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.COSMIC);
        var denseIn = makeDenseCable(CapacityTier.COSMIC);
        var tunnel = makeTunnel(CapacityTier.SINGULARITY);
        var pair = makePairEdge();
        var denseOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, denseIn);
        GridHelper.createConnection(denseIn, tunnel.outer());
        GridHelper.createConnection(tunnel.outer(), pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        attachConsumers(denseOut, 40);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // 4. Cosmic Controller 256 -> Mk I Quantum 32 -> Quantum P2P 64 -> consumers = 32.
    // =====================================================================

    @Test
    void scenario4_cosmicControllerMkIQuantumQuantumP2PEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.COSMIC);
        var pair = makePairEdge();
        var tunnel = makeTunnel(CapacityTier.QUANTUM);
        GridHelper.createConnection(controller, pair.a());
        GridHelper.createConnection(pair.b(), tunnel.outer());
        attachConsumers(tunnel.outer(), 70);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // 5. Standard Controller 32 -> Cosmic Dense 256 -> Mk I Quantum 32 = 32.
    // =====================================================================

    @Test
    void scenario5_standardControllerCosmicDenseMkIQuantumEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.STANDARD);
        var cosmicDense = makeDenseCable(CapacityTier.COSMIC);
        var pair = makePairEdge();
        GridHelper.createConnection(controller, cosmicDense);
        GridHelper.createConnection(cosmicDense, pair.a());
        attachConsumers(pair.b(), 40);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // 6. Quantum Controller 64 -> Quantum Dense 64 -> Mk I Quantum 32 -> Cosmic Dense 256 = 32.
    // =====================================================================

    @Test
    void scenario6_quantumControllerQuantumDenseMkIQuantumCosmicDenseEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.QUANTUM);
        var quantumDense = makeDenseCable(CapacityTier.QUANTUM);
        var pair = makePairEdge();
        var cosmicDense = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, quantumDense);
        GridHelper.createConnection(quantumDense, pair.a());
        GridHelper.createConnection(pair.b(), cosmicDense);
        attachConsumers(cosmicDense, 70);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // 7. Upstream bottleneck = 17 available channels -> Mk I Quantum 32: downstream usable <= 17, NEVER the
    // "restored" 32 the pair's own ceiling would otherwise allow. A Standard(32) controller's budget is shared: 15
    // channels are already claimed by an unrelated direct branch BEFORE the pair branch is even attached, leaving
    // exactly 17 of the controller's 32 for the pair's own branch.
    // =====================================================================

    @Test
    void scenario7_upstreamSharedBottleneckOfSeventeenIsNeverRestoredToTheMkIQuantumsOwnThirtyTwoCeiling() {
        var controller = makeController(CapacityTier.STANDARD);
        var hub = makeReadyNode(GridFlags.DENSE_CAPACITY);
        GridHelper.createConnection(controller, hub);
        // An unrelated direct branch claims 15 of the controller's 32 channels first.
        attachConsumers(hub, 15);

        var pair = makePairEdge();
        var denseOut = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(hub, pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        var pairBranchConsumers = attachConsumers(denseOut, 30); // far more than the 17 that can possibly remain

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32); // the controller's own hard ceiling, never exceeded
        long onlineOnPairBranch = pairBranchConsumers.stream().filter(c -> c.getUsedChannels() > 0).count();
        assertThat(onlineOnPairBranch)
                .as("the pair branch must never see its own 32-ceiling 'restored' past what the shared upstream "
                        + "budget actually has left")
                .isEqualTo(17);
    }

    // =====================================================================
    // Round 11 pre-gate (Phase B.C): the EXPLICIT 31/32/33 boundary of the Mk I ceiling.
    //
    // Scenarios 1-8 above all prove "an over-subscribed downstream is clamped to 32". None of them proves the exact
    // edge of that clamp - that 31 is fully served, 32 is fully served, and 33 is served EXACTLY 32 with the 33rd
    // consumer left with no channel at all. An off-by-one in either direction (a 33rd consumer sneaking online, or a
    // 32nd being denied) would pass every one of the scenarios above unnoticed, which is precisely why the boundary
    // gets its own three tests here.
    // =====================================================================

    /** Controller -> dense -> Mk I PAIR edge -> dense -> {@code consumerCount} consumers; returns those online. */
    private long onlineConsumersAcrossMkIPair(int consumerCount) {
        var controller = makeController(CapacityTier.COSMIC);
        var denseIn = makeDenseCable(CapacityTier.COSMIC);
        var pair = makePairEdge();
        var denseOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, denseIn);
        GridHelper.createConnection(denseIn, pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        var consumers = attachConsumers(denseOut, consumerCount);

        runTick(controller.getGrid(), 3);

        return consumers.stream().filter(c -> c.getUsedChannels() > 0).count();
    }

    @Test
    void boundary_thirtyOneConsumersBehindTheMkIPairAreAllServed() {
        assertThat(onlineConsumersAcrossMkIPair(31))
                .as("31 is strictly under the Mk I ceiling - every consumer must be online")
                .isEqualTo(31);
    }

    @Test
    void boundary_thirtyTwoConsumersBehindTheMkIPairAreAllServed() {
        assertThat(onlineConsumersAcrossMkIPair(32))
                .as("32 is exactly the Mk I ceiling - every consumer must still be online")
                .isEqualTo(32);
    }

    @Test
    void boundary_thirtyThreeConsumersBehindTheMkIPairGetExactlyThirtyTwoAndTheThirdThirdIsUnavailable() {
        assertThat(onlineConsumersAcrossMkIPair(33))
                .as("33 exceeds the Mk I ceiling by exactly one - exactly 32 allocated, the 33rd unavailable")
                .isEqualTo(32);
    }

    // =====================================================================
    // Round 11 pre-gate (Phase B.C): upstream bottleneck is never "restored" by a wider downstream.
    //
    // Scenario 7 proves the 17-remaining case. These two pin the two numbers the Round 11 brief names explicitly:
    // an upstream with 31 left stays 31 (the Mk I's own 32 never lifts it), and an upstream with 64 available is
    // itself clamped down to the Mk I's 32 (a wider upstream never lifts the Mk I either). Together they prove the
    // rule is a genuine minimum-along-path in BOTH directions, not a one-sided clamp.
    // =====================================================================

    @Test
    void boundary_upstreamRemainingThirtyOneIsNeverLiftedToTheMkICeilingByAWiderDownstream() {
        var controller = makeController(CapacityTier.STANDARD); // 32 total
        var hub = makeReadyNode(GridFlags.DENSE_CAPACITY);
        GridHelper.createConnection(controller, hub);
        // Exactly ONE channel is claimed by an unrelated branch first, leaving 31 upstream.
        attachConsumers(hub, 1);

        var pair = makePairEdge(); // Mk I ceiling 32
        var denseOut = makeDenseCable(CapacityTier.COSMIC); // downstream far wider than 32
        GridHelper.createConnection(hub, pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        var pairBranch = attachConsumers(denseOut, 64);

        runTick(controller.getGrid(), 3);

        assertThat(pairBranch.stream().filter(c -> c.getUsedChannels() > 0).count())
                .as("upstream remaining 31, Mk I 32, downstream >32 => 31 - never restored to 32")
                .isEqualTo(31);
    }

    @Test
    void boundary_upstreamSixtyFourIsClampedDownToTheMkICeilingOfThirtyTwo() {
        var controller = makeController(CapacityTier.QUANTUM); // 64 total upstream
        var denseIn = makeDenseCable(CapacityTier.QUANTUM);
        var pair = makePairEdge(); // Mk I ceiling 32
        var denseOut = makeDenseCable(CapacityTier.COSMIC); // 256 downstream
        GridHelper.createConnection(controller, denseIn);
        GridHelper.createConnection(denseIn, pair.a());
        GridHelper.createConnection(pair.b(), denseOut);
        var consumers = attachConsumers(denseOut, 256);

        runTick(controller.getGrid(), 3);

        assertThat(consumers.stream().filter(c -> c.getUsedChannels() > 0).count())
                .as("upstream 64, Mk I 32, downstream 256 => 32 - the Mk I edge is the real bottleneck")
                .isEqualTo(32);
    }

    // =====================================================================
    // 8. Recursive P2P -> Mk I Quantum -> recursive P2P: minimum-along-path preserved, no generation, exactly the
    // Round 9/HighTierP2PTest "nesting" shape with the Mk I PAIR's own edge spliced into the middle.
    // =====================================================================

    @Test
    void scenario8_recursiveP2pAroundTheMkIQuantumPairPreservesMinimumAlongPathWithoutGeneration() {
        var home = makeReadyNode();
        var a = makeTunnel(CapacityTier.COSMIC);
        assertThat(GridHelper.createConnection(home, a.main())).isNotNull();

        var middle = makeReadyNode();
        var aOut = makeTunnel(CapacityTier.COSMIC);
        assertThat(GridHelper.createConnection(aOut.main(), middle)).isNotNull();
        assertThat(GridHelper.createConnection(a.outer(), aOut.outer())).isNotNull();

        // The Mk I PAIR's own remote edge sits directly in "middle"'s local network.
        var pair = makePairEdge();
        assertThat(GridHelper.createConnection(middle, pair.a())).isNotNull();

        var far = makeReadyNode();
        var b = makeTunnel(CapacityTier.COSMIC);
        assertThat(GridHelper.createConnection(pair.b(), b.main())).isNotNull();
        var bOut = makeTunnel(CapacityTier.COSMIC);
        assertThat(GridHelper.createConnection(bOut.main(), far)).isNotNull();
        assertThat(GridHelper.createConnection(b.outer(), bOut.outer())).isNotNull();

        var consumer = makeConsumer();
        assertThat(GridHelper.createConnection(far, consumer)).isNotNull();

        runTick(middle.getGrid(), 3);
        runTick(far.getGrid(), 3);

        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(far).getAdHocNetworkError()).isNull();
        // middle's own grid: aOut's main (1) + the pair's own "a" side endpoint cost (1, it's a plain DENSE_CAPACITY
        // node, not itself channel-requiring) ... what actually costs a channel here is b's main node reaching back
        // through the pair to middle - exactly 1, plus aOut's main (1) = 2. Never 256, never summed/multiplied by
        // nesting depth or tier - P2P (and the Mk I PAIR transport it wraps) is transport, never generation.
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2);
        assertThat(pathingServiceOf(far).getUsedChannels()).isEqualTo(2); // bOut's main (1) + consumer (1)
    }
}
