package appeng.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.service.AdHocNetworkError;
import appeng.me.service.PathingService;

/**
 * Phase I — Recursive P2P.
 * <p/>
 * Verifies the "Colonial &amp; AE Fleet" Phase I hard design decision: P2P must be able to carry P2P (nesting to any
 * reasonable depth), while genuine dependency cycles are detected and fail safe instead of silently rejecting every
 * nesting attempt.
 * <p/>
 * <b>Fixture strategy, matching Q1/Q2's own documented judgment call</b>: {@link appeng.parts.p2p.MEP2PTunnelPart}
 * exposes two independent {@link appeng.api.networking.IGridNode}s per physical tunnel device — an in-network "main"
 * node ({@code REQUIRE_CHANNEL, COMPRESSED_CHANNEL}, wired into the tunnel's own local cable network, exactly like any
 * ordinary channel-consuming device) and a separate "outer" node ({@code DENSE_CAPACITY,
 * CANNOT_CARRY_COMPRESSED}, used only for the manual per-frequency link to the paired tunnel and for whatever is
 * physically touching the tunnel's external face). A throwaway diagnostic test (built and run against real production
 * classes during Phase I archaeology, then deleted — not part of this suite) proved by direct inspection that these two
 * nodes are NEVER connected to each other, and empirically located the actual channel behaviour documented in the Phase
 * I report. This fixture reproduces that same node topology directly with
 * {@link GridNode}/{@link GridHelper#createConnection}, exactly as {@code RemoteQuantumLaneIsolationTest} and
 * {@code MultipointSingleLaneQ2Test} already do for the Quantum prototype — real production channel/pathing code, a
 * synthetic (but structurally faithful) world. Building a live in-world {@code MEP2PTunnelPart} pair across two real,
 * ticking, far-apart {@code CableBusContainer}s was judged out of proportion for this round, matching the "honest scope
 * boundary" {@code MultipointSingleLaneQ2Test} already documented for its own controller fixture.
 * <p/>
 * Every tunnel-pair helper below gives the input side and the output side of one logical tunnel their OWN distinct
 * owner object per side (never the shared {@link AbstractGridNodeTest#owner} field), mirroring the real
 * {@code MEP2PTunnelPart} invariant that a main node and its own outer node share one owner (the part instance) while
 * two different tunnel devices never do — this is exactly the identity the Phase I production fix
 * ({@code GridNode#getSubtreeCompressedChannelBlockerOwners}, {@code PathingService#calculateAdHocChannels}) keys on to
 * tell a genuine self-referencing loop apart from harmless nesting.
 */
class RecursiveP2PTest extends AbstractGridNodeTest {

    // =====================================================================
    // Fixture helpers
    // =====================================================================

    private GridNode makeOwnedNode(Object nodeOwner, GridFlags... flags) {
        var node = new GridNode(level, nodeOwner, listener, Set.of(flags));
        node.markReady();
        return node;
    }

    /** One simulated ME-P2P tunnel device: a main node and its own outer node, sharing one owner. */
    private record SimTunnel(GridNode main, GridNode outer, Object owner) {
    }

    private SimTunnel makeTunnel() {
        var owner = new Object();
        var main = makeOwnedNode(owner, GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
        var outer = makeOwnedNode(owner, GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
        return new SimTunnel(main, outer, owner);
    }

    /** Wires {@code tunnel.main()} into {@code localCable}, exactly like a real tunnel's internal center connection. */
    private void wireIntoLocalNetwork(SimTunnel tunnel, GridNode localCable) {
        var conn = GridHelper.createConnection(localCable, tunnel.main());
        assertThat(conn).as("wiring tunnel main node into its own local network").isNotNull();
    }

    /**
     * Forms the manual per-frequency P2P link between an input tunnel's outer node and an output tunnel's outer node.
     */
    private void linkFrequency(SimTunnel input, SimTunnel output) {
        var conn = GridHelper.createConnection(input.outer(), output.outer());
        assertThat(conn).as("forming the P2P frequency link").isNotNull();
    }

    private PathingService pathingServiceOf(GridNode anyMemberNode) {
        return (PathingService) anyMemberNode.getGrid().getPathingService();
    }

    // =====================================================================
    // A. BASIC NESTING — the natural "place a relay tunnel inside the network another tunnel feeds" pattern.
    // =====================================================================

    @Test
    void twoHopChainGrantsAChannelAtEachHopWithoutRejection() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);

        var middle = makeReadyNode();
        var aOut = makeTunnel(); // A's own output-side device, physically living in "Middle"
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        var consumer = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(middle, consumer);

        runTick(middle.getGrid(), 3);
        var ps = pathingServiceOf(middle);

        assertThat(ps.getAdHocNetworkError()).as("no nesting rejection for the natural relay pattern").isNull();
        assertThat(ps.getUsedChannels()).as("aOut's own main node (1) + the consumer (1)").isEqualTo(2);
    }

