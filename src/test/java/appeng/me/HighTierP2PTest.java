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
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.pathing.CapacityTier;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.service.PathingService;

/**
 * Round 9 ("HIGH-TIER ME P2P") - proves {@link appeng.parts.p2p.QuantumMEP2PTunnelPart}/
 * {@link appeng.parts.p2p.SingularityMEP2PTunnelPart}/{@link appeng.parts.p2p.CosmicMEP2PTunnelPart} (each of which
 * calls {@code getOuterNode().setCapacityTier(TIER)} once in its constructor, mirroring {@link MEP2PTunnelPart}'s
 * unmodified {@code outerNode} field exactly) turn ME P2P into a real, tier-aware TRANSPORT CEILING - never a channel
 * SOURCE - through the REAL production {@link PathingService}/{@link PathingCalculation}/{@link ControllerValidator}
 * allocation code. No {@code QuantumPathingService}, no separate tunnel network, no per-tier scheduler exists anywhere
 * in this change - {@code MEP2PTunnelPart} itself is untouched except for exposing
 * {@link appeng.parts.p2p.MEP2PTunnelPart#getOuterNode()}.
 * <p/>
 * Two distinct, both real, fixture shapes are used, matching the two distinct things being proven:
 * <ul>
 * <li><b>Single-grid chain</b> (mirrors {@code CapacityTierTest}'s controller+dense-cable chains): a tunnel's OUTER
 * node spliced directly into one controller-rooted tree via {@link GridHelper#createConnection}, exactly the same
 * physical pattern {@code RecursiveP2PTest}'s own cycle-detection tests already use for {@code a.outer()} (see e.g.
 * {@code RecursiveP2PTest#multiHopAncestorChainCycleIsDetectedInControllerModeWithoutBlockingAnUnrelatedTunnelBetween}).
 * This is what makes the outer node's own {@code CapacityTier}-scaled {@code DENSE_CAPACITY} base (32/64/128/256) a
 * REAL bottleneck node in the SAME {@code subtreeMaxChannels} walk {@code CapacityTierTest} already validates for
 * cables/controllers - used for every exact-ceiling / mixed-environment-bottleneck scenario in this file.</li>
 * <li><b>Two-grid nested pair</b> (mirrors {@code RecursiveP2PTest} exactly, tier added): an input tunnel's main node
 * wired into "home", linked via the manual outer-to-outer frequency edge to an output tunnel's main node wired into
 * "middle" - two independent {@link Grid}s. Used for nesting/no-generation/cycle-detection/endpoint-cost tests, where
 * what matters is topology, not a specific numeric ceiling.</li>
 * </ul>
 */
class HighTierP2PTest extends AbstractGridNodeTest {

    @Mock
    private MockedStatic<IPlayerRegistry> playerRegistry;

    @BeforeEach
    void stubPlayerRegistryForAchievements() {
        playerRegistry.when(() -> IPlayerRegistry.getConnected(any(), anyInt())).thenReturn(null);
    }

    // =====================================================================
    // Fixture helpers
    // =====================================================================

    private GridNode makeOwnedNode(Object nodeOwner, GridFlags... flags) {
        var node = new GridNode(level, nodeOwner, listener, Set.of(flags));
        node.markReady();
        return node;
    }

    /** One simulated ME-P2P tunnel device: a main node (always Standard-tier) and its own tiered outer node. */
    private record SimTunnel(GridNode main, GridNode outer, Object owner) {
    }

