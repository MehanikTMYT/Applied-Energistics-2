package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainId;
import appeng.api.networking.security.SecurityPermissions;

/**
 * Unit-level coverage of {@link SecurityDomain} mutation invariants that don't require a formed grid, in particular the
 * exact operation {@code /ae2 security recover} performs ({@link SecurityDomain#setOwner} +
 * {@link SecurityDomain#audit}) - see {@code appeng.server.subcommands.SecurityRecoverCommand}. The command class
 * itself needs a live {@code MinecraftServer}/Brigadier context that isn't available to the unit test sourceset, so
 * this test exercises the domain-level primitive the command is built on instead, proving the anti-lockout tool cannot
 * accidentally wipe a domain's ACL/groups/audit history - it only ever reassigns the owner field and appends one audit
 * entry, exactly like the command does.
 */
class SecurityDomainTest {

    @Test
    void ownerRecoveryPreservesAclGroupsAndPriorAuditHistoryAndAppendsExactlyOneNewEntry() {
        var originalOwner = UUID.randomUUID();
        var domain = new SecurityDomain(SecurityDomainId.create(), originalOwner);

        var grantedPlayer = UUID.randomUUID();
        domain.grant(PrincipalId.ofPlayer(grantedPlayer),
                Set.of(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT));
        var group = SecurityGroup.create("miners").withMember(grantedPlayer);
        domain.putGroup(group);
        domain.grant(PrincipalId.ofGroup(group.id()), Set.of(SecurityPermissions.INJECT));
        domain.audit("domain created, owner=" + originalOwner);

        var aclBefore = domain.getAcl();
        var groupsBefore = domain.getGroups();
        var auditSizeBefore = domain.getAuditLog().size();

        // Exactly what SecurityRecoverCommand#recover does: reassign the owner, then append one audit entry.
        var newOwner = UUID.randomUUID();
        domain.setOwner(newOwner);
        domain.audit("owner reassigned from " + originalOwner + " to " + newOwner
                + " by operator ... via /ae2 security recover");

        assertThat(domain.owner()).isEqualTo(newOwner);
        assertThat(domain.isOwner(originalOwner)).isFalse();
        assertThat(domain.isOwner(newOwner)).isTrue();

        // ACL, groups, and prior audit history are completely untouched - recovery is not a wipe.
        assertThat(domain.getAcl()).isEqualTo(aclBefore);
        assertThat(domain.getGroups()).isEqualTo(groupsBefore);
        assertThat(domain.getPermissions(PrincipalId.ofPlayer(grantedPlayer)))
                .containsExactlyInAnyOrder(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT);
        assertThat(domain.getAuditLog()).hasSize(auditSizeBefore + 1);
        assertThat(domain.getAuditLog().get(domain.getAuditLog().size() - 1)).contains("owner reassigned");
    }

    @Test
    void previousOwnerLosesImplicitAccessAfterRecoveryAndNewOwnerGainsIt() {
        var originalOwner = UUID.randomUUID();
        var domain = new SecurityDomain(SecurityDomainId.create(), originalOwner);
        assertThat(domain.isOwner(originalOwner)).isTrue();

        var newOwner = UUID.randomUUID();
        domain.setOwner(newOwner);

        assertThat(domain.isOwner(originalOwner)).isFalse();
        assertThat(domain.isOwner(newOwner)).isTrue();
    }
}