    @Test
    void threeHopChainNestsWithoutRejectionAtAnyHop() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);

        var middle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        // A second, independent tunnel B nested INSIDE the same "Middle" network that A's output feeds.
        var b = makeTunnel();
        wireIntoLocalNetwork(b, middle);

        var far = makeReadyNode();
        var bOut = makeTunnel();
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        var consumer = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(far, consumer);

        runTick(middle.getGrid(), 3);
        runTick(far.getGrid(), 3);

        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(far).getAdHocNetworkError()).isNull();
        // Middle hosts aOut's main node + b's (input) main node = 2, completely independent of Far's own count.
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2);
        // Far hosts bOut's main node + the consumer = 2 — never "32 from A plus 32 from B", never multiplied by depth.
        assertThat(pathingServiceOf(far).getUsedChannels()).isEqualTo(2);
    }

    @Test
    void deepSixHopChainNestsWithNoArtificialDepthCap() {
        GridNode previousCable = makeReadyNode();
        for (int hop = 0; hop < 6; hop++) {
            var input = makeTunnel();
            wireIntoLocalNetwork(input, previousCable);

            var nextCable = makeReadyNode();
            var output = makeTunnel();
            wireIntoLocalNetwork(output, nextCable);
            linkFrequency(input, output);

            previousCable = nextCable;
        }
        var consumer = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(previousCable, consumer);

        runTick(previousCable.getGrid(), 3);
        var ps = pathingServiceOf(previousCable);

        assertThat(ps.getAdHocNetworkError())
                .as("no depth cap: six independent tunnel hops, no rejection anywhere along the chain")
                .isNull();
        assertThat(ps.getUsedChannels()).isEqualTo(2); // this hop's own tunnel main node + the final consumer
    }

    // =====================================================================
    // B. CHANNEL TRANSPORT — nesting never generates channel capacity.
    // =====================================================================

    @Test
    void multipleConsumersAfterNestedP2pDoNotGainExtraCapacityFromNestingDepth() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        var middle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);
        var b = makeTunnel();
        wireIntoLocalNetwork(b, middle);
        var far = makeReadyNode();
        var bOut = makeTunnel();
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        // Three ordinary consumers at the far end, after two P2P layers.
        for (int i = 0; i < 3; i++) {
            GridHelper.createConnection(far, makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL));
        }

        runTick(far.getGrid(), 3);
        var ps = pathingServiceOf(far);
        assertThat(ps.getAdHocNetworkError()).isNull();
        // bOut's own main node (1) + 3 consumers = 4 — not 4*32, not multiplied by the 2 layers of nesting above it.
        assertThat(ps.getUsedChannels()).isEqualTo(4);
    }

    @Test
    void tunnelEndpointOwnLocalChannelCostIsPreservedAtEveryHop() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        var middle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        runTick(home.getGrid(), 3);
        runTick(middle.getGrid(), 3);

        // Each tunnel's own main node still costs exactly 1 channel in ITS OWN local network — the endpoint cost is
        // never waived just because it participates in a nested chain.
        assertThat(pathingServiceOf(home).getUsedChannels()).isEqualTo(1);
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(1);
    }

    @Test
    void upstreamNetworkBeingAtCapacityDoesNotAffectAnIndependentDownstreamNetwork() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        // Saturate Home's own ad-hoc budget with unrelated devices (default ad-hoc cap is 8 channels).
        for (int i = 0; i < 10; i++) {
            GridHelper.createConnection(home, makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL));
        }

        var middle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);
        var consumer = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(middle, consumer);

        runTick(home.getGrid(), 3);
        runTick(middle.getGrid(), 3);

        assertThat(pathingServiceOf(home).getAdHocNetworkError())
                .as("Home is over its own separate ad-hoc budget")
                .isEqualTo(AdHocNetworkError.TOO_MANY_CHANNELS);
        // Middle is an entirely independent local grid/channel domain: nesting never "borrows" or is throttled by
        // an upstream network's own saturation.
        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2);
    }

    // =====================================================================
    // C. MULTIPLE / INDEPENDENT TUNNEL PAIRS — unrelated pairs sharing infrastructure don't conflict.
    // =====================================================================

    @Test
    void twoIndependentTunnelPairsNestedIntoTheSameNetworkDoNotConflict() {
        var homeA = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, homeA);
        var homeZ = makeReadyNode();
        var z = makeTunnel();
        wireIntoLocalNetwork(z, homeZ);

        var shared = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, shared);
        linkFrequency(a, aOut);
        var zOut = makeTunnel();
        wireIntoLocalNetwork(zOut, shared);
        linkFrequency(z, zOut);

        runTick(shared.getGrid(), 3);
        var ps = pathingServiceOf(shared);

        assertThat(ps.getAdHocNetworkError()).as("two unrelated tunnel pairs coexisting is not a routing loop")
                .isNull();
        assertThat(ps.getUsedChannels()).isEqualTo(2);
    }

    // =====================================================================
    // D. QUANTUM COMBINATIONS — GridHelper.createConnection "lane" primitive (Q1/Q2 style), combined with P2P.
    // =====================================================================

    @Test
    void controllerlessNetworkP2pThenQuantumLaneThenDevicesWorks() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        var middle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        // A "Quantum Lane" in the Q1/Q2 sense: a plain GridHelper.createConnection to a physically distant node,
        // owned by its own separate host object — no production Quantum system involved.
        var remoteHost = new Object();
        var remoteNode = makeOwnedNode(remoteHost);
        var lane = GridHelper.createConnection(middle, remoteNode);
        assertThat(lane).isNotNull();

        var device = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(remoteNode, device);

        runTick(middle.getGrid(), 3);
        var ps = pathingServiceOf(middle);
        assertThat(ps.getAdHocNetworkError()).isNull();
        assertThat(ps.getUsedChannels()).isEqualTo(2); // aOut's main node + the device beyond the quantum lane
    }

    @Test
    void p2pThenQuantumThenP2pChainWorksWithoutGeneratingChannels() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        var middle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, middle);
        linkFrequency(a, aOut);

        var remoteHost = new Object();
        var remoteNode = makeOwnedNode(remoteHost);
        GridHelper.createConnection(middle, remoteNode);

        // A second P2P pair nested beyond the quantum lane.
        var b = makeTunnel();
        wireIntoLocalNetwork(b, remoteNode);
        var far = makeReadyNode();
        var bOut = makeTunnel();
        wireIntoLocalNetwork(bOut, far);
        linkFrequency(b, bOut);

        var device = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(far, device);

        runTick(middle.getGrid(), 3);
        runTick(far.getGrid(), 3);

        assertThat(pathingServiceOf(middle).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(far).getAdHocNetworkError()).isNull();
        // aOut's own main node (1) + b's own main node (1) — b's main is wired to remoteNode, which the quantum
        // lane connection merged into Middle's own grid, exactly like a real dense/quantum link would. Still no
        // generation: it's the same two ordinary REQUIRE_CHANNEL devices this grid would cost regardless of how
        // remoteNode was reached.
        assertThat(pathingServiceOf(middle).getUsedChannels()).isEqualTo(2);
        assertThat(pathingServiceOf(far).getUsedChannels()).isEqualTo(2); // bOut's main node + device
    }

    // =====================================================================
    // F. CYCLES — genuine self-referencing loops are detected and fail safe; unrelated nesting is never punished.
    // =====================================================================

    @Test
    void selfLoopWhereATunnelsOwnOuterNodeSharesItsOwnLocalNetworkIsRejected() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);

        // Degenerate physical placement: this tunnel's own OUTER (external-facing) node is wired directly into its
        // OWN local network (e.g. ordinary cable built directly against the tunnel's working face) — the tunnel
        // indirectly carries itself.
        var selfLoop = GridHelper.createConnection(home, a.outer());
        assertThat(selfLoop).isNotNull();

        runTick(home.getGrid(), 3);
        var ps = pathingServiceOf(home);

        assertThat(ps.getAdHocNetworkError())
                .as("P2P ROUTING LOOP: a tunnel whose own outer node reappears in its own local network")
                .isEqualTo(AdHocNetworkError.NESTED_P2P_TUNNEL);
        assertThat(ps.getUsedChannels()).isZero();
    }

    @Test
    void selfLoopDoesNotAffectACompletelyUnrelatedIndependentAdHocNetwork() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        GridHelper.createConnection(home, a.outer()); // self-loop on Home's grid

        // A totally separate, unrelated ad-hoc grid with its own tunnel pair — no shared node, no shared owner.
        var otherHome = makeReadyNode();
        var z = makeTunnel();
        wireIntoLocalNetwork(z, otherHome);
        var otherMiddle = makeReadyNode();
        var zOut = makeTunnel();
        wireIntoLocalNetwork(zOut, otherMiddle);
        linkFrequency(z, zOut);

        runTick(home.getGrid(), 3);
        runTick(otherMiddle.getGrid(), 3);

        assertThat(pathingServiceOf(home).getAdHocNetworkError()).isEqualTo(AdHocNetworkError.NESTED_P2P_TUNNEL);
        assertThat(pathingServiceOf(otherMiddle).getAdHocNetworkError())
                .as("an unrelated, independent P2P frequency keeps working")
                .isNull();
        assertThat(pathingServiceOf(otherMiddle).getUsedChannels()).isEqualTo(1);
    }

    @Test
    void multiHopAncestorChainCycleIsDetectedInControllerModeWithoutBlockingAnUnrelatedTunnelBetween() {
        // Controller-mode ancestor-chain check: GridNode#getSubtreeCompressedChannelBlockerOwners accumulates the
        // owners of every CANNOT_CARRY_COMPRESSED ancestor on the path from the controller down to a given node, so
        // a cycle can be detected even when it closes through more than one intermediate hop, while a genuinely
        // unrelated tunnel sitting between the two ends of the loop is left alone. Mockito can construct
        // ControllerBlockEntity directly (bypassing its BlockEntity constructor requirements entirely, since
        // PathingCalculation only ever checks `owner instanceof ControllerBlockEntity`), so this exercises the real
        // controller-rooted appeng.me.pathfinding.PathingCalculation algorithm without needing a live world/block.
        var controllerOwner = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(controllerOwner);
        // ControllerValidator (real production code, run on every repath) needs the owner to answer getGridNode()
        // and getBlockPos() to accept a single controller as CONTROLLER_ONLINE rather than CONTROLLER_CONFLICT.
        when(controllerOwner.getGridNode()).thenReturn(controllerNode);
        when(controllerOwner.getBlockPos()).thenReturn(BlockPos.ZERO);

        var x = makeTunnel(); // owns X's main + outer node
        var cableAfterXOuter = makeReadyNode();
        GridHelper.createConnection(controllerNode, x.outer());
        GridHelper.createConnection(x.outer(), cableAfterXOuter);

        // An unrelated tunnel Y nested strictly BETWEEN X's outer node and X's own main node (a dead-end branch off
        // the same cable segment - Y's own main and outer nodes are never connected to each other, exactly like a
        // real MEP2PTunnelPart).
        var y = makeTunnel();
        wireIntoLocalNetwork(y, cableAfterXOuter);

        // Keep following the SAME tree (not a separate island) down through Y's outer node.
        var cableAfterYOuter = makeReadyNode();
        GridHelper.createConnection(cableAfterXOuter, cableAfterYOuter);
        GridHelper.createConnection(y.outer(), cableAfterYOuter);

        // X's own main node sits downstream of BOTH X's own outer node and Y's outer node — a real ancestor-chain
        // self-reference for X, two hops removed from the controller.
        wireIntoLocalNetwork(x, cableAfterYOuter);

        runTick(controllerNode.getGrid(), 3);

        assertThat(y.main().getUsedChannels())
                .as("Y is unrelated to X's loop and must still get its own channel")
                .isEqualTo(1);
        assertThat(x.main().getUsedChannels())
                .as("X's own outer node reappears on X's own path to the controller — refused a channel")
                .isZero();
    }

    @Test
    void repeatedRepathAfterACycleIsDetectedNeverRecalculatesRunawayOrThrows() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        var selfLoop = GridHelper.createConnection(home, a.outer());
        assertThat(selfLoop).isNotNull();

        // Many repath cycles in a row (as would happen over many real game ticks with a standing cycle in the
        // world) must stay stable: the same, unchanging result every time, no exception, no growth in state size.
        for (int i = 0; i < 25; i++) {
            runTick(home.getGrid(), 1);
            var ps = pathingServiceOf(home);
            assertThat(ps.getAdHocNetworkError()).isEqualTo(AdHocNetworkError.NESTED_P2P_TUNNEL);
            assertThat(ps.getUsedChannels()).isZero();
        }
    }

    // =====================================================================
    // E. FUNCTIONAL EQUIVALENCE — replacing a physical link with a P2P transport does not itself create channels.
    // =====================================================================

    @Test
    void p2pTransportSegmentGrantsTheSameChannelCountAsAnEquivalentPlainCableTopology() {
        // Baseline: Home -- plain cable -- consumer, no P2P at all.
        var plainHome = makeReadyNode();
        GridHelper.createConnection(plainHome,
                makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL));
        runTick(plainHome.getGrid(), 3);
        var plainChannels = pathingServiceOf(plainHome).getUsedChannels();

        // Equivalent topology, except the direct cable link between Home and Middle is replaced by a P2P transport
        // segment (an input/output tunnel pair) — same surrounding shape, same single consumer at the far end.
        var p2pHome = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, p2pHome);
        var p2pMiddle = makeReadyNode();
        var aOut = makeTunnel();
        wireIntoLocalNetwork(aOut, p2pMiddle);
        linkFrequency(a, aOut);
        GridHelper.createConnection(p2pMiddle, makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL));

        runTick(p2pMiddle.getGrid(), 3);
        var p2pChannels = pathingServiceOf(p2pMiddle).getUsedChannels();

        // Middle's own count is "tunnel endpoint (1) + consumer (1)" = 2: the tunnel endpoint itself is an ordinary
        // channel-consuming device, exactly like the plain-cable baseline's consumer, plus its own transport cost —
        // swapping a physical link for a P2P transport segment does not, by itself, manufacture extra capacity.
        assertThat(plainChannels).isEqualTo(1);
        assertThat(p2pChannels).isEqualTo(plainChannels + 1);
    }

    // =====================================================================
    // G. BREAK CYCLE / RECOVERY — clearing the offending edge restores normal operation without a manual reset.
    // =====================================================================

    @Test
    void breakingTheSelfLoopEdgeAutomaticallyRestoresNormalChannelsOnNextRepath() {
        var home = makeReadyNode();
        var a = makeTunnel();
        wireIntoLocalNetwork(a, home);
        var selfLoop = GridHelper.createConnection(home, a.outer());
        assertThat(selfLoop).isNotNull();

        runTick(home.getGrid(), 3);
        assertThat(pathingServiceOf(home).getAdHocNetworkError()).isEqualTo(AdHocNetworkError.NESTED_P2P_TUNNEL);

        // Break the offending edge — exactly like a player removing/relocating the tunnel that caused the loop.
        selfLoop.destroy();

        runTick(home.getGrid(), 3);
        var ps = pathingServiceOf(home);
        assertThat(ps.getAdHocNetworkError()).as("recovered automatically on the next ordinary repath").isNull();
        assertThat(ps.getUsedChannels()).isEqualTo(1); // just A's own main node now
    }

    // =====================================================================
    // H. ADVERSARIAL DEPENDENCY-GRAPH CLOSURE — genuine multi-tunnel cycles (DIFFERENT owners forming a real
    // A -> B -> A / A -> B -> C -> A dependency chain), as opposed to test #12's single self-referencing tunnel with
    // one unrelated bystander nested in between. See the Phase L report for the full archaeology of why these
    // reduce, in this codebase's owner-identity/tree model, to the same underlying check as #12 - these tests exist
    // to PROVE that generalization empirically for a real closed multi-owner chain, not just assert it by reading
    // code, and to prove the false-positive-avoidance side (shared ancestors / shared downstream targets / fully
    // disconnected grids are never mistaken for cycles).
    // =====================================================================

    @Test
    void twoDistinctTunnelsFormingAGenuineMutualDependencyCycleIsDetectedWithoutBlockingAnUnrelatedTunnel() {
        // A TRUE 2-tunnel mutual cycle (different owners, not a single tunnel self-looping through one inert
        // bystander): A's outer feeds the network that carries B's own main ("A carries B"), and B's outer feeds
        // the network that carries A's own main ("B carries A"), closing the loop back to A itself.
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel();
        var b = makeTunnel();

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);
        wireIntoLocalNetwork(b, cable1); // B's own main sits downstream of A's own outer.

        var cable2 = makeReadyNode();
        GridHelper.createConnection(cable1, cable2);
        GridHelper.createConnection(b.outer(), cable2); // B's own outer re-enters the SAME tree branch.

        var cable3 = makeReadyNode();
        GridHelper.createConnection(cable2, cable3);
        wireIntoLocalNetwork(a, cable3); // A's own main sits downstream of B's own outer - the loop closes on A.

        // An entirely unrelated, healthy two-hop P2P chain elsewhere. The "home" side is merged directly into the
        // SAME controller grid (a real sibling branch off the controller); the "middle"/output side of a P2P chain
        // is, as established by twoHopChainGrantsAChannelAtEachHopWithoutRejection, always a SEPARATE ad-hoc grid
        // (the manual frequency link only connects the two tunnels' OUTER nodes to each other, never their main
        // networks) - so it is ticked and asserted on separately, exactly like every other nesting test above.
        var healthyHome = makeReadyNode();
        var z = makeTunnel();
        wireIntoLocalNetwork(z, healthyHome);
        var healthyMiddle = makeReadyNode();
        var zOut = makeTunnel();
        wireIntoLocalNetwork(zOut, healthyMiddle);
        linkFrequency(z, zOut);
        var healthyConsumer = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(healthyMiddle, healthyConsumer);
        // Merge the unrelated chain's "home" cable into the SAME grid as the controller network so it is actually
        // driven by the same PathingCalculation pass (a controller-mode network is single-grid by construction).
        GridHelper.createConnection(controllerNode, healthyHome);

        runTick(controllerNode.getGrid(), 3);
        runTick(healthyMiddle.getGrid(), 3);

        assertThat(a.main().getUsedChannels())
                .as("A's own outer reappears on A's own path back to the controller (via B) - a genuine "
                        + "self-reference - refused a channel")
                .isZero();
        assertThat(b.main().getUsedChannels())
                .as("B is not, itself, downstream of its OWN outer - B is an active participant in the "
                        + "conceptual cycle but is not the node whose own reference loops - it keeps its channel")
                .isEqualTo(1);
        assertThat(z.main().getUsedChannels())
                .as("Z's input side, a sibling branch of the SAME controller grid as A/B's cycle, is unaffected")
                .isEqualTo(1);
        assertThat(pathingServiceOf(healthyMiddle).getAdHocNetworkError())
                .as("Z's independent ad-hoc output grid never even sees A/B's cycle")
                .isNull();
        // healthyMiddle is an ad-hoc (no-controller) grid: getUsedChannels() on a node there reflects the whole
        // grid's total (AdHocChannelUpdater), not a per-node count - zOut's own main (1) + healthyConsumer (1).
        assertThat(pathingServiceOf(healthyMiddle).getUsedChannels())
                .as("an unrelated tunnel pair elsewhere is completely unaffected by A/B's cycle")
                .isEqualTo(2);
    }

    @Test
    void threeDistinctTunnelsFormingAGenuineDependencyCycleIsDetectedWithoutPunishingTheOtherTwo() {
        // A -> B -> C -> A: three DIFFERENT tunnels (three different owners), each one's outer feeding the network
        // that carries the NEXT tunnel's own main, with C's outer finally closing the loop back onto A's own main.
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel();
        var b = makeTunnel();
        var c = makeTunnel();

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);
        wireIntoLocalNetwork(b, cable1); // A carries B.

        var cable2 = makeReadyNode();
        GridHelper.createConnection(cable1, cable2);
        GridHelper.createConnection(b.outer(), cable2);
        wireIntoLocalNetwork(c, cable2); // B carries C.

        var cable3 = makeReadyNode();
        GridHelper.createConnection(cable2, cable3);
        GridHelper.createConnection(c.outer(), cable3);

        var cable4 = makeReadyNode();
        GridHelper.createConnection(cable3, cable4);
        wireIntoLocalNetwork(a, cable4); // C carries A - the loop closes.

        runTick(controllerNode.getGrid(), 3);

        assertThat(a.main().getUsedChannels())
                .as("A's own outer is an ancestor of A's own main (via B, then C) - the real closed 3-tunnel loop "
                        + "is detected and A is refused")
                .isZero();
        assertThat(b.main().getUsedChannels())
                .as("B is a genuine link in the cycle (its outer carries C) but B's own outer is not an ancestor "
                        + "of B's own main - B is not self-referencing and keeps its channel")
                .isEqualTo(1);
        assertThat(c.main().getUsedChannels())
                .as("C is likewise a genuine link (its outer closes the loop onto A) but not self-referencing "
                        + "itself - C keeps its channel too")
                .isEqualTo(1);
    }

    @Test
    void cycleClosingThroughAQuantumLaneIsStillDetectedWithoutBlockingTheLane() {
        // Same 2-tunnel mutual cycle as above, except one hop of the loop is carried across a Q1/Q2-style "Quantum
        // Lane" (a plain GridHelper.createConnection to a node owned by its own separate host, exactly like
        // RemoteQuantumLaneIsolationTest / tests #8-9), proving the lane primitive neither hides nor falsely
        // triggers the loop check - lane nodes carry none of the COMPRESSED_CHANNEL/CANNOT_CARRY_COMPRESSED flags.
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel();
        var b = makeTunnel();

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);

        // Quantum lane #1, between A's outer-side network and B's own main.
        var lane1Host = new Object();
        var lane1Node = makeOwnedNode(lane1Host);
        GridHelper.createConnection(cable1, lane1Node);
        wireIntoLocalNetwork(b, lane1Node);

        var cable2 = makeReadyNode();
        GridHelper.createConnection(lane1Node, cable2);
        GridHelper.createConnection(b.outer(), cable2);

        // Quantum lane #2, between B's outer-side network and A's own main, closing the loop.
        var lane2Host = new Object();
        var lane2Node = makeOwnedNode(lane2Host);
        GridHelper.createConnection(cable2, lane2Node);
        wireIntoLocalNetwork(a, lane2Node);

        runTick(controllerNode.getGrid(), 3);

        assertThat(a.main().getUsedChannels())
                .as("the loop still closes back onto A's own main even though two of its hops cross quantum lanes")
                .isZero();
        assertThat(b.main().getUsedChannels())
                .as("B, reached only through a lane, is not itself self-referencing and keeps its channel")
                .isEqualTo(1);
    }

    @Test
    void twoCompletelyDisconnectedGridsWithIndependentTunnelChainsDoNotAffectEachOther() {
        // Two-hop P2P chains (see twoHopChainGrantsAChannelAtEachHopWithoutRejection) built TWICE, in two entirely
        // separate ad-hoc Grid instances that share no node, no owner, and no connection whatsoever - not merely two
        // pairs coexisting in one shared network (that's test #7). This also covers "same frequency reused in
        // disconnected contexts": the production check (GridNode#setControllerRoute /
        // PathingService#calculateAdHocChannels, both re-read for this round) never references a P2P frequency
        // value at all - only java object owner identity and Grid/tree membership - so two fully disconnected
        // grids can never influence each other via a coincidentally-shared frequency number; this test demonstrates
        // that independence directly, at the strongest level (zero physical connectivity between them).
        var homeOne = makeReadyNode();
        var aOne = makeTunnel();
        wireIntoLocalNetwork(aOne, homeOne);
        var middleOne = makeReadyNode();
        var aOneOut = makeTunnel();
        wireIntoLocalNetwork(aOneOut, middleOne);
        linkFrequency(aOne, aOneOut);
        var consumerOne = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(middleOne, consumerOne);

        var homeTwo = makeReadyNode();
        var aTwo = makeTunnel();
        wireIntoLocalNetwork(aTwo, homeTwo);
        var middleTwo = makeReadyNode();
        var aTwoOut = makeTunnel();
        wireIntoLocalNetwork(aTwoOut, middleTwo);
        linkFrequency(aTwo, aTwoOut);
        var consumerTwo = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(middleTwo, consumerTwo);

        assertThat(middleOne.getGrid()).as("no accidental physical merge between the two setups")
                .isNotSameAs(middleTwo.getGrid());

        runTick(middleOne.getGrid(), 3);
        runTick(middleTwo.getGrid(), 3);

        assertThat(pathingServiceOf(middleOne).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(middleTwo).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(middleOne).getUsedChannels()).isEqualTo(2);
        assertThat(pathingServiceOf(middleTwo).getUsedChannels()).isEqualTo(2);
    }

    @Test
    void sharedAncestorBranchingIntoTwoIndependentSubtreesIsNotTreatedAsACycle() {
        // A
        // |\
        // B D
        // | |
        // C E
        // A single ad-hoc grid where A fans out into two independent downstream branches (B->C and D->E); neither
        // branch returns to A nor crosses into the other. This must be recognized as ordinary nesting, never as a
        // routing loop, and must not cross-contaminate channel counts between the branches. Note (established by
        // twoHopChainGrantsAChannelAtEachHopWithoutRejection): a P2P hop's OUTPUT side (afterB/afterD below) is
        // always its own separate ad-hoc grid from its input side's "home" grid - the frequency link only merges
        // the two OUTER nodes with each other, never the main networks - so each of the three grids here (rootA,
        // afterB, afterD) is ticked and asserted independently, exactly like the existing multi-hop tests.
        var rootA = makeReadyNode();

        var b = makeTunnel();
        wireIntoLocalNetwork(b, rootA);
        var afterB = makeReadyNode();
        var bOut = makeTunnel();
        wireIntoLocalNetwork(bOut, afterB);
        linkFrequency(b, bOut);
        var c = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(afterB, c);

        var d = makeTunnel();
        wireIntoLocalNetwork(d, rootA);
        var afterD = makeReadyNode();
        var dOut = makeTunnel();
        wireIntoLocalNetwork(dOut, afterD);
        linkFrequency(d, dOut);
        var e = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(afterD, e);

        runTick(rootA.getGrid(), 3);
        runTick(afterB.getGrid(), 3);
        runTick(afterD.getGrid(), 3);

        assertThat(pathingServiceOf(rootA).getAdHocNetworkError())
                .as("a shared ancestor fanning out into two independent, non-crossing subtrees is not a cycle")
                .isNull();
        assertThat(pathingServiceOf(afterB).getAdHocNetworkError()).isNull();
        assertThat(pathingServiceOf(afterD).getAdHocNetworkError()).isNull();
        // rootA's own flat grid: b's main (1) + d's main (1) - the two branch roots fanning out from A.
        assertThat(pathingServiceOf(rootA).getUsedChannels()).isEqualTo(2);
        // Each branch's own output grid: its own tunnel main (1) + its own leaf consumer (1) - completely
        // independent of the other branch, proving no cross-contamination between B->C and D->E.
        assertThat(pathingServiceOf(afterB).getUsedChannels()).isEqualTo(2);
        assertThat(pathingServiceOf(afterD).getUsedChannels()).isEqualTo(2);
    }

    @Test
    void twoIndependentSourcesConvergingOnASharedDownstreamTargetIsNotACycle() {
        // B --\
        // X (shared consumer network, does not lead anywhere further)
        // D --/
        // Two independent P2P outputs converge their local networks onto one shared downstream cable segment X.
        // X does not feed back into either source - this is an ordinary convergence, not a cycle.
        var homeB = makeReadyNode();
        var b = makeTunnel();
        wireIntoLocalNetwork(b, homeB);
        var homeD = makeReadyNode();
        var d = makeTunnel();
        wireIntoLocalNetwork(d, homeD);

        var sharedTargetX = makeReadyNode();
        var bOut = makeTunnel();
        wireIntoLocalNetwork(bOut, sharedTargetX);
        linkFrequency(b, bOut);
        var dOut = makeTunnel();
        wireIntoLocalNetwork(dOut, sharedTargetX);
        linkFrequency(d, dOut);

        var consumer = makeOwnedNode(new Object(), GridFlags.REQUIRE_CHANNEL);
        GridHelper.createConnection(sharedTargetX, consumer);

        runTick(sharedTargetX.getGrid(), 3);
        var ps = pathingServiceOf(sharedTargetX);

        assertThat(ps.getAdHocNetworkError())
                .as("two independent sources converging on one shared, non-returning target is not a cycle")
                .isNull();
        // bOut's main (1) + dOut's main (1) + consumer (1).
        assertThat(ps.getUsedChannels()).isEqualTo(3);
    }

    @Test
    void breakingOneEdgeOfAGenuineMultiTunnelCycleAutomaticallyRestoresNormalChannelsOnNextRepath() {
        // Reuses the exact 2-tunnel mutual-cycle shape proven above; breaking the single edge that closes the loop
        // (exactly like a player removing/relocating the offending cable run) must restore A's channel with no
        // manual reset, mirroring #15 but for a genuine two-DIFFERENT-tunnel cycle rather than a single self-loop.
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel();
        var b = makeTunnel();

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

        // Break the loop-closing edge and reconnect A's main harmlessly, directly off the controller (bypassing
        // cable3/B's outer entirely) - exactly like a player relocating the tunnel that caused the loop.
        closingEdge.destroy();
        var freshCable = makeReadyNode();
        GridHelper.createConnection(controllerNode, freshCable);
        GridHelper.createConnection(freshCable, a.main());

        runTick(controllerNode.getGrid(), 3);
        assertThat(a.main().getUsedChannels())
                .as("A's own outer is no longer on A's own path (B's outer no longer closes back onto A) - "
                        + "recovered automatically on the next ordinary repath")
                .isEqualTo(1);
    }

    @Test
    void repeatedRepathAfterAGenuineMultiTunnelCycleNeverRecalculatesRunawayOrThrows() {
        // Same as #13 (repeatedRepathAfterACycleIsDetectedNeverRecalculatesRunawayOrThrows) but for a real, closed
        // 3-DIFFERENT-tunnel dependency chain instead of a single tunnel's self-loop - proves the controller-mode
        // ancestor-chain check is stable under many repath cycles for a genuine multi-owner cycle too, not just the
        // degenerate single-owner case.
        var mockController = mock(ControllerBlockEntity.class);
        var controllerNode = makeOwnedNode(mockController);
        when(mockController.getGridNode()).thenReturn(controllerNode);
        when(mockController.getBlockPos()).thenReturn(BlockPos.ZERO);

        var a = makeTunnel();
        var b = makeTunnel();
        var c = makeTunnel();

        GridHelper.createConnection(controllerNode, a.outer());
        var cable1 = makeReadyNode();
        GridHelper.createConnection(a.outer(), cable1);
        wireIntoLocalNetwork(b, cable1);

        var cable2 = makeReadyNode();
        GridHelper.createConnection(cable1, cable2);
        GridHelper.createConnection(b.outer(), cable2);
        wireIntoLocalNetwork(c, cable2);

        var cable3 = makeReadyNode();
        GridHelper.createConnection(cable2, cable3);
        GridHelper.createConnection(c.outer(), cable3);

        var cable4 = makeReadyNode();
        GridHelper.createConnection(cable3, cable4);
        wireIntoLocalNetwork(a, cable4);

        for (int i = 0; i < 25; i++) {
            runTick(controllerNode.getGrid(), 1);
            assertThat(a.main().getUsedChannels()).as("iteration %d: still refused, stably", i).isZero();
            assertThat(b.main().getUsedChannels()).as("iteration %d: still granted, stably", i).isEqualTo(1);
            assertThat(c.main().getUsedChannels()).as("iteration %d: still granted, stably", i).isEqualTo(1);
        }
    }
}
