package appeng.parts.networking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.player.Player;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityPermissions;

/**
 * Covers the BUILD-permission routing hook used by {@link CablePart#changeColor} (recoloring an already-placed cable
 * with a color applicator, either directly or via {@code CableBusContainer#recolourBlock}). This is a real,
 * historically-grounded hook (the pre-2023 AE2 security system gated this exact operation the same way; see
 * {@link CablePart#hasBuildPermissionToRecolor}'s doc comment for the archaeology) reinstated this session, on top of
 * the two BUILD hooks the previous Security Gate session already found and wired (part placement, wrench disassembly) -
 * a cable's color affects the network's physical connectivity graph ({@code InWorldGridNode#hasCompatibleColor}),
 * exactly the class of "physical layout modification" BUILD was always meant to cover.
 * <p/>
 * Exercises {@link CablePart#hasBuildPermissionToRecolor} directly (the same routing method the real
 * {@code changeColor} production code path calls), since a full in-world part/host/level setup needs the gametest run
 * configuration, not the unit test sourceset - mirrors {@code PartPlacementSecurityTest}'s own reasoning.
 */
class CablePartSecurityTest {

    @Test
    void noPlayerAttachedToTheActionIsAlwaysAllowed() {
        var mainNode = mock(IManagedGridNode.class);

        assertThat(CablePart.hasBuildPermissionToRecolor(mainNode, null)).isTrue();
    }

    @Test
    void nodeWithNoGridYetHasNothingToCheckAgainstAndIsAllowed() {
        var mainNode = mock(IManagedGridNode.class);
        when(mainNode.getGrid()).thenReturn(null);
        var player = playerMock();

        assertThat(CablePart.hasBuildPermissionToRecolor(mainNode, player)).isTrue();
    }

    @Test
    void deniedWithoutBuildPermissionOnTheAttachedSecuredGrid() {
        var mainNode = mock(IManagedGridNode.class);
        var grid = mock(IGrid.class);
        var security = mock(ISecurityService.class);
        var player = playerMock();

        when(mainNode.getGrid()).thenReturn(grid);
        when(grid.getService(ISecurityService.class)).thenReturn(security);
        when(security.hasPermission(argThatMatchesPlayer(player),
                org.mockito.ArgumentMatchers.eq(SecurityPermissions.BUILD)))
                .thenReturn(false);

        assertThat(CablePart.hasBuildPermissionToRecolor(mainNode, player)).isFalse();
    }

    @Test
    void allowedWithBuildPermissionOnTheAttachedSecuredGrid() {
        var mainNode = mock(IManagedGridNode.class);
        var grid = mock(IGrid.class);
        var security = mock(ISecurityService.class);
        var player = playerMock();

        when(mainNode.getGrid()).thenReturn(grid);
        when(grid.getService(ISecurityService.class)).thenReturn(security);
        when(security.hasPermission(argThatMatchesPlayer(player),
                org.mockito.ArgumentMatchers.eq(SecurityPermissions.BUILD)))
                .thenReturn(true);

        assertThat(CablePart.hasBuildPermissionToRecolor(mainNode, player)).isTrue();
    }

    private static Player playerMock() {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        return player;
    }

    /**
     * {@link IActionSource} is a plain value-ish wrapper without a public equals(); matching by "any IActionSource
     * whose player() resolves to this exact player instance" avoids depending on its internal representation.
     */
    private static IActionSource argThatMatchesPlayer(Player player) {
        return org.mockito.ArgumentMatchers.argThat(source -> source != null && source.player().isPresent()
                && source.player().get() == player);
    }
}
