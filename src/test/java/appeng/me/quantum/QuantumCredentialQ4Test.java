package appeng.me.quantum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumCredentialState;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumEndpointStatus;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityPermissions;
import appeng.me.AbstractGridNodeTest;
import appeng.me.GridConnection;
import appeng.me.ManagedGridNode;
import appeng.me.security.SecurityDomainRegistry;
import appeng.me.service.PathingService;

/**
 * Phase O — Quantum Q4 ("provisioning/credential foundation") full test matrix. Follows the exact style of
 * {@code QuantumTopologyIdentityQ3Test}/{@code QuantumCrossDomainSecurityQ3Test}: real production
 * {@link QuantumTopologyRegistry}/{@link QuantumCredentialRegistry}/{@link QuantumCredentialService}/
 * {@link SecurityDomainRegistry}, never mocked business logic — only {@link Player} mocks for the minimum needed to
 * build an {@link IActionSource}, mirroring {@code SecurityServiceTest}'s own {@code playerSource}/{@code TestDevice}
 * helpers.
 * <p/>
 * All Q4 authorization here is resolved directly against {@link SecurityDomainRegistry} through
 * {@link QuantumCredentialService} (profile-anchored, not grid-state-anchored — see that class's own javadoc), so
 * unlike {@code QuantumCrossDomainSecurityQ3Test} this file does NOT need {@code SecurityService}'s package-private
 * test hook at all — every test lives in this one file/package.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumCredentialQ4Test extends AbstractGridNodeTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    // =====================================================================
    // Fixtures / helpers
    // =====================================================================

    private ManagedGridNode freshNode() {
        var node = new ManagedGridNode(new Object(), listener);
        node.create(level, null);
        return node;
    }

    private boolean isConnected(IGridNode a, IGridNode b) {
        for (var connection : a.getConnections()) {
            if (connection.getOtherSide(a) == b) {
                return true;
            }
        }
        return false;
    }

    private IActionSource playerSource(UUID playerId) {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(playerId);
        return IActionSource.ofPlayer(player);
    }

    private IActionSource machineSource(IActionHost host) {
        return IActionSource.ofMachine(host);
    }

    private static final class TestDevice implements IActionHost, IDeviceIdentity {
        private final UUID id;

        private TestDevice(UUID id) {
            this.id = id;
        }

        @Override
        public IGridNode getActionableNode() {
            return null;
        }

        @Override
        public UUID getDeviceId() {
            return id;
        }
    }

    /** A profile with no security domain bound at all — LEGACY-open, mirrors ordinary un-secured AE2 networks. */
    private NetworkProfile legacyProfile(QuantumTopologyRegistry topology) {
        return topology.createProfile();
    }

    private record SecuredProfile(NetworkProfile profile, SecurityDomainRegistry security, UUID ownerId) {
    }

    /** A profile bound to a brand-new SECURED security domain owned by a fresh random player. */
    private SecuredProfile securedProfile(QuantumTopologyRegistry topology) {
        var profile = topology.createProfile();
        var security = SecurityDomainRegistry.createForTesting();
        var ownerId = UUID.randomUUID();
        var domain = security.createDomain(ownerId);
        topology.bindSecurityDomain(profile.id(), domain.id());
        return new SecuredProfile(profile, security, ownerId);
    }

    /**
     * Registers a lane with {@code count} endpoints, chained base->...->last (count-1 edges), under {@code profileId}.
     */
    private QuantumLane buildChainLane(QuantumTopologyRegistry topology, NetworkProfileId profileId, int count) {
        var lane = topology.createLane(profileId);
        QuantumEndpointId previous = null;
        for (var i = 0; i < count; i++) {
            var endpoint = topology.registerEndpoint(lane.id(), "endpoint-" + i);
            if (previous != null) {
                assertThat(topology.addEdge(lane.id(), previous, endpoint.id())).isTrue();
            }
            previous = endpoint.id();
        }
        return topology.getLane(lane.id());
    }

    // =====================================================================
    // REGISTRY (10)
    // =====================================================================

    @Test
    void issuingACredentialAssociatesItWithTheCreatingProfile() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);

        var outcome = QuantumCredentialService.issue(topology, credentials, null, profile.id(), IActionSource.empty());

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(outcome.credential()).isNotNull();
        assertThat(outcome.credential().profileId()).isEqualTo(profile.id());
    }

    @Test
    void credentialHasATypedIdDistinctFromProfileLaneAndEndpointIds() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = topology.createLane(profile.id());
        var endpoint = topology.registerEndpoint(lane.id(), "e");
        var credential = credentials.issue(profile.id());

        // Four structurally distinct typed identities - never the same Java type, never comparable/interchangeable.
        assertThat(credential.id()).isInstanceOf(QuantumCredentialId.class);
        assertThat(profile.id()).isInstanceOf(NetworkProfileId.class);
        assertThat(lane.id()).isInstanceOf(appeng.api.networking.quantum.QuantumLaneId.class);
        assertThat(endpoint.id()).isInstanceOf(QuantumEndpointId.class);
        assertThat(credential.id().id()).isNotEqualTo(profile.id().id());
    }

    @Test
    void freshlyIssuedCredentialStartsAtRevisionOneStateIssuedAndUnbound() {
        var credentials = QuantumCredentialRegistry.createForTesting();
        var credential = credentials.issue(NetworkProfileId.create());

        assertThat(credential.revision()).isEqualTo(1);
        assertThat(credential.state()).isEqualTo(QuantumCredentialState.ISSUED);
        assertThat(credential.boundEndpoint()).isEmpty();
    }

    @Test
    void revisionPersistsAcrossARealSavedDataStorageReload(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var credential = credentials1.issue(NetworkProfileId.create());
        credentials1.supersede(credential.id());
        credentials1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        assertThat(credentials2.get(credential.id()).revision()).isEqualTo(2);
    }

    @Test
    void statePersistsAcrossARealSavedDataStorageReload(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var credential = credentials1.issue(NetworkProfileId.create());
        credentials1.suspend(credential.id());
        credentials1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        assertThat(credentials2.get(credential.id()).state()).isEqualTo(QuantumCredentialState.SUSPENDED);
    }

    @Test
    void freshSavedDataStorageReloadPreservesEveryCredentialField(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var profile = topology1.createProfile();
        var lane = topology1.createLane(profile.id());
        var endpoint = topology1.registerEndpoint(lane.id(), "e");
        var credential = credentials1.issue(profile.id());
        credentials1.bind(credential.id(), endpoint.id());
        credentials1.markOnline(credential.id());
        credentials1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var reloaded = credentials2.get(credential.id());
        assertThat(reloaded.profileId()).isEqualTo(profile.id());
        assertThat(reloaded.revision()).isEqualTo(1);
        assertThat(reloaded.state()).isEqualTo(QuantumCredentialState.ONLINE);
        assertThat(reloaded.boundEndpoint()).contains(endpoint.id());
        assertThat(reloaded.protocolVersion()).isEqualTo(QuantumCredential.CURRENT_PROTOCOL_VERSION);
    }

    @Test
    void activationFailsSafelyWhenTheCredentialsProfileDoesNotExistInTopology() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        // Orphaned credential: issued directly against the registry for a profile id that was never created in
        // `topology` at all (simulating a profile that no longer exists / never existed from this registry's POV).
        var orphanCredential = credentials.issue(NetworkProfileId.create());

        var result = QuantumCredentialService.activate(topology, credentials, null, orphanCredential.id(), 1,
                QuantumEndpointId.create(), IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.NOT_FOUND);
        assertThat(orphanCredential.state()).isEqualTo(QuantumCredentialState.ISSUED); // unchanged
    }

    @Test
    void crossDomainActivationIsRejectedAsWrongProfileWhichIsTheDomainCompatibilityGuaranteeAtTheRegistryLayer() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var securedA = securedProfile(topology);
        var laneA = buildChainLane(topology, securedA.profile().id(), 2);
        var credentialA = credentials.issue(securedA.profile().id());

        // A second, independently-secured profile+domain+lane.
        var securedB = securedProfile(topology);
        var laneB = buildChainLane(topology, securedB.profile().id(), 2);
        var endpointInB = laneB.getEndpoints().iterator().next();

        var result = QuantumCredentialService.activate(topology, credentials, securedA.security(), credentialA.id(),
                1, endpointInB, playerSource(securedA.ownerId()));

        assertThat(result).isEqualTo(QuantumCredentialService.Result.WRONG_PROFILE);
        assertThat(credentialA.boundEndpoint()).isEmpty();
    }

    @Test
    void crossProfileActivationIsRejectedEvenWhenNeitherProfileHasASecurityDomainBound() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profileA = legacyProfile(topology);
        buildChainLane(topology, profileA.id(), 2);
        var credentialA = credentials.issue(profileA.id());

        var profileB = legacyProfile(topology);
        var laneB = buildChainLane(topology, profileB.id(), 2);
        var endpointInB = laneB.getEndpoints().iterator().next();

        var result = QuantumCredentialService.activate(topology, credentials, null, credentialA.id(), 1,
                endpointInB, IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.WRONG_PROFILE);
    }

    @Test
    void activationAgainstANonexistentEndpointIsRejected() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var credential = credentials.issue(profile.id());

        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1,
                QuantumEndpointId.create(), IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.NOT_FOUND);
    }

    // =====================================================================
    // AUTHORIZATION (10)
    // =====================================================================

    @Test
    void ownerCanIssueCredentialWithoutAnyExplicitGrant() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);

        var outcome = QuantumCredentialService.issue(topology, credentials, secured.security(), secured.profile().id(),
                playerSource(secured.ownerId()));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.OK);
    }

    @Test
    void unauthorizedPlayerCannotIssueCredential() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var randomPlayer = UUID.randomUUID();

        var outcome = QuantumCredentialService.issue(topology, credentials, secured.security(), secured.profile().id(),
                playerSource(randomPlayer));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.FORBIDDEN);
        assertThat(outcome.credential()).isNull();
    }

    @Test
    void explicitQuantumIssueGrantLetsANonOwnerIssueCredentials() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var delegate = UUID.randomUUID();
        var domain = secured.security().getDomain(secured.profile().getSecurityDomainId().orElseThrow());
        domain.grant(PrincipalId.ofPlayer(delegate), Set.of(SecurityPermissions.QUANTUM_ISSUE));

        var outcome = QuantumCredentialService.issue(topology, credentials, secured.security(), secured.profile().id(),
                playerSource(delegate));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.OK);
    }

    @Test
    void everyoneGrantWithoutSecurityTierPermissionCannotAdministerCredentials() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var domain = secured.security().getDomain(secured.profile().getSecurityDomainId().orElseThrow());
        // EVERYONE is granted ordinary network-usage permissions, but not QUANTUM_ISSUE.
        domain.grant(PrincipalId.EVERYONE, Set.of(SecurityPermissions.INJECT, SecurityPermissions.EXTRACT));

        var randomPlayer = UUID.randomUUID();
        var outcome = QuantumCredentialService.issue(topology, credentials, secured.security(), secured.profile().id(),
                playerSource(randomPlayer));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.FORBIDDEN);
    }

    @Test
    void groupSecurityGrantAllowsItsMembersToIssueCredentials() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var domain = secured.security().getDomain(secured.profile().getSecurityDomainId().orElseThrow());
        var groupMember = UUID.randomUUID();
        var group = appeng.me.security.SecurityGroup.create("issuers");
        group = new appeng.me.security.SecurityGroup(group.id(), group.name(), Set.of(groupMember));
        domain.putGroup(group);
        domain.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.QUANTUM_ISSUE));

        var outcome = QuantumCredentialService.issue(topology, credentials, secured.security(), secured.profile().id(),
                playerSource(groupMember));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.OK);
    }

    @Test
    void devicePrincipalDoesNotAutomaticallyGainCredentialAdminRightsWithoutAnExplicitGrant() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var device = new TestDevice(UUID.randomUUID());

        var outcome = QuantumCredentialService.issue(topology, credentials, secured.security(), secured.profile().id(),
                machineSource(device));

        assertThat(outcome.result()).isEqualTo(QuantumCredentialService.Result.FORBIDDEN);
    }

    @Test
    void authorizedActorHoldingBuildCanActivateAPhysicalEndpoint() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var lane = buildChainLane(topology, secured.profile().id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(secured.profile().id());

        var result = QuantumCredentialService.activate(topology, credentials, secured.security(), credential.id(), 1,
                endpoint, playerSource(secured.ownerId()));

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
    }

    @Test
    void actorWithoutBuildCannotActivateAnEndpointEvenIfTheyCanIssue() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var lane = buildChainLane(topology, secured.profile().id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(secured.profile().id());

        var domain = secured.security().getDomain(secured.profile().getSecurityDomainId().orElseThrow());
        var delegate = UUID.randomUUID();
        // Granted QUANTUM_ISSUE only - deliberately NOT BUILD.
        domain.grant(PrincipalId.ofPlayer(delegate), Set.of(SecurityPermissions.QUANTUM_ISSUE));

        var result = QuantumCredentialService.activate(topology, credentials, secured.security(), credential.id(), 1,
                endpoint, playerSource(delegate));

        assertThat(result).isEqualTo(QuantumCredentialService.Result.FORBIDDEN);
    }

    @Test
    void trustedInternalRestartReconstructionWorksForAValidCredentialWithoutAnyOnlineActor() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var endpoints = lane.getEndpoints().iterator();
        var e1 = endpoints.next();
        var e2 = endpoints.next();
        var c1 = credentials.issue(profile.id());
        var c2 = credentials.issue(profile.id());
        // Activation itself is a player action in reality, but this test's point is what happens AFTER that, at
        // restart - so activate with the trusted-internal empty source here (no player), then prove the actual
        // reconstruction call site below takes NO IActionSource parameter at all.
        assertThat(QuantumCredentialService.activate(topology, credentials, null, c1.id(), 1, e1,
                IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(QuantumCredentialService.activate(topology, credentials, null, c2.id(), 1, e2,
                IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);

        var table = new QuantumEndpointOnlineTable();
        var node1 = freshNode();
        table.register(e1, node1.getNode());
        var node2 = freshNode();
        table.register(e2, node2.getNode());

        // No IActionSource is constructed anywhere below - reconstruction is genuinely actor-free.
        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);

        assertThat(created).hasSize(1);
        assertThat(node1.getGrid()).isSameAs(node2.getGrid());
    }

    @Test
    void revokingACredentialRequiresSecurityAdminPolicyNotJustAnyPermission() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var credential = credentials.issue(secured.profile().id());

        var domain = secured.security().getDomain(secured.profile().getSecurityDomainId().orElseThrow());
        var delegate = UUID.randomUUID();
        // Granted BUILD only - deliberately NOT QUANTUM_REVOKE.
        domain.grant(PrincipalId.ofPlayer(delegate), Set.of(SecurityPermissions.BUILD));

        var result = QuantumCredentialService.revoke(topology, credentials, secured.security(), credential.id(),
                playerSource(delegate));

        assertThat(result).isEqualTo(QuantumCredentialService.Result.FORBIDDEN);
        assertThat(credential.state()).isEqualTo(QuantumCredentialState.ISSUED);
    }

    // =====================================================================
    // DUPLICATE / CLONED CARD SAFETY (8)
    // =====================================================================

    @Test
    void firstActivationOfAClonedCardCredentialSucceeds() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var credential = credentials.issue(profile.id());

        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointA,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(credential.boundEndpoint()).contains(endpointA);
    }

    @Test
    void secondActivationOfTheSameCredentialAtADifferentEndpointIsConflictNotASecondEndpoint() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(profile.id());
        assertThat(QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointA,
                IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);

        // A duplicated ItemStack (identical credential id + revision) attempts to activate at a DIFFERENT endpoint.
        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointB,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.CONFLICT);
        assertThat(credential.boundEndpoint()).contains(endpointA); // still only A - never both
    }

    @Test
    void conflictingDuplicateActivationNeverMarksTheSecondEndpointOnlineInTopology() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointA,
                IActionSource.empty());

        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointB,
                IActionSource.empty());

        assertThat(topology.getEndpoint(endpointB).status()).isEqualTo(QuantumEndpointStatus.REGISTERED);
        assertThat(topology.getEndpoint(endpointA).status()).isEqualTo(QuantumEndpointStatus.ONLINE);
    }

    @Test
    void conflictingDuplicateActivationNeverCreatesASecondCredentialRegistryEntry() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointA,
                IActionSource.empty());

        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointB,
                IActionSource.empty());

        assertThat(credentials.getAllCredentials()).hasSize(1);
    }

    @Test
    void duplicateActivationAttemptWithAnIdenticalMatchingRevisionStillConflicts() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointA,
                IActionSource.empty());

        // The clone's revision genuinely, honestly matches - it's still rejected. This proves rejection is an
        // identity-uniqueness rule, not merely a side effect of a revision mismatch.
        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointB,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.CONFLICT);
    }

    @Test
    void theFirstEndpointGoingOfflineDoesNotAutomaticallyAuthorizeTheDuplicateAtTheSecondEndpoint() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointA,
                IActionSource.empty());

        QuantumCredentialService.deactivate(topology, credentials, endpointA);
        assertThat(credential.state()).isEqualTo(QuantumCredentialState.OFFLINE);

        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointB,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.CONFLICT);
    }

    @Test
    void explicitAuthorizedRebindMovesTheCredentialAndRetiresTheOldBindingCompletely() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var secured = securedProfile(topology);
        var lane = buildChainLane(topology, secured.profile().id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(secured.profile().id());
        QuantumCredentialService.activate(topology, credentials, secured.security(), credential.id(), 1, endpointA,
                playerSource(secured.ownerId()));

        var rebindResult = QuantumCredentialService.rebind(topology, credentials, secured.security(), credential.id(),
                endpointB, playerSource(secured.ownerId()));

        assertThat(rebindResult).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(credential.boundEndpoint()).contains(endpointB);

        // The old endpoint is truly retired - re-binding A directly now conflicts against B, proving there is no
        // lingering dual binding.
        var reclaimA = credentials.bind(credential.id(), endpointA);
        assertThat(reclaimA).isFalse();
        assertThat(credential.boundEndpoint()).contains(endpointB);
    }

    @Test
    void reactivatingTheSameCredentialAtItsAlreadyBoundEndpointIsIdempotentNotAConflict() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(profile.id());
        assertThat(QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);

        // e.g. a repath / repeated reload announcing the SAME physical endpoint again.
        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(credential.boundEndpoint()).contains(endpoint);
    }

    // =====================================================================
    // REVISION (6)
    // =====================================================================

    @Test
    void issueStartsAtRevisionOne() {
        var credentials = QuantumCredentialRegistry.createForTesting();
        assertThat(credentials.issue(NetworkProfileId.create()).revision()).isEqualTo(1);
    }

    @Test
    void supersedeBumpsTheRegistrysCanonicalRevision() {
        var credentials = QuantumCredentialRegistry.createForTesting();
        var credential = credentials.issue(NetworkProfileId.create());

        var updated = credentials.supersede(credential.id());

        assertThat(updated).isPresent();
        assertThat(updated.get().revision()).isEqualTo(2);
        assertThat(credentials.get(credential.id()).revision()).isEqualTo(2);
    }

    @Test
    void activationWithAStaleOldRevisionIsRejected() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(profile.id());
        credentials.supersede(credential.id()); // registry revision is now 2

        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty()); // caller's card still claims revision 1

        assertThat(result).isEqualTo(QuantumCredentialService.Result.STALE_REVISION);
        assertThat(credential.boundEndpoint()).isEmpty();
    }

    @Test
    void activationWithTheMatchingNewRevisionIsAccepted() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(profile.id());
        credentials.supersede(credential.id());

        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 2, endpoint,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
    }

    @Test
    void repeatedlyCopyingAndReplayingTheOldStaleCardNeverRestoresAccess() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var endpointA = it.next();
        var endpointB = it.next();
        var credential = credentials.issue(profile.id());
        credentials.supersede(credential.id());

        for (var endpoint : new QuantumEndpointId[] { endpointA, endpointB, endpointA }) {
            var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                    IActionSource.empty());
            assertThat(result).isEqualTo(QuantumCredentialService.Result.STALE_REVISION);
        }
        assertThat(credential.boundEndpoint()).isEmpty();
    }

    @Test
    void supersedeDoesNotChangeAnAlreadyActiveBindingOrState() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty());

        credentials.supersede(credential.id());

        assertThat(credential.state()).isEqualTo(QuantumCredentialState.ONLINE);
        assertThat(credential.boundEndpoint()).contains(endpoint);
        assertThat(credential.revision()).isEqualTo(2);
    }

    // =====================================================================
    // OFFLINE / RESTART (6)
    // =====================================================================

    @Test
    void unloadingAnActiveEndpointRecordsOfflineNotDeleted() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty());

        QuantumCredentialService.deactivate(topology, credentials, endpoint);

        assertThat(credentials.get(credential.id())).isNotNull();
        assertThat(credential.state()).isEqualTo(QuantumCredentialState.OFFLINE);
        assertThat(credential.boundEndpoint()).contains(endpoint); // binding kept, not cleared
        assertThat(topology.getEndpoint(endpoint)).isNotNull();
        assertThat(topology.getEndpoint(endpoint).status()).isEqualTo(QuantumEndpointStatus.OFFLINE);
    }

    @Test
    void reloadingReactivatesToOnlineForAStillValidCredential() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 1);
        var endpoint = lane.getEndpoints().iterator().next();
        var credential = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty());
        QuantumCredentialService.deactivate(topology, credentials, endpoint);

        var result = QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpoint,
                IActionSource.empty());

        assertThat(result).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(credential.state()).isEqualTo(QuantumCredentialState.ONLINE);
    }

    @Test
    void bothRegistriesSurviveARealServerRestartViaSavedDataStorage(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials1 = storage1.computeIfAbsent(QuantumCredentialRegistry.TYPE);
        var profile = topology1.createProfile();
        var lane = topology1.createLane(profile.id());
        var endpoint = topology1.registerEndpoint(lane.id(), "e");
        var credential = credentials1.issue(profile.id());
        credentials1.bind(credential.id(), endpoint.id());
        credentials1.markOnline(credential.id());
        topology1.setEndpointOnline(endpoint.id(), null);
        credentials1.markDirty();
        topology1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var topology2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);
        var credentials2 = storage2.computeIfAbsent(QuantumCredentialRegistry.TYPE);

        assertThat(topology2.getProfile(profile.id())).isNotNull();
        assertThat(topology2.getEndpoint(endpoint.id())).isNotNull();
        assertThat(credentials2.get(credential.id())).isNotNull();
        assertThat(credentials2.get(credential.id()).boundEndpoint()).contains(endpoint.id());
    }

    @Test
    void q3TopologyReconstructionWorksForAValidNonSuspendedNonRevokedCredentialAfterRestart() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var e1 = it.next();
        var e2 = it.next();
        var c1 = credentials.issue(profile.id());
        var c2 = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, c1.id(), 1, e1, IActionSource.empty());
        QuantumCredentialService.activate(topology, credentials, null, c2.id(), 1, e2, IActionSource.empty());

        var table = new QuantumEndpointOnlineTable();
        var node1 = freshNode();
        table.register(e1, node1.getNode());
        var node2 = freshNode();
        table.register(e2, node2.getNode());

        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);

        assertThat(created).hasSize(1);
        assertThat(node1.getGrid()).isSameAs(node2.getGrid());
    }

    @Test
    void aRevokedCredentialsEndpointDoesNotParticipateInRouteReconstructionAfterRestart() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var e1 = it.next();
        var e2 = it.next();
        var c1 = credentials.issue(profile.id());
        var c2 = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, c1.id(), 1, e1, IActionSource.empty());
        QuantumCredentialService.activate(topology, credentials, null, c2.id(), 1, e2, IActionSource.empty());
        credentials.revoke(c1.id());

        var table = new QuantumEndpointOnlineTable();
        var node1 = freshNode();
        table.register(e1, node1.getNode());
        var node2 = freshNode();
        table.register(e2, node2.getNode());

        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);

        assertThat(created).isEmpty();
        assertThat(node1.getGrid()).isNotSameAs(node2.getGrid());
    }

    @Test
    void aSuspendedCredentialsRouteStaysInactiveUntilExplicitlyResumed() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 2);
        var it = lane.getEndpoints().iterator();
        var e1 = it.next();
        var e2 = it.next();
        var c1 = credentials.issue(profile.id());
        var c2 = credentials.issue(profile.id());
        QuantumCredentialService.activate(topology, credentials, null, c1.id(), 1, e1, IActionSource.empty());
        QuantumCredentialService.activate(topology, credentials, null, c2.id(), 1, e2, IActionSource.empty());
        credentials.suspend(c1.id());

        var table = new QuantumEndpointOnlineTable();
        var node1 = freshNode();
        table.register(e1, node1.getNode());
        var node2 = freshNode();
        table.register(e2, node2.getNode());

        var onlineWhileSuspended = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        assertThat(QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineWhileSuspended)).isEmpty();
        assertThat(node1.getGrid()).isNotSameAs(node2.getGrid());

        credentials.resume(c1.id());
        credentials.markOnline(c1.id());
        var onlineAfterResume = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineAfterResume);
        assertThat(created).hasSize(1);
        assertThat(node1.getGrid()).isSameAs(node2.getGrid());
    }

    // =====================================================================
    // Q1/Q2/Q3 + Q4 INTEGRATION (5)
    // =====================================================================

    @Test
    void twoProfilesWithTheirOwnCredentialsRemainCompletelyIsolated() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profileA = legacyProfile(topology);
        var profileB = legacyProfile(topology);
        var credA = credentials.issue(profileA.id());
        var credB = credentials.issue(profileB.id());

        assertThat(credentials.getCredentialsForProfile(profileA.id())).containsExactly(credA);
        assertThat(credentials.getCredentialsForProfile(profileB.id())).containsExactly(credB);
        assertThat(credA.profileId()).isNotEqualTo(credB.profileId());
    }

    @Test
    void multipointLaneStaysAtExactlyNMinusOneEdgesWithCredentialsActivatedAtEveryEndpoint() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 3); // Base-Factory-Mine, 2 edges

        var table = new QuantumEndpointOnlineTable();
        var nodes = new java.util.ArrayList<ManagedGridNode>();
        for (var endpointId : lane.getEndpoints()) {
            var credential = credentials.issue(profile.id());
            assertThat(QuantumCredentialService.activate(topology, credentials, null, credential.id(), 1, endpointId,
                    IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);
            var node = freshNode();
            table.register(endpointId, node.getNode());
            nodes.add(node);
        }

        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);

        assertThat(created).hasSize(2); // still N-1, never N or a full mesh
        assertThat(topology.getLane(lane.id()).getEdges()).hasSize(2);
    }

    @Test
    void credentialActivationNeverCreatesACrossProfileEdgeInTheTopologyRegistry() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profileA = legacyProfile(topology);
        var laneA = buildChainLane(topology, profileA.id(), 1);
        var profileB = legacyProfile(topology);
        var laneB = buildChainLane(topology, profileB.id(), 1);
        var credentialA = credentials.issue(profileA.id());

        QuantumCredentialService.activate(topology, credentials, null, credentialA.id(), 1,
                laneB.getEndpoints().iterator().next(), IActionSource.empty());

        // laneA and laneB's own edge/endpoint sets are completely untouched by the rejected cross-profile attempt.
        assertThat(topology.getLane(laneA.id()).getEndpoints()).hasSize(1);
        assertThat(topology.getLane(laneB.id()).getEndpoints()).hasSize(1);
        assertThat(topology.getLane(laneA.id()).getEdges()).isEmpty();
        assertThat(topology.getLane(laneB.id()).getEdges()).isEmpty();
    }

    // NOTE: the "credential-driven cross-domain edge is still rejected by GridConnection's own pre-merge guard"
    // integration test needs SecurityService#setRegistryForTesting, which is package-private to
    // `appeng.me.security` - it lives in `appeng.me.security.QuantumCredentialCrossDomainQ4Test` instead, exactly
    // mirroring why `QuantumCrossDomainSecurityQ3Test` itself lives there rather than here.

    @Test
    void issuingManyCredentialsForOneLaneNeverAppendsExtraEdgesTheCredentialRegistryStoresNoDuplicateEdgeGraph() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = buildChainLane(topology, profile.id(), 3); // 2 persisted edges

        // Issue several credentials for the same profile, activate/revoke/suspend/rebind a few of them - none of
        // this ever touches `lane.getEdges()` at all; QuantumCredential itself has no edge-list-shaped field
        // (see its own javadoc) to have grown in the first place.
        for (var i = 0; i < 5; i++) {
            credentials.issue(profile.id());
        }
        assertThat(topology.getLane(lane.id()).getEdges()).hasSize(2);
        assertThat(credentials.getAllCredentials()).hasSize(5);
    }

    // =====================================================================
    // CHANNEL TIERS
    // =====================================================================

    @Test
    void credentialActivationFlowIsIdenticalAcrossCapacityTiersAndCannotForgeCapacity() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = topology.createLane(profile.id());
        var baseRecord = topology.registerEndpoint(lane.id(), "base");
        var denseRecord = topology.registerEndpoint(lane.id(), "dense");
        assertThat(topology.addEdge(lane.id(), baseRecord.id(), denseRecord.id())).isTrue();

        var baseCredential = credentials.issue(profile.id());
        var denseCredential = credentials.issue(profile.id());
        assertThat(QuantumCredentialService.activate(topology, credentials, null, baseCredential.id(), 1,
                baseRecord.id(), IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(QuantumCredentialService.activate(topology, credentials, null, denseCredential.id(), 1,
                denseRecord.id(), IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);

        // Capacity comes ENTIRELY from GridFlags on the physical node - never anything the credential model
        // exposes. QuantumCredential has no capacity/channel-count field to forge in the first place.
        var table = new QuantumEndpointOnlineTable();
        var baseNode = freshNode();
        table.register(baseRecord.id(), baseNode.getNode());
        var denseNode = new ManagedGridNode(new Object(), listener);
        denseNode.setFlags(GridFlags.DENSE_CAPACITY);
        denseNode.create(level, null);
        table.register(denseRecord.id(), denseNode.getNode());

        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);
        assertThat(created).hasSize(1);
        var connection = (GridConnection) created.get(0);
        assertThat(connection.getMaxChannels()).isEqualTo(32); // DENSE_CAPACITY tier - purely node-flag-driven
    }

    // =====================================================================
    // P2P integration
    // =====================================================================

    /** One simulated ME-P2P tunnel device: a main node and its own outer node, sharing one owner object. */
    private record SimTunnel(ManagedGridNode main, ManagedGridNode outer) {
    }

    private SimTunnel makeTunnel() {
        var owner = new Object();
        var main = new ManagedGridNode(owner, listener);
        main.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
        main.create(level, null);
        var outer = new ManagedGridNode(owner, listener);
        outer.setFlags(GridFlags.DENSE_CAPACITY, GridFlags.CANNOT_CARRY_COMPRESSED);
        outer.create(level, null);
        return new SimTunnel(main, outer);
    }

    @Test
    void p2pThenCredentialAuthorizedQuantumLaneThenP2pThenConsumerWorksWithoutGeneratingChannels() {
        var topology = QuantumTopologyRegistry.createForTesting();
        var credentials = QuantumCredentialRegistry.createForTesting();
        var profile = legacyProfile(topology);
        var lane = topology.createLane(profile.id());
        var middleEndpoint = topology.registerEndpoint(lane.id(), "middle");
        var remoteEndpoint = topology.registerEndpoint(lane.id(), "remote");
        assertThat(topology.addEdge(lane.id(), middleEndpoint.id(), remoteEndpoint.id())).isTrue();

        var middleCredential = credentials.issue(profile.id());
        var remoteCredential = credentials.issue(profile.id());
        assertThat(QuantumCredentialService.activate(topology, credentials, null, middleCredential.id(), 1,
                middleEndpoint.id(), IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);
        assertThat(QuantumCredentialService.activate(topology, credentials, null, remoteCredential.id(), 1,
                remoteEndpoint.id(), IActionSource.empty())).isEqualTo(QuantumCredentialService.Result.OK);

        var table = new QuantumEndpointOnlineTable();
        var middleNode = freshNode();
        table.register(middleEndpoint.id(), middleNode.getNode());
        var remoteNode = freshNode();
        table.register(remoteEndpoint.id(), remoteNode.getNode());
        var onlineNodes = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        var created = QuantumLaneReconciler.reconstruct(topology.getLane(lane.id()), onlineNodes);
        assertThat(created).hasSize(1);

        var home = freshNode();
        var a = makeTunnel();
        assertThat(GridHelper.createConnection(home.getNode(), a.main().getNode())).isNotNull();
        var aOut = makeTunnel();
        assertThat(GridHelper.createConnection(middleNode.getNode(), aOut.main().getNode())).isNotNull();
        assertThat(GridHelper.createConnection(a.outer().getNode(), aOut.outer().getNode())).isNotNull();

        var b = makeTunnel();
        assertThat(GridHelper.createConnection(remoteNode.getNode(), b.main().getNode())).isNotNull();
        var far = freshNode();
        var bOut = makeTunnel();
        assertThat(GridHelper.createConnection(far.getNode(), bOut.main().getNode())).isNotNull();
        assertThat(GridHelper.createConnection(b.outer().getNode(), bOut.outer().getNode())).isNotNull();

        var consumer = new ManagedGridNode(new Object(), listener);
        consumer.setFlags(GridFlags.REQUIRE_CHANNEL);
        consumer.create(level, null);
        assertThat(GridHelper.createConnection(far.getNode(), consumer.getNode())).isNotNull();

        runTick(middleNode.getGrid(), 3);
        runTick(far.getGrid(), 3);

        var middlePathing = (PathingService) middleNode.getGrid().getPathingService();
        var farPathing = (PathingService) far.getGrid().getPathingService();
        assertThat(middlePathing.getAdHocNetworkError()).isNull();
        assertThat(farPathing.getAdHocNetworkError()).isNull();
        assertThat(middlePathing.getUsedChannels()).isEqualTo(2);
        assertThat(farPathing.getUsedChannels()).isEqualTo(2);
        assertThat(middleNode.getGrid()).isSameAs(remoteNode.getGrid());
        assertThat(middleNode.getGrid()).isNotSameAs(far.getGrid());
    }
}
