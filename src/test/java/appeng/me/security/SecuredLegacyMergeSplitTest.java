package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.AbstractGridNodeTest;
import appeng.me.GridConnection;
import appeng.me.ManagedGridNode;

/**
 * Phase E, Part 1 - SECURED + LEGACY grid merge/split semantics.
 * <p/>
 * This is deliberately a <em>different</em> scenario from the Security Gate's cross-domain pre-merge rejection
 * ({@code SecurityServiceTest}'s "CROSS-DOMAIN MERGE" section, which covers two <b>different, already-SECURED</b>
 * domains touching): here, one side is {@link SecurityDomainState#SECURED} (bound to a real domain) and the other side
 * is genuinely {@link SecurityDomainState#LEGACY} (no domain has ever been bound to it - a plain, pre-existing,
 * independently-formed AE2 network). {@code GridConnection#securityDomainsAreCompatible} already treats this case as
 * compatible ({@code domainA.isEmpty() || domainB.isEmpty() || ...}) - the connection is always allowed. What this test
 * class establishes, empirically (not by assumption), is <b>what happens after</b> that allowed connection forms and
 * later un-forms.
 * <p/>
 * <b>Archaeology performed before writing these tests (see {@link SecurityService}'s own class doc and
 * {@link SecurityService#addNode}):</b> a node's own per-node domain binding is only ever written into its persisted
 * per-node {@code CompoundTag} (the {@code aeSecDomain} key) in exactly one situation - a brand-new node whose
 * {@code savedData} is {@code null} (i.e. it has never before been part of <em>any</em> grid, so there is nothing for
 * it to "keep" through the merge) joining a grid that is already SECURED with exactly one domain. A node that
 * <em>already</em> belongs to its own established grid (e.g. {@code createAndInitNode()} below, or any real,
 * previously-existing LEGACY network) always has {@code savedData != null} (possibly empty, but never {@code null}) by
 * the time {@code GridNode#setGrid} runs during a merge, because {@code setGrid} always captures a fresh
 * {@code CompoundTag} from the node's <em>previous</em> grid first ({@code this.savedData = new CompoundTag(); this
 * .myGrid.saveNodeData(this, savedData);}) - and {@code SecurityService#addNode}'s inheritance branch
 * ({@code else if (domainCounts.elementSet().size() == 1)}) is only reachable when {@code savedData == null}, i.e. it
 * never runs for such a node. A genuinely pre-existing LEGACY node's own domain binding is therefore <b>never
 * permanently written</b> merely by being physically connected to a SECURED network - the tests below prove this
 * empirically, at every observable boundary (aggregate grid state while connected, post-split state, repeated cycles,
 * and raw per-node NBT content).
 */
class SecuredLegacyMergeSplitTest extends AbstractGridNodeTest {

    private static final String TAG_DOMAIN = "aeSecDomain";

    // ---------------------------------------------------------------------
    // 1. SECURED + LEGACY connection succeeds (not rejected)
    // ---------------------------------------------------------------------

