package appeng.parts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityService;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/**
 * Covers the BUILD-permission routing hook used by {@code PartPlacement.placePart} (part placement) and
 * {@code CableBusBlockEntity.disassembleWithWrench} (part removal): both look up the {@link ISecurityService} of
 * whatever grid an existing part on the target host already belongs to, via
 * {@link PartPlacement#findExistingGridSecurity}. This test exercises that routing logic directly (the same production
 * method used by the real block-placement/removal code paths), since a full in-world placement flow needs a live
 * {@code Level} only available under the gametest run configuration, not the unit test sourceset.
 */
class PartPlacementSecurityTest {

    @Test
    void newlyPlacedHostWithNoPartsHasNoSecurityServiceToCheck() {
        var host = mock(IPartHost.class);
        // getPart returns null for every side and the center - matches a brand-new, empty host.

        assertThat(PartPlacement.findExistingGridSecurity(host)).isNull();
    }

    @Test
    void partWithoutAGridNodeYetHasNoSecurityServiceToCheck() {
        var host = mock(IPartHost.class);
        var part = mock(IPart.class);
        when(host.getPart(null)).thenReturn(part);
        when(part.getGridNode()).thenReturn(null);

        assertThat(PartPlacement.findExistingGridSecurity(host)).isNull();
    }

    @Test
    void partWithANodeNotYetInAFormedGridHasNoSecurityServiceToCheck() {
        var host = mock(IPartHost.class);
        var part = mock(IPart.class);
        var node = mock(IGridNode.class);
        when(host.getPart(null)).thenReturn(part);
        when(part.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(null);

        assertThat(PartPlacement.findExistingGridSecurity(host)).isNull();
    }

    @Test
    void centerCablePartRoutesToItsGridsSecurityService() {
        var host = mock(IPartHost.class);
        var cable = mock(IPart.class);
        var node = mock(IGridNode.class);
        var grid = mock(IGrid.class);
        var security = mock(ISecurityService.class);

        when(host.getPart(null)).thenReturn(cable);
        when(cable.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getService(ISecurityService.class)).thenReturn(security);

        assertThat(PartPlacement.findExistingGridSecurity(host)).isSameAs(security);
    }

    @Test
    void sidePartRoutesToItsGridsSecurityServiceWhenNoCenterCableExists() {
        var host = mock(IPartHost.class);
        var part = mock(IPart.class);
        var node = mock(IGridNode.class);
        var grid = mock(IGrid.class);
        var security = mock(ISecurityService.class);

        // No center cable.
        when(host.getPart(null)).thenReturn(null);
        when(host.getPart(Direction.NORTH)).thenReturn(part);
        when(part.getGridNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getService(ISecurityService.class)).thenReturn(security);

        assertThat(PartPlacement.findExistingGridSecurity(host)).isSameAs(security);
    }
}