    /**
     * Mirrors {@code MEP2PTunnelPart}'s real node setup exactly: main = REQUIRE_CHANNEL+COMPRESSED_CHANNEL, always
     * Standard-tier (never touched by any tier subclass); outer = DENSE_CAPACITY+CANNOT_CARRY_COMPRESSED, tiered the
     * same way {@code QuantumMEP2PTunnelPart}/etc.'s constructor calls {@code getOuterNode().setCapacityTier}.
     */
    private SimTunnel makeTunnel(CapacityTier outerTier) {
        var owner = new Object();
        var main = makeOwnedNode(owner, GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
        var outer = makeOwnedNode(owner, GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
        outer.setCapacityTier(outerTier);
        return new SimTunnel(main, outer, owner);
    }

    private void wireIntoLocalNetwork(SimTunnel tunnel, GridNode localCable) {
        var conn = GridHelper.createConnection(localCable, tunnel.main());
        assertThat(conn).as("wiring tunnel main node into its own local network").isNotNull();
    }

    private void linkFrequency(SimTunnel input, SimTunnel output) {
        var conn = GridHelper.createConnection(input.outer(), output.outer());
        assertThat(conn).as("forming the P2P frequency link").isNotNull();
    }

    private PathingService pathingServiceOf(GridNode anyMemberNode) {
        return (PathingService) anyMemberNode.getGrid().getPathingService();
    }

    private int usedChannelsOf(GridNode anyMemberNode) {
        return pathingServiceOf(anyMemberNode).getUsedChannels();
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

    // =====================================================================
    // 1. EXACT CEILINGS - outer node's own getMaxChannels(), the same formula H/I already proved for
    // cables/controllers.
    // =====================================================================

    @Test
    void standardP2POuterNodeMaxChannelsIsThirtyTwo() {
        assertThat(makeTunnel(CapacityTier.STANDARD).outer().getMaxChannels()).isEqualTo(32);
    }

    @Test
    void quantumP2POuterNodeMaxChannelsIsSixtyFour() {
        assertThat(makeTunnel(CapacityTier.QUANTUM).outer().getMaxChannels()).isEqualTo(64);
    }

    @Test
    void singularityP2POuterNodeMaxChannelsIsOneTwentyEight() {
        assertThat(makeTunnel(CapacityTier.SINGULARITY).outer().getMaxChannels()).isEqualTo(128);
    }

    @Test
    void cosmicP2POuterNodeMaxChannelsIsTwoFiftySix() {
        assertThat(makeTunnel(CapacityTier.COSMIC).outer().getMaxChannels()).isEqualTo(256);
    }

    @Test
    void mainNodeStaysStandardTierRegardlessOfOuterNodeTier() {
        // Main node is never touched by the tier subclasses (only getOuterNode().setCapacityTier(...) is called) -
        // confirm this holds even when the outer node is the highest tier.
        var tunnel = makeTunnel(CapacityTier.COSMIC);
        assertThat(tunnel.main().getCapacityTier()).isEqualTo(CapacityTier.STANDARD);
        assertThat(tunnel.main().getMaxChannels()).isEqualTo(8); // ordinary Standard Smart-cable-equivalent cap
    }

    // =====================================================================
    // 2. SATURATION - each tier's outer node, spliced into one controller-rooted chain, saturates at exactly its
    // ceiling with the real PathingService; the next consumer stays offline, never an exception.
    // =====================================================================

    private void assertOuterNodeSaturatesAt(CapacityTier tier, int ceiling, int consumerCount) {
        var controller = makeController(tier);
        var tunnel = makeTunnel(tier);
        GridHelper.createConnection(controller, tunnel.outer());
        var consumers = attachConsumers(tunnel.outer(), consumerCount);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(ceiling);
        long online = consumers.stream().filter(c -> c.getUsedChannels() > 0).count();
        long offline = consumers.stream().filter(c -> c.getUsedChannels() == 0).count();
        assertThat(online).isEqualTo(ceiling);
        assertThat(offline).isEqualTo(consumerCount - ceiling);
    }

    @Test
    void standardP2PSaturatesAtExactlyThirtyTwo() {
        assertOuterNodeSaturatesAt(CapacityTier.STANDARD, 32, 40);
    }

    @Test
    void quantumP2PSaturatesAtExactlySixtyFour() {
        assertOuterNodeSaturatesAt(CapacityTier.QUANTUM, 64, 70);
    }

    @Test
    void singularityP2PSaturatesAtExactlyOneTwentyEight() {
        assertOuterNodeSaturatesAt(CapacityTier.SINGULARITY, 128, 135);
    }

    @Test
    void cosmicP2PSaturatesAtExactlyTwoFiftySix() {
        assertOuterNodeSaturatesAt(CapacityTier.COSMIC, 256, 260);
    }

    // =====================================================================
    // 3. MIXED ENVIRONMENT - a P2P tunnel's own ceiling vs. the surrounding controller/cable tier.
    // =====================================================================

    @Test
    void standardP2PInsideCosmicEnvironmentIsBottleneckedAtThirtyTwo() {
        var controller = makeController(CapacityTier.COSMIC);
        var cosmicCableIn = makeDenseCable(CapacityTier.COSMIC);
        var tunnel = makeTunnel(CapacityTier.STANDARD);
        var cosmicCableOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cosmicCableIn);
        GridHelper.createConnection(cosmicCableIn, tunnel.outer());
        GridHelper.createConnection(tunnel.outer(), cosmicCableOut);
        attachConsumers(cosmicCableOut, 40);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void quantumP2PInsideCosmicEnvironmentIsBottleneckedAtSixtyFour() {
        var controller = makeController(CapacityTier.COSMIC);
        var cosmicCableIn = makeDenseCable(CapacityTier.COSMIC);
        var tunnel = makeTunnel(CapacityTier.QUANTUM);
        var cosmicCableOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cosmicCableIn);
        GridHelper.createConnection(cosmicCableIn, tunnel.outer());
        GridHelper.createConnection(tunnel.outer(), cosmicCableOut);
        attachConsumers(cosmicCableOut, 70);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    @Test
    void singularityP2PInsideCosmicEnvironmentIsBottleneckedAtOneTwentyEight() {
        var controller = makeController(CapacityTier.COSMIC);
        var cosmicCableIn = makeDenseCable(CapacityTier.COSMIC);
        var tunnel = makeTunnel(CapacityTier.SINGULARITY);
        var cosmicCableOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cosmicCableIn);
        GridHelper.createConnection(cosmicCableIn, tunnel.outer());
        GridHelper.createConnection(tunnel.outer(), cosmicCableOut);
        attachConsumers(cosmicCableOut, 135);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(128);
    }

    @Test
    void cosmicP2PUnderStandardControllerIsBottleneckedAtThirtyTwo() {
        var controller = makeController(CapacityTier.STANDARD);
        var tunnel = makeTunnel(CapacityTier.COSMIC);
        var cosmicCableOut = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, tunnel.outer());
        GridHelper.createConnection(tunnel.outer(), cosmicCableOut);
        attachConsumers(cosmicCableOut, 40);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // 4. THE FIVE EXPLICIT "P2P CAPACITY HARD RULE" SCENARIOS (brief, Part 2) - effective capacity is always the
    // narrowest relevant transport/path element, proven end to end through the real controller-rooted allocation.
    // =====================================================================

    @Test
    void cosmicControllerCosmicDenseStandardP2PCosmicDenseEqualsThirtyTwo() {
        var controller = makeController(CapacityTier.COSMIC);
        var denseA = makeDenseCable(CapacityTier.COSMIC);
        var tunnel = makeTunnel(CapacityTier.STANDARD);
        var denseB = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, denseA);
        GridHelper.createConnection(denseA, tunnel.outer());
        GridHelper.createConnection(tunnel.outer(), denseB);
        attachConsumers(denseB, 40);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void cosmicControllerCosmicP2PQuantumP2PCosmicP2PEqualsSixtyFour() {
        var controller = makeController(CapacityTier.COSMIC);
        var tunnelCosmicA = makeTunnel(CapacityTier.COSMIC);
        var tunnelQuantum = makeTunnel(CapacityTier.QUANTUM);
        var tunnelCosmicB = makeTunnel(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, tunnelCosmicA.outer());
        GridHelper.createConnection(tunnelCosmicA.outer(), tunnelQuantum.outer());
        GridHelper.createConnection(tunnelQuantum.outer(), tunnelCosmicB.outer());
        attachConsumers(tunnelCosmicB.outer(), 70);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    @Test
    void singularityControllerCosmicP2PSingularityP2PCosmicDenseEqualsOneTwentyEight() {
        var controller = makeController(CapacityTier.SINGULARITY);
        var tunnelCosmic = makeTunnel(CapacityTier.COSMIC);
        var tunnelSingularity = makeTunnel(CapacityTier.SINGULARITY);
        var cosmicDense = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, tunnelCosmic.outer());
        GridHelper.createConnection(tunnelCosmic.outer(), tunnelSingularity.outer());
        GridHelper.createConnection(tunnelSingularity.outer(), cosmicDense);
        attachConsumers(cosmicDense, 135);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(128);
    }

    @Test
    void quantumControllerCosmicP2PEqualsSixtyFour() {
        var controller = makeController(CapacityTier.QUANTUM);
        var tunnel = makeTunnel(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, tunnel.outer());
        attachConsumers(tunnel.outer(), 70);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    @Test
    void cosmicControllerQuantumDenseCosmicP2PEqualsSixtyFour() {
        var controller = makeController(CapacityTier.COSMIC);
        var quantumDense = makeDenseCable(CapacityTier.QUANTUM);
        var tunnel = makeTunnel(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, quantumDense);
        GridHelper.createConnection(quantumDense, tunnel.outer());
        attachConsumers(tunnel.outer(), 70);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    // =====================================================================
    // 5. RECURSIVE HIGH-TIER P2P - two-grid nested pairs, mixed tiers, proving nesting works and cycle semantics are
    // completely tier-agnostic (owner-identity based, per Round 7/8's
    // GridNode#getSubtreeCompressedChannelBlockerOwners).
    // =====================================================================

    @Test
    void standardThenQuantumThenCosmicNestingWorksWithoutRejection() {
        var home = makeReadyNode();
        var a = makeTunnel(CapacityTier.STANDARD);
        wireIntoLocalNetwork(a, home);

        var middle = makeReadyNode();
        var aOut = makeTunnel(CapacityTier.QUANTUM);
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        var b = makeTunnel(CapacityTier.QUANTUM);
        wireIntoLocalNetwork(b, middle);

        var far = makeReadyNode();
        var bOut = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        var consumer = makeConsumer();
        GridHelper.createConnection(far, consumer);

        runTick(middle.getGrid(), 3);
        runTick(far.getGrid(), 3);

        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(far).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2); // aOut's main + b's main
        assertThat(pathingServiceOf(far).getUsedChannels()).isEqualTo(2); // bOut's main + consumer
    }

    @Test
    void cosmicThenStandardThenCosmicNestingWorksWithoutRejection() {
        var home = makeReadyNode();
        var a = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(a, home);

        var middle = makeReadyNode();
        var aOut = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        var b = makeTunnel(CapacityTier.STANDARD);
        wireIntoLocalNetwork(b, middle);

        var far = makeReadyNode();
        var bOut = makeTunnel(CapacityTier.STANDARD);
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        var consumer = makeConsumer();
        GridHelper.createConnection(far, consumer);

        runTick(middle.getGrid(), 3);
        runTick(far.getGrid(), 3);

        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(far).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2);
        assertThat(pathingServiceOf(far).getUsedChannels()).isEqualTo(2);
    }

    @Test
    void quantumThenSingularityThenQuantumNestingWorksWithoutRejection() {
        var home = makeReadyNode();
        var a = makeTunnel(CapacityTier.QUANTUM);
        wireIntoLocalNetwork(a, home);

        var middle = makeReadyNode();
        var aOut = makeTunnel(CapacityTier.SINGULARITY);
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        var b = makeTunnel(CapacityTier.SINGULARITY);
        wireIntoLocalNetwork(b, middle);

        var far = makeReadyNode();
        var bOut = makeTunnel(CapacityTier.QUANTUM);
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        var consumer = makeConsumer();
        GridHelper.createConnection(far, consumer);

        runTick(middle.getGrid(), 3);
        runTick(far.getGrid(), 3);

        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(far).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2);
        assertThat(pathingServiceOf(far).getUsedChannels()).isEqualTo(2);
    }

    // =====================================================================
    // 6. CYCLES WITH MIXED TIERS - same owner-identity mechanism as Round 7/8, proven tier-agnostic empirically.
    // =====================================================================

    @Test
    void mixedTierMutualCycleBetweenTwoTunnelsIsDetectedWithoutBlockingAnUnrelatedTunnel() {
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel(CapacityTier.COSMIC);
        var b = makeTunnel(CapacityTier.QUANTUM);

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);
        wireIntoLocalNetwork(b, cable1);

        var cable2 = makeReadyNode();
        GridHelper.createConnection(cable1, cable2);
        GridHelper.createConnection(b.outer(), cable2);

        var cable3 = makeReadyNode();
        GridHelper.createConnection(cable2, cable3);
        wireIntoLocalNetwork(a, cable3);

        // An unrelated, healthy tunnel pair, merged into the same controller grid as a sibling branch.
        var z = makeTunnel(CapacityTier.SINGULARITY);
        var healthyHome = makeReadyNode();
        wireIntoLocalNetwork(z, healthyHome);
        GridHelper.createConnection(controllerNode, healthyHome);
        var healthyMiddle = makeReadyNode();
        var zOut = makeTunnel(CapacityTier.SINGULARITY);
        wireIntoLocalNetwork(zOut, healthyMiddle);
        linkFrequency(z, zOut);
        var healthyConsumer = makeConsumer();
        GridHelper.createConnection(healthyMiddle, healthyConsumer);

        runTick(controllerNode.getGrid(), 3);
        runTick(healthyMiddle.getGrid(), 3);

        assertThat(a.main().getUsedChannels())
                .as("A's own outer reappears on A's own path back to the controller (via B) - refused, even with "
                        + "mixed tiers (A=Cosmic, B=Quantum) on either side of the cycle")
                .isZero();
        assertThat(b.main().getUsedChannels())
                .as("B participates in the cycle but is not itself self-referencing - keeps its channel")
                .isEqualTo(1);
        assertThat(z.main().getUsedChannels())
                .as("an unrelated Singularity-tier tunnel pair sitting right next to the cycle is unaffected")
                .isEqualTo(1);
        assertThat(pathingServiceOf(healthyMiddle).getAdHocNetworkError()).isNull();
    }

