package appeng.server.subcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import appeng.api.networking.security.SecurityDomainId;
import appeng.me.security.SecurityDomain;
import appeng.me.security.SecurityGroup;
import appeng.server.Commands;

/**
 * Covers {@code /ae2 securitygroup <addmember|removemember|listmembers>}: the first player-reachable entry point for
 * {@code SecurityDomain}'s group <em>membership</em> mutation (as opposed to group ACL permissions or a group's
 * existence, both already editable from the Security Station screen). See {@link SecurityGroupCommand}'s own doc
 * comment for why this is a separate, {@code LEVEL_ALL}-registered top-level command rather than a subcommand of the
 * operator-only {@code /ae2 security} node.
 */
class SecurityGroupCommandTest {

    @Test
    void registeredAsASeparateTopLevelCommandAtPlayerLevelNotNestedUnderTheOperatorOnlySecurityNode() {
        assertThat(Commands.SECURITY_GROUP.requiredPermission).isEqualTo(net.minecraft.commands.Commands.LEVEL_ALL);
        assertThat(Commands.SECURITY_GROUP.literal).isEqualTo("securitygroup");
        assertThat(Commands.SECURITY_GROUP.command).isInstanceOf(SecurityGroupCommand.class);
        // Distinct from the operator-only "security" node - the real gate is the in-game SECURITY ACL permission,
        // checked inside the command body, not Brigadier's permission level.
        assertThat(Commands.SECURITY_GROUP.requiredPermission).isNotEqualTo(Commands.SECURITY.requiredPermission);
    }

    @Test
    void findGroupByNameIsCaseInsensitiveMatchingTheScreensOwnConvention() {
        var domain = new SecurityDomain(SecurityDomainId.create(), UUID.randomUUID());
        var group = SecurityGroup.create("Miners");
        domain.putGroup(group);

        assertThat(SecurityGroupCommand.findGroupByName(domain, "Miners")).isEqualTo(group);
        assertThat(SecurityGroupCommand.findGroupByName(domain, "miners")).isEqualTo(group);
        assertThat(SecurityGroupCommand.findGroupByName(domain, "MINERS")).isEqualTo(group);
    }

    @Test
    void findGroupByNameReturnsNullForAnUnknownGroup() {
        var domain = new SecurityDomain(SecurityDomainId.create(), UUID.randomUUID());
        domain.putGroup(SecurityGroup.create("Miners"));

        assertThat(SecurityGroupCommand.findGroupByName(domain, "Builders")).isNull();
    }

    @Test
    void addMemberAndRemoveMemberMutateTheGroupsMembershipSet() {
        // Exercises the exact SecurityGroup#withMember/withoutMember + SecurityDomain#putGroup sequence the
        // command's ADD/REMOVE branches perform.
        var domain = new SecurityDomain(SecurityDomainId.create(), UUID.randomUUID());
        var group = SecurityGroup.create("Miners");
        domain.putGroup(group);
        var playerId = UUID.randomUUID();

        var resolved = SecurityGroupCommand.findGroupByName(domain, "miners");
        assertThat(resolved.members()).doesNotContain(playerId);

        domain.putGroup(resolved.withMember(playerId));
        assertThat(SecurityGroupCommand.findGroupByName(domain, "miners").members()).contains(playerId);

        var afterAdd = SecurityGroupCommand.findGroupByName(domain, "miners");
        domain.putGroup(afterAdd.withoutMember(playerId));
        assertThat(SecurityGroupCommand.findGroupByName(domain, "miners").members()).doesNotContain(playerId);
    }
}
