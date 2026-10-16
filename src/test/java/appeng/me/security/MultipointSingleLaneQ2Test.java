package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.crafting.simulation.helpers.ProcessingPatternBuilder;
import appeng.me.AbstractGridNodeTest;
import appeng.me.GridConnection;
import appeng.me.ManagedGridNode;

/**
 * Phase G — Quantum Q2 — Multipoint Single-Lane prototype.
 * <p/>
 * Answers a single narrow architectural question, building directly on Phase F's Q1 result (two independent REMOTE
 * Quantum Lanes never merge): can <b>one</b> Quantum Lane connect <b>three</b> remote Endpoints of the <em>same</em>
 * Network Slot — {@code Base <-> Factory <-> Mine} — using exactly <b>2</b> {@link GridConnection}s (a chain), not 3 (a
 * triangle/mesh)? Since {@code Grid} in AE2 is already a compute-on-demand connected component of the
 * {@code GridNode}/{@code GridConnection} graph (confirmed by reading {@code GridConnection#mergeGrids} and
 * {@code GridPropagator}), a chain of 2 connections transitively merges all 3 endpoints into a single {@code Grid} with
 * no new production code needed — exactly the same primitive Q1 used twice for two independent lanes, used here twice
 * for one connected lane.
 * <p/>
 * <b>Topology under test</b>:
 *
 * <pre>
 *   Base --conn1-- Factory --conn2-- Mine
 * </pre>
 *
 * Factory is a relay: it has 2 {@link GridConnection}s (to Base and to Mine). Base and Mine are <b>never</b> directly
 * connected. This is the deliberate non-goal of this prototype: NOT a triangle (Base<->Factory, Factory<->Mine,
 * Base<->Mine = 3 connections = O(N^2) for N=3), which would not scale. For a chain of N endpoints this pattern uses
 * exactly N-1 {@link GridConnection}s — O(N), not O(N^2).
 * <p/>
 * <b>Channel semantics investigated (the central technical question of this round)</b>: see the class-level comment
 * block above {@link #channelUsageAcrossTheChainIsAGridWideAggregateNotAPerHopMultiplier()} for the full writeup;
 * summarized in the incremental report at {@code phase-g-quantum-q2-multipoint-report.md}.
 * <p/>
 * Lives in {@code appeng.me.security} for the same package-private test hooks Q1 and its Phase D sibling use
 * ({@code SecurityService#setRegistryForTesting}, {@code SecurityDomainRegistry#TYPE}/{@code createForTesting}) — no
 * new test-only hooks were added to production code, and (as anticipated) no production code was changed at all.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class MultipointSingleLaneQ2Test extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    /**
     * Same lazy-bootstrap-ordering hazard Q1 found and fixed in {@code RemoteQuantumLaneIsolationTest} — forcing the
     * ephemeral server bootstrap here, unconditionally, up front, makes every test in this class deterministic
     * regardless of JUnit's chosen method order.
     */
    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    // =====================================================================
    // 1: GRID IDENTITY - all three endpoints share ONE grid, not three
    // =====================================================================

    @Test
    void allThreeEndpointsShareExactlyOneGridAfterTheChainConnects() {
        var chain = new MultipointChainHost();
        chain.connectChain();

        assertThat(chain.base.getGrid()).isNotNull();
        assertThat(chain.base.getGrid()).isSameAs(chain.factory.getGrid());
        assertThat(chain.base.getGrid()).isSameAs(chain.mine.getGrid());

        var nodesInGrid = new java.util.ArrayList<IGridNode>();
        chain.base.getGrid().getNodes().forEach(nodesInGrid::add);
        assertThat(nodesInGrid).containsExactlyInAnyOrder(
                chain.base.getNode(), chain.factory.getNode(), chain.mine.getNode());
    }

    // =====================================================================
    // 2: EXACTLY 2 GridConnections, not 3 - no triangle/mesh
    // =====================================================================

    @Test
    void exactlyTwoGridConnectionsExistForThreeEndpointsNotThree() {
        var chain = new MultipointChainHost();
        chain.connectChain();

        // Explicit connection-count audit: Base has 1 connection (to Factory), Mine has 1 connection (to Factory),
        // Factory - the relay - has 2 connections (to Base and to Mine). Total distinct GridConnection objects: 2.
        assertThat(chain.base.getNode().getConnections()).containsExactly(chain.conn1);
        assertThat(chain.mine.getNode().getConnections()).containsExactly(chain.conn2);
        assertThat(chain.factory.getNode().getConnections()).containsExactlyInAnyOrder(chain.conn1, chain.conn2);

        var distinctConnections = Set.of(chain.conn1, chain.conn2);
        assertThat(distinctConnections).hasSize(2);
        // Base and Mine are NEVER directly connected - no triangle, no mesh.
        assertThat(chain.conn1).isNotSameAs(chain.conn2);
        assertThat(chain.base.getNode().getConnections()).doesNotContain(chain.conn2);
        assertThat(chain.mine.getNode().getConnections()).doesNotContain(chain.conn1);
    }

    // =====================================================================
    // 3: REAL MEStorage sharing across the chain (insert at Base, extract at Mine)
    // =====================================================================

    @Test
    void itemInsertedAtBaseIsExtractableAtMineAcrossTwoHops() {
        var chain = new MultipointChainHost();
        var mineStorage = new SimpleFakeStorage();
        chain.mine.addService(IStorageProvider.class, new SimpleStorageProvider(mineStorage));
        chain.connectChain();

        var diamond = AEItemKey.of(Items.DIAMOND);
        var inserted = chain.base.getGrid().getStorageService().getInventory()
                .insert(diamond, 64, Actionable.MODULATE, IActionSource.empty());
        assertThat(inserted).isEqualTo(64L);

        // Visible from Factory (the relay) too - one shared grid-wide storage service.
        var seenFromFactory = new KeyCounter();
        chain.factory.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromFactory);
        assertThat(seenFromFactory.get(diamond)).isEqualTo(64L);

        var extracted = chain.mine.getGrid().getStorageService().getInventory()
                .extract(diamond, 64, Actionable.MODULATE, IActionSource.empty());
        assertThat(extracted).isEqualTo(64L);
    }

    // =====================================================================
    // 4: REAL ICraftingService sharing - pattern at one endpoint visible from all
    // =====================================================================

    @Test
    void craftingPatternDiscoveredAtOneEndpointIsVisibleThroughTheWholeMergedGrid() {
        var chain = new MultipointChainHost();
        var andesite = AEItemKey.of(Items.ANDESITE);
        var pattern = new ProcessingPatternBuilder(new GenericStack(andesite, 1)).build();
        // The pattern is hosted at Mine - the far end of the chain, two hops from Base.
        chain.mine.addService(ICraftingProvider.class, new SinglePatternProvider(pattern));
        chain.connectChain();

        assertThat(chain.base.getGrid().getCraftingService().isCraftable(andesite)).isTrue();
        assertThat(chain.factory.getGrid().getCraftingService().isCraftable(andesite)).isTrue();
        assertThat(chain.mine.getGrid().getCraftingService().getCraftables(k -> true)).containsExactly(andesite);
    }

    // =====================================================================
    // 5: FULL SECURITY MATRIX (PLAYER/GROUP/DEVICE/EVERYONE) across all 3 endpoints
    // =====================================================================

    @Test
    void playerGrantOnTheSharedDomainIsCheckedIdenticallyThroughAllThreeEndpoints() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var registry = SecurityDomainRegistry.createForTesting();
        var domainId = securityOf(chain.base, registry).bootstrapDomain(UUID.randomUUID());

        var grantedPlayer = UUID.randomUUID();
        registry.getDomain(domainId).grant(PrincipalId.ofPlayer(grantedPlayer), Set.of(SecurityPermissions.EXTRACT));

        assertThat(securityOf(chain.base, registry).hasPermission(playerSource(grantedPlayer),
                SecurityPermissions.EXTRACT)).isTrue();
        assertThat(securityOf(chain.factory, registry).hasPermission(playerSource(grantedPlayer),
                SecurityPermissions.EXTRACT)).isTrue();
        assertThat(securityOf(chain.mine, registry).hasPermission(playerSource(grantedPlayer),
                SecurityPermissions.EXTRACT)).isTrue();

        var stranger = UUID.randomUUID();
        assertThat(securityOf(chain.mine, registry).hasPermission(playerSource(stranger), SecurityPermissions.EXTRACT))
                .isFalse();
    }

    @Test
    void groupGrantOnTheSharedDomainIsCheckedIdenticallyThroughAllThreeEndpoints() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var registry = SecurityDomainRegistry.createForTesting();
        var domainId = securityOf(chain.base, registry).bootstrapDomain(UUID.randomUUID());

        var groupMember = UUID.randomUUID();
        var group = SecurityGroup.create("chain-miners").withMember(groupMember);
        var domain = registry.getDomain(domainId);
        domain.putGroup(group);
        domain.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.CRAFT));

        assertThat(securityOf(chain.base, registry).hasPermission(playerSource(groupMember), SecurityPermissions.CRAFT))
                .isTrue();
        assertThat(
                securityOf(chain.factory, registry).hasPermission(playerSource(groupMember), SecurityPermissions.CRAFT))
                .isTrue();
        assertThat(securityOf(chain.mine, registry).hasPermission(playerSource(groupMember), SecurityPermissions.CRAFT))
                .isTrue();
    }

    @Test
    void deviceGrantOnTheSharedDomainIsCheckedIdenticallyThroughAllThreeEndpoints() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var registry = SecurityDomainRegistry.createForTesting();
        var domainId = securityOf(chain.base, registry).bootstrapDomain(UUID.randomUUID());

        var deviceId = UUID.randomUUID();
        registry.getDomain(domainId).grant(PrincipalId.ofDevice(deviceId),
                Set.of(SecurityPermissions.INJECT));
        // Round 32: a device is something AE2 can locate and the registry authenticated - not an object that
        // says what it is. The helper builds exactly that shape.
        var deviceSource = RegisteredTestDevice.source(deviceRegistry, level, deviceId);

        assertThat(securityOf(chain.base, registry).hasPermission(deviceSource, SecurityPermissions.INJECT)).isTrue();
        assertThat(securityOf(chain.factory, registry).hasPermission(deviceSource, SecurityPermissions.INJECT))
                .isTrue();
        assertThat(securityOf(chain.mine, registry).hasPermission(deviceSource, SecurityPermissions.INJECT)).isTrue();
    }

    @Test
    void everyoneGrantOnTheSharedDomainAppliesIdenticallyThroughAllThreeEndpoints() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var registry = SecurityDomainRegistry.createForTesting();
        var domainId = securityOf(chain.base, registry).bootstrapDomain(UUID.randomUUID());
        registry.getDomain(domainId).grant(PrincipalId.EVERYONE, Set.of(SecurityPermissions.EXTRACT));

        var stranger = UUID.randomUUID();
        assertThat(securityOf(chain.base, registry).hasPermission(playerSource(stranger), SecurityPermissions.EXTRACT))
                .isTrue();
        assertThat(
                securityOf(chain.factory, registry).hasPermission(playerSource(stranger), SecurityPermissions.EXTRACT))
                .isTrue();
        assertThat(securityOf(chain.mine, registry).hasPermission(playerSource(stranger), SecurityPermissions.EXTRACT))
                .isTrue();
        // Every other permission stays denied for the stranger - EVERYONE grant is exact, not blanket.
        assertThat(securityOf(chain.mine, registry).hasPermission(playerSource(stranger), SecurityPermissions.INJECT))
                .isFalse();
    }

    @Test
    void allThreeEndpointsAreBoundToTheExactSameSingleSecurityDomainBecauseItIsOneGrid() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var registry = SecurityDomainRegistry.createForTesting();
        var domainId = securityOf(chain.base, registry).bootstrapDomain(UUID.randomUUID());

        assertThat(securityOf(chain.base, registry).getDomainId()).contains(domainId);
        assertThat(securityOf(chain.factory, registry).getDomainId()).contains(domainId);
        assertThat(securityOf(chain.mine, registry).getDomainId()).contains(domainId);
        assertThat(securityOf(chain.base, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(chain.factory, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(chain.mine, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
    }

    // =====================================================================
    // 6: NEGATIVE - 4th endpoint under a DIFFERENT security domain is rejected
    // =====================================================================

    @Test
    void connectingAFourthEndpointUnderADifferentSecurityDomainIsRejectedByThePreMergeGuard() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var registryChain = SecurityDomainRegistry.createForTesting();
        var registryOutpost = SecurityDomainRegistry.createForTesting();
        securityOf(chain.base, registryChain).bootstrapDomain(UUID.randomUUID());

        var outpost = new ManagedGridNode(new Object(), listener);
        outpost.create(level, null);
        securityOf(outpost, registryOutpost).bootstrapDomain(UUID.randomUUID());
        var gridChainBefore = chain.mine.getGrid();
        var gridOutpostBefore = outpost.getGrid();

        // Attempt to attach a 4th endpoint (Outpost) onto Mine, the far end of the chain - under a DIFFERENT domain.
        var rejected = GridHelper.createConnection(chain.mine.getNode(), outpost.getNode());

        assertThat(rejected).isNull();
        // Nothing changed: the 3-endpoint chain grid and Outpost's own grid are exactly the same instances as
        // before the rejected attempt - no transient leak, no partial merge.
        assertThat(chain.mine.getGrid()).isSameAs(gridChainBefore);
        assertThat(outpost.getGrid()).isSameAs(gridOutpostBefore);
        assertThat(chain.mine.getNode().getConnections()).containsExactly(chain.conn2);
        assertThat(outpost.getNode().getConnections()).isEmpty();
        // The chain is still exactly 2 GridConnections - the rejected attempt did not create a 3rd.
        assertThat(chain.base.getNode().getConnections()).containsExactly(chain.conn1);
        assertThat(chain.factory.getNode().getConnections()).containsExactlyInAnyOrder(chain.conn1, chain.conn2);
    }

    // =====================================================================
    // 7: SECURED+LEGACY combination applied to the multipoint chain
    // =====================================================================

    @Test
    void legacyMineEndpointJoiningAnAlreadySecuredBaseFactoryPairMergesIntoTheSingleSecuredDomain() {
        var chain = new MultipointChainHost();
        // Connect only Base<->Factory first and secure that pair, mirroring SecuredLegacyMergeSplitTest's own
        // "SECURED A + LEGACY B connects" scenario, then extend the chain to Mine (never bootstrapped - genuinely
        // LEGACY) exactly the way a real Quantum Lane would grow by one more hop.
        chain.base.create(level, null);
        chain.factory.create(level, null);
        chain.mine.create(level, null);
        chain.conn1 = (GridConnection) GridHelper.createConnection(chain.base.getNode(), chain.factory.getNode());
        assertThat(chain.conn1).isNotNull();

        var registry = SecurityDomainRegistry.createForTesting();
        var domainId = securityOf(chain.base, registry).bootstrapDomain(UUID.randomUUID());
        assertThat(securityOf(chain.factory, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(chain.mine, registry).getState()).isEqualTo(SecurityDomainState.LEGACY);

        chain.conn2 = (GridConnection) GridHelper.createConnection(chain.factory.getNode(), chain.mine.getNode());
        assertThat(chain.conn2).isNotNull();

        // Same mechanism SecuredLegacyMergeSplitTest already proved for a 2-node case: Mine's savedData is non-null
        // at merge time (it was already a standalone grid before joining), so it never registers a permanent domain
        // binding, yet the WHOLE 3-node merged grid (one Grid, one SecurityService) is SECURED under domain A.
        assertThat(securityOf(chain.mine, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(chain.mine, registry).getDomainId()).contains(domainId);
        assertThat(chain.base.getGrid()).isSameAs(chain.mine.getGrid());

        // Unknown players still get zero access despite Mine's legacy origin - LEGACY-open semantics do not leak in.
        var stranger = UUID.randomUUID();
        for (var permission : SecurityPermissions.values()) {
            assertThat(securityOf(chain.mine, registry).hasPermission(playerSource(stranger), permission)).isFalse();
        }

        // Splitting conn2 back off returns Mine to genuinely LEGACY, exactly like the 2-node case.
        chain.conn2.destroy();
        assertThat(securityOf(chain.mine, registry).getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(securityOf(chain.base, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
    }

    // =====================================================================
    // 8: ENDPOINT ADD/REMOVE CYCLES
    // =====================================================================

    @Test
    void disconnectingAndReconnectingMineSplitsThenReMergesTheGridCorrectly() {
        var chain = new MultipointChainHost();
        chain.connectChain();
        var baseFactoryGridBeforeSplit = chain.base.getGrid();

        // Disconnect Mine (destroy conn2 - the Factory<->Mine hop).
        chain.conn2.destroy();

        assertThat(chain.base.getGrid()).isSameAs(chain.factory.getGrid());
        assertThat(chain.base.getGrid()).isNotSameAs(chain.mine.getGrid());
        assertThat(chain.base.getGrid()).isSameAs(baseFactoryGridBeforeSplit);
        assertThat(chain.mine.getNode().getConnections()).isEmpty();
        assertThat(chain.factory.getNode().getConnections()).containsExactly(chain.conn1);

        // Reconnect Mine.
        var reconnected = GridHelper.createConnection(chain.factory.getNode(), chain.mine.getNode());
        assertThat(reconnected).isNotNull();
        chain.conn2 = (GridConnection) reconnected;

        assertThat(chain.base.getGrid()).isSameAs(chain.factory.getGrid());
        assertThat(chain.base.getGrid()).isSameAs(chain.mine.getGrid());
        assertThat(chain.base.getGrid()).isSameAs(baseFactoryGridBeforeSplit);
        // Back to exactly 2 GridConnections total.
        assertThat(chain.factory.getNode().getConnections()).containsExactlyInAnyOrder(chain.conn1, chain.conn2);
    }

    // =====================================================================
    // 9: PARTITION BEHAVIOR when the relay (Factory) is removed
    // =====================================================================

    @Test
    void removingTheRelayFactoryCorrectlyPartitionsBaseAndMineIntoTwoSeparateGrids() {
        var chain = new MultipointChainHost();
        chain.connectChain();

        // Destroying Factory's node severs BOTH conn1 and conn2 at once (GridNode#destroy already severs every
        // connection of the destroyed node - the same primitive Q1 used to model a lane's remote endpoint
        // unloading). This is the standard, expected behavior of a connected graph losing an articulation point -
        // NOT a bug, and explicitly out of scope to "fix": no automatic Base<->Mine direct reconnection is
        // attempted or expected. A future "MESH" round would be the place to decide whether/how to offer
        // alternate-route healing; this Q2 prototype deliberately does not.
        chain.factory.destroy();

        assertThat(chain.base.getNode().getConnections()).isEmpty();
        assertThat(chain.mine.getNode().getConnections()).isEmpty();
        assertThat(chain.base.getGrid()).isNotNull();
        assertThat(chain.mine.getGrid()).isNotNull();
        assertThat(chain.base.getGrid()).isNotSameAs(chain.mine.getGrid());

        // Base and Mine are each now their own standalone single-node grid - no direct connection was created or
        // implied between them.
        var baseGridNodes = new java.util.ArrayList<IGridNode>();
        chain.base.getGrid().getNodes().forEach(baseGridNodes::add);
        assertThat(baseGridNodes).containsExactly(chain.base.getNode());

        var mineGridNodes = new java.util.ArrayList<IGridNode>();
        chain.mine.getGrid().getNodes().forEach(mineGridNodes::add);
        assertThat(mineGridNodes).containsExactly(chain.mine.getNode());
    }

    // =====================================================================
    // 10: UNLOAD/RELOAD IN DIFFERENT ORDERS CONVERGE TO THE SAME MERGED STATE
    // =====================================================================

    @Test
    void reloadingBaseFirstThenFactoryThenMineConvergesToOneMergedGrid() {
        var order = new String[] { "base", "factory", "mine" };
        assertReloadOrderConvergesToOneMergedGrid(order);
    }

    @Test
    void reloadingMineFirstThenFactoryThenBaseConvergesToTheSameMergedGridShape() {
        var order = new String[] { "mine", "factory", "base" };
        assertReloadOrderConvergesToOneMergedGrid(order);
    }

    private void assertReloadOrderConvergesToOneMergedGrid(String[] createOrder) {
        // Simulated "world reload": three brand-new nodes are created (in the given order), each owned by its own
        // separate plain object (modeling three physically distant endpoints of the same slot loading back in, in
        // whatever chunk order the server happens to load them), then the two chain connections are formed once all
        // three nodes exist - exactly how real world chunk loading is order-independent for AE2's grid formation
        // (a node with no neighbors yet simply sits as its own 1-node grid until a connection reaches it).
        var nodesByRole = new java.util.HashMap<String, ManagedGridNode>();
        for (var role : createOrder) {
            var node = new ManagedGridNode(new Object(), listener);
            node.create(level, null);
            nodesByRole.put(role, node);
        }

        var base = nodesByRole.get("base");
        var factory = nodesByRole.get("factory");
        var mine = nodesByRole.get("mine");

        var conn1 = GridHelper.createConnection(base.getNode(), factory.getNode());
        var conn2 = GridHelper.createConnection(factory.getNode(), mine.getNode());

        assertThat(conn1).isNotNull();
        assertThat(conn2).isNotNull();
        assertThat(base.getGrid()).isSameAs(factory.getGrid());
        assertThat(base.getGrid()).isSameAs(mine.getGrid());

        var nodesInGrid = new java.util.ArrayList<IGridNode>();
        base.getGrid().getNodes().forEach(nodesInGrid::add);
        assertThat(nodesInGrid).containsExactlyInAnyOrder(base.getNode(), factory.getNode(), mine.getNode());

        // Exactly 2 connections regardless of creation order.
        assertThat(factory.getNode().getConnections()).hasSize(2);
        assertThat(base.getNode().getConnections()).hasSize(1);
        assertThat(mine.getNode().getConnections()).hasSize(1);
    }

    // =====================================================================
    // 11 / 12: CHANNEL SEMANTICS AND COMPLEXITY AUDIT
    // =====================================================================

    /**
     * <b>Channel semantics investigation - the central technical question of Q2.</b>
     * <p/>
     * AE2's channel accounting has TWO structurally different mechanisms, confirmed by reading the real production code
     * (not assumed):
     * <ol>
     * <li>{@link GridConnection#getMaxChannels()} - {@code 32 * mode.getCableCapacityFactor()} - a per-connection
     * theoretical capacity ceiling, computed purely from the shared grid's {@code ChannelMode}. Since {@code conn1} and
     * {@code conn2} both belong to the exact same merged {@code Grid} (same {@code PathingService}, same
     * {@code ChannelMode}), each independently reports 32 - never 64 (32*2) and never 16 (32/2). This mirrors exactly
     * what Q1's test 20 already established per-lane: the figure is grid-channel-mode-derived, not
     * connection-count-derived. Proven live, below.</li>
     * <li>{@link appeng.me.service.PathingService#getUsedChannels()} - in AD-HOC mode (no {@code ControllerBlockEntity}
     * present in the grid, which is this prototype's entire fixture, exactly like Q1's and matching the sibling
     * {@code NetworkSlotIsolationTest}'s own documented reason for not building a full controller fixture), actual
     * channel consumption is {@code PathingService#calculateAdHocChannels()} - a flat count of the distinct
     * {@code GridFlags.REQUIRE_CHANNEL} nodes in the WHOLE grid's {@code nodesNeedingChannels} set, capped by
     * {@code channelMode.getAdHocNetworkChannels()}. This count is structurally topology-independent: a
     * channel-consuming leaf attached at Mine (two hops from Base) costs exactly 1 unit of the single grid-wide budget,
     * identically to a leaf attached directly at Base. Proven live, below.</li>
     * </ol>
     * <b>Honest scope boundary on the per-hop bottleneck mechanism</b>: AE2 also has a SECOND, controller-driven
     * pathing algorithm ({@code appeng.me.pathfinding.PathingCalculation}, only exercised when the grid contains a real
     * {@code ControllerBlockEntity}-owned node) whose {@code tryUseChannel}/{@code getHighestSimilarAncestor} walk
     * assigns each logical channel to a path from a channel-requiring device up to the controller, and (via
     * {@code GridNode#propagateChannelsUpwards}) sums {@code connection.usedChannels} at every relay node it passes
     * through. Reading that code directly answers the question this round asked: a single logical channel adds exactly
     * <b>+1</b> to {@code usedChannels} at EVERY hop it transits (Factory, in this topology, would show
     * {@code usedChannels} equal to the count of distinct channels currently routed through it - never doubled, never
     * multiplied by hop count) - i.e. cable/connection capacity is a limit on a physical segment, not a per-device
     * multiplier, confirming the project's "32 per lane, never 64/128 for going through more hops" invariant. This
     * conclusion is reached by READING {@code GridNode.propagateChannelsUpwards} and
     * {@code PathingCalculation.tryUseChannel} (both cited above and quoted in the incremental report), NOT by a live
     * integration test through a real controller: constructing a genuine, in-test {@code ControllerBlockEntity}
     * requires full {@code BlockEntityType}/{@code BlockPos}/{@code BlockState}/level-tick wiring that is out of
     * proportion for this test-only architecture prototype - the exact same judgment call the Q1-sibling
     * {@code NetworkSlotIsolationTest} already made and documented (see its own comment above
     * {@code controllerRelatedPathingStateOnSlotADoesNotAffectSlotB}) for an equivalent controller-state question. This
     * is stated here plainly, not glossed over: the ad-hoc-mode live test below is real production-code proof of "no
     * per-hop multiplication" for the mechanism this fixture can exercise; the controller-mode per-hop accounting is
     * proof by code-reading, not by live test, for the mechanism this fixture deliberately does not build.
     */
    @Test
    void channelUsageAcrossTheChainIsAGridWideAggregateNotAPerHopMultiplier() {
        var chain = new MultipointChainHost();
        chain.connectChain();

        // (1) GridConnection#getMaxChannels(): both hops of the chain report the SAME 32 - not 64 for having 2 hops.
        assertThat(chain.conn1.getMaxChannels()).isEqualTo(32);
        assertThat(chain.conn2.getMaxChannels()).isEqualTo(32);

        // (2) PathingService#getUsedChannels() in ad-hoc mode: one channel-consuming leaf attached at Mine (2 hops
        // from Base) costs exactly 1 unit of the single grid-wide budget - proving usage is a flat, topology
        // independent, per-GRID aggregate, not a per-connection or per-hop multiplied figure.
        addChannelConsumingLeaf(chain.mine);
        chain.base.getGrid().getPathingService().repath();
        runTick(chain.base.getGrid(), 2);

        assertThat(chain.base.getGrid().getPathingService().getUsedChannels()).isEqualTo(1);
        assertThat(chain.factory.getGrid().getPathingService()).isSameAs(chain.base.getGrid().getPathingService());
        assertThat(chain.mine.getGrid().getPathingService()).isSameAs(chain.base.getGrid().getPathingService());

        // (3) Adding a SECOND leaf, this time at Base (0 hops), brings the same single grid-wide count to 2 - not
        // "1 per hop-distance-bucket". One shared budget for the whole 3-endpoint, 2-connection chain.
        addChannelConsumingLeaf(chain.base);
        chain.base.getGrid().getPathingService().repath();
        runTick(chain.base.getGrid(), 2);

        assertThat(chain.base.getGrid().getPathingService().getUsedChannels()).isEqualTo(2);
    }

    /**
     * Complexity audit (task requirement 12): for N=3 endpoints in a chain, exactly N-1=2 {@link GridConnection}s are
     * required - O(N), not O(N^2). A triangle/mesh topology (Base<->Factory, Factory<->Mine, Base<->Mine) would need 3
     * connections for N=3 and, in general, N*(N-1)/2 for N endpoints - O(N^2), which does not scale. This prototype
     * deliberately never forms that third edge.
     * <p/>
     * For the manifest: <i>"MESH is a user-facing routing/topology semantic, not permission to build the physical
     * GridConnection graph with O(N^2) complexity."</i>
     */
    @Test
    void complexityAuditConfirmsNMinusOneConnectionsForNEndpointsNotNSquared() {
        var chain = new MultipointChainHost();
        chain.connectChain();

        int n = 3; // Base, Factory, Mine
        var allConnections = new java.util.HashSet<GridConnection>();
        allConnections.add(chain.conn1);
        allConnections.add(chain.conn2);
        // Also derive the count purely from each node's own connection list, to cross-check.
        allConnections.addAll(castConnections(chain.base.getNode()));
        allConnections.addAll(castConnections(chain.factory.getNode()));
        allConnections.addAll(castConnections(chain.mine.getNode()));

        assertThat(allConnections).hasSize(n - 1);
        assertThat(n - 1).isEqualTo(2);
        // A full mesh for the same N would need N*(N-1)/2 = 3 connections - one more than this chain uses, and the
        // gap only widens for larger N (e.g. N=10: chain=9, mesh=45).
        assertThat(n * (n - 1) / 2).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<GridConnection> castConnections(IGridNode node) {
        var result = new java.util.ArrayList<GridConnection>();
        for (var c : node.getConnections()) {
            result.add((GridConnection) c);
        }
        return result;
    }

    // =====================================================================
    // Fixtures / helpers
    // =====================================================================

    /**
     * Models "one Quantum Lane spanning three physically distant Endpoints of the same Network Slot": Base, Factory and
     * Mine are each owned by their own separate plain object (never a shared host - each is a genuinely distinct
     * physical location, unlike Q1's shared-host LOCAL pair). {@code connectChain()} forms conn1
     * ({@code base<->factory}) and conn2 ({@code factory<->mine}) via the exact same
     * {@link GridHelper#createConnection} primitive Q1 and the real production {@code QuantumCluster} both use - Base
     * and Mine are never directly connected.
     */
    private final class MultipointChainHost {
        final ManagedGridNode base = new ManagedGridNode(new Object(), listener);
        final ManagedGridNode factory = new ManagedGridNode(new Object(), listener);
        final ManagedGridNode mine = new ManagedGridNode(new Object(), listener);
        GridConnection conn1;
        GridConnection conn2;
        private boolean connected;

        void connectChain() {
            if (!connected) {
                connected = true;
                if (base.getNode() == null) {
                    base.create(level, null);
                }
                if (factory.getNode() == null) {
                    factory.create(level, null);
                }
                if (mine.getNode() == null) {
                    mine.create(level, null);
                }
                conn1 = (GridConnection) GridHelper.createConnection(base.getNode(), factory.getNode());
                conn2 = (GridConnection) GridHelper.createConnection(factory.getNode(), mine.getNode());
            }
        }
    }

    private void addChannelConsumingLeaf(ManagedGridNode joinPoint) {
        var leaf = new ManagedGridNode(new Object(), listener);
        leaf.setFlags(GridFlags.REQUIRE_CHANNEL);
        leaf.create(level, null);
        var connection = GridConnection.create(joinPoint.getNode(), leaf.getNode(), null);
        assertThat(connection).isNotNull();
    }

    private SecurityService securityOf(ManagedGridNode mgn, SecurityDomainRegistry registry) {
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(registry);
        // Round 32: device principals now come from the registry, not from the machine.
        security.setDeviceRegistryForTesting(deviceRegistry);
        return security;
    }

    private IActionSource playerSource(UUID playerId) {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(playerId);
        return IActionSource.ofPlayer(player);
    }

    /** Minimal in-memory {@link MEStorage} fixture, real enough to prove actual insert/extract across the chain. */
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
            return Component.literal("MultipointSingleLaneQ2Test fake storage");
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

    /**
     * A device that owns a <em>real</em> grid node, mirroring {@code GridHelper.createManagedNode(this, ...)}. Since
     * Round 27 the device id is read from {@code node.getOwner()}; a stand-in with no node is treated as an
     * unauthenticated claim (that shape is FINDING-3 and is covered by Round26SourceForgeryTest).
     */
    private final class TestDevice implements IActionHost, IDeviceIdentity {
        private final UUID id;
        private final ManagedGridNode ownNode;

        private TestDevice(UUID id) {
            this.id = id;
            this.ownNode = new ManagedGridNode(this, listener);
            this.ownNode.create(level, null);
        }

        @Override
        public IGridNode getActionableNode() {
            return ownNode.getNode();
        }

        @Override
        public UUID getDeviceId() {
            return id;
        }
    }
}
