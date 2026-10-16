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
import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.pathfinding.ControllerValidator;
import appeng.me.service.PathingService;

/**
 * Round 8 ("HIGH-CAPACITY ME INFRASTRUCTURE") — proves the generalized {@link CapacityTier} capacity model against the
 * REAL production {@link PathingService}/{@link PathingCalculation}/{@link GridConnection} allocation code, not an
 * isolated helper. Fixture strategy matches {@code RecursiveP2PTest}/{@code MultipointSingleLaneQ2Test}: raw
 * {@link GridNode}s wired directly with {@link GridHelper#createConnection}, a mocked {@link ControllerBlockEntity}
 * owner where a controller root is needed (production {@code PathingCalculation} only ever checks
 * {@code owner instanceof ControllerBlockEntity} and, for {@link ControllerValidator}, {@code getGridNode()} /
 * {@code getBlockPos()}).
 */
class CapacityTierTest extends AbstractGridNodeTest {

    /**
     * These tests are the first in the suite to legitimately push {@code PathingService.channelsInUse} past 8 (the
     * threshold for {@code PathingService#getAchievementBracket}), which triggers
     * {@code PathingService#achievementPost} to look up connected players via
     * {@code IPlayerRegistry#getConnected(MinecraftServer, int)} - that needs a real, ticking
     * {@code MinecraftServer}/overworld {@code DataStorage}, which this Mockito-based unit harness (see
     * {@link AbstractGridNodeTest}) intentionally does not provide (same pattern as the existing {@code Platform}
     * static mock there). Stub the static player-registry lookup directly, exactly like {@code Platform} is stubbed, so
     * achievement bookkeeping becomes a no-op and the real channel-allocation code path under test is otherwise
     * completely unmodified.
     */
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

    /** A single dense cable node of the given tier, ready and wired for controller-rooted pathing. */
    private GridNode makeDenseCable(CapacityTier tier) {
        var node = makeReadyNode(GridFlags.DENSE_CAPACITY);
        node.setCapacityTier(tier);
        return node;
    }

    private GridNode makeSmartCable(CapacityTier tier) {
        var node = makeReadyNode();
        node.setCapacityTier(tier);
        return node;
    }

    private GridNode makeConsumer() {
        return makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
    }

