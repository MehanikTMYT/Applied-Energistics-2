package appeng.me.quantum;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.quantum.QuantumEndpointStatus;
import appeng.api.networking.security.SecurityDomainId;

/**
 * Phase J — Quantum Q3 (part 1 of 3) — persistent Network Profile / Lane / Endpoint identity.
 * <p/>
 * Exercises {@link QuantumTopologyRegistry} through a REAL {@link SavedDataStorage} backed by an on-disk temp
 * directory, exactly mirroring {@code appeng.me.security.SecurityDomainRegistryPersistenceTest}'s own proof technique
 * for its direct architectural sibling {@code SecurityDomainRegistry}: create/mutate against one
 * {@link SavedDataStorage} instance, flush to disk with {@code saveAndJoin()}, then read back through a brand-new
 * {@link SavedDataStorage} instance pointed at the same directory — a fresh instance has no in-memory cache, so this
 * can only pass if the NBT round-trip through disk is actually correct, mirroring a server restart/chunk reload.
 * <p/>
 * Lives in {@code appeng.me.quantum} specifically to reach {@link QuantumTopologyRegistry#TYPE}, which is
 * package-private for the exact same reason {@code SecurityDomainRegistry#TYPE} is (see that field's own javadoc): so a
 * unit test can drive the real {@code SavedDataType} round-trip without needing a full loaded
 * {@code ServerLevel#OVERWORLD}, which the ephemeral JUnit test server does not have.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class QuantumTopologyRegistryPersistenceTest {

    @TempDir
    Path tempDir;

    // =====================================================================
    // A. CREATE PROFILE (matrix items 1-5)
    // =====================================================================

    @Test
    void profileGetsAStableIdBoundToASecurityDomainAndSurvivesAFreshSavedDataStorageInstance(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        var profile = registry1.createProfile();
        var profileId = profile.id();
        var domainId = SecurityDomainId.create();
        assertThat(registry1.bindSecurityDomain(profileId, domainId)).isTrue();
        registry1.markDirty();

        // Force a real, synchronous flush to disk, then throw away the in-memory instance entirely and open the
        // same directory fresh — equivalent to a server restart / chunk reload.
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        var reloaded = registry2.getProfile(profileId);
        assertThat(reloaded).isNotNull();
        // The id itself is stable across the reload — the same NetworkProfileId, never re-derived.
        assertThat(reloaded.id()).isEqualTo(profileId);
        assertThat(reloaded.getSecurityDomainId()).contains(domainId);
    }

    // =====================================================================
    // B (partial: 6-8) + G (28-31): full registry — profile, lane, endpoints, edges — survives a reload together,
    // with no duplicate records created by the round trip.
    // =====================================================================

    @Test
    void laneEndpointsAndEdgesAllSurviveAFreshSavedDataStorageInstanceWithNoDuplicates(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        var profile = registry1.createProfile();
        var lane = registry1.createLane(profile.id());
        var base = registry1.registerEndpoint(lane.id(), "base");
        var factory = registry1.registerEndpoint(lane.id(), "factory");
        var mine = registry1.registerEndpoint(lane.id(), "mine");
        assertThat(registry1.addEdge(lane.id(), base.id(), factory.id())).isTrue();
        assertThat(registry1.addEdge(lane.id(), factory.id(), mine.id())).isTrue();
        registry1.setEndpointOnline(base.id(), null);
        registry1.markDirty();

        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        assertThat(registry2.getAllProfiles()).hasSize(1);
        assertThat(registry2.getAllLanes()).hasSize(1);
        // Exactly 3 endpoint records survive — never duplicated by the round trip.
        assertThat(registry2.getAllEndpoints()).hasSize(3);

        var reloadedLane = registry2.getLane(lane.id());
        assertThat(reloadedLane).isNotNull();
        assertThat(reloadedLane.getEndpoints()).containsExactlyInAnyOrder(base.id(), factory.id(), mine.id());
        // Exactly the persisted N-1 = 2 edges survive — never a 3rd (no direct base<->mine), never duplicated.
        assertThat(reloadedLane.getEdges()).hasSize(2);
        assertThat(reloadedLane.hasEdge(base.id(), factory.id())).isTrue();
        assertThat(reloadedLane.hasEdge(factory.id(), mine.id())).isTrue();
        assertThat(reloadedLane.hasEdge(base.id(), mine.id())).isFalse();

        var reloadedBase = registry2.getEndpoint(base.id());
        assertThat(reloadedBase).isNotNull();
        assertThat(reloadedBase.laneId()).isEqualTo(lane.id());
        assertThat(reloadedBase.status()).isEqualTo(QuantumEndpointStatus.ONLINE);
        assertThat(reloadedBase.revision()).isEqualTo(1);

        var reloadedFactory = registry2.getEndpoint(factory.id());
        assertThat(reloadedFactory).isNotNull();
        // Never marked online in this session — status/lifecycle itself survives the round trip too, not just ids.
        assertThat(reloadedFactory.status()).isEqualTo(QuantumEndpointStatus.REGISTERED);
    }

    @Test
    void multipleProfilesEachWithTheirOwnLaneAllSurviveTogetherThroughAReload(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        var profileA = registry1.createProfile();
        var laneA = registry1.createLane(profileA.id());
        var profileB = registry1.createProfile();
        var laneB = registry1.createLane(profileB.id());
        registry1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        assertThat(registry2.getAllProfiles()).hasSize(2);
        assertThat(registry2.getProfile(profileA.id()).getLanes()).containsExactly(laneA.id());
        assertThat(registry2.getProfile(profileB.id()).getLanes()).containsExactly(laneB.id());
        assertThat(registry2.getLane(laneA.id()).profileId()).isEqualTo(profileA.id());
        assertThat(registry2.getLane(laneB.id()).profileId()).isEqualTo(profileB.id());
    }

    @Test
    void explicitHubStarSurvivesRestartWithoutAlternativeMemberEdges(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry1 = storage1.computeIfAbsent(QuantumTopologyRegistry.TYPE);

        var profile = registry1.createProfile();
        var lane = registry1.createLane(profile.id());
        var anchor = registry1.registerEndpoint(lane.id(), "anchor");
        var b = registry1.registerEndpoint(lane.id(), "B");
        var c = registry1.registerEndpoint(lane.id(), "C");
        var d = registry1.registerEndpoint(lane.id(), "D");
        assertThat(registry1.addEdge(lane.id(), anchor.id(), d.id())).isTrue();
        assertThat(registry1.addEdge(lane.id(), anchor.id(), b.id())).isTrue();
        assertThat(registry1.addEdge(lane.id(), anchor.id(), c.id())).isTrue();
        registry1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var reloaded = storage2.computeIfAbsent(QuantumTopologyRegistry.TYPE).getLane(lane.id());

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getEndpoints()).containsExactlyInAnyOrder(anchor.id(), b.id(), c.id(), d.id());
        assertThat(reloaded.getEdges()).hasSize(3);
        assertThat(reloaded.hasEdge(anchor.id(), b.id())).isTrue();
        assertThat(reloaded.hasEdge(anchor.id(), c.id())).isTrue();
        assertThat(reloaded.hasEdge(anchor.id(), d.id())).isTrue();
        assertThat(reloaded.hasEdge(b.id(), c.id())).isFalse();
        assertThat(reloaded.hasEdge(b.id(), d.id())).isFalse();
        assertThat(reloaded.hasEdge(c.id(), d.id())).isFalse();
    }
}
