package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainState;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.quantum.QuantumEndpointOnlineTable;
import appeng.me.quantum.QuantumLaneReconciler;
import appeng.me.quantum.QuantumTopologyRegistry;

/**
 * Phase J — Quantum Q3 (part 3 of 3) — the two scenarios that genuinely need {@code appeng.me.security}'s own
 * package-private test hook ({@link SecurityService#setRegistryForTesting}), so they cannot live in
 * {@code appeng.me.quantum.QuantumTopologyIdentityQ3Test} alongside the rest of the Q3 suite (parts 1-2 live in
 * {@code appeng.me.quantum}, exactly mirroring why {@code MultipointSingleLaneQ2Test} itself lives in this package).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumCrossDomainSecurityQ3Test extends AbstractGridNodeTest {

    @BeforeAll
    static void bootstrapEphemeralServer() {
        EphemeralTestServerProvider.grabServer();
    }

    // =====================================================================
    // H. CROSS-PROFILE SAFETY (matrix item 34/35 — 33 is covered by
    // QuantumTopologyIdentityQ3Test#anEndpointAlreadyBoundToOneLaneCannotBeSilentlyAttachedToADifferentLanesEdge)
    // =====================================================================

    /**
     * The registry's own {@code addEdge} guard (proven in {@code QuantumTopologyIdentityQ3Test}) already makes it
     * structurally impossible for two DIFFERENT lanes' endpoints to be wired into one edge. This test proves the
     * separate, deeper claim required by the manifest: even a topologically VALID, single-lane edge — one the registry
     * itself has no reason to reject — must still be refused by {@code GridConnection}'s own, unmodified
     * cross-security-domain pre-merge guard if the two physical nodes backing it already belong to two different
     * already-SECURED real networks (e.g. a data migration/recovery edge case). The registry is the first line
     * (topological validity); {@code GridConnection.securityDomainsAreCompatible()} remains the untouched second line
     * (ACL safety) — {@link QuantumLaneReconciler} does not, and must not, bypass it.
     */
    @Test
    void crossDomainEdgeIsStillRejectedByTheExistingPreMergeGuardAsSecondLineDefense() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile();
        var lane = registry.createLane(profile.id());
        var baseEndpoint = registry.registerEndpoint(lane.id(), "base");
        var mineEndpoint = registry.registerEndpoint(lane.id(), "mine");
        // Valid at the REGISTRY level - both endpoints legitimately belong to the same lane/profile.
        assertThat(registry.addEdge(lane.id(), baseEndpoint.id(), mineEndpoint.id())).isTrue();

        // The two PHYSICAL nodes backing these endpoints already independently belong to two different,
        // already-SECURED real networks. The registry has no idea, and does not need to.
        var securityRegistry = SecurityDomainRegistry.createForTesting();
        var baseNode = new ManagedGridNode(new Object(), listener);
        baseNode.create(level, null);
        var securityOfBase = (SecurityService) baseNode.getGrid().getService(ISecurityService.class);
        securityOfBase.setRegistryForTesting(securityRegistry);
        securityOfBase.bootstrapDomain(UUID.randomUUID());

        var mineNode = new ManagedGridNode(new Object(), listener);
        mineNode.create(level, null);
        var securityOfMine = (SecurityService) mineNode.getGrid().getService(ISecurityService.class);
        securityOfMine.setRegistryForTesting(securityRegistry);
        securityOfMine.bootstrapDomain(UUID.randomUUID());

        assertThat(securityOfBase.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOfMine.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(securityOfBase.getDomainId()).isNotEqualTo(securityOfMine.getDomainId());

        var table = new QuantumEndpointOnlineTable();
        table.register(baseEndpoint.id(), baseNode.getNode());
        table.register(mineEndpoint.id(), mineNode.getNode());

        // The registry says these two SHOULD be connected (a valid, persisted edge) - reconciliation must still
        // come back empty: QuantumLaneReconciler calls the exact same, unmodified GridConnection.create() the rest
        // of AE2 uses, which rejects the connection outright. The registry itself never generates a wrong-domain
        // route.
        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());

        assertThat(created).isEmpty();
        assertThat(baseNode.getGrid()).isNotSameAs(mineNode.getGrid());
        assertThat(baseNode.getNode().getConnections()).isEmpty();
        assertThat(mineNode.getNode().getConnections()).isEmpty();
    }

    // =====================================================================
    // I. LEGACY / UNSECURED (matrix item 36)
    // =====================================================================

    /**
     * Reconstruction alone — with NO explicit {@code registry.bindSecurityDomain} call, and NO
     * {@code SecurityService.bootstrapDomain} call anywhere in this test — must never itself bind or "infect" a
     * security domain. The merged grid must stay genuinely {@link SecurityDomainState#LEGACY} (open,
     * vanilla-AE2-compatible), matching the already-proven SECURED+LEGACY semantics from the Security Foundation work
     * (Round 5A) — never silently upgraded just because the Quantum registry reconstructed a lane for it.
     */
    @Test
    void reconstructingALegacyProfilesLaneNeverInfectsASecurityDomainBinding() {
        var registry = QuantumTopologyRegistry.createForTesting();
        var profile = registry.createProfile(); // never bound to any SecurityDomainId - stays LEGACY
        var lane = registry.createLane(profile.id());
        var base = registry.registerEndpoint(lane.id(), "base");
        var mine = registry.registerEndpoint(lane.id(), "mine");
        assertThat(registry.addEdge(lane.id(), base.id(), mine.id())).isTrue();

        var table = new QuantumEndpointOnlineTable();
        var baseNode = new ManagedGridNode(new Object(), listener);
        baseNode.create(level, null);
        table.register(base.id(), baseNode.getNode());
        var mineNode = new ManagedGridNode(new Object(), listener);
        mineNode.create(level, null);
        table.register(mine.id(), mineNode.getNode());

        var created = QuantumLaneReconciler.reconstruct(lane, table.onlineNodes());
        assertThat(created).hasSize(1);

        var security = (SecurityService) baseNode.getGrid().getService(ISecurityService.class);
        assertThat(security.getState()).isEqualTo(SecurityDomainState.LEGACY);
        assertThat(security.getDomainId()).isEmpty();
        assertThat(profile.getSecurityDomainId()).isEmpty();
    }
}