    @Test
    void mixedTierThreeTunnelDependencyCycleIsDetectedWithoutPunishingTheOtherTwo() {
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel(CapacityTier.COSMIC);
        var b = makeTunnel(CapacityTier.STANDARD);
        var c = makeTunnel(CapacityTier.SINGULARITY);

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);
        wireIntoLocalNetwork(b, cable1); // A carries B

        var cable2 = makeReadyNode();
        GridHelper.createConnection(cable1, cable2);
        GridHelper.createConnection(b.outer(), cable2);
        wireIntoLocalNetwork(c, cable2); // B carries C

        var cable3 = makeReadyNode();
        GridHelper.createConnection(cable2, cable3);
        GridHelper.createConnection(c.outer(), cable3);

        var cable4 = makeReadyNode();
        GridHelper.createConnection(cable3, cable4);
        wireIntoLocalNetwork(a, cable4); // C carries A - loop closes

        runTick(controllerNode.getGrid(), 3);

        assertThat(a.main().getUsedChannels()).as("A (Cosmic) refused - closes the loop onto itself").isZero();
        assertThat(b.main().getUsedChannels()).as("B (Standard) is a link in the cycle but not self-referencing")
                .isEqualTo(1);
        assertThat(c.main().getUsedChannels()).as("C (Singularity) is a link in the cycle but not self-referencing")
                .isEqualTo(1);
    }

    // =====================================================================
    // 7. BREAK CYCLE / RECOVERY - clearing the offending edge restores normal operation, mixed tiers.
    // =====================================================================

    @Test
    void breakingMixedTierCycleEdgeAutomaticallyRestoresNormalChannelsOnNextRepath() {
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel(CapacityTier.COSMIC);
        var b = makeTunnel(CapacityTier.QUANTUM);

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);
        wireIntoLocalNetwork(b, cable1);

        var cable2 = makeReadyNode();
        GridHelper.createConnection(cable1, cable2);
        GridHelper.createConnection(b.outer(), cable2);

        var cable3 = makeReadyNode();
        GridHelper.createConnection(cable2, cable3);
        var closingEdge = GridHelper.createConnection(cable3, a.main());
        assertThat(closingEdge).isNotNull();

        runTick(controllerNode.getGrid(), 3);
        assertThat(a.main().getUsedChannels()).as("cycle detected, A refused before the break").isZero();

        closingEdge.destroy();
        var freshCable = makeReadyNode();
        GridHelper.createConnection(controllerNode, freshCable);
        GridHelper.createConnection(freshCable, a.main());

        runTick(controllerNode.getGrid(), 3);
        assertThat(a.main().getUsedChannels())
                .as("recovered automatically on the next ordinary repath, even with mixed tiers on the (former) "
                        + "cycle's two tunnels")
                .isEqualTo(1);
    }

    // =====================================================================
    // 8. NO CHANNEL GENERATION - nested high-tier P2P never sums/multiplies capacity.
    // =====================================================================

    @Test
    void nestedHighTierP2pDoesNotGenerateExtraCapacityFromNestingDepthOrTier() {
        var home = makeReadyNode();
        var a = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(a, home);
        var middle = makeReadyNode();
        var aOut = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);
        var b = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(b, middle);
        var far = makeReadyNode();
        var bOut = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        for (int i = 0; i < 3; i++) {
            GridHelper.createConnection(far, makeConsumer());
        }

        runTick(far.getGrid(), 3);
        var ps = pathingServiceOf(far);
        assertThat(ps.getAdHocNetworkError()).isNull();
        // bOut's own main node (1) + 3 consumers = 4 - never 256, never 256*256, regardless of every tunnel in this
        // chain being Cosmic (highest) tier: P2P is transport, not generation.
        assertThat(ps.getUsedChannels()).isEqualTo(4);
    }

    // =====================================================================
    // 9. NO ENDPOINT COST BYPASS - a tunnel's own main node still costs exactly 1 channel in its own local network
    // at every hop, regardless of its outer node's tier.
    // =====================================================================

    @Test
    void tunnelEndpointLocalChannelCostIsPreservedRegardlessOfOuterNodeTier() {
        var home = makeReadyNode();
        var a = makeTunnel(CapacityTier.COSMIC); // highest tier outer, but its own main endpoint cost is unaffected
        wireIntoLocalNetwork(a, home);
        var middle = makeReadyNode();
        var aOut = makeTunnel(CapacityTier.COSMIC);
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        runTick(home.getGrid(), 3);
        runTick(middle.getGrid(), 3);

        assertThat(pathingServiceOf(home).getUsedChannels()).isEqualTo(1);
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(1);
    }

    // =====================================================================
    // 10. TIER SURVIVES REPATH - not NBT-persisted (see 11 below), but stable in memory across many repaths, exactly
    // like Round 8 proved for cable/controller tiers.
    // =====================================================================

    @Test
    void outerNodeTierIsStableAcrossManyRepaths() {
        var controller = makeController(CapacityTier.QUANTUM);
        var tunnel = makeTunnel(CapacityTier.QUANTUM);
        GridHelper.createConnection(controller, tunnel.outer());
        attachConsumers(tunnel.outer(), 70);

        for (int i = 0; i < 10; i++) {
            runTick(controller.getGrid(), 1);
            assertThat(tunnel.outer().getCapacityTier()).isEqualTo(CapacityTier.QUANTUM);
            assertThat(usedChannelsOf(controller)).isEqualTo(64);
        }
    }

    // =====================================================================
    // 11. TIER IS NOT NBT-PERSISTED - re-derived from the owning part class's constructor every time, exactly like
    // GridFlags.DENSE_CAPACITY itself and Round 8's CapacityTier for cables/controllers (see ManagedGridNodeTest's own
    // serialize/deserialize pattern, reused here).
    // =====================================================================

    @Test
    void capacityTierIsNeverWrittenToOrRestoredFromNbt() {
        var owner = new Object();
        var mgn = new ManagedGridNode(owner, listener);
        mgn.setFlags(GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
        mgn.setCapacityTier(CapacityTier.COSMIC);
        mgn.create(level, BlockPos.ZERO);
        assertThat(mgn.getNode().getCapacityTier()).isEqualTo(CapacityTier.COSMIC);

        var output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        mgn.serialize(output);
        var savedNode = output.buildResult();
        mgn.destroy();

        // A brand-new node, restored from the SAME saved NBT, defaults back to STANDARD - proving the tier was never
        // written into the NBT payload in the first place (exactly like GridFlags.DENSE_CAPACITY: it is set fresh by
        // the owning part's constructor - here simulated by simply not calling setCapacityTier again - every time the
        // node is (re)created, never read back from saved data).
        var restored = new ManagedGridNode(owner, listener);
        restored.deserialize(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, savedNode));
        restored.create(level, BlockPos.ZERO);
        assertThat(restored.getNode().getCapacityTier())
                .as("tier is not restored from NBT - a real Quantum/Singularity/Cosmic ME P2P tunnel part's "
                        + "constructor re-applies its own tier via getOuterNode().setCapacityTier(TIER) on every "
                        + "(re)creation instead, exactly like Round 8 already established for cable/controller tiers")
                .isEqualTo(CapacityTier.STANDARD);
    }

    // =====================================================================
    // 12. EXACT CAPACITY SURVIVES "SERIALIZATION" - 128/256 is never truncated/clamped/zeroed by the allocation path.
    // Already proven numerically by the saturation tests above (2); this test additionally proves the full round-trip
    // end-to-end for the two largest values via the real controller allocation, matching Round 8's own N-section
    // precedent (cosmicDenseCableSaturatesAtExactlyTwoFiftySixAndTheNextConsumerStaysOffline).
    // =====================================================================

    @Test
    void oneTwentyEightAndTwoFiftySixAreGrantedInFullNotTruncated() {
        var singularityController = makeController(CapacityTier.SINGULARITY);
        var singularityTunnel = makeTunnel(CapacityTier.SINGULARITY);
        GridHelper.createConnection(singularityController, singularityTunnel.outer());
        var singularityConsumers = attachConsumers(singularityTunnel.outer(), 130);
        runTick(singularityController.getGrid(), 3);
        assertThat(usedChannelsOf(singularityController)).isEqualTo(128);
        assertThat(singularityConsumers.stream().filter(c -> c.getUsedChannels() > 0).count()).isEqualTo(128);

        var cosmicController = makeController(CapacityTier.COSMIC);
        var cosmicTunnel = makeTunnel(CapacityTier.COSMIC);
        GridHelper.createConnection(cosmicController, cosmicTunnel.outer());
        var cosmicConsumers = attachConsumers(cosmicTunnel.outer(), 256);
        runTick(cosmicController.getGrid(), 3);
        assertThat(usedChannelsOf(cosmicController)).isEqualTo(256);
        assertThat(cosmicConsumers.stream().filter(c -> c.getUsedChannels() > 0).count()).isEqualTo(256);
    }
}