    /** Wires up {@code count} independent channel-consuming leaves hanging off {@code cable}. */
    private List<GridNode> attachConsumers(GridNode cable, int count) {
        List<GridNode> consumers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var consumer = makeConsumer();
            var conn = GridHelper.createConnection(cable, consumer);
            assertThat(conn).isNotNull();
            consumers.add(consumer);
        }
        return consumers;
    }

    /** A controller (root) node owned by a real-enough mocked {@link ControllerBlockEntity}, at the given tier. */
    private GridNode makeController(CapacityTier tier) {
        var controllerOwner = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(controllerOwner, GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
        controllerNode.setCapacityTier(tier);
        when(controllerOwner.getGridNode()).thenReturn(controllerNode);
        when(controllerOwner.getBlockPos()).thenReturn(BlockPos.ZERO);
        return controllerNode;
    }

    private int usedChannelsOf(GridNode anyMemberNode) {
        var ps = (PathingService) anyMemberNode.getGrid().getPathingService();
        return ps.getUsedChannels();
    }

    // =====================================================================
    // G. STANDARD REGRESSION — the tier system must not change a single existing number.
    // =====================================================================

    @Test
    void standardSmartCableMaxChannelsIsEight() {
        var cable = makeReadyNode();
        assertThat(cable.getMaxChannels()).isEqualTo(8);
        assertThat(cable.getCapacityTier()).isEqualTo(CapacityTier.STANDARD);
    }

    @Test
    void standardDenseCableMaxChannelsIsThirtyTwo() {
        var cable = makeReadyNode(GridFlags.DENSE_CAPACITY);
        assertThat(cable.getMaxChannels()).isEqualTo(32);
    }

    @Test
    void standardControllerBranchSaturatesAtThirtyTwoThroughRealPathing() {
        var controller = makeController(CapacityTier.STANDARD);
        var denseCable = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(controller, denseCable);
        attachConsumers(denseCable, 40); // more than 32, to prove saturation

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
        assertThat(denseCable.getUsedChannels()).isEqualTo(32);
    }

    @Test
    void existingGridConnectionFlatThirtyTwoInvariantIsUnaffectedByTier() {
        // Q1/Q2/Q3 load-bearing invariant (NetworkSlotIsolationTest, MultipointSingleLaneQ2Test,
        // RemoteQuantumLaneIsolationTest): GridConnection#getMaxChannels() stays a flat 32 regardless of what's
        // attached - it is informational only and never consulted by the real allocation algorithm (see
        // GridConnection#getMaxChannels javadoc). Prove a Cosmic-tiered connection still reports 32 here.
        var a = makeDenseCable(CapacityTier.COSMIC);
        var b = makeDenseCable(CapacityTier.COSMIC);
        var conn = (GridConnection) GridHelper.createConnection(a, b);
        assertThat(conn.getMaxChannels()).isEqualTo(32);
    }

    // =====================================================================
    // H. HIGH-TIER CABLES — exact max, real saturation via PathingCalculation.
    // =====================================================================

    @Test
    void quantumSmartCableMaxIsSixteen() {
        assertThat(makeSmartCable(CapacityTier.QUANTUM).getMaxChannels()).isEqualTo(16);
    }

    @Test
    void quantumDenseCableMaxIsSixtyFour() {
        assertThat(makeDenseCable(CapacityTier.QUANTUM).getMaxChannels()).isEqualTo(64);
    }

    @Test
    void singularitySmartCableMaxIsThirtyTwo() {
        assertThat(makeSmartCable(CapacityTier.SINGULARITY).getMaxChannels()).isEqualTo(32);
    }

    @Test
    void singularityDenseCableMaxIsOneTwentyEight() {
        assertThat(makeDenseCable(CapacityTier.SINGULARITY).getMaxChannels()).isEqualTo(128);
    }

    @Test
    void cosmicSmartCableMaxIsSixtyFour() {
        assertThat(makeSmartCable(CapacityTier.COSMIC).getMaxChannels()).isEqualTo(64);
    }

    @Test
    void cosmicDenseCableMaxIsTwoFiftySix() {
        assertThat(makeDenseCable(CapacityTier.COSMIC).getMaxChannels()).isEqualTo(256);
    }

    @Test
    void cosmicDenseCableSaturatesAtExactlyTwoFiftySixAndTheNextConsumerStaysOffline() {
        var controller = makeController(CapacityTier.COSMIC);
        var cable = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cable);
        var consumers = attachConsumers(cable, 260); // 4 more than capacity

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(256);
        long online = consumers.stream().filter(c -> c.getUsedChannels() > 0).count();
        assertThat(online).isEqualTo(256);
        long offline = consumers.stream().filter(c -> c.getUsedChannels() == 0).count();
        assertThat(offline).isEqualTo(4);
    }

    @Test
    void reconnectAfterSaturationRepathsCorrectly() {
        var controller = makeController(CapacityTier.QUANTUM);
        var cable = makeDenseCable(CapacityTier.QUANTUM); // max 64
        GridHelper.createConnection(controller, cable);
        var consumers = attachConsumers(cable, 70);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);

        // Disconnect 10 consumers, forcing a repath - the remaining 60 must all get a channel now.
        for (int i = 0; i < 10; i++) {
            var conn = consumers.get(i).getConnections().get(0);
            conn.destroy();
        }
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(60);
    }

    // =====================================================================
    // I. HIGH-TIER CONTROLLERS — controller face capacity comes from the controller's own tier.
    // =====================================================================

    @Test
    void quantumControllerBranchSaturatesAtSixtyFour() {
        var controller = makeController(CapacityTier.QUANTUM);
        var cable = makeDenseCable(CapacityTier.QUANTUM);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 70);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    @Test
    void singularityControllerBranchSaturatesAtOneTwentyEight() {
        var controller = makeController(CapacityTier.SINGULARITY);
        var cable = makeDenseCable(CapacityTier.SINGULARITY);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 135);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(128);
    }

    @Test
    void cosmicControllerBranchSaturatesAtTwoFiftySix() {
        var controller = makeController(CapacityTier.COSMIC);
        var cable = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 260);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(256);
    }

    // =====================================================================
    // J. MIXED-TIER BOTTLENECK — proven via real Controller+Pathing allocation, not an isolated min() helper.
    // =====================================================================

    @Test
    void cosmicControllerWithStandardDenseCableIsBottleneckedToThirtyTwo() {
        var controller = makeController(CapacityTier.COSMIC);
        var cable = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 40);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void standardControllerWithCosmicDenseCableIsBottleneckedToThirtyTwo() {
        var controller = makeController(CapacityTier.STANDARD);
        var cable = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 40);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void cosmicControllerWithQuantumDenseCableIsBottleneckedToSixtyFour() {
        var controller = makeController(CapacityTier.COSMIC);
        var cable = makeDenseCable(CapacityTier.QUANTUM);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 70);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    @Test
    void singularityControllerWithCosmicDenseCableIsBottleneckedToOneTwentyEight() {
        var controller = makeController(CapacityTier.SINGULARITY);
        var cable = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cable);
        attachConsumers(cable, 135);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(128);
    }

    @Test
    void chainedDowngradeToThirtyTwoAcrossFourTiers() {
        // controller(Cosmic 256) -- Singularity dense(128) -- Quantum dense(64) -- Standard dense(32) -- consumers
        var controller = makeController(CapacityTier.COSMIC);
        var singularity = makeDenseCable(CapacityTier.SINGULARITY);
        var quantum = makeDenseCable(CapacityTier.QUANTUM);
        var standard = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(controller, singularity);
        GridHelper.createConnection(singularity, quantum);
        GridHelper.createConnection(quantum, standard);
        attachConsumers(standard, 40);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void cosmicToQuantumToCosmicBottlenecksAtQuantumSixtyFour() {
        // controller(Cosmic) -- Cosmic dense -- Quantum dense (bottleneck) -- Cosmic dense -- consumers = 64
        var controller = makeController(CapacityTier.COSMIC);
        var cosmicA = makeDenseCable(CapacityTier.COSMIC);
        var quantum = makeDenseCable(CapacityTier.QUANTUM);
        var cosmicB = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cosmicA);
        GridHelper.createConnection(cosmicA, quantum);
        GridHelper.createConnection(quantum, cosmicB);
        attachConsumers(cosmicB, 70);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(64);
    }

    @Test
    void singularityIntoCosmicIsBottleneckedAtOneTwentyEightNotTwoFiftySix() {
        // controller(Singularity 128) -- Cosmic dense(256) -- consumers: upstream (controller) tier still gates it.
        var controller = makeController(CapacityTier.SINGULARITY);
        var cosmic = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controller, cosmic);
        attachConsumers(cosmic, 135);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(128);
    }

    @Test
    void quantumStandardQuantumChainBottlenecksAtStandardThirtyTwo() {
        var controller = makeController(CapacityTier.QUANTUM);
        var quantumA = makeDenseCable(CapacityTier.QUANTUM);
        var standard = makeDenseCable(CapacityTier.STANDARD);
        var quantumB = makeDenseCable(CapacityTier.QUANTUM);
        GridHelper.createConnection(controller, quantumA);
        GridHelper.createConnection(quantumA, standard);
        GridHelper.createConnection(standard, quantumB);
        attachConsumers(quantumB, 40);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void downstreamUpgradeWithoutUpstreamUpgradeDoesNotIncreaseCapacity() {
        // Baseline: Standard controller + Standard dense cable -> 32.
        var controllerA = makeController(CapacityTier.STANDARD);
        var cableA = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(controllerA, cableA);
        attachConsumers(cableA, 40);
        runTick(controllerA.getGrid(), 3);
        int baseline = usedChannelsOf(controllerA);
        assertThat(baseline).isEqualTo(32);

        // Same controller tier, but the downstream cable is upgraded to Cosmic - must NOT increase capacity,
        // because the controller (upstream root) is still Standard.
        var controllerB = makeController(CapacityTier.STANDARD);
        var cableB = makeDenseCable(CapacityTier.COSMIC);
        GridHelper.createConnection(controllerB, cableB);
        attachConsumers(cableB, 40);
        runTick(controllerB.getGrid(), 3);
        assertThat(usedChannelsOf(controllerB)).isEqualTo(baseline);
    }

    @Test
    void upstreamUpgradeWithRemainingLowerTierBottleneckDoesNotIncreaseCapacity() {
        // Controller upgraded to Cosmic, but the cable directly touching consumers is still Standard - the
        // low-tier link anywhere along the branch still gates the whole branch.
        var controller = makeController(CapacityTier.COSMIC);
        var cosmicRoot = makeDenseCable(CapacityTier.COSMIC);
        var standardLeaf = makeDenseCable(CapacityTier.STANDARD);
        GridHelper.createConnection(controller, cosmicRoot);
        GridHelper.createConnection(cosmicRoot, standardLeaf);
        attachConsumers(standardLeaf, 40);

        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    // =====================================================================
    // K. CONTROLLER MULTIBLOCK TIER SAFETY
    // =====================================================================

    private ControllerBlockEntity makeMultiblockControllerOwner(BlockPos pos, CapacityTier tier) {
        var owner = mock(ControllerBlockEntity.class);
        var node = makeOwnedNode(owner, GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
        node.setCapacityTier(tier);
        when(owner.getGridNode()).thenReturn(node);
        when(owner.getBlockPos()).thenReturn(pos);
        return owner;
    }

    @Test
    void homogeneousStandardControllerPairIsValid() {
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.STANDARD);
        var b = makeMultiblockControllerOwner(BlockPos.ZERO.east(), CapacityTier.STANDARD);
        GridHelper.createConnection(a.getGridNode(), b.getGridNode());

        var state = ControllerValidator.calculateState(List.of(a, b));
        assertThat(state).isEqualTo(ControllerState.CONTROLLER_ONLINE);
    }

    @Test
    void homogeneousCosmicControllerPairIsValid() {
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.COSMIC);
        var b = makeMultiblockControllerOwner(BlockPos.ZERO.east(), CapacityTier.COSMIC);
        GridHelper.createConnection(a.getGridNode(), b.getGridNode());

        var state = ControllerValidator.calculateState(List.of(a, b));
        assertThat(state).isEqualTo(ControllerState.CONTROLLER_ONLINE);
    }

    @Test
    void mixedStandardAndCosmicControllerPairIsConflicted() {
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.STANDARD);
        var b = makeMultiblockControllerOwner(BlockPos.ZERO.east(), CapacityTier.COSMIC);
        GridHelper.createConnection(a.getGridNode(), b.getGridNode());

        var state = ControllerValidator.calculateState(List.of(a, b));
        assertThat(state).isEqualTo(ControllerState.CONTROLLER_CONFLICT);
    }

    @Test
    void mixedQuantumAndSingularityControllerPairIsConflicted() {
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.QUANTUM);
        var b = makeMultiblockControllerOwner(BlockPos.ZERO.east(), CapacityTier.SINGULARITY);
        GridHelper.createConnection(a.getGridNode(), b.getGridNode());

        var state = ControllerValidator.calculateState(List.of(a, b));
        assertThat(state).isEqualTo(ControllerState.CONTROLLER_CONFLICT);
    }

    @Test
    void oneHighTierBlockDoesNotUpgradeAnOtherwiseCheapMultiblock() {
        // A 1x3 line: Standard, Standard, Cosmic. The single Cosmic block must not silently make the whole
        // structure Cosmic - and per the mixed-tier test above, it must instead invalidate the whole structure.
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.STANDARD);
        var b = makeMultiblockControllerOwner(BlockPos.ZERO.east(), CapacityTier.STANDARD);
        var c = makeMultiblockControllerOwner(BlockPos.ZERO.east().east(), CapacityTier.COSMIC);
        GridHelper.createConnection(a.getGridNode(), b.getGridNode());
        GridHelper.createConnection(b.getGridNode(), c.getGridNode());

        var state = ControllerValidator.calculateState(List.of(a, b, c));
        assertThat(state).isEqualTo(ControllerState.CONTROLLER_CONFLICT);
    }

    @Test
    void rebuildFromMixedToHomogeneousBecomesValidAgain() {
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.STANDARD);
        var b = makeMultiblockControllerOwner(BlockPos.ZERO.east(), CapacityTier.COSMIC);
        GridHelper.createConnection(a.getGridNode(), b.getGridNode());
        assertThat(ControllerValidator.calculateState(List.of(a, b))).isEqualTo(ControllerState.CONTROLLER_CONFLICT);

        // "Rebuild" the second block to match the first block's tier (simulating breaking + replacing it).
        ((GridNode) b.getGridNode()).setCapacityTier(CapacityTier.STANDARD);
        assertThat(ControllerValidator.calculateState(List.of(a, b))).isEqualTo(ControllerState.CONTROLLER_ONLINE);
    }

    @Test
    void singleControllerBlockOfAnyTierIsAlwaysHomogeneousByDefinition() {
        var a = makeMultiblockControllerOwner(BlockPos.ZERO, CapacityTier.SINGULARITY);
        assertThat(ControllerValidator.calculateState(List.of(a))).isEqualTo(ControllerState.CONTROLLER_ONLINE);
    }
}
