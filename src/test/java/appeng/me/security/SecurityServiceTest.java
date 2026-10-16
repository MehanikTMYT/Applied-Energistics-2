package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.me.AbstractGridNodeTest;
import appeng.me.GridConnection;
import appeng.me.ManagedGridNode;

/**
 * Covers the security foundation's core invariants at the grid-service level (mirroring the style of
 * {@code EnergyServiceTest}/{@code GridServicesTest}): OWNER, PLAYER, GROUP, DEVICE and SECURITY permission resolution,
 * persistence (NBT round-trip and grid split/rejoin), and the fail-closed CONFLICT grid-merge guard.
 */
class SecurityServiceTest extends AbstractGridNodeTest {

    // ---------------------------------------------------------------------
    // OWNER
    // ---------------------------------------------------------------------

    @Test
    void ownerAlwaysHasEveryPermissionEvenWithoutAnExplicitGrant() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var ownerId = UUID.randomUUID();
        var domainId = security.bootstrapDomain(ownerId);
        assertThat(domainId).isNotNull();
        assertThat(security.getState()).isEqualTo(SecurityDomainState.SECURED);

        var ownerSource = playerSource(ownerId);
        for (var permission : SecurityPermissions.values()) {
            assertThat(security.hasPermission(ownerSource, permission))
                    .as("owner should implicitly have %s", permission)
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // PLAYER
    // ---------------------------------------------------------------------

    @Test
    void playerOnlyHasExplicitlyGrantedPermissions() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var ownerId = UUID.randomUUID();
        var domainId = security.bootstrapDomain(ownerId);
        var domain = registry.getDomain(domainId);

        var playerId = UUID.randomUUID();
        domain.grant(PrincipalId.ofPlayer(playerId), Set.of(SecurityPermissions.EXTRACT));

        var playerSource = playerSource(playerId);
        assertThat(security.hasPermission(playerSource, SecurityPermissions.EXTRACT)).isTrue();
        assertThat(security.hasPermission(playerSource, SecurityPermissions.INJECT)).isFalse();
    }

    @Test
    void randomPlayerHasNoPermissionsOnASecuredDomainByDefault() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        security.bootstrapDomain(UUID.randomUUID());