    @Test
    void securedPlusLegacyConnectionSucceedsAndIsNotRejected() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());

        var originalGridB = mgnB.getGrid();
        assertThat(securityOf(mgnB, registry).getState()).isEqualTo(SecurityDomainState.LEGACY);

        var connection = GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);

        assertThat(connection).as("SECURED + LEGACY must be an allowed, ordinary connection - not rejected")
                .isNotNull();
        assertThat(mgnA.getGrid()).isSameAs(mgnB.getGrid());
        assertThat(mgnB.getGrid()).isNotSameAs(originalGridB);
    }

    // ---------------------------------------------------------------------
    // 2. Whole merged grid is SECURED under Domain A while connected
    // ---------------------------------------------------------------------

    @Test
    void wholeMergedGridIsSecuredUnderDomainAWhileConnected() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var ownerA = UUID.randomUUID();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(ownerA);

        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);

        var merged = securityOf(mgnA, registry);
        assertThat(merged.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(merged.getDomainId()).contains(domainIdA);
        // Re-fetching via B's node reaches the exact same grid-level service (one Grid, one SecurityService).
        assertThat(mgnB.getGrid().getService(ISecurityService.class)).isSameAs(merged);
        assertThat(merged.hasPermission(playerSource(ownerA), SecurityPermissions.SECURITY)).isTrue();
    }

    // ---------------------------------------------------------------------
    // 3. Unknown player gets no LEGACY-open bypass while connected
    // ---------------------------------------------------------------------

    @Test
    void unknownPlayerGetsNoLegacyOpenBypassWhileConnected() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());

        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);

        var merged = securityOf(mgnA, registry);
        var stranger = playerSource(UUID.randomUUID());
        for (var permission : SecurityPermissions.values()) {
            assertThat(merged.hasPermission(stranger, permission))
                    .as("stranger must not get LEGACY-open access to a merged SECURED grid via %s", permission)
                    .isFalse();
        }
    }

    // ---------------------------------------------------------------------
    // 4. EVERYONE grant on Domain A works on the former-legacy side too
    // ---------------------------------------------------------------------

    @Test
    void everyoneGrantOnDomainAAppliesAcrossTheWholeMergedGridIncludingFormerLegacySide() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());

        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);

        var merged = securityOf(mgnA, registry);
        var stranger = playerSource(UUID.randomUUID());
        assertThat(merged.hasPermission(stranger, SecurityPermissions.EXTRACT)).isFalse();

        registry.getDomain(domainIdA).grant(PrincipalId.everyone(), Set.of(SecurityPermissions.EXTRACT));

        // Re-fetch through mgnB's node specifically - proving the grant is visible from the former-legacy side,
        // not just from A's own node.
        var mergedViaB = (SecurityService) mgnB.getGrid().getService(ISecurityService.class);
        assertThat(mergedViaB.hasPermission(stranger, SecurityPermissions.EXTRACT)).isTrue();
    }

    // ---------------------------------------------------------------------
    // 5. Storage visibility goes through SECURED permission semantics while connected
    // ---------------------------------------------------------------------

    @Test
    void storageVisibilityGoesThroughSecuredPermissionSemanticsWhileConnected() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = new ManagedGridNode(new Object(), listener);
        var mgnB = new ManagedGridNode(new Object(), listener);
        var storage = new SimpleFakeStorage();
        mgnB.addService(IStorageProvider.class, new SimpleStorageProvider(storage));
        mgnA.create(level, null);
        mgnB.create(level, null);

        var ownerA = UUID.randomUUID();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(ownerA);
        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);
        securityOf(mgnA, registry); // re-pin the registry override on whichever instance survived the merge

        var diamond = AEItemKey.of(net.minecraft.world.item.Items.DIAMOND);
        var inventory = mgnA.getGrid().getStorageService().getInventory();

        // Stranger cannot insert/extract through the merged grid's storage, even though the item physically lives
        // behind the formerly-legacy node.
        var stranger = playerSource(UUID.randomUUID());
        assertThat(inventory.insert(diamond, 10, Actionable.MODULATE, stranger)).isZero();

        // The owner can, via the real SecurityAwareMEStorage-wrapped path (StorageService.getInventory()).
        var ownerSource = playerSource(ownerA);
        assertThat(inventory.insert(diamond, 10, Actionable.MODULATE, ownerSource)).isEqualTo(10L);
        assertThat(inventory.extract(diamond, 10, Actionable.MODULATE, stranger)).isZero();
        assertThat(inventory.extract(diamond, 10, Actionable.MODULATE, ownerSource)).isEqualTo(10L);

        // Granting EXTRACT to a specific player also works from the merged storage's real enforcement path.
        var grantedPlayerId = UUID.randomUUID();
        registry.getDomain(domainIdA).grant(PrincipalId.ofPlayer(grantedPlayerId), Set.of(SecurityPermissions.INJECT,
                SecurityPermissions.EXTRACT));
        var grantedSource = playerSource(grantedPlayerId);
        assertThat(inventory.insert(diamond, 5, Actionable.MODULATE, grantedSource)).isEqualTo(5L);
        assertThat(inventory.extract(diamond, 5, Actionable.MODULATE, grantedSource)).isEqualTo(5L);
    }

    // ---------------------------------------------------------------------
    // 6. Split returns the genuinely-legacy fragment to LEGACY
    // ---------------------------------------------------------------------

    @Test
    void splitReturnsGenuinelyLegacyFragmentToLegacy() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());
        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);
        assertThat(securityOf(mgnA, registry).getState()).isEqualTo(SecurityDomainState.SECURED);

        // Split: destroy the (only) connection between them.
        var connection = mgnA.getNode().getConnections().iterator().next();
        connection.destroy();

        assertThat(mgnA.getGrid()).isNotSameAs(mgnB.getGrid());
        var legacyAgain = (SecurityService) mgnB.getGrid().getService(ISecurityService.class);
        assertThat(legacyAgain.getState()).isEqualTo(SecurityDomainState.LEGACY);
        // Fully open again, exactly like vanilla AE2 - no lingering restriction from the temporary connection.
        assertThat(legacyAgain.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.EXTRACT)).isTrue();
    }

    // ---------------------------------------------------------------------
    // 7. Secured fragment remains Domain A after split
    // ---------------------------------------------------------------------

    @Test
    void securedFragmentRemainsDomainAAfterSplit() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());
        var grantedPlayer = UUID.randomUUID();
        registry.getDomain(domainIdA).grant(PrincipalId.ofPlayer(grantedPlayer), Set.of(SecurityPermissions.EXTRACT));

        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);
        var connection = mgnA.getNode().getConnections().iterator().next();
        connection.destroy();

        var securedAgain = securityOf(mgnA, registry);
        assertThat(securedAgain.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securedAgain.getDomainId()).contains(domainIdA);
        // ACL survived the round trip intact.
        assertThat(securedAgain.hasPermission(playerSource(grantedPlayer), SecurityPermissions.EXTRACT)).isTrue();
        assertThat(securedAgain.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.EXTRACT))
                .isFalse();
    }

    // ---------------------------------------------------------------------
    // 8. Repeated connect/split cycles never permanently mutate the legacy node into Domain A
    // ---------------------------------------------------------------------

    @Test
    void repeatedConnectSplitCyclesDoNotMutateTheLegacyNodeIntoAPermanentDomain() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());

        for (int cycle = 0; cycle < 4; cycle++) {
            GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);

            var merged = securityOf(mgnA, registry);
            assertThat(merged.getState()).as("cycle %d: merged state", cycle).isEqualTo(SecurityDomainState.SECURED);
            assertThat(merged.getDomainId()).as("cycle %d: same domain every time, never a new one", cycle)
                    .contains(domainIdA);

            var connection = mgnA.getNode().getConnections().iterator().next();
            connection.destroy();

            assertThat(mgnA.getGrid()).isNotSameAs(mgnB.getGrid());
            var legacyAgain = (SecurityService) mgnB.getGrid().getService(ISecurityService.class);
            assertThat(legacyAgain.getState()).as("cycle %d: B reverts to LEGACY every time", cycle)
                    .isEqualTo(SecurityDomainState.LEGACY);

            // Structural proof, not just behavioral: B's own persisted per-node NBT never carries the domain tag.
            var savedB = toTag(mgnB::serialize);
            assertThat(savedB.contains(TAG_DOMAIN)).as("cycle %d: B's own NBT must never carry aeSecDomain", cycle)
                    .isFalse();

            var securedAgain = securityOf(mgnA, registry);
            assertThat(securedAgain.getState()).as("cycle %d: A reverts to SECURED domain A every time", cycle)
                    .isEqualTo(SecurityDomainState.SECURED);
            assertThat(securedAgain.getDomainId()).contains(domainIdA);
        }

        // Only ever the one domain in the whole registry, across all cycles.
        assertThat(registry.getAllDomains()).hasSize(1);
    }

    // ---------------------------------------------------------------------
    // 9. Restart during connected state does not create an unexpected second domain
    // ---------------------------------------------------------------------

    @Test
    void restartDuringConnectedStateDoesNotCreateAnUnexpectedSecondDomain() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var ownerA = UUID.randomUUID();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(ownerA);
        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);
        assertThat(securityOf(mgnA, registry).getState()).isEqualTo(SecurityDomainState.SECURED);

        // "Restart" while still connected: serialize both nodes' persisted data, destroy both, recreate both fresh
        // from that saved NBT, then re-form the physical connection (mirrors what really happens on world reload:
        // block entities/nodes are recreated first, then InWorldGridNode#findInWorldConnections re-forms in-world
        // connections on the next tick).
        var savedA = toTag(mgnA::serialize);
        var savedB = toTag(mgnB::serialize);
        mgnA.destroy();
        mgnB.destroy();

        var reloadedA = new ManagedGridNode(owner, listener);
        fromTag(savedA, reloadedA::deserialize);
        reloadedA.create(level, null);

        var reloadedB = new ManagedGridNode(owner, listener);
        fromTag(savedB, reloadedB::deserialize);
        reloadedB.create(level, null);

        GridConnection.create(reloadedA.getNode(), reloadedB.getNode(), null);

        var afterRestart = securityOf(reloadedA, registry);
        assertThat(afterRestart.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(afterRestart.getDomainId()).contains(domainIdA);
        assertThat(registry.getAllDomains()).as("no second domain was ever created by the restart").hasSize(1);
        assertThat(afterRestart.hasPermission(playerSource(ownerA), SecurityPermissions.SECURITY)).isTrue();
        assertThat(afterRestart.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.EXTRACT))
                .isFalse();
    }

    // ---------------------------------------------------------------------
    // 10. After split/reload, semantics remain the same
    // ---------------------------------------------------------------------

    @Test
    void afterSplitAndReloadSemanticsRemainTheSame() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var domainIdA = securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());
        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);
        var connection = mgnA.getNode().getConnections().iterator().next();
        connection.destroy();

        // Both fragments now independent again; serialize, destroy, and reload each independently (simulating a
        // world save/reload of the already-split state), with no reconnection this time.
        var savedA = toTag(mgnA::serialize);
        var savedB = toTag(mgnB::serialize);
        mgnA.destroy();
        mgnB.destroy();

        var reloadedA = new ManagedGridNode(owner, listener);
        fromTag(savedA, reloadedA::deserialize);
        reloadedA.create(level, null);

        var reloadedB = new ManagedGridNode(owner, listener);
        fromTag(savedB, reloadedB::deserialize);
        reloadedB.create(level, null);

        var reloadedSecurityA = securityOf(reloadedA, registry);
        assertThat(reloadedSecurityA.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(reloadedSecurityA.getDomainId()).contains(domainIdA);

        var reloadedSecurityB = (SecurityService) reloadedB.getGrid().getService(ISecurityService.class);
        assertThat(reloadedSecurityB.getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(reloadedSecurityB.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.EXTRACT))
                .isTrue();

        assertThat(reloadedA.getGrid()).isNotSameAs(reloadedB.getGrid());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private SecurityService securityOf(ManagedGridNode mgn, SecurityDomainRegistry registry) {
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(registry);
        return security;
    }

    private ManagedGridNode createAndInitNode() {
        var mgn = new ManagedGridNode(owner, listener);
        mgn.create(level, null);
        return mgn;
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

    /** Minimal in-memory {@link MEStorage} fixture, real enough to prove actual insert/extract enforcement. */
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
            return Component.literal("SecuredLegacyMergeSplitTest fake storage");
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
}
