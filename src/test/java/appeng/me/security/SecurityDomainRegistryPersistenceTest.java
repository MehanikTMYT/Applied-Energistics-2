package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityPermissions;

/**
 * Exercises {@link SecurityDomainRegistry} through a <em>real</em> {@link SavedDataStorage} backed by an on-disk temp
 * directory (the same mechanism used by real world saves), rather than the plain in-memory
 * {@link SecurityDomainRegistry#createForTesting()} escape hatch used by {@code SecurityServiceTest}. This is the
 * closest a unit test can get to proving requirement "server/SavedData reload does not lose a security domain" without
 * booting a full dedicated server: domains are created and granted against one {@link SavedDataStorage} instance,
 * flushed to disk with {@code saveAndJoin()}, and then read back through a brand-new {@link SavedDataStorage} instance
 * pointed at the same directory - a fresh instance has no in-memory cache, so this can only pass if the NBT round-trip
 * through disk is actually correct, mirroring a server restart/chunk reload.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class SecurityDomainRegistryPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void domainAndAclSurviveAFreshSavedDataStorageInstanceLikeAServerRestart(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());

        var registry1 = storage1.computeIfAbsent(SecurityDomainRegistry.TYPE);
        var owner = UUID.randomUUID();
        var domain = registry1.createDomain(owner);
        var playerId = UUID.randomUUID();
        domain.grant(PrincipalId.ofPlayer(playerId), Set.of(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT));
        var group = SecurityGroup.create("miners").withMember(playerId);
        domain.putGroup(group);
        domain.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.INJECT));
        domain.audit("test setup");
        registry1.markDirty();

        // Force a real, synchronous flush to disk (equivalent to a world save), then throw away the in-memory
        // instance entirely and open the same directory fresh - equivalent to a server restart / chunk reload.
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(SecurityDomainRegistry.TYPE);
        var reloadedDomain = registry2.getDomain(domain.id());

        assertThat(reloadedDomain).isNotNull();
        assertThat(reloadedDomain.owner()).isEqualTo(owner);
        assertThat(reloadedDomain.getPermissions(PrincipalId.ofPlayer(playerId)))
                .containsExactlyInAnyOrder(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT);
        assertThat(reloadedDomain.getGroups()).containsKey(group.id());
        assertThat(reloadedDomain.getEffectivePermissions(PrincipalId.ofPlayer(playerId)))
                .contains(SecurityPermissions.INJECT);
        assertThat(reloadedDomain.getAuditLog()).contains("test setup");
    }

    @Test
    void multipleDomainsAllSurviveTogetherThroughAReload(MinecraftServer server) {
        var storage1 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());

        var registry1 = storage1.computeIfAbsent(SecurityDomainRegistry.TYPE);
        var domainA = registry1.createDomain(UUID.randomUUID());
        var domainB = registry1.createDomain(UUID.randomUUID());
        var domainC = registry1.createDomain(UUID.randomUUID());
        registry1.markDirty();
        storage1.saveAndJoin();

        var storage2 = new SavedDataStorage(tempDir, DataFixers.getDataFixer(), server.registryAccess());
        var registry2 = storage2.computeIfAbsent(SecurityDomainRegistry.TYPE);

        assertThat(registry2.getAllDomains()).hasSize(3);
        assertThat(registry2.getDomain(domainA.id())).isNotNull();
        assertThat(registry2.getDomain(domainB.id())).isNotNull();
        assertThat(registry2.getDomain(domainC.id())).isNotNull();
    }
}