        var strangerSource = playerSource(UUID.randomUUID());
        assertThat(security.hasPermission(strangerSource, SecurityPermissions.EXTRACT)).isFalse();
    }

    @Test
    void legacyNetworkIsFullyOpenLikeVanillaAe2() {
        var mgn = createAndInitNode();
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(SecurityDomainRegistry.createForTesting());

        assertThat(security.getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(security.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.SECURITY)).isTrue();
    }

    // ---------------------------------------------------------------------
    // GROUP
    // ---------------------------------------------------------------------

    @Test
    void playerInheritsPermissionsFromGroupMembership() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var domainId = security.bootstrapDomain(UUID.randomUUID());
        var domain = registry.getDomain(domainId);

        var playerId = UUID.randomUUID();
        var group = SecurityGroup.create("miners").withMember(playerId);
        domain.putGroup(group);
        domain.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.CRAFT));

        var source = playerSource(playerId);
        assertThat(security.hasPermission(source, SecurityPermissions.CRAFT)).isTrue();
        assertThat(security.hasPermission(source, SecurityPermissions.BUILD)).isFalse();

        // Removing the player from the group revokes the inherited permission
        domain.putGroup(group.withoutMember(playerId));
        assertThat(security.hasPermission(source, SecurityPermissions.CRAFT)).isFalse();
    }

    // ---------------------------------------------------------------------
    // DEVICE
    // ---------------------------------------------------------------------

    @Test
    void deviceWithIdentityCanBeGrantedPermissionsButNeverInheritsOwnerRights() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var domainId = security.bootstrapDomain(UUID.randomUUID());
        var domain = registry.getDomain(domainId);

        var deviceId = UUID.randomUUID();
        domain.grant(PrincipalId.ofDevice(deviceId), Set.of(SecurityPermissions.INJECT));

        var deviceSource = deviceSource(deviceId);
        assertThat(security.hasPermission(deviceSource, SecurityPermissions.INJECT)).isTrue();
        // Not granted BUILD, and building the machine does not imply ownership.
        assertThat(security.hasPermission(deviceSource, SecurityPermissions.BUILD)).isFalse();
        assertThat(security.hasPermission(deviceSource, SecurityPermissions.SECURITY)).isFalse();
    }

    @Test
    void machineWithoutDeviceIdentityOnlyGetsWhateverIsGrantedToEveryone() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var domainId = security.bootstrapDomain(UUID.randomUUID());
        var domain = registry.getDomain(domainId);

        var anonymousMachine = machineSource(mock(IActionHost.class));
        // Denied by default.
        assertThat(security.hasPermission(anonymousMachine, SecurityPermissions.EXTRACT)).isFalse();

        // Granting EVERYONE now lets it through.
        domain.grant(PrincipalId.everyone(), Set.of(SecurityPermissions.EXTRACT));
        assertThat(security.hasPermission(anonymousMachine, SecurityPermissions.EXTRACT)).isTrue();
    }

    @Test
    void everyoneGrantAlsoAppliesToAnUnlistedPlayerNotJustAnonymousMachines() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var domainId = security.bootstrapDomain(UUID.randomUUID());
        var domain = registry.getDomain(domainId);

        var strangerId = UUID.randomUUID();
        var strangerSource = playerSource(strangerId);
        assertThat(security.hasPermission(strangerSource, SecurityPermissions.EXTRACT)).isFalse();

        domain.grant(PrincipalId.everyone(), Set.of(SecurityPermissions.EXTRACT));
        assertThat(security.hasPermission(strangerSource, SecurityPermissions.EXTRACT)).isTrue();
        // Still no other permission was implied.
        assertThat(security.hasPermission(strangerSource, SecurityPermissions.INJECT)).isFalse();
    }

    @Test
    void actorlessSourcesAreUnidentifiedNotTrustedOnASecuredNetwork() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var domain = registry.getDomain(security.bootstrapDomain(UUID.randomUUID()));

        // Round 27 inverted this. It used to assert `isTrue()`: a source naming neither a player nor a machine
        // bypassed the ACL outright, on the assumption that only AE2's own bookkeeping could produce one. Round 26
        // disproved the assumption (IActionSource.empty() is public API, reachable by anything holding a storage
        // capability) and measured that no production path needs the bypass, so Round 27 deleted it.
        assertThat(security.hasPermission(IActionSource.empty(), SecurityPermissions.SECURITY)).isFalse();
        assertThat(security.hasPermission(IActionSource.empty(), SecurityPermissions.EXTRACT)).isFalse();

        // "Unidentified" rather than "denied": an explicit public grant still applies, which is what keeps this a
        // security fix rather than a blanket lockout.
        domain.grant(PrincipalId.EVERYONE, Set.of(SecurityPermissions.EXTRACT));
        assertThat(security.hasPermission(IActionSource.empty(), SecurityPermissions.EXTRACT)).isTrue();
        assertThat(security.hasPermission(IActionSource.empty(), SecurityPermissions.SECURITY)).isFalse();
    }

    @Test
    void actorlessSourcesRemainOpenOnLegacyNetworks() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        assertThat(security.getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(security.hasPermission(IActionSource.empty(), SecurityPermissions.EXTRACT))
                .as("LEGACY interoperability is deliberately untouched by Round 27")
                .isTrue();
    }

    // ---------------------------------------------------------------------
    // NULL SOURCE (defensive fail-closed guard - regression coverage for the Round 8 automation-baseline NPE, see
    // SubnetPlots.java's "subnet" gametest which used to pass a literal `null` instead of IActionSource.empty())
    // ---------------------------------------------------------------------

    @Test
    void nullSourceOnALegacyNetworkIsTreatedAsOpenNotAsAnNpe() {
        var mgn = createAndInitNode();
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(SecurityDomainRegistry.createForTesting());

        assertThat(security.getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(security.hasPermission(null, SecurityPermissions.INJECT)).isTrue();
        assertThat(security.hasPermission(null, SecurityPermissions.SECURITY)).isTrue();
    }

    @Test
    void nullSourceOnASecuredNetworkFailsClosedInsteadOfNpeOrImplicitlyTrustedBypass() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        security.bootstrapDomain(UUID.randomUUID());
        assertThat(security.getState()).isEqualTo(SecurityDomainState.SECURED);

        // Unlike IActionSource.empty() (a deliberate, explicit "no specific actor" declaration, which correctly
        // resolves to the unidentified principal - see actorlessSourcesAreUnidentifiedNotTrustedOnASecuredNetwork
        // above), a literal null is never a valid IActionSource and must not be treated the same way: it fails
        // closed instead.
        assertThat(security.hasPermission(null, SecurityPermissions.INJECT)).isFalse();
        assertThat(security.hasPermission(null, SecurityPermissions.EXTRACT)).isFalse();
    }

    @Test
    void nullSourceOnAConflictedNetworkAlsoFailsClosed() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(registry);

        var domainA = appeng.api.networking.security.SecurityDomainId.create();
        var domainB = appeng.api.networking.security.SecurityDomainId.create();
        var fakeNodeA = mock(IGridNode.class);
        var fakeNodeB = mock(IGridNode.class);
        var tagA = new CompoundTag();
        tagA.store("aeSecDomain", appeng.api.networking.security.SecurityDomainId.CODEC, domainA);
        var tagB = new CompoundTag();
        tagB.store("aeSecDomain", appeng.api.networking.security.SecurityDomainId.CODEC, domainB);
        security.addNode(fakeNodeA, tagA);
        security.addNode(fakeNodeB, tagB);
        assertThat(security.getState()).isEqualTo(SecurityDomainState.CONFLICT);

        assertThat(security.hasPermission(null, SecurityPermissions.EXTRACT)).isFalse();
    }

    // ---------------------------------------------------------------------
    // SECURITY
    // ---------------------------------------------------------------------

    @Test
    void securityPermissionGatesAclEditingAndIsNotImpliedByOtherGrants() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var domainId = security.bootstrapDomain(UUID.randomUUID());
        var domain = registry.getDomain(domainId);

        var playerId = UUID.randomUUID();
        // Grant every *other* permission, but not SECURITY.
        domain.grant(PrincipalId.ofPlayer(playerId),
                Set.of(SecurityPermissions.INJECT, SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT,
                        SecurityPermissions.BUILD));

        var source = playerSource(playerId);
        assertThat(security.hasPermission(source, SecurityPermissions.SECURITY)).isFalse();

        domain.grant(PrincipalId.ofPlayer(playerId), Set.of(SecurityPermissions.SECURITY));
        assertThat(security.hasPermission(source, SecurityPermissions.SECURITY)).isTrue();
    }

    // ---------------------------------------------------------------------
    // PERSISTENCE
    // ---------------------------------------------------------------------

    @Test
    void securityDomainSurvivesACodecRoundTrip() {
        var domain = new SecurityDomain(appeng.api.networking.security.SecurityDomainId.create(), UUID.randomUUID());
        var playerId = UUID.randomUUID();
        domain.grant(PrincipalId.ofPlayer(playerId), Set.of(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT));
        var group = SecurityGroup.create("admins").withMember(playerId);
        domain.putGroup(group);
        domain.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.SECURITY));
        domain.audit("test entry");

        var encoded = SecurityDomain.CODEC.encodeStart(NbtOps.INSTANCE, domain).getOrThrow();
        var decoded = SecurityDomain.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertThat(decoded.id()).isEqualTo(domain.id());
        assertThat(decoded.owner()).isEqualTo(domain.owner());
        assertThat(decoded.getPermissions(PrincipalId.ofPlayer(playerId)))
                .containsExactlyInAnyOrder(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT);
        assertThat(decoded.getGroups()).containsKey(group.id());
        assertThat(decoded.getGroups().get(group.id()).members()).containsExactly(playerId);
        assertThat(decoded.getEffectivePermissions(PrincipalId.ofPlayer(playerId)))
                .contains(SecurityPermissions.SECURITY);
        assertThat(decoded.getAuditLog()).containsExactly("test entry");
    }

    @Test
    void bootstrappingASecondTimeOnAnAlreadySecuredNetworkIsANoOpAndReturnsTheSameDomain() {
        // Models "installing/binding a second Security Station to an already-secured network": the second
        // station's onReady() calls bootstrapDomain() again, exactly like the first one did.
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);

        var ownerId = UUID.randomUUID();
        var firstDomainId = security.bootstrapDomain(ownerId);
        assertThat(security.getState()).isEqualTo(SecurityDomainState.SECURED);

        // A second (or third...) station joining/bootstrapping against the same already-secured grid must not
        // create a competing domain, must not change the owner, and must not touch the existing ACL.
        var domain = registry.getDomain(firstDomainId);
        var otherPlayer = UUID.randomUUID();
        domain.grant(PrincipalId.ofPlayer(otherPlayer), Set.of(SecurityPermissions.EXTRACT));

        var secondBootstrapAttemptOwner = UUID.randomUUID(); // a different player tries to "claim" it
        var secondDomainId = security.bootstrapDomain(secondBootstrapAttemptOwner);

        assertThat(secondDomainId).isEqualTo(firstDomainId);
        assertThat(registry.getAllDomains()).hasSize(1);
        assertThat(domain.owner()).isEqualTo(ownerId); // owner unchanged
        assertThat(domain.getPermissions(PrincipalId.ofPlayer(otherPlayer))).contains(SecurityPermissions.EXTRACT);
    }

    @Test
    void removingOneNodeFromAMultiNodeSecuredGridLeavesTheRestSecuredWithTheAclIntact() {
        // Models "destroying one Security Station while another (or just plain cable/drive nodes) still exist on
        // the network": the domain binding lives on *every* node, not just the one representing a station, so
        // losing any single node (including the one that originally bootstrapped the domain) must not affect the
        // rest of the network's security state or its ACL.
        var registry = SecurityDomainRegistry.createForTesting();
        var stationNode = createAndInitNode();
        var otherNode = createAndInitNode();
        GridConnection.create(stationNode.getNode(), otherNode.getNode(), null);

        var security = securityOf(stationNode, registry);
        var domainId = security.bootstrapDomain(UUID.randomUUID());
        var domain = registry.getDomain(domainId);
        var grantedPlayer = UUID.randomUUID();
        domain.grant(PrincipalId.ofPlayer(grantedPlayer), Set.of(SecurityPermissions.EXTRACT));

        // Destroy just the "station" node - the rest of the grid (otherNode) survives as its own (now
        // single-node) grid, still bound to the same domain, still enforcing the same ACL.
        stationNode.destroy();

        var remainingSecurity = (SecurityService) otherNode.getGrid().getService(ISecurityService.class);
        remainingSecurity.setRegistryForTesting(registry);

        assertThat(remainingSecurity.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(remainingSecurity.getDomainId()).contains(domainId);
        assertThat(remainingSecurity.hasPermission(playerSource(grantedPlayer), SecurityPermissions.EXTRACT))
                .isTrue();
        assertThat(remainingSecurity.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.EXTRACT))
                .isFalse();
        // The registry itself (where the ACL actually lives) still has exactly the one domain, untouched.
        assertThat(registry.getAllDomains()).hasSize(1);
    }

    @Test
    void destroyingEveryNodeOfASecuredNetworkDoesNotResurrectItAsOpenLegacyLater() {
        // "Destroying all Stations does not revert a secured network back to open": there is no global toggle to
        // flip back - the domain simply keeps existing in the registry (independent of any grid/node), and if any
        // *new* node is later formed and manually rebound to the same domain id (the only way that could happen is
        // via a fresh bootstrap, which creates a *new* independent domain, never resurrecting the old open state),
        // the old domain and its ACL are simply still sitting in the registry, inert but intact - never silently
        // merged back into "everyone has access again".
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);
        var domainId = security.bootstrapDomain(UUID.randomUUID());

        mgn.destroy();

        // The domain and its ACL still exist in the registry, completely independent of there being zero live
        // nodes bound to it right now.
        assertThat(registry.getDomain(domainId)).isNotNull();

        // A brand new, never-before-seen node/grid is LEGACY (fully open) by default - forming a new network from
        // scratch is *never* silently secured by a pre-existing domain elsewhere in the registry. Security is only
        // ever established by an explicit bootstrap on that specific grid.
        var freshNode = createAndInitNode();
        var freshSecurity = (SecurityService) freshNode.getGrid().getService(ISecurityService.class);
        freshSecurity.setRegistryForTesting(registry);
        assertThat(freshSecurity.getState()).isEqualTo(SecurityDomainState.LEGACY);
    }

    @Test
    void reconnectingTwoHalvesOfTheSameFormerlySplitDomainStaysSecuredNotConflict() {
        // Grid rejoin of the *same* domain (as opposed to two originally-different domains, covered by the
        // CONFLICT tests below): split a secured 3-node line, then reconnect the same two ends. Both halves still
        // carry the same domain id, so rejoining must resolve to SECURED again, never CONFLICT.
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn1 = createAndInitNode();
        var mgn2 = createAndInitNode();
        var mgn3 = createAndInitNode();

        GridConnection.create(mgn1.getNode(), mgn2.getNode(), null);
        GridConnection.create(mgn2.getNode(), mgn3.getNode(), null);

        var security = securityOf(mgn1, registry);
        var domainId = security.bootstrapDomain(UUID.randomUUID());
        securityOf(mgn2, registry);
        securityOf(mgn3, registry);

        IGridNode node2 = mgn2.getNode();
        appeng.api.networking.IGridConnection severed = null;
        for (var connection : node2.getConnections()) {
            if (connection.getOtherSide(node2) == mgn3.getNode()) {
                severed = connection;
                break;
            }
        }
        assertThat(severed).isNotNull();
        severed.destroy();

        // Confirm the split actually happened (two independent SECURED grids, same domain id).
        assertThat(securityOf(mgn1, registry).getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOf(mgn3, registry).getState()).isEqualTo(SecurityDomainState.SECURED);

        // Rejoin: reconnect node2 and node3. Since both sides still carry the exact same domain id, this must
        // resolve back to a single SECURED grid - not CONFLICT (that's reserved for genuinely different domains).
        GridConnection.create(mgn2.getNode(), mgn3.getNode(), null);

        var rejoinedSecurity = securityOf(mgn1, registry);
        assertThat(rejoinedSecurity.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(rejoinedSecurity.getDomainId()).contains(domainId);
        assertThat(mgn3.getGrid()).isSameAs(mgn1.getGrid());
    }

    @Test
    void nodeDomainBindingSurvivesDestroyAndRecreateFromSavedNbt() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = securityOf(mgn, registry);
        var domainId = security.bootstrapDomain(UUID.randomUUID());

        var savedNodeData = toTag(mgn::serialize);
        mgn.destroy();

        var reloaded = new ManagedGridNode(owner, listener);
        fromTag(savedNodeData, reloaded::deserialize);
        reloaded.create(level, null);

        var reloadedSecurity = (SecurityService) reloaded.getGrid().getService(ISecurityService.class);
        reloadedSecurity.setRegistryForTesting(registry);

        assertThat(reloadedSecurity.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(reloadedSecurity.getDomainId()).contains(domainId);
    }

    @Test
    void gridSplitAndRejoinPreservesEachSidesOwnDomain() {
        var registry = SecurityDomainRegistry.createForTesting();

        var mgn1 = createAndInitNode();
        var mgn2 = createAndInitNode();
        var mgn3 = createAndInitNode();

        GridConnection.create(mgn1.getNode(), mgn2.getNode(), null);
        GridConnection.create(mgn2.getNode(), mgn3.getNode(), null);

        var security = securityOf(mgn1, registry);
        var domainId = security.bootstrapDomain(UUID.randomUUID());
        assertThat(security.getState()).isEqualTo(SecurityDomainState.SECURED);
        // The merged grid's service instance may differ per node reference; re-fetch and set the registry override
        // on every node's *current* grid to make sure whichever instance ends up being used has it.
        securityOf(mgn2, registry);
        securityOf(mgn3, registry);

        // Split the network by destroying the connection between node 2 and node 3.
        IGridNode node2 = mgn2.getNode();
        for (var connection : node2.getConnections()) {
            if (connection.getOtherSide(node2) == mgn3.getNode()) {
                connection.destroy();
                break;
            }
        }

        var securityLeft = securityOf(mgn1, registry);
        var securityRight = securityOf(mgn3, registry);

        assertThat(securityLeft.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityLeft.getDomainId()).contains(domainId);
        assertThat(securityRight.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityRight.getDomainId()).contains(domainId);

        // Permissions still resolve correctly on both halves after the split.
        var domain = registry.getDomain(domainId);
        assertThat(securityLeft.hasPermission(playerSource(domain.owner()), SecurityPermissions.SECURITY)).isTrue();
        assertThat(securityRight.hasPermission(playerSource(domain.owner()), SecurityPermissions.SECURITY)).isTrue();
    }

    // ---------------------------------------------------------------------
    // CROSS-DOMAIN MERGE - fail-closed PRE-MERGE REJECTION (primary defense)
    // ---------------------------------------------------------------------
    // Design decision (this session): GridConnection.create() itself now refuses to form a connection between two
    // nodes belonging to two already-SECURED grids bound to different domains - see
    // GridConnection#securityDomainsAreCompatible. The two grids are NEVER merged into one logical Grid: there is
    // no intermediate "merged-but-quarantined" state to reason about, no shared IGrid instance, no shared service
    // container. This is a strictly stronger guarantee than the previous merge+CONFLICT-then-deny-everyone design
    // (which is kept below only as a defense-in-depth backstop, see the "CONFLICT (defense-in-depth backstop)"
    // section), because a network that never became one Grid can, by construction, never have visibility into the
    // other side's storage/crafting/channels/topology - there is nothing to leak.

    @Test
    void connectingTwoDifferentlySecuredNetworksIsRejectedAndBothStayFullyIndependent() {
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();

        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();

        var securityA = securityOf(mgnA, registryA);
        var securityB = securityOf(mgnB, registryB);

        var ownerA = UUID.randomUUID();
        var ownerB = UUID.randomUUID();
        var domainIdA = securityA.bootstrapDomain(ownerA);
        var domainIdB = securityB.bootstrapDomain(ownerB);
        assertThat(domainIdA).isNotEqualTo(domainIdB);

        var originalGridA = mgnA.getGrid();
        var originalGridB = mgnB.getGrid();

        // The pre-merge rejection: no connection is formed at all.
        var connection = GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);
        assertThat(connection).isNull();

        // Neither node's grid identity changed - they were never merged, not even transiently.
        assertThat(mgnA.getGrid()).isSameAs(originalGridA);
        assertThat(mgnB.getGrid()).isSameAs(originalGridB);
        assertThat(mgnA.getGrid()).isNotSameAs(mgnB.getGrid());
        assertThat(mgnA.getNode().getConnections()).isEmpty();
        assertThat(mgnB.getNode().getConnections()).isEmpty();

        // Both networks are completely unaffected and keep enforcing their own, distinct ACLs correctly - the
        // owners are NOT denied (unlike the old CONFLICT design), because there never was a conflict to deny.
        var stillSecurityA = securityOf(mgnA, registryA);
        var stillSecurityB = securityOf(mgnB, registryB);
        assertThat(stillSecurityA.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(stillSecurityA.getDomainId()).contains(domainIdA);
        assertThat(stillSecurityB.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(stillSecurityB.getDomainId()).contains(domainIdB);
        assertThat(stillSecurityA.hasPermission(playerSource(ownerA), SecurityPermissions.EXTRACT)).isTrue();
        assertThat(stillSecurityB.hasPermission(playerSource(ownerB), SecurityPermissions.EXTRACT)).isTrue();
        // Owner A has no special access to network B at all, and vice versa - not even denied-with-a-record-of-
        // trying, simply never touched.
        assertThat(stillSecurityB.hasPermission(playerSource(ownerA), SecurityPermissions.EXTRACT)).isFalse();
        assertThat(stillSecurityA.hasPermission(playerSource(ownerB), SecurityPermissions.EXTRACT)).isFalse();
    }

    @Test
    void rejectedCrossDomainConnectionLeaksNoStorageVisibilityAcrossTheTwoNetworks() {
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();

        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        securityOf(mgnA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(mgnB, registryB).bootstrapDomain(UUID.randomUUID());

        GridConnection.create(mgnA.getNode(), mgnB.getNode(), null);

        // Since the two grids were never merged, IGrid.getPivot()/getNodes() for one can never enumerate the
        // other's nodes - there is no shared IGrid instance through which a storage/crafting service on one side
        // could ever iterate the other side's nodes. This is a structural guarantee (different Grid objects), not
        // a behavioral one that a future bug could quietly regress without also breaking grid identity itself.
        var nodesInA = new java.util.ArrayList<IGridNode>();
        mgnA.getGrid().getNodes().forEach(nodesInA::add);
        assertThat(nodesInA).containsExactly(mgnA.getNode());
        assertThat(nodesInA).doesNotContain(mgnB.getNode());

        var nodesInB = new java.util.ArrayList<IGridNode>();
        mgnB.getGrid().getNodes().forEach(nodesInB::add);
        assertThat(nodesInB).containsExactly(mgnB.getNode());
        assertThat(nodesInB).doesNotContain(mgnA.getNode());
    }

    @Test
    void repeatedConnectionAttemptsBetweenTwoConflictingDomainsAreConsistentlyRejectedEveryTime() {
        // Mirrors what actually happens in the world: InWorldGridNode.findInWorldConnections() re-evaluates every
        // neighbor on every relevant world update, so a player leaving two differently-secured networks physically
        // touching must see the rejection hold up on every retry, not just the first attempt.
        var registryA = SecurityDomainRegistry.createForTesting();
        var registryB = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        securityOf(mgnA, registryA).bootstrapDomain(UUID.randomUUID());
        securityOf(mgnB, registryB).bootstrapDomain(UUID.randomUUID());

        for (int i = 0; i < 3; i++) {
            assertThat(GridConnection.create(mgnA.getNode(), mgnB.getNode(), null)).isNull();
        }
        assertThat(mgnA.getGrid()).isNotSameAs(mgnB.getGrid());
    }

    @Test
    void sameDomainReconnectionIsNotRejectedOnlyGenuinelyDifferentDomainsAre() {
        // Control test: the rejection is specific to *different* domains, not to "any two already-secured grids".
        var registry = SecurityDomainRegistry.createForTesting();
        var mgnA = createAndInitNode();
        var mgnB = createAndInitNode();
        var domainId = securityOf(mgnA, registry).bootstrapDomain(UUID.randomUUID());
        // Bootstrapping B independently first, then rebinding it to A's domain by hand is awkward with this
        // harness; instead, connect a brand-new (LEGACY) node C to the already-SECURED A, which must succeed and
        // must make C inherit A's domain - proving the guard only blocks genuine domain *mismatches*.
        var mgnC = createAndInitNode();
        var connection = GridConnection.create(mgnA.getNode(), mgnC.getNode(), null);
        assertThat(connection).isNotNull();
        assertThat(mgnA.getGrid()).isSameAs(mgnC.getGrid());
        var merged = securityOf(mgnA, registry);
        assertThat(merged.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(merged.getDomainId()).contains(domainId);
    }

    // ---------------------------------------------------------------------
    // CONFLICT (defense-in-depth backstop)
    // ---------------------------------------------------------------------
    // With the pre-merge rejection above in place, two differently-secured networks can no longer become one Grid
    // through any of GridConnection.create()'s six call sites (in-world neighbor connections, P2P tunnels, the
    // toggle bus, quantum network bridges, and same-host part wiring). SecurityService's own CONFLICT detection
    // (counting distinct domain ids actually bound to the nodes currently in *its* grid) is kept as a second,
    // independent layer of defense - exercised here directly at the grid-service level, bypassing
    // GridConnection.create() entirely, to prove that IF two differently-bound nodes were ever still added to the
    // same grid through some path this session didn't find or a future regression reopens, the service itself
    // still fails closed rather than silently granting cross-domain access.

    @Test
    void securityServiceStillFailsClosedIfTwoDifferentDomainsEndUpBoundToTheSameGridDirectly() {
        var registry = SecurityDomainRegistry.createForTesting();
        var mgn = createAndInitNode();
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(registry);

        var domainA = appeng.api.networking.security.SecurityDomainId.create();
        var domainB = appeng.api.networking.security.SecurityDomainId.create();

        // Bypass GridConnection entirely and drive the IGridServiceProvider contract directly, simulating two
        // differently-bound nodes somehow ending up added to the very same SecurityService instance.
        var fakeNodeA = mock(IGridNode.class);
        var fakeNodeB = mock(IGridNode.class);
        var tagA = new CompoundTag();
        tagA.store("aeSecDomain", appeng.api.networking.security.SecurityDomainId.CODEC, domainA);
        var tagB = new CompoundTag();
        tagB.store("aeSecDomain", appeng.api.networking.security.SecurityDomainId.CODEC, domainB);

        security.addNode(fakeNodeA, tagA);
        security.addNode(fakeNodeB, tagB);

        assertThat(security.getState()).isEqualTo(SecurityDomainState.CONFLICT);
        assertThat(security.hasPermission(playerSource(UUID.randomUUID()), SecurityPermissions.EXTRACT)).isFalse();
        assertThat(security.hasPermission(IActionSource.empty(), SecurityPermissions.EXTRACT))
                .as("Round 27: an actorless source no longer short-circuits ahead of the CONFLICT check, so a "
                        + "conflicted grid now fails closed for it too - strictly safer than before, where the "
                        + "trusted-internal branch ran first and returned true even here")
                .isFalse();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Round 32: the device registry is installed alongside the domain registry, because a device principal is now
     * resolved through it rather than from whatever the machine says about itself.
     */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    private SecurityService securityOf(ManagedGridNode mgn, SecurityDomainRegistry registry) {
        var security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
        security.setRegistryForTesting(registry);
        security.setDeviceRegistryForTesting(deviceRegistry);
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

    private IActionSource machineSource(IActionHost host) {
        return IActionSource.ofMachine(host);
    }

    /**
     * Builds the source of a <em>real</em> device: an object that owns an actual grid node, which is the only shape
     * {@code SecurityService#resolveDeviceId} accepts since Round 27. Before Round 27 a bare {@code new TestDevice(id)}
     * was enough, which is exactly the forgery FINDING-3 described.
     */
    /**
     * A device the way one really exists: placed somewhere AE2 can locate, with its identity held by the registry.
     * Before Round 32 this could be a bare object reporting an id, which is exactly the assertion the security model
     * stopped accepting.
     */
    private IActionSource deviceSource(UUID deviceId) {
        return RegisteredTestDevice.source(deviceRegistry, level, deviceId);
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

    /**
     * A device: the <em>owner</em> of a grid node, mirroring {@code GridHelper.createManagedNode(this, ...)}. It
     * deliberately no longer implements {@link IActionHost} - since Round 27 the identity is read from the node's
     * owner, never from the object handed to {@code ofMachine(...)}, so implementing the host side here would prove
     * nothing.
     */
    private static final class TestDevice implements IDeviceIdentity {
        private final UUID id;

        private TestDevice(UUID id) {
            this.id = id;
        }

        @Override
        public UUID getDeviceId() {
            return id;
        }
    }
}
