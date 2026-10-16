package appeng.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

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
import appeng.me.pathfinding.PathingCalculation;
import appeng.me.service.PathingService;

/**
 * Round 9 ("GLASS 4-CHANNEL CLOSURE") — proves {@code GridFlags.REDUCED_CAPACITY} (set by
 * {@link appeng.parts.networking.GlassCablePart}'s constructor, exactly {@code setFlags(GridFlags.PREFERRED,
 * GridFlags.REDUCED_CAPACITY)}) makes Glass Cable a real, distinct 4-channel capacity tier through the REAL production
 * {@link PathingService}/{@link PathingCalculation}/{@link GridConnection} allocation code - not an isolated helper.
 * Fixture strategy matches {@code CapacityTierTest}/{@code RecursiveP2PTest}: raw {@link GridNode}s wired directly with
 * {@link GridHelper#createConnection}, using the exact flag combination the real part constructors set (see
 * {@link #makeGlassCable()}/{@link #makeSmartCable()}/{@link #makeDenseCable()} javadoc for the production call site
 * each mirrors). No `if (glass) return 4;` special-case exists anywhere in the pathfinder - the capacity comes purely
 * from {@code GridNode#getNativeChannelCapacity}'s {@code REDUCED_CAPACITY -> 4} branch (see {@code GridNode.java}),
 * the same generalized formula {@code CapacityTier}/{@code ChannelMode} already multiply through for every other cable.
 */
class GlassCableCapacityTest extends AbstractGridNodeTest {

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
        var node = new GridNode(level, nodeOwner, listener, java.util.Set.of(flags));
        node.markReady();
        return node;
    }

    /** Mirrors {@link appeng.parts.networking.GlassCablePart#GlassCablePart} exactly: PREFERRED + REDUCED_CAPACITY. */
    private GridNode makeGlassCable() {
        return makeReadyNode(GridFlags.PREFERRED, GridFlags.REDUCED_CAPACITY);
    }

    /** Mirrors {@link appeng.parts.networking.SmartCablePart}/{@code CablePart}'s base constructor: PREFERRED only. */
    private GridNode makeSmartCable() {
        return makeReadyNode(GridFlags.PREFERRED);
    }

    /**
     * Mirrors {@link appeng.parts.networking.DenseCablePart#DenseCablePart}: DENSE_CAPACITY only (replaces PREFERRED,
     * exactly as the real production constructor does - see {@code DenseCablePart}'s single {@code setFlags} call).
     */
    private GridNode makeDenseCable() {
        return makeReadyNode(GridFlags.DENSE_CAPACITY);
    }

    private GridNode makeConsumer() {
        return makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
    }

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
    // C/D. GLASS = 4 CHANNELS
    // =====================================================================

    @Test
    void glassCableMaxChannelsIsFour() {
        var cable = makeGlassCable();
        assertThat(cable.getMaxChannels()).isEqualTo(4);
        // Glass Cable is intentionally Standard-tier only - no Quantum/Singularity/Cosmic Glass Cable exists.
        assertThat(cable.getCapacityTier()).isEqualTo(CapacityTier.STANDARD);
    }

    @Test
    void glassCableWithFourConsumersAllOnline() {
        var controller = makeController(CapacityTier.STANDARD);
        var glass = makeGlassCable();
        GridHelper.createConnection(controller, glass);
        var consumers = attachConsumers(glass, 4);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(4);
        assertThat(consumers).allMatch(c -> c.getUsedChannels() > 0);
    }

    @Test
    void glassCableWithFiveConsumersExactlyFourOnlineOneOffline() {
        var controller = makeController(CapacityTier.STANDARD);
        var glass = makeGlassCable();
        GridHelper.createConnection(controller, glass);
        var consumers = attachConsumers(glass, 5);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(4);
        long online = consumers.stream().filter(c -> c.getUsedChannels() > 0).count();
        long offline = consumers.stream().filter(c -> c.getUsedChannels() == 0).count();
        assertThat(online).isEqualTo(4);
        assertThat(offline).isEqualTo(1);
        // Native AE2 saturation semantics: the excess consumer simply stays dark, no exception/crash.
    }

    @Test
    void replacingGlassWithSmartInTheSamePositionRestoresEightChannels() {
        // "Replacement" is modeled the same way a real in-world block swap works: destroy the old node's
        // connections and build a fresh node (new part instance) in the same topological position - matches how
        // CablePart#changeColor / PartPlacement physically replace one cable part with another. No world migration
        // of the *existing* item/registry entry is implied or required by this test.
        var controller = makeController(CapacityTier.STANDARD);
        var glass = makeGlassCable();
        GridHelper.createConnection(controller, glass);
        attachConsumers(glass, 4);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(4);

        // Swap: tear down the glass cable's connections, then wire up a fresh Smart cable node in its place.
        for (var conn : List.copyOf(glass.getConnections())) {
            conn.destroy();
        }
        var smart = makeSmartCable();
        GridHelper.createConnection(controller, smart);
        attachConsumers(smart, 8);
        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(8);
    }

    @Test
    void replacingGlassWithDenseInTheSamePositionRestoresThirtyTwoChannels() {
        var controller = makeController(CapacityTier.STANDARD);
        var glass = makeGlassCable();
        GridHelper.createConnection(controller, glass);
        attachConsumers(glass, 4);
        runTick(controller.getGrid(), 3);
        assertThat(usedChannelsOf(controller)).isEqualTo(4);

        for (var conn : List.copyOf(glass.getConnections())) {
            conn.destroy();
        }
        var dense = makeDenseCable();
        GridHelper.createConnection(controller, dense);
        attachConsumers(dense, 40);
        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(32);
    }

    @Test
    void oldWorldGlassBranchWithFiveToEightConsumersSaturatesAtFourAfterRepathNoCrash() {
        // Simulates the "existing world with a Glass branch feeding 5-8 consumers" migration scenario the brief
        // calls out: after this round's Glass=4 rebalance, a repath must simply saturate at 4 (native AE2 semantics),
        // never throw, duplicate items, wipe security, or touch the cable's registry identity.
        var controller = makeController(CapacityTier.STANDARD);
        var glass = makeGlassCable();
        GridHelper.createConnection(controller, glass);
        var consumers = attachConsumers(glass, 8);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(4);
        long online = consumers.stream().filter(c -> c.getUsedChannels() > 0).count();
        assertThat(online).isEqualTo(4);
    }

    @Test
    void glassCableIsBottleneckedEvenBehindACosmicController() {
        // Mixed-tier bottleneck (Part 1 + Part 3 overlap check): a Glass Cable's own native 4-channel ceiling gates
        // the branch no matter how high-tier the controller is - Glass Cable never gets a CapacityTier upgrade.
        var controller = makeController(CapacityTier.COSMIC);
        var glass = makeGlassCable();
        GridHelper.createConnection(controller, glass);
        attachConsumers(glass, 10);

        runTick(controller.getGrid(), 3);

        assertThat(usedChannelsOf(controller)).isEqualTo(4);
    }

    @Test
    void glassGridConnectionFlatThirtyTwoInvariantIsUnaffectedByReducedCapacity() {
        // Q1/Q2/Q3 load-bearing invariant (see CapacityTierTest's identically-named test):
        // GridConnection#getMaxChannels()
        // stays a flat 32 regardless of what's attached, including a REDUCED_CAPACITY node - it is informational only.
        var a = makeGlassCable();
        var b = makeGlassCable();
        var conn = (GridConnection) GridHelper.createConnection(a, b);
        assertThat(conn.getMaxChannels()).isEqualTo(32);
    }
}
