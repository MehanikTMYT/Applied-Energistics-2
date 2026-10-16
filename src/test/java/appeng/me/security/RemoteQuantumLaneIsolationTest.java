package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
 * Phase F — Quantum Q1 — Remote Quantum Lane Isolation prototype.
 * <p/>
 * Answers a single narrow architectural question: can a future Quantum host hold two independent REMOTE Quantum Lanes —
 * each connecting a LOCAL node of one Network Slot to a physically distant REMOTE node of the <em>same</em> slot —
 * without Grid A (Slot A's lane) and Grid B (Slot B's lane) ever merging?
 * <p/>
 * <b>Design (verified against real production code, not invented):</b> {@code appeng.me.cluster.implementations
 * .QuantumCluster#updateStatus()} forms the real, in-game Quantum Bridge's far-side link with nothing more than
 * {@code GridHelper.createConnection(sideA.getNode(), sideB.getNode())} — i.e. the ordinary
 * {@link GridConnection#create} primitive already exercised by {@code NetworkSlotIsolationTest}'s same-slot
 * leaf-connection test. There is no separate "remote connection" class in the current Grid API; "remote" is a
 * real-world placement fact about a Quantum Bridge pair, not a distinct physical connection type. A "Quantum Lane" at
 * the primitive level is therefore exactly {@code GridHelper.createConnection(localNode, remoteNode)} between two nodes
 * that happen to be physically distant — which is what this prototype builds, directly, with no new production code.
 * <p/>
 * Fixture: one plain host object owns the two LOCAL nodes ({@code localA}, {@code localB} — mirroring
 * {@code NetworkSlotIsolationTest}'s {@code TwoSlotHost}). Each REMOTE node ({@code remoteA}, {@code remoteB}) is owned
 * by its own separate plain object, never the shared host — modeling a physically distant Quantum Bridge partner, the
 * same way a real far-side {@code QuantumCluster} is an independent {@link IActionHost} unrelated to the near side's
 * owning block entity. Lane A ({@code localA<->remoteA}) and Lane B ({@code localB<->remoteB}) are formed by two
 * independent {@code GridHelper.createConnection} calls; <b>no connection is ever created between the A-side and B-side
 * nodes</b> — that is the entire property under test.
 * <p/>
 * Lives in {@code appeng.me.security} (like its Phase D sibling {@code NetworkSlotIsolationTest}) purely for the same
 * package-private test hooks ({@code SecurityService#setRegistryForTesting}, {@code SecurityDomainRegistry#TYPE}) — no
 * new test-only hooks were added to production code.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RemoteQuantumLaneIsolationTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    @TempDir
    Path tempDir;

    /**
     * {@link EphemeralTestServerProvider#grabServer()} synchronously spins up the full ephemeral Minecraft server (and,
     * with it, the vanilla registry/{@code DataComponentMap} binding that {@code Items.DIAMOND} etc. need) but only
     * does so lazily, the first time some test method in the class requests a {@code MinecraftServer} parameter. Since
     * JUnit Jupiter's default method order is not declaration order, an item-using test can easily run before that lazy
     * trigger fires, throwing "Components not bound yet". Forcing it here, once, up front, makes every test in this
     * class deterministic regardless of JUnit's chosen method order - this changes nothing about what is being proven,
     * it only removes an ordering hazard in the shared test harness itself (already latent in
     * {@code NetworkSlotIsolationTest}, which happened not to trigger it only because JUnit's order for that particular
     * method set put its one {@code MinecraftServer}-parameter test early).
     */
    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    // =====================================================================
    // 1-4: BASIC IDENTITY
    // =====================================================================

    @Test
    void localAAndRemoteAShareOneGridAfterLaneAConnects() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        assertThat(lanes.laneA).isNotNull();
        assertThat(lanes.localA.getGrid()).isNotNull();
        assertThat(lanes.localA.getGrid()).isSameAs(lanes.remoteA.getGrid());

        var nodesInA = new java.util.ArrayList<IGridNode>();
        lanes.localA.getGrid().getNodes().forEach(nodesInA::add);
        assertThat(nodesInA).containsExactlyInAnyOrder(lanes.localA.getNode(), lanes.remoteA.getNode());
    }

    @Test
    void localBAndRemoteBShareOneGridAfterLaneBConnects() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        assertThat(lanes.laneB).isNotNull();
        assertThat(lanes.localB.getGrid()).isNotNull();
        assertThat(lanes.localB.getGrid()).isSameAs(lanes.remoteB.getGrid());

        var nodesInB = new java.util.ArrayList<IGridNode>();
        lanes.localB.getGrid().getNodes().forEach(nodesInB::add);
        assertThat(nodesInB).containsExactlyInAnyOrder(lanes.localB.getNode(), lanes.remoteB.getNode());
    }

    @Test
    void gridAAndGridBAreNeverTheSameGridInstance() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        assertThat(lanes.localA.getGrid()).isNotSameAs(lanes.localB.getGrid());
        assertThat(lanes.remoteA.getGrid()).isNotSameAs(lanes.remoteB.getGrid());
        // No connection at all between anything on the A side and anything on the B side.
        assertThat(lanes.localA.getNode().getConnections()).containsExactly(lanes.laneA);
        assertThat(lanes.localB.getNode().getConnections()).containsExactly(lanes.laneB);
    }

    @Test
    void bothLocalNodesShareOneHostObjectButThatDoesNotMergeGridAAndGridB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        // Both LOCAL nodes are owned by the exact same host object...
        assertThat(lanes.localA.getNode().getOwner()).isSameAs(lanes.host);
        assertThat(lanes.localB.getNode().getOwner()).isSameAs(lanes.host);
        assertThat(lanes.localA.getNode().getOwner()).isSameAs(lanes.localB.getNode().getOwner());

        // ...yet their grids remain fully distinct: owning a node and connecting it to another node are two
        // separate concerns, exactly as already established by NetworkSlotIsolationTest for co-location.
        assertThat(lanes.localA.getGrid()).isNotSameAs(lanes.localB.getGrid());
        var nodesInA = new java.util.ArrayList<IGridNode>();
        lanes.localA.getGrid().getNodes().forEach(nodesInA::add);
        assertThat(nodesInA).doesNotContain(lanes.localB.getNode(), lanes.remoteB.getNode());
    }

    // =====================================================================
    // 5-7: STORAGE ISOLATION (real MEStorage insert/extract, not mocked)
    // =====================================================================

    @Test
    void itemInsertedThroughLocalAIsVisibleFromRemoteA() {
        var lanes = new QuantumLaneHost();
        var storageA = new SimpleFakeStorage();
        // The drive physically lives at the remote endpoint - proving visibility travels the lane, not just the
        // trivial "same object" case.
        lanes.remoteA.addService(IStorageProvider.class, new SimpleStorageProvider(storageA));
        lanes.connectBoth();

        var diamond = AEItemKey.of(Items.DIAMOND);
        var inserted = lanes.localA.getGrid().getStorageService().getInventory()
                .insert(diamond, 64, Actionable.MODULATE, IActionSource.empty());
        assertThat(inserted).isEqualTo(64L);

        var seenFromRemoteA = new KeyCounter();
        lanes.remoteA.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromRemoteA);
        assertThat(seenFromRemoteA.get(diamond)).isEqualTo(64L);
    }

    @Test
    void itemInsertedThroughLocalAIsNotVisibleFromLocalBOrRemoteB() {
        var lanes = new QuantumLaneHost();
        var storageA = new SimpleFakeStorage();
        lanes.remoteA.addService(IStorageProvider.class, new SimpleStorageProvider(storageA));
        lanes.connectBoth();

        var diamond = AEItemKey.of(Items.DIAMOND);
        lanes.localA.getGrid().getStorageService().getInventory()
                .insert(diamond, 64, Actionable.MODULATE, IActionSource.empty());

        var seenFromLocalB = new KeyCounter();
        lanes.localB.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromLocalB);
        assertThat(seenFromLocalB.get(diamond)).isZero();

        var seenFromRemoteB = new KeyCounter();
        lanes.remoteB.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromRemoteB);
        assertThat(seenFromRemoteB.get(diamond)).isZero();

        // B cannot drain A's storage either.
        var stolen = lanes.remoteB.getGrid().getStorageService().getInventory()
                .extract(diamond, 64, Actionable.MODULATE, IActionSource.empty());
        assertThat(stolen).isZero();
    }

    @Test
    void itemInsertedThroughRemoteBIsVisibleFromLocalBButNotFromEitherASideNode() {
        var lanes = new QuantumLaneHost();
        var storageB = new SimpleFakeStorage();
        // The drive physically lives at the local endpoint of slot B this time, inserted from the remote side.
        lanes.localB.addService(IStorageProvider.class, new SimpleStorageProvider(storageB));
        lanes.connectBoth();

        var goldIngot = AEItemKey.of(Items.GOLD_INGOT);
        var inserted = lanes.remoteB.getGrid().getStorageService().getInventory()
                .insert(goldIngot, 32, Actionable.MODULATE, IActionSource.empty());
        assertThat(inserted).isEqualTo(32L);

        var seenFromLocalB = new KeyCounter();
        lanes.localB.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromLocalB);
        assertThat(seenFromLocalB.get(goldIngot)).isEqualTo(32L);

        var seenFromLocalA = new KeyCounter();
        lanes.localA.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromLocalA);
        assertThat(seenFromLocalA.get(goldIngot)).isZero();

        var seenFromRemoteA = new KeyCounter();
        lanes.remoteA.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromRemoteA);
        assertThat(seenFromRemoteA.get(goldIngot)).isZero();
    }

    // =====================================================================
    // 8-10: CRAFTING/SERVICE ISOLATION (real ICraftingProvider/ICraftingService)
    // =====================================================================

    @Test
    void craftingPatternOnRemoteAIsDiscoverableThroughLocalAsSameGridCraftingService() {
        var lanes = new QuantumLaneHost();
        var andesite = AEItemKey.of(Items.ANDESITE);
        var patternA = new ProcessingPatternBuilder(new GenericStack(andesite, 1)).build();
        lanes.remoteA.addService(ICraftingProvider.class, new SinglePatternProvider(patternA));
        lanes.connectBoth();

        assertThat(lanes.localA.getGrid().getCraftingService().isCraftable(andesite)).isTrue();
        assertThat(lanes.remoteA.getGrid().getCraftingService().getCraftables(k -> true)).containsExactly(andesite);
    }

    @Test
    void craftingPatternOnRemoteAIsNotDiscoverableThroughLaneBsCraftingService() {
        var lanes = new QuantumLaneHost();
        var andesite = AEItemKey.of(Items.ANDESITE);
        var patternA = new ProcessingPatternBuilder(new GenericStack(andesite, 1)).build();
        lanes.remoteA.addService(ICraftingProvider.class, new SinglePatternProvider(patternA));
        lanes.connectBoth();

        assertThat(lanes.localB.getGrid().getCraftingService().isCraftable(andesite)).isFalse();
        assertThat(lanes.remoteB.getGrid().getCraftingService().isCraftable(andesite)).isFalse();
    }

    @Test
    void craftingPatternOnLocalBNeverAppearsInLaneAsCraftingService() {
        var lanes = new QuantumLaneHost();
        var basalt = AEItemKey.of(Items.BASALT);
        var patternB = new ProcessingPatternBuilder(new GenericStack(basalt, 1)).build();
        lanes.localB.addService(ICraftingProvider.class, new SinglePatternProvider(patternB));
        lanes.connectBoth();

        assertThat(lanes.localB.getGrid().getCraftingService().isCraftable(basalt)).isTrue();
        assertThat(lanes.localA.getGrid().getCraftingService().isCraftable(basalt)).isFalse();
        assertThat(lanes.remoteA.getGrid().getCraftingService().getCraftables(k -> true)).isEmpty();
    }

    // =====================================================================
    // 11-17: SECURITY
    // =====================================================================

    @Test
    void slotACanBeSecuredUnderDomainAWithLaneAConnected() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();

        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());

        assertThat(domainIdA).isNotNull();
        assertThat(securityOf(lanes.remoteA, registryA).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(lanes.remoteA, registryA).getDomainId()).contains(domainIdA);
    }

    @Test
    void slotBCanBeSecuredUnderDomainBWithLaneBConnected() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryB = SecurityDomainRegistry.createForTesting();

        var domainIdB = securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        assertThat(domainIdB).isNotNull();
        assertThat(securityOf(lanes.remoteB, registryB).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(lanes.remoteB, registryB).getDomainId()).contains(domainIdB);
    }

    @Test
    void bothLanesBeingIndependentlySecuredIsValidBecauseNoConnectionExistsBetweenGridAAndGridB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();

        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        var domainIdB = securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        assertThat(domainIdA).isNotEqualTo(domainIdB);
        assertThat(securityOf(lanes.localA, registryA).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(lanes.localB, registryB).getState()).isEqualTo(SecurityDomainState.SECURED);
        // Neither ever became CONFLICT - proof there was never a merge attempt between them.
        assertThat(lanes.localA.getNode().getConnections()).hasSize(1);
        assertThat(lanes.localB.getNode().getConnections()).hasSize(1);
    }

    @Test
    void playerGrantOnDomainAGrantsAccessCheckedThroughRemoteANodeButNotThroughLaneB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        var grantedPlayer = UUID.randomUUID();
        registryA.getDomain(domainIdA).grant(PrincipalId.ofPlayer(grantedPlayer), Set.of(SecurityPermissions.EXTRACT));

        assertThat(securityOf(lanes.remoteA, registryA).hasPermission(playerSource(grantedPlayer),
                SecurityPermissions.EXTRACT))
                .isTrue();
        assertThat(securityOf(lanes.remoteB, registryB).hasPermission(playerSource(grantedPlayer),
                SecurityPermissions.EXTRACT))
                .isFalse();
        assertThat(securityOf(lanes.localB, registryB).hasPermission(playerSource(grantedPlayer),
                SecurityPermissions.EXTRACT))
                .isFalse();
    }

    @Test
    void groupGrantOnDomainADoesNotGrantAccessOnDomainB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        var groupMember = UUID.randomUUID();
        var group = SecurityGroup.create("laneA-miners").withMember(groupMember);
        var domainA = registryA.getDomain(domainIdA);
        domainA.putGroup(group);
        domainA.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.CRAFT));

        assertThat(securityOf(lanes.remoteA, registryA).hasPermission(playerSource(groupMember),
                SecurityPermissions.CRAFT))
                .isTrue();
        assertThat(securityOf(lanes.remoteB, registryB).hasPermission(playerSource(groupMember),
                SecurityPermissions.CRAFT))
                .isFalse();
    }

    @Test
    void deviceGrantOnDomainADoesNotGrantAccessOnDomainB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        var deviceId = UUID.randomUUID();
        registryA.getDomain(domainIdA).grant(PrincipalId.ofDevice(deviceId),
                Set.of(SecurityPermissions.INJECT));
        // Round 32: a device is something AE2 can locate and the registry authenticated - not an object that
        // says what it is. The helper builds exactly that shape.
        var deviceSource = RegisteredTestDevice.source(deviceRegistry, level, deviceId);

        assertThat(securityOf(lanes.remoteA, registryA).hasPermission(deviceSource, SecurityPermissions.INJECT))
                .isTrue();
        assertThat(securityOf(lanes.remoteB, registryB).hasPermission(deviceSource, SecurityPermissions.INJECT))
                .isFalse();
    }

    @Test
    void everyoneGrantOnDomainADoesNotChangeDomainBsEffectivePermissions() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        var domainIdB = securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        registryA.getDomain(domainIdA).grant(PrincipalId.EVERYONE, Set.of(SecurityPermissions.EXTRACT));

        var stranger = UUID.randomUUID();
        assertThat(
                securityOf(lanes.remoteA, registryA).hasPermission(playerSource(stranger), SecurityPermissions.EXTRACT))
                .isTrue();
        assertThat(registryB.getDomain(domainIdB).getEffectivePermissions(PrincipalId.ofPlayer(stranger))).isEmpty();
        assertThat(
                securityOf(lanes.remoteB, registryB).hasPermission(playerSource(stranger), SecurityPermissions.EXTRACT))
                .isFalse();
    }

    // =====================================================================
    // 18-20: CHANNELS - 32 per logical lane, independent accounting
    // =====================================================================

    @Test
    void laneAChannelLoadDoesNotReduceLaneBsAvailableCapacityOrUsage() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        addChannelConsumingLeaf(lanes.localA);
        lanes.localA.getGrid().getPathingService().repath();
        runTick(lanes.localA.getGrid(), 2);
        runTick(lanes.localB.getGrid(), 2);

        assertThat(lanes.localA.getGrid().getPathingService().getUsedChannels()).isEqualTo(1);
        assertThat(lanes.localB.getGrid().getPathingService().getUsedChannels()).isZero();
        assertThat(lanes.laneB.getMaxChannels()).isEqualTo(32);
    }

    @Test
    void laneBChannelLoadDoesNotReduceLaneAsAvailableCapacityOrUsage() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        addChannelConsumingLeaf(lanes.localB);
        addChannelConsumingLeaf(lanes.remoteB);
        lanes.localB.getGrid().getPathingService().repath();
        runTick(lanes.localB.getGrid(), 2);
        runTick(lanes.localA.getGrid(), 2);

        assertThat(lanes.localB.getGrid().getPathingService().getUsedChannels()).isEqualTo(2);
        assertThat(lanes.localA.getGrid().getPathingService().getUsedChannels()).isZero();
        assertThat(lanes.laneA.getMaxChannels()).isEqualTo(32);
    }

    @Test
    void eachLaneIndependentlyResolves32ChannelsPerLogicalLane() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();

        assertThat(lanes.laneA.getMaxChannels()).isEqualTo(32);
        assertThat(lanes.laneB.getMaxChannels()).isEqualTo(32);
        // Not 64/128/256 - each lane resolves this purely from its own grid's channel mode, per
        // GridConnection#getMaxChannels.
    }

    // =====================================================================
    // 21-24: LANE TEARDOWN / RECREATE
    // =====================================================================

    @Test
    void disconnectingLaneASeparatesLocalAAndRemoteAWithoutChangingGridB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var gridBBefore = lanes.localB.getGrid();

        lanes.laneA.destroy();

        assertThat(lanes.localA.getGrid()).isNotSameAs(lanes.remoteA.getGrid());
        assertThat(lanes.localA.getNode().getConnections()).isEmpty();
        assertThat(lanes.remoteA.getNode().getConnections()).isEmpty();
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
        assertThat(lanes.localB.getNode().getConnections()).containsExactly(lanes.laneB);
    }

    @Test
    void reconnectingLaneARestoresGridAWithoutReconstructingOrRemergingGridB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var gridBBefore = lanes.localB.getGrid();
        lanes.laneA.destroy();

        var reconnected = GridHelper.createConnection(lanes.localA.getNode(), lanes.remoteA.getNode());

        assertThat(reconnected).isNotNull();
        assertThat(lanes.localA.getGrid()).isSameAs(lanes.remoteA.getGrid());
        assertThat(lanes.localA.getGrid()).isNotSameAs(gridBBefore);
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
    }

    @Test
    void removingAndReAddingTheRemoteAEndpointDoesNotAffectLaneB() {
        var lanes = new QuantumLaneHost();
        var storageB = new SimpleFakeStorage();
        lanes.localB.addService(IStorageProvider.class, new SimpleStorageProvider(storageB));
        lanes.connectBoth();
        var emerald = AEItemKey.of(Items.EMERALD);
        lanes.localB.getGrid().getStorageService().getInventory().insert(emerald, 10, Actionable.MODULATE,
                IActionSource.empty());
        var gridBBefore = lanes.localB.getGrid();

        lanes.remoteA.destroy();
        assertThat(lanes.localA.getNode().getConnections()).isEmpty();
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);

        var remoteA2 = new ManagedGridNode(new Object(), listener);
        remoteA2.create(level, null);
        var reconnected = GridHelper.createConnection(lanes.localA.getNode(), remoteA2.getNode());
        assertThat(reconnected).isNotNull();

        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
        var stacks = new KeyCounter();
        lanes.localB.getGrid().getStorageService().getInventory().getAvailableStacks(stacks);
        assertThat(stacks.get(emerald)).isEqualTo(10L);
    }

    @Test
    void removingAndReAddingTheLocalSlotANodeLeavesSlotBFullyOperational() {
        var lanes = new QuantumLaneHost();
        var storageB = new SimpleFakeStorage();
        lanes.localB.addService(IStorageProvider.class, new SimpleStorageProvider(storageB));
        lanes.connectBoth();
        var emerald = AEItemKey.of(Items.EMERALD);
        lanes.localB.getGrid().getStorageService().getInventory().insert(emerald, 10, Actionable.MODULATE,
                IActionSource.empty());
        var gridBBefore = lanes.localB.getGrid();

        lanes.localA.destroy();
        assertThat(lanes.remoteA.getNode().getConnections()).isEmpty();
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);

        var localA2 = new ManagedGridNode(lanes.host, listener);
        localA2.create(level, null);
        var reconnected = GridHelper.createConnection(localA2.getNode(), lanes.remoteA.getNode());
        assertThat(reconnected).isNotNull();

        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
        var stacks = new KeyCounter();
        lanes.localB.getGrid().getStorageService().getInventory().getAvailableStacks(stacks);
        assertThat(stacks.get(emerald)).isEqualTo(10L);
    }

    // =====================================================================
    // 25-28: UNLOAD / RELOAD
    // =====================================================================

    @Test
    void simulatedUnloadOfRemoteATakesOnlyLaneAOfflineWhileGridBStaysOnline() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var gridBBefore = lanes.localB.getGrid();
        var laneBConnectionCountBefore = lanes.localB.getNode().getConnections().size();

        // Simulated unload: the far-side Quantum Bridge's chunk unloads -> its node is destroyed, exactly like
        // GridNode#destroy already severs a connection today (no new mechanism invented here).
        lanes.remoteA.destroy();

        // Lane A is offline per current Grid primitives: no remote reachable, localA reverted to a standalone grid.
        assertThat(lanes.localA.getNode().getConnections()).isEmpty();
        assertThat(lanes.localA.getGrid()).isNotNull();

        // Grid B: completely untouched.
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
        assertThat(lanes.localB.getNode().getConnections()).hasSize(laneBConnectionCountBefore);
    }

    @Test
    void reloadingRemoteARestoresOnlyLaneANotLaneB() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var gridBBefore = lanes.localB.getGrid();
        lanes.remoteA.destroy();

        var remoteA2 = new ManagedGridNode(new Object(), listener);
        remoteA2.create(level, null);
        var reconnected = GridHelper.createConnection(lanes.localA.getNode(), remoteA2.getNode());

        assertThat(reconnected).isNotNull();
        assertThat(lanes.localA.getGrid()).isSameAs(remoteA2.getGrid());
        // B was never touched by A's reload.
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
    }

    @Test
    void simulatedUnloadOfTheSharedHostTakesBothLocalSidesOfflineCleanlyWithoutCrossWiringOnReload() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        var domainIdB = securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        // Simulated unload: the chunk holding the shared host unloads -> both LOCAL nodes are destroyed together.
        lanes.localA.destroy();
        lanes.localB.destroy();
        assertThat(lanes.remoteA.getNode().getConnections()).isEmpty();
        assertThat(lanes.remoteB.getNode().getConnections()).isEmpty();

        // Simulated reload: new LOCAL nodes on the same host, each reconnected to its OWN correct remote endpoint.
        var localA2 = new ManagedGridNode(lanes.host, listener);
        localA2.create(level, null);
        var localB2 = new ManagedGridNode(lanes.host, listener);
        localB2.create(level, null);
        var reconnectedA = GridHelper.createConnection(localA2.getNode(), lanes.remoteA.getNode());
        var reconnectedB = GridHelper.createConnection(localB2.getNode(), lanes.remoteB.getNode());

        assertThat(reconnectedA).isNotNull();
        assertThat(reconnectedB).isNotNull();
        assertThat(localA2.getGrid()).isNotSameAs(localB2.getGrid());
        // Domain identity followed the correct remote node, no cross-wire: A's remote node is still bound to
        // domain A, B's remote node is still bound to domain B (nodeDomains survives node.destroy() only being
        // called on the LOCAL side; the REMOTE nodes here were never destroyed).
        assertThat(securityOf(lanes.remoteA, registryA).getDomainId()).contains(domainIdA);
        assertThat(securityOf(lanes.remoteB, registryB).getDomainId()).contains(domainIdB);
    }

    @Test
    void restartSimulationOfBothLanesFromSavedNbtReturnsAToAAndBToBWithNoLaneSwap() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        var domainIdB = securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());

        var savedLocalA = toTag(lanes.localA::serialize);
        var savedRemoteA = toTag(lanes.remoteA::serialize);
        var savedLocalB = toTag(lanes.localB::serialize);
        var savedRemoteB = toTag(lanes.remoteB::serialize);

        lanes.laneA.destroy();
        lanes.laneB.destroy();
        lanes.localA.destroy();
        lanes.remoteA.destroy();
        lanes.localB.destroy();
        lanes.remoteB.destroy();

        // "Restart": brand-new nodes loaded from saved NBT, reconnected pairwise A<->A, B<->B (the correct routing).
        var reloadedLocalA = new ManagedGridNode(lanes.host, listener);
        fromTag(savedLocalA, reloadedLocalA::deserialize);
        reloadedLocalA.create(level, null);
        var reloadedRemoteA = new ManagedGridNode(new Object(), listener);
        fromTag(savedRemoteA, reloadedRemoteA::deserialize);
        reloadedRemoteA.create(level, null);
        var reloadedLocalB = new ManagedGridNode(lanes.host, listener);
        fromTag(savedLocalB, reloadedLocalB::deserialize);
        reloadedLocalB.create(level, null);
        var reloadedRemoteB = new ManagedGridNode(new Object(), listener);
        fromTag(savedRemoteB, reloadedRemoteB::deserialize);
        reloadedRemoteB.create(level, null);

        var reconnectedA = GridHelper.createConnection(reloadedLocalA.getNode(), reloadedRemoteA.getNode());
        var reconnectedB = GridHelper.createConnection(reloadedLocalB.getNode(), reloadedRemoteB.getNode());
        assertThat(reconnectedA).isNotNull();
        assertThat(reconnectedB).isNotNull();

        assertThat(securityOf(reloadedLocalA, registryA).getDomainId()).contains(domainIdA);
        assertThat(securityOf(reloadedLocalB, registryB).getDomainId()).contains(domainIdB);
        assertThat(domainIdA).isNotEqualTo(domainIdB);
        assertThat(reloadedLocalA.getGrid()).isNotSameAs(reloadedLocalB.getGrid());
    }

    // =====================================================================
    // 29: NEGATIVE / WRONG-CONNECTION TEST
    // =====================================================================

    @Test
    void connectingLocalAToRemoteBAcrossDifferentSecurityDomainsIsRejectedByThePreMergeGuard() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(lanes.localB, registryB).bootstrapDomain(UUID.randomUUID());
        var gridABefore = lanes.localA.getGrid();
        var gridBBefore = lanes.localB.getGrid();

        // Simulates a mis-wired Quantum Lane: LocalA's endpoint accidentally connects to RemoteB instead of RemoteA.
        // The already-tested, already-shipped Security Foundation pre-merge guard
        // (GridConnection#securityDomainsAreCompatible) must reject this outright, exactly like it already rejects
        // two differently-secured co-located networks in SecurityServiceTest.
        var wrongConnection = GridHelper.createConnection(lanes.localA.getNode(), lanes.remoteB.getNode());

        assertThat(wrongConnection).isNull();
        // Nothing changed: both grids are the exact same instances as before the attempt, no transient leak.
        assertThat(lanes.localA.getGrid()).isSameAs(gridABefore);
        assertThat(lanes.localB.getGrid()).isSameAs(gridBBefore);
        assertThat(lanes.localA.getNode().getConnections()).containsExactly(lanes.laneA);
        assertThat(lanes.remoteB.getNode().getConnections()).containsExactly(lanes.laneB);
        assertThat(securityOf(lanes.localA, registryA).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(lanes.localB, registryB).getState()).isEqualTo(SecurityDomainState.SECURED);
    }

    // =====================================================================
    // 30-32: LEGACY COMBINATION
    // =====================================================================

    @Test
    void securedSlotAAndLegacySlotBCoexistOnOneHostWithBothLanesConnected() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();

        securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        // Slot B is never bootstrapped - genuinely LEGACY (its own SecurityService, empty domainCounts).
        var registryB = SecurityDomainRegistry.createForTesting();

        assertThat(securityOf(lanes.localA, registryA).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(lanes.localB, registryB).getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(lanes.localA.getGrid()).isNotSameAs(lanes.localB.getGrid());
    }

    @Test
    void connectingLaneADoesNotMakeTheStillUnconnectedLegacySlotBSecured() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();

        securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());

        // Forming lane A (and even securing it) has zero effect on slot B's state.
        assertThat(securityOf(lanes.localB, registryB).getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(securityOf(lanes.remoteB, registryB).getState()).isEqualTo(SecurityDomainState.LEGACY);
    }

    @Test
    void connectingLaneBOnLegacySlotBNeverGrantsAccessToDomainA() {
        var lanes = new QuantumLaneHost();
        lanes.connectBoth();
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        securityOf(lanes.localA, registryA).bootstrapDomain(UUID.randomUUID());
        // Slot B stays LEGACY - fully open by design (SecurityService.hasPermission returns true for everyone
        // while LEGACY).

        var stranger = UUID.randomUUID();
        // The stranger gets full (LEGACY-open) access on slot B purely because B was never secured...
        for (var permission : SecurityPermissions.values()) {
            assertThat(securityOf(lanes.remoteB, registryB).hasPermission(playerSource(stranger), permission)).isTrue();
        }
        // ...but that openness never leaks across to domain A, which the stranger was never granted anything on.
        for (var permission : SecurityPermissions.values()) {
            assertThat(securityOf(lanes.remoteA, registryA).hasPermission(playerSource(stranger), permission))
                    .isFalse();
        }
    }

    // =====================================================================
    // Fixtures / helpers
    // =====================================================================

    /**
     * Models "one future Quantum host with two independent REMOTE Quantum Lanes": a shared host object owns the two
     * LOCAL nodes; each REMOTE node is owned by its own separate plain object (never the host), modeling a physically
     * distant Quantum Bridge partner. {@code connectBoth()} forms Lane A ({@code localA<->remoteA}) and Lane B
     * ({@code localB<->remoteB}) via the exact same {@link GridHelper#createConnection} call the real
     * {@code QuantumCluster} uses in production - nothing ever connects the A side to the B side.
     */
    private final class QuantumLaneHost {
        final Object host = new Object();
        final ManagedGridNode localA = new ManagedGridNode(host, listener);
        final ManagedGridNode localB = new ManagedGridNode(host, listener);
        final ManagedGridNode remoteA = new ManagedGridNode(new Object(), listener);
        final ManagedGridNode remoteB = new ManagedGridNode(new Object(), listener);
        GridConnection laneA;
        GridConnection laneB;
        private boolean connected;

        void connectBoth() {
            if (!connected) {
                connected = true;
                if (localA.getNode() == null) {
                    localA.create(level, null);
                }
                if (localB.getNode() == null) {
                    localB.create(level, null);
                }
                if (remoteA.getNode() == null) {
                    remoteA.create(level, null);
                }
                if (remoteB.getNode() == null) {
                    remoteB.create(level, null);
                }
                laneA = (GridConnection) GridHelper.createConnection(localA.getNode(), remoteA.getNode());
                laneB = (GridConnection) GridHelper.createConnection(localB.getNode(), remoteB.getNode());
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

    private CompoundTag toTag(java.util.function.Consumer<ValueOutput> serializer) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        serializer.accept(output);
        return output.buildResult();
    }

    private void fromTag(CompoundTag tag, java.util.function.Consumer<ValueInput> deserializer) {
        var input = TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag);
        deserializer.accept(input);
    }

    /** Minimal in-memory {@link MEStorage} fixture, real enough to prove actual insert/extract isolation. */
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
            return Component.literal("RemoteQuantumLaneIsolationTest fake storage");
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
