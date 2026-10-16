package appeng.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for requirement "recovery command does not accidentally turn a SECURITY-permission-holding player
 * into a server op": {@code /ae2 security recover} is registered like every other admin-only AE2 subcommand, gated on
 * {@link net.minecraft.commands.Commands#LEVEL_OWNERS} (vanilla operator level), completely independent of the in-game
 * ACL system. Holding {@code SecurityPermissions.SECURITY} on a domain never grants command-level server permissions,
 * and this stays true regardless of any future change to the ACL/domain code, since the gate here is Brigadier's own
 * permission level, enforced before AE2's command body ever runs.
 */
class SecurityRecoverCommandPermissionTest {

    @Test
    void recoverCommandIsRegisteredAtOperatorLevel() {
        assertThat(Commands.SECURITY.requiredPermission).isEqualTo(net.minecraft.commands.Commands.LEVEL_OWNERS);
        assertThat(Commands.SECURITY.literal).isEqualTo("security");
        assertThat(Commands.SECURITY.command).isInstanceOf(appeng.server.subcommands.SecurityRecoverCommand.class);
    }
}
