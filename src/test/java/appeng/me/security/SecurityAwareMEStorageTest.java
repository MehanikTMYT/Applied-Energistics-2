package appeng.me.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.BootstrapMinecraft;

/**
 * Unit test for the storage enforcement decorator installed by {@code StorageService.getInventory()}. Covers the
 * "enforcement" part of the security foundation independently from grid formation/{@link SecurityService}, using plain
 * mocks for the delegate storage and the security service it consults.
 */
@BootstrapMinecraft
class SecurityAwareMEStorageTest {
    private final MEStorage delegate = mock(MEStorage.class);
    private final ISecurityService security = mock(ISecurityService.class);
    private final SecurityAwareMEStorage storage = new SecurityAwareMEStorage(delegate, security);
    private final IActionSource source = IActionSource.empty();
    private final AEItemKey key = AEItemKey.of(net.minecraft.world.item.Items.DIAMOND);

    @Test
    void insertIsDeniedWithoutInjectPermission() {
        when(security.hasPermission(source, SecurityPermissions.INJECT)).thenReturn(false);

        var result = storage.insert(key, 64, Actionable.MODULATE, source);

        assertThat(result).isZero();
        verify(delegate, never()).insert(any(), anyLong(), any(), any());
    }

    @Test
    void insertIsForwardedWithInjectPermission() {
        when(security.hasPermission(source, SecurityPermissions.INJECT)).thenReturn(true);
        when(delegate.insert(key, 64, Actionable.MODULATE, source)).thenReturn(64L);

        var result = storage.insert(key, 64, Actionable.MODULATE, source);

        assertThat(result).isEqualTo(64L);
    }

    @Test
    void extractIsDeniedWithoutExtractPermission() {
        when(security.hasPermission(source, SecurityPermissions.EXTRACT)).thenReturn(false);

        var result = storage.extract(key, 64, Actionable.MODULATE, source);

        assertThat(result).isZero();
        verify(delegate, never()).extract(any(), anyLong(), any(), any());
    }

    @Test
    void extractIsForwardedWithExtractPermission() {
        when(security.hasPermission(source, SecurityPermissions.EXTRACT)).thenReturn(true);
        when(delegate.extract(key, 32, Actionable.MODULATE, source)).thenReturn(32L);

        var result = storage.extract(key, 32, Actionable.MODULATE, source);

        assertThat(result).isEqualTo(32L);
    }

    @Test
    void readingAvailableStacksIsNeverGated() {
        // Visibility != access: browsing what a secured network contains must not require any permission at all -
        // the security service is never even consulted.
        var out = new KeyCounter();
        storage.getAvailableStacks(out);

        verify(delegate).getAvailableStacks(out);
        verify(security, never()).hasPermission(any(), any());
    }

    @Test
    void descriptionIsDelegated() {
        when(delegate.getDescription()).thenReturn(Component.literal("test"));
        assertThat(storage.getDescription().getString()).isEqualTo("test");
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
