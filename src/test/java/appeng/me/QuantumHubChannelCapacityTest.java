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

/** Round 12 native PathingService proof for Mk II and mixed endpoint bottlenecks. */
class QuantumHubChannelCapacityTest extends AbstractGridNodeTest {
    @Mock
    private MockedStatic<IPlayerRegistry> playerRegistry;

    @BeforeEach
    void stubPlayerRegistryForAchievements() {
        playerRegistry.when(() -> IPlayerRegistry.getConnected(any(), anyInt())).thenReturn(null);
    }

    private GridNode owned(Object owner, GridFlags... flags) {
        var node = new GridNode(level, owner, listener, Set.of(flags));
        node.markReady();
        return node;
    }

    private GridNode dense(CapacityTier tier) {
        var node = makeReadyNode(GridFlags.DENSE_CAPACITY);
        node.setCapacityTier(tier);
        return node;
    }

    private GridNode controller(CapacityTier tier) {
        var owner = mock(ControllerBlockEntity.class);
        var node = owned(owner, GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
        node.setCapacityTier(tier);
        when(owner.getGridNode()).thenReturn(node);
        when(owner.getBlockPos()).thenReturn(BlockPos.ZERO);
        return node;
    }

    private GridNode consumer() {
        return owned(new Object(), GridFlags.REQUIRE_CHANNEL);
    }

    private List<GridNode> consumers(GridNode parent, int count) {
        var result = new ArrayList<GridNode>();
        for (int i = 0; i < count; i++) {
            var consumer = consumer();
            GridHelper.createConnection(parent, consumer);
            result.add(consumer);
        }
        return result;
    }

    private long online(List<GridNode> consumers) {
        return consumers.stream().filter(node -> node.getUsedChannels() > 0).count();
    }

    private int used(GridNode node) {
        return ((PathingService) node.getGrid().getPathingService()).getUsedChannels();
    }

    @Test
    void mkIIMkIIRealPathingCeilingIsSixtyFour() {
        var root = controller(CapacityTier.COSMIC);
        var a = dense(CapacityTier.QUANTUM);
        var b = dense(CapacityTier.QUANTUM);
        GridHelper.createConnection(root, a);
        GridHelper.createConnection(a, b);
        var consumers = consumers(b, 70);
        runTick(root.getGrid(), 3);
        assertThat(used(root)).isEqualTo(64);
        assertThat(online(consumers)).isEqualTo(64);
    }

    @Test
    void mkIMkIIMixedEdgeIsClampedToThirtyTwo() {
        var root = controller(CapacityTier.COSMIC);
        var mk2 = dense(CapacityTier.QUANTUM);
        var mk1 = dense(CapacityTier.STANDARD);
        GridHelper.createConnection(root, mk2);
        GridHelper.createConnection(mk2, mk1);
        var consumers = consumers(mk1, 70);
        runTick(root.getGrid(), 3);
        assertThat(online(consumers)).isEqualTo(32);
    }

    @Test
    void standardP2PStyleNodeBeforeMkIIKeepsPathAtThirtyTwo() {
        var root = controller(CapacityTier.COSMIC);
        var standardP2POuter = dense(CapacityTier.STANDARD);
        var mk2a = dense(CapacityTier.QUANTUM);
        var mk2b = dense(CapacityTier.QUANTUM);
        GridHelper.createConnection(root, standardP2POuter);
        GridHelper.createConnection(standardP2POuter, mk2a);
        GridHelper.createConnection(mk2a, mk2b);
        var consumers = consumers(mk2b, 70);
        runTick(root.getGrid(), 3);
        assertThat(online(consumers)).isEqualTo(32);
    }

    @Test
    void standardDenseAfterMkIIKeepsPathAtThirtyTwo() {
        var root = controller(CapacityTier.COSMIC);
        var mk2a = dense(CapacityTier.QUANTUM);
        var mk2b = dense(CapacityTier.QUANTUM);
        var downstream = dense(CapacityTier.STANDARD);
        GridHelper.createConnection(root, mk2a);
        GridHelper.createConnection(mk2a, mk2b);
        GridHelper.createConnection(mk2b, downstream);
        var consumers = consumers(downstream, 70);
        runTick(root.getGrid(), 3);
        assertThat(online(consumers)).isEqualTo(32);
    }

    @Test
    void upstreamRemainingSeventeenIsNotRestoredByMkIIHubArm() {
        var root = controller(CapacityTier.QUANTUM);
        var branch = dense(CapacityTier.QUANTUM);
        GridHelper.createConnection(root, branch);
        consumers(branch, 47);
        var mk2a = dense(CapacityTier.QUANTUM);
        var mk2b = dense(CapacityTier.QUANTUM);
        GridHelper.createConnection(branch, mk2a);
        GridHelper.createConnection(mk2a, mk2b);
        var hubConsumers = consumers(mk2b, 40);
        runTick(root.getGrid(), 3);
        assertThat(used(root)).isEqualTo(64);
        assertThat(online(hubConsumers)).isEqualTo(17);
    }
}
