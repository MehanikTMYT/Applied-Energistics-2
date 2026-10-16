package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.pathing.ControllerState;
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
 * Phase D — Network Slot Isolation prototype.
 * <p/>
 * Answers a single narrow architectural question ahead of any future "Quantum host": can a single future Quantum host
 * hold two fully independent ME Network Slots (= independent logical networks / Network Profiles / Security Domains)
 * without them ever accidentally becoming one {@link appeng.api.networking.IGrid}?
 * <p/>
 * <b>Design (verified against real, already-existing API, not invented):</b> one plain test-only object plays the role
 * of "the host" (mirroring the existing convention in {@code AbstractGridNodeTest#owner}, which is already shared
 * across multiple independent nodes in {@code EnergyServiceTest}/{@code SecurityServiceTest} without those nodes ever
 * being implicitly connected — owning a node and connecting it to another node are two completely separate concerns in
 * this API). The host creates two {@link ManagedGridNode} instances via the exact same
 * {@code GridHelper.createManagedNode}-backing constructor used everywhere else in this codebase's tests, one per slot,
 * and calls {@code create(level, null)} on each. Confirmed by reading {@code GridNode.markReady()} (calls
 * {@code Grid.create(this)} directly) and confirmed behaviorally by every pre-existing test that calls
 * {@code mgn.getGrid()} immediately after {@code mgn.create(...)} with zero connections formed (e.g.
 * {@code EnergyServiceTest.createAndInitNode()}): a lone node <em>already</em> forms its own single-node
 * {@link appeng.api.networking.IGrid} with no call to {@link GridConnection#create} whatsoever. Two slots on the same
 * host are therefore, by construction, two independent {@code Grid} objects the moment both nodes exist — <b>no new
 * production code was needed to achieve this</b>; the isolation is an existing, load-bearing property of the current
 * Grid API, only exercised here for the first time in this specific two-slot-on-one-host shape.
 * <p/>
 * Every method below is a JUnit test (not a "just trust me" comment) and lives in {@code appeng.me.security} (like its
 * siblings {@code SecurityServiceTest}/{@code SecurityDomainRegistryPersistenceTest}) specifically because it needs the
 * same package-private test-only hooks they already use ({@code SecurityService#setRegistryForTesting},
 * {@code SecurityDomainRegistry#TYPE}) — no new test-only hooks were added to production code for this prototype.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class NetworkSlotIsolationTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    @TempDir
    Path tempDir;

    // ---------------------------------------------------------------------
    // 1. Different IGrid instances
    // ---------------------------------------------------------------------

    @Test
    void slotAAndSlotBOnTheSameHostAreTwoDifferentGrids() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        assertThat(slots.mgnA.getGrid()).isNotNull();
        assertThat(slots.mgnB.getGrid()).isNotNull();
        assertThat(slots.mgnA.getGrid()).isNotSameAs(slots.mgnB.getGrid());

        // Structural, not just behavioral: neither grid's node list ever enumerates the other slot's node - there is
        // no shared IGrid instance through which any service on one slot could ever iterate the other's nodes.
        var nodesInA = new ArrayList<IGridNode>();
        slots.mgnA.getGrid().getNodes().forEach(nodesInA::add);
        assertThat(nodesInA).containsExactly(slots.mgnA.getNode());

        var nodesInB = new ArrayList<IGridNode>();
        slots.mgnB.getGrid().getNodes().forEach(nodesInB::add);
        assertThat(nodesInB).containsExactly(slots.mgnB.getNode());

        // No GridConnection was ever formed between them, and none exists now.
        assertThat(slots.mgnA.getNode().getConnections()).isEmpty();
        assertThat(slots.mgnB.getNode().getConnections()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // 2. Storage A not visible from B
    // ---------------------------------------------------------------------

    @Test
    void storageInsertedIntoSlotAIsNotVisibleFromSlotB() {
        var slots = new TwoSlotHost();
        var storageA = new SimpleFakeStorage();
        var storageB = new SimpleFakeStorage();
        slots.mgnA.addService(IStorageProvider.class, new SimpleStorageProvider(storageA));
        slots.mgnB.addService(IStorageProvider.class, new SimpleStorageProvider(storageB));
        slots.createBoth();

        var diamond = AEItemKey.of(Items.DIAMOND);
        var goldIngot = AEItemKey.of(Items.GOLD_INGOT);

        // Trusted-internal source (mirrors SecurityServiceTest#trustedInternalActionsBypassTheAclEntirely) so this
        // test exercises storage isolation specifically, independent of ACL enforcement (covered separately below).
        var inserted = slots.mgnA.getGrid().getStorageService().getInventory()
                .insert(diamond, 64, Actionable.MODULATE, IActionSource.empty());
        assertThat(inserted).isEqualTo(64L);
        slots.mgnB.getGrid().getStorageService().getInventory()
                .insert(goldIngot, 32, Actionable.MODULATE, IActionSource.empty());

        var seenFromA = new KeyCounter();
        slots.mgnA.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromA);
        assertThat(seenFromA.get(diamond)).isEqualTo(64L);
        assertThat(seenFromA.get(goldIngot)).isZero();

        var seenFromB = new KeyCounter();
        slots.mgnB.getGrid().getStorageService().getInventory().getAvailableStacks(seenFromB);
        assertThat(seenFromB.get(goldIngot)).isEqualTo(32L);
        assertThat(seenFromB.get(diamond)).isZero();

        // Extraction is likewise scoped: B can never drain A's storage.
        var stolen = slots.mgnB.getGrid().getStorageService().getInventory()
                .extract(diamond, 64, Actionable.MODULATE, IActionSource.empty());
        assertThat(stolen).isZero();
    }

    // ---------------------------------------------------------------------
    // 3. Crafting A not visible from B
    // ---------------------------------------------------------------------

    @Test
    void craftingPatternsOnSlotAAreNotVisibleFromSlotB() {
        var slots = new TwoSlotHost();
        var andesite = AEItemKey.of(Items.ANDESITE);
        var basalt = AEItemKey.of(Items.BASALT);
        var patternA = new ProcessingPatternBuilder(new GenericStack(andesite, 1)).build();
        var patternB = new ProcessingPatternBuilder(new GenericStack(basalt, 1)).build();
        slots.mgnA.addService(ICraftingProvider.class, new SinglePatternProvider(patternA));
        slots.mgnB.addService(ICraftingProvider.class, new SinglePatternProvider(patternB));
        slots.createBoth();

        assertThat(slots.mgnA.getGrid().getCraftingService().isCraftable(andesite)).isTrue();
        assertThat(slots.mgnA.getGrid().getCraftingService().isCraftable(basalt)).isFalse();

        assertThat(slots.mgnB.getGrid().getCraftingService().isCraftable(basalt)).isTrue();
        assertThat(slots.mgnB.getGrid().getCraftingService().isCraftable(andesite)).isFalse();

        assertThat(slots.mgnA.getGrid().getCraftingService().getCraftables(k -> true)).containsExactly(andesite);
        assertThat(slots.mgnB.getGrid().getCraftingService().getCraftables(k -> true)).containsExactly(basalt);
    }

    // ---------------------------------------------------------------------
    // 4. Controller A does not influence B
    // ---------------------------------------------------------------------
    // Both slots in this minimal prototype are single-node grids with no physical multiblock ME Controller placed
    // (building a full ControllerBlockEntity fixture is out of proportion for a test-only architecture prototype -
    // see the task's own stop condition about not building unneeded production fixtures). What IS proven here, at
    // the real appeng.api.networking.pathing.IPathingService boundary that a real controller relies on for its
    // state, is that (a) each slot's controller-related service is a genuinely separate instance and (b) forcing a
    // controller-relevant recalculation (repath()) on slot A never touches slot B's reported controller/channel
    // state. This is the exact mechanism a real controller's presence/absence would be observed through.

    @Test
    void controllerRelatedPathingStateOnSlotADoesNotAffectSlotB() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var pathingA = slots.mgnA.getGrid().getPathingService();
        var pathingB = slots.mgnB.getGrid().getPathingService();
        assertThat(pathingA).isNotSameAs(pathingB);

        // Neither slot has a physical controller block in this prototype, so both correctly report NO_CONTROLLER.
        assertThat(pathingA.getControllerState()).isEqualTo(ControllerState.NO_CONTROLLER);
        assertThat(pathingB.getControllerState()).isEqualTo(ControllerState.NO_CONTROLLER);

        var usedChannelsBBefore = pathingB.getUsedChannels();
        var controllerStateBBefore = pathingB.getControllerState();

        // Force a controller/topology recalculation on slot A only.
        pathingA.repath();
        runTick(slots.mgnA.getGrid(), 2);

        assertThat(pathingB.getControllerState()).isEqualTo(controllerStateBBefore);
        assertThat(pathingB.getUsedChannels()).isEqualTo(usedChannelsBBefore);
    }

    // ---------------------------------------------------------------------
    // 5. Channels A independent of B — 32 channels per lane preserved
    // ---------------------------------------------------------------------
    // GridConnection.getMaxChannels() (see GridConnection.java line ~150) is computed exclusively from
    // `sideB.getGrid().getPathingService().getChannelMode()` - i.e. purely from the connection's OWN grid. Since a
    // GridConnection can, by construction (the pre-merge security check plus normal grid semantics), only ever exist
    // between two nodes that share the same grid, this formula can never reference another slot's grid. This test
    // exercises that concretely: each slot gets one legitimate SAME-SLOT internal connection (a "leaf" node joining
    // its own slot, exactly like a second cable joining an existing network - not a cross-slot connection), and both
    // resolve independently to the standard 32-channel-per-lane baseline.

    @Test
    void eachSlotIndependentlyKeeps32ChannelsPerLaneBaseline() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var leafOwnerA = new Object();
        var leafA = new ManagedGridNode(leafOwnerA, listener);
        leafA.create(level, null);
        var connectionA = GridConnection.create(slots.mgnA.getNode(), leafA.getNode(), null);
        assertThat(connectionA).isNotNull();

        var leafOwnerB = new Object();
        var leafB = new ManagedGridNode(leafOwnerB, listener);
        leafB.create(level, null);
        var connectionB = GridConnection.create(slots.mgnB.getNode(), leafB.getNode(), null);
        assertThat(connectionB).isNotNull();

        assertThat(connectionA.getMaxChannels()).isEqualTo(32);
        assertThat(connectionB.getMaxChannels()).isEqualTo(32);

        // The leaf nodes joined their own slot's grid, not the other slot's, and the two slots remain distinct.
        assertThat(leafA.getGrid()).isSameAs(slots.mgnA.getGrid());
        assertThat(leafB.getGrid()).isSameAs(slots.mgnB.getGrid());
        assertThat(slots.mgnA.getGrid()).isNotSameAs(slots.mgnB.getGrid());
    }

    // ---------------------------------------------------------------------
    // 6. Security Domain A independent of Security Domain B (two SecurityService instances, independent state)
    // ---------------------------------------------------------------------

    @Test
    void securityServiceInstancesForSlotAAndSlotBAreIndependent() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var securityA = securityOf(slots.mgnA, registryA);
        var securityB = securityOf(slots.mgnB, registryB);
        assertThat(securityA).isNotSameAs(securityB);

        var ownerA = UUID.randomUUID();
        var ownerB = UUID.randomUUID();
        var domainIdA = securityA.bootstrapDomain(ownerA);
        var domainIdB = securityB.bootstrapDomain(ownerB);
        assertThat(domainIdA).isNotEqualTo(domainIdB);

        // Mutating domain A's ACL must never be observable through domain B's registry/service, and vice versa.
        var strangerId = UUID.randomUUID();
        registryA.getDomain(domainIdA).grant(PrincipalId.ofPlayer(strangerId), Set.of(SecurityPermissions.EXTRACT));

        assertThat(securityA.hasPermission(playerSource(strangerId), SecurityPermissions.EXTRACT)).isTrue();
        assertThat(securityB.hasPermission(playerSource(strangerId), SecurityPermissions.EXTRACT)).isFalse();
        assertThat(registryB.getAllDomains()).hasSize(1);
        assertThat(registryB.getDomain(domainIdA)).isNull();
    }

    // ---------------------------------------------------------------------
    // 7. Permissions A do not grant access to B
    // ---------------------------------------------------------------------

    @Test
    void ownerAndGrantsOnSlotAGiveNoAccessWhatsoeverOnSlotB() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var securityA = securityOf(slots.mgnA, registryA);
        var securityB = securityOf(slots.mgnB, registryB);

        var ownerA = UUID.randomUUID();
        securityA.bootstrapDomain(ownerA);
        securityB.bootstrapDomain(UUID.randomUUID());

        // Owner of A has every permission on A (by definition)...
        for (var permission : SecurityPermissions.values()) {
            assertThat(securityA.hasPermission(playerSource(ownerA), permission)).isTrue();
        }
        // ...and precisely zero permissions on B - not even EXTRACT, not even after being A's full owner.
        for (var permission : SecurityPermissions.values()) {
            assertThat(securityB.hasPermission(playerSource(ownerA), permission)).isFalse();
        }

        // An explicit grant on A is likewise scoped to A only.
        var grantedPlayer = UUID.randomUUID();
        registryA.getDomain(securityA.getDomainId().orElseThrow())
                .grant(PrincipalId.ofPlayer(grantedPlayer), Set.of(SecurityPermissions.INJECT));
        assertThat(securityA.hasPermission(playerSource(grantedPlayer), SecurityPermissions.INJECT)).isTrue();
        assertThat(securityB.hasPermission(playerSource(grantedPlayer), SecurityPermissions.INJECT)).isFalse();
    }

    // ---------------------------------------------------------------------
    // 8. Machine/device identity A does not grant access to B
    // ---------------------------------------------------------------------

    @Test
    void deviceGrantedAccessOnSlotAHasNoAccessOnSlotB() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var securityA = securityOf(slots.mgnA, registryA);
        var securityB = securityOf(slots.mgnB, registryB);
        securityA.bootstrapDomain(UUID.randomUUID());
        securityB.bootstrapDomain(UUID.randomUUID());

        var deviceId = UUID.randomUUID();
        registryA.getDomain(securityA.getDomainId().orElseThrow())
                .grant(PrincipalId.ofDevice(deviceId), Set.of(SecurityPermissions.INJECT));

        // Round 32: a device is something AE2 can locate and the registry authenticated - not an object that
        // says what it is. The helper builds exactly that shape.
        var deviceSource = RegisteredTestDevice.source(deviceRegistry, level, deviceId);
        assertThat(securityA.hasPermission(deviceSource, SecurityPermissions.INJECT)).isTrue();
        // Same device identity, same UUID, but domain B never granted it anything - and domain B has no idea domain
        // A even exists.
        assertThat(securityB.hasPermission(deviceSource, SecurityPermissions.INJECT)).isFalse();
    }

    // ---------------------------------------------------------------------
    // 9. Remove/re-add one slot does not rebuild the other
    // ---------------------------------------------------------------------

    @Test
    void destroyingAndRecreatingSlotADoesNotAffectSlotB() {
        var slots = new TwoSlotHost();
        var storageB = new SimpleFakeStorage();
        slots.mgnB.addService(IStorageProvider.class, new SimpleStorageProvider(storageB));
        slots.createBoth();

        var registryB = SecurityDomainRegistry.createForTesting();
        var securityB = securityOf(slots.mgnB, registryB);
        var domainIdB = securityB.bootstrapDomain(UUID.randomUUID());
        var originalGridB = slots.mgnB.getGrid();

        var emerald = AEItemKey.of(Items.EMERALD);
        // Seed the backing storage directly rather than through the network's secured facade. This test is about slot
        // isolation, not about an access decision, and since Round 27 an actorless source is no longer trusted on a
        // SECURED network - routing the fixture setup through the ACL would have made it a security test by accident.
        storageB.insert(emerald, 10, Actionable.MODULATE, IActionSource.empty());

        // Destroy slot A entirely.
        slots.mgnA.destroy();

        // Slot B: completely unaffected - same grid instance, same domain, same storage contents.
        assertThat(slots.mgnB.getGrid()).isSameAs(originalGridB);
        assertThat(securityOf(slots.mgnB, registryB).getDomainId()).contains(domainIdB);
        var stacksAfterADestroyed = new KeyCounter();
        slots.mgnB.getGrid().getStorageService().getInventory().getAvailableStacks(stacksAfterADestroyed);
        assertThat(stacksAfterADestroyed.get(emerald)).isEqualTo(10L);

        // Re-add slot A as a brand-new node on the very same host object.
        var mgnA2 = new ManagedGridNode(slots.host, listener);
        mgnA2.create(level, null);
        var registryA2 = SecurityDomainRegistry.createForTesting();
        var securityA2 = securityOf(mgnA2, registryA2);
        var domainIdA2 = securityA2.bootstrapDomain(UUID.randomUUID());

        // Slot B is still completely untouched by slot A's re-add.
        assertThat(slots.mgnB.getGrid()).isSameAs(originalGridB);
        assertThat(securityOf(slots.mgnB, registryB).getDomainId()).contains(domainIdB);
        var stacksAfterARecreated = new KeyCounter();
        slots.mgnB.getGrid().getStorageService().getInventory().getAvailableStacks(stacksAfterARecreated);
        assertThat(stacksAfterARecreated.get(emerald)).isEqualTo(10L);

        // The new slot A is its own independent grid, not accidentally merged with B or resurrecting the old A.
        assertThat(mgnA2.getGrid()).isNotSameAs(slots.mgnB.getGrid());
        assertThat(domainIdA2).isNotEqualTo(domainIdB);
    }

    // ---------------------------------------------------------------------
    // 10. Chunk unload/reload (closest feasible test-only proxy) does not mix slots
    // ---------------------------------------------------------------------
    // Mirrors SecurityDomainRegistryPersistenceTest#multipleDomainsAllSurviveTogetherThroughAReload: a real on-disk
    // SavedDataStorage, flushed synchronously (saveAndJoin(), equivalent to a world save), then re-opened as a
    // BRAND NEW SavedDataStorage/registry instance pointed at the same directory with no shared in-memory state at
    // all - the closest a unit test can get to "the chunk containing the host unloads and reloads" without booting a
    // full dedicated server with a real overworld. Both domains live in the very same on-disk registry (= the same
    // host/world), which is exactly the "one host, two slots" shape - and after reload they are still two distinct
    // domains with non-overlapping ACLs, not merged.

    @Test
    void chunkUnloadReloadSimulationKeepsBothSlotsDomainsSeparate(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry1 = storage1.computeIfAbsent(SecurityDomainRegistry.TYPE);

        var ownerA = UUID.randomUUID();
        var ownerB = UUID.randomUUID();
        var domainA = registry1.createDomain(ownerA);
        var domainB = registry1.createDomain(ownerB);
        var playerGrantedOnAOnly = UUID.randomUUID();
        domainA.grant(PrincipalId.ofPlayer(playerGrantedOnAOnly), Set.of(SecurityPermissions.EXTRACT));
        registry1.markDirty();

        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(SecurityDomainRegistry.TYPE);

        assertThat(registry2.getAllDomains()).hasSize(2);
        var reloadedA = registry2.getDomain(domainA.id());
        var reloadedB = registry2.getDomain(domainB.id());
        assertThat(reloadedA).isNotNull();
        assertThat(reloadedB).isNotNull();
        assertThat(reloadedA.owner()).isEqualTo(ownerA);
        assertThat(reloadedB.owner()).isEqualTo(ownerB);

        // The grant on A never leaked into B across the reload.
        assertThat(reloadedA.getEffectivePermissions(PrincipalId.ofPlayer(playerGrantedOnAOnly)))
                .contains(SecurityPermissions.EXTRACT);
        assertThat(reloadedB.getEffectivePermissions(PrincipalId.ofPlayer(playerGrantedOnAOnly))).isEmpty();
    }

    // ---------------------------------------------------------------------
    // 11. Persistence/restart does not change slot identity
    // ---------------------------------------------------------------------
    // Node-level NBT round-trip (mirrors EnergyServiceTest#testStoredEnergyIsRemovedOnNbtReload /
    // SecurityServiceTest#nodeDomainBindingSurvivesDestroyAndRecreateFromSavedNbt), applied independently to both
    // slots in the same test to prove restarting/reloading one slot's node data has zero effect on the other's.

    @Test
    void restartingBothSlotsFromSavedNbtPreservesEachSlotsOwnIdentityIndependently() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var domainIdA = securityOf(slots.mgnA, registryA).bootstrapDomain(UUID.randomUUID());
        var domainIdB = securityOf(slots.mgnB, registryB).bootstrapDomain(UUID.randomUUID());

        var savedA = toTag(slots.mgnA::serialize);
        var savedB = toTag(slots.mgnB::serialize);
        slots.mgnA.destroy();
        slots.mgnB.destroy();

        // "Restart": brand-new ManagedGridNode instances, loaded from the saved NBT, on the same host object.
        var reloadedA = new ManagedGridNode(slots.host, listener);
        fromTag(savedA, reloadedA::deserialize);
        reloadedA.create(level, null);

        var reloadedB = new ManagedGridNode(slots.host, listener);
        fromTag(savedB, reloadedB::deserialize);
        reloadedB.create(level, null);

        var reloadedSecurityA = securityOf(reloadedA, registryA);
        var reloadedSecurityB = securityOf(reloadedB, registryB);

        assertThat(reloadedSecurityA.getDomainId()).contains(domainIdA);
        assertThat(reloadedSecurityB.getDomainId()).contains(domainIdB);
        assertThat(reloadedA.getGrid()).isNotSameAs(reloadedB.getGrid());
        assertThat(reloadedA.getNode().getConnections()).isEmpty();
        assertThat(reloadedB.getNode().getConnections()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // SPECIAL: same host, two SECURED domains, is a VALID (never rejected) state
    // ---------------------------------------------------------------------
    // This is deliberately a DIFFERENT scenario from Security Foundation's pre-merge rejection
    // (GridConnection#securityDomainsAreCompatible / SecurityServiceTest's
    // "connectingTwoDifferentlySecuredNetworksIsRejectedAndBothStayFullyIndependent"): that mechanism only ever
    // triggers when GridConnection.create() is actually called to bridge two nodes. Here, GridConnection.create() is
    // NEVER called between slot A and slot B at all - they simply coexist on the same host, each with its own
    // Security Domain, and nothing about that should be rejected, denied, or flagged CONFLICT. CONFLICT is reserved
    // exclusively for two differently-bound nodes ending up in the SAME grid/SecurityService (see
    // SecurityServiceTest#securityServiceStillFailsClosedIfTwoDifferentDomainsEndUpBoundToTheSameGridDirectly) -
    // which structurally cannot happen here since securityA and securityB are two different SecurityService
    // instances belonging to two different Grid objects.

    @Test
    void sameHostTwoDifferentSecuredDomainsCoexistingWithNoConnectionAttemptIsAValidState() {
        var slots = new TwoSlotHost();
        slots.createBoth();

        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var securityA = securityOf(slots.mgnA, registryA);
        var securityB = securityOf(slots.mgnB, registryB);

        // Bootstrapping both domains on the same host, back to back, with no connection attempt between them at any
        // point, must succeed cleanly for both - not throw, not deny, not conflict.
        var domainIdA = securityA.bootstrapDomain(UUID.randomUUID());
        var domainIdB = securityB.bootstrapDomain(UUID.randomUUID());

        assertThat(domainIdA).isNotNull();
        assertThat(domainIdB).isNotNull();
        assertThat(domainIdA).isNotEqualTo(domainIdB);

        // Both are SECURED, neither is CONFLICT - proving this is materially different from the merge-rejection
        // scenario, where a merge is at least attempted and refused. Here there was never anything to refuse.
        assertThat(securityA.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityB.getState()).isEqualTo(SecurityDomainState.SECURED);

        // Confirm no connection exists and none was ever attempted by this test.
        assertThat(slots.mgnA.getNode().getConnections()).isEmpty();
        assertThat(slots.mgnB.getNode().getConnections()).isEmpty();
        assertThat(slots.mgnA.getGrid()).isNotSameAs(slots.mgnB.getGrid());
    }

    // ---------------------------------------------------------------------
    // Fixtures / helpers
    // ---------------------------------------------------------------------

    /**
     * Models "one future Quantum host holding two independent Network Slots": a single plain host object owns two
     * {@link ManagedGridNode}s, one per slot. Nothing ever calls {@link GridConnection#create} between {@code mgnA} and
     * {@code mgnB} - that is the entire point being proven throughout this file.
     */
    private final class TwoSlotHost {
        final Object host = new Object();
        final ManagedGridNode mgnA = new ManagedGridNode(host, listener);
        final ManagedGridNode mgnB = new ManagedGridNode(host, listener);
        private boolean created;

        TwoSlotHost() {
            // Services (storage/crafting providers) must be added before create(), so tests that need them call
            // createBoth() explicitly after configuring services; tests that don't need extra services can rely on
            // the default eager creation below.
        }

        void createBoth() {
            if (!created) {
                created = true;
                if (mgnA.getNode() == null) {
                    mgnA.create(level, null);
                }
                if (mgnB.getNode() == null) {
                    mgnB.create(level, null);
                }
            }
        }
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
            return Component.literal("NetworkSlotIsolationTest fake storage");
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
