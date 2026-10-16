/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2026, AlgorithmX2, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.core.network.clientbound.SecurityStationStatusPacket;
import appeng.menu.implementations.SecurityStationMenu;

/**
 * Client screen for {@link SecurityStationMenu}. Purely a view + input surface: every mutation it triggers goes through
 * the menu's client-action methods, which are re-checked for {@link SecurityPermissions#SECURITY} on the server
 * regardless of what this screen displays or thinks the viewer can do (see
 * {@code SecurityStationMenu#hasSecurityPermission}) - nothing here is authoritative.
 * <p/>
 * Known UI debt (see the session report for the full list): group <em>membership</em> editing
 * ({@code SET_GROUP_MEMBERS}) has no widget on this screen yet (groups can be created, deleted, and granted permissions
 * from this screen, but who is in a group cannot be changed here) - use
 * {@code /ae2 securitygroup <addmember|removemember|listmembers> <group> [player]} while looking at this station in the
 * meantime (real, server-authoritative, gated by the same SECURITY ACL permission this screen checks, see
 * {@code appeng.server.subcommands.SecurityGroupCommand}); adding a PLAYER principal by name only works for players who
 * are currently online (no persistent name-to-UUID cache is queried client-side).
 */
public class SecurityStationScreen extends AEBaseScreen<SecurityStationMenu> {

    private enum AddMode {
        PLAYER,
        GROUP,
        DEVICE,
        EVERYONE
    }

    private static final SecurityPermissions[] TOGGLE_PERMISSIONS = {
            SecurityPermissions.INJECT,
            SecurityPermissions.EXTRACT,
            SecurityPermissions.CRAFT,
            SecurityPermissions.BUILD,
            SecurityPermissions.SECURITY
    };
    private static final String[] TOGGLE_LETTERS = { "I", "E", "C", "B", "S" };

    // Layout constants for the custom-drawn ACL list (independent of the fixed-position JSON widgets below it).
    private static final int LIST_LEFT = 8;
    private static final int LIST_TOP = 38;
    private static final int LIST_ROW_HEIGHT = 9;
    private static final int LIST_VISIBLE_ROWS = 7;
    private static final int COL_DESC = LIST_LEFT;
    private static final int[] COL_PERM = { 110, 119, 128, 137, 146 };
    private static final int COL_REVOKE = 158;

    private final Scrollbar scrollbar;
    private final AE2Button modeButton;
    private final AETextField nameField;
    private final AE2Button[] wantToggle = new AE2Button[TOGGLE_PERMISSIONS.length];
    private final AE2Button grantButton;
    private final AE2Button revokeButton;
    private final AE2Button newGroupButton;
    private final AE2Button activateButton;

    private AddMode addMode = AddMode.PLAYER;
    private final EnumSet<SecurityPermissions> wantPermissions = EnumSet.noneOf(SecurityPermissions.class);

    @org.jetbrains.annotations.Nullable
    private SecurityStationStatusPacket status;

    private final List<RowHit> rowHits = new ArrayList<>();

    private record RowHit(int y, SecurityStationStatusPacket.AclRow row) {
    }

    public SecurityStationScreen(SecurityStationMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.scrollbar = widgets.addScrollBar("scrollbar", Scrollbar.SMALL);

        this.modeButton = widgets.addButton("mode_button", modeLabel(), this::cycleMode);
        this.nameField = widgets.addTextField("name_field");
        this.nameField.setMaxLength(48);
        this.nameField.setPlaceholder(GuiText.SecurityNamePlaceholder.text());

        for (var i = 0; i < TOGGLE_PERMISSIONS.length; i++) {
            var index = i;
            this.wantToggle[i] = widgets.addButton("perm_" + TOGGLE_PERMISSIONS[i].name().toLowerCase(Locale.ROOT),
                    Component.literal(TOGGLE_LETTERS[i]), () -> toggleWant(index));
        }

        this.grantButton = widgets.addButton("grant_button", GuiText.SecurityGrant.text(), this::doGrant);
        this.revokeButton = widgets.addButton("revoke_button", GuiText.SecurityRevoke.text(), this::doRevoke);
        this.newGroupButton = widgets.addButton("new_group_button", GuiText.SecurityNewGroup.text(),
                this::doNewGroup);
        this.activateButton = widgets.addButton("activate_button", GuiText.SecurityActivate.text(), this::doActivate);
    }

    public void processServerUpdate(SecurityStationStatusPacket packet) {
        this.status = packet;

        var rowCount = packet.rows().size();
        var overflow = rowCount - LIST_VISIBLE_ROWS;
        scrollbar.setRange(0, Math.max(0, overflow), 1);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var state = status != null ? SecurityDomainState.values()[status.stateOrdinal()] : SecurityDomainState.LEGACY;
        var secured = state == SecurityDomainState.SECURED;
        var canEdit = status != null && status.canEdit();

        activateButton.visible = state == SecurityDomainState.LEGACY;

        var editingVisible = secured;
        scrollbar.setVisible(editingVisible);
        modeButton.visible = editingVisible;
        nameField.setVisible(editingVisible);
        for (var b : wantToggle) {
            b.visible = editingVisible;
        }
        grantButton.visible = editingVisible;
        revokeButton.visible = editingVisible;
        newGroupButton.visible = editingVisible;

        var interactive = editingVisible && canEdit;
        modeButton.active = interactive;
        nameField.setEditable(interactive);
        for (var b : wantToggle) {
            b.active = interactive;
        }
        grantButton.active = interactive;
        revokeButton.active = interactive;
        newGroupButton.active = interactive;

        modeButton.setMessage(modeLabel());
        nameField.setVisible(editingVisible && addMode != AddMode.EVERYONE);
    }

    private Component modeLabel() {
        return switch (addMode) {
            case PLAYER -> GuiText.SecurityPrincipalPlayer.text();
            case GROUP -> GuiText.SecurityPrincipalGroup.text();
            case DEVICE -> GuiText.SecurityPrincipalDevice.text();
            case EVERYONE -> GuiText.SecurityPrincipalEveryone.text();
        };
    }

    private void cycleMode() {
        var values = AddMode.values();
        addMode = values[(addMode.ordinal() + 1) % values.length];
        modeButton.setMessage(modeLabel());
    }

    private void toggleWant(int index) {
        var perm = TOGGLE_PERMISSIONS[index];
        if (wantPermissions.contains(perm)) {
            wantPermissions.remove(perm);
        } else {
            wantPermissions.add(perm);
        }
        wantToggle[index].setMessage(toggleLabel(index));
    }

    private Component toggleLabel(int index) {
        var active = wantPermissions.contains(TOGGLE_PERMISSIONS[index]);
        var text = Component.literal(TOGGLE_LETTERS[index]);
        return active ? text.copy().withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD) : text;
    }

    @org.jetbrains.annotations.Nullable
    private PrincipalId resolveTargetPrincipal() {
        return switch (addMode) {
            case EVERYONE -> PrincipalId.EVERYONE;
            case PLAYER -> {
                var name = nameField.getValue().trim();
                if (name.isEmpty()) {
                    yield null;
                }
                var connection = Minecraft.getInstance().getConnection();
                if (connection == null) {
                    yield null;
                }
                for (var info : connection.getOnlinePlayers()) {
                    if (info.getProfile().name().equalsIgnoreCase(name)) {
                        yield PrincipalId.ofPlayer(info.getProfile().id());
                    }
                }
                notifyLocally(GuiText.SecurityPlayerNotFound.text(name));
                yield null;
            }
            case GROUP -> {
                var name = nameField.getValue().trim();
                if (status == null || name.isEmpty()) {
                    yield null;
                }
                for (var group : status.groups()) {
                    if (group.name().equalsIgnoreCase(name)) {
                        yield PrincipalId.ofGroup(group.id());
                    }
                }
                notifyLocally(GuiText.SecurityGroupNotFound.text(name));
                yield null;
            }
            case DEVICE -> {
                var text = nameField.getValue().trim();
                try {
                    yield PrincipalId.ofDevice(UUID.fromString(text));
                } catch (IllegalArgumentException e) {
                    notifyLocally(GuiText.SecurityInvalidDeviceId.text(text));
                    yield null;
                }
            }
        };
    }

    private void notifyLocally(Component message) {
        var player = getPlayer();
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    private void doGrant() {
        var principal = resolveTargetPrincipal();
        if (principal != null) {
            menu.sendGrant(principal, toPermissionSet(wantPermissions));
        }
    }

    private static java.util.Set<SecurityPermissions> toPermissionSet(EnumSet<SecurityPermissions> src) {
        return src.isEmpty() ? java.util.Set.of() : EnumSet.copyOf(src);
    }

    private void doRevoke() {
        var principal = resolveTargetPrincipal();
        if (principal != null) {
            menu.sendRevoke(principal);
        }
    }

    private void doNewGroup() {
        var name = nameField.getValue().trim();
        if (!name.isEmpty()) {
            menu.sendCreateGroup(name);
        }
    }

    private void doActivate() {
        menu.sendActivate();
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var state = status != null ? SecurityDomainState.values()[status.stateOrdinal()] : SecurityDomainState.LEGACY;
        var textColor = 0xFFFFFF;

        switch (state) {
            case CONFLICT -> {
                drawCentered(guiGraphics, GuiText.SecurityStateConflict.text().withStyle(ChatFormatting.RED), 30);
                drawWrapped(guiGraphics, GuiText.SecurityConflictHint.text(), 8, 45, 178);
                rowHits.clear();
                return;
            }
            case LEGACY -> {
                guiGraphics.text(font, GuiText.SecurityStateLegacy.text(), 8, 18, textColor, false);
                drawWrapped(guiGraphics, GuiText.SecurityLegacyHint.text(), 8, 32, 178);
                rowHits.clear();
                return;
            }
            default -> {
                // SECURED - fall through to the full ACL editor below.
            }
        }

        // Header lines
        guiGraphics.text(font, GuiText.SecurityStateSecured.text().withStyle(ChatFormatting.GREEN), 8, 18, textColor,
                false);

        if (status != null) {
            var ownerText = status.hasOwner()
                    ? (status.ownerName().isEmpty() ? shortId(status.ownerId()) : status.ownerName())
                    : GuiText.SecurityUnknownOwner.text().getString();
            var domainText = status.hasDomain() ? shortId(status.domainId()) : "-";
            var combined = GuiText.SecurityOwnerLabel.text(ownerText).getString() + "   "
                    + GuiText.SecurityDomainIdLabel.text(domainText).getString();
            guiGraphics.text(font, trimToWidth(combined, 178), 8, 28, 0xB0B0B0, false);
        }

        if (status != null && !status.canEdit()) {
            guiGraphics.text(font, GuiText.SecurityNoPermission.text().withStyle(ChatFormatting.YELLOW), 8, 103,
                    0xFFFF60, false);
        }

        rowHits.clear();
        if (status == null) {
            return;
        }

        var scrollOffset = scrollbar.getCurrentScroll();
        var rows = status.rows();
        for (var i = 0; i < LIST_VISIBLE_ROWS; i++) {
            var rowIndex = i + scrollOffset;
            if (rowIndex >= rows.size()) {
                break;
            }
            var row = rows.get(rowIndex);
            var y = LIST_TOP + i * LIST_ROW_HEIGHT;
            rowHits.add(new RowHit(y, row));

            var description = describeRowForDisplay(row);
            var descColor = isHovering(COL_DESC, y, COL_PERM[0] - COL_DESC, LIST_ROW_HEIGHT, mouseX, mouseY)
                    ? 0xFFFFA0
                    : 0xE0E0E0;
            guiGraphics.text(font, trimToWidth(description, COL_PERM[0] - COL_DESC - 2), COL_DESC, y, descColor,
                    false);

            for (var p = 0; p < TOGGLE_PERMISSIONS.length; p++) {
                var active = row.permissions().contains(TOGGLE_PERMISSIONS[p]);
                var hovered = isHovering(COL_PERM[p], y, 8, LIST_ROW_HEIGHT, mouseX, mouseY);
                int color;
                if (active) {
                    color = hovered ? 0x80FF80 : 0x40C040;
                } else {
                    color = hovered ? 0x909090 : 0x505050;
                }
                guiGraphics.text(font, TOGGLE_LETTERS[p], COL_PERM[p], y, color, false);
            }

            var revokeHovered = isHovering(COL_REVOKE, y, 8, LIST_ROW_HEIGHT, mouseX, mouseY);
            guiGraphics.text(font, "x", COL_REVOKE, y, revokeHovered ? 0xFF8080 : 0xA04040, false);
        }

        if (rows.isEmpty()) {
            guiGraphics.text(font, GuiText.SecurityAclEmpty.text(), LIST_LEFT, LIST_TOP, 0x909090, false);
        }
    }

    private String describeRowForDisplay(SecurityStationStatusPacket.AclRow row) {
        return switch (row.principal()) {
            case PrincipalId.Everyone ignored -> GuiText.SecurityEveryoneRow.text().getString();
            case PrincipalId.Group g -> GuiText.SecurityGroupRow.text(row.description()).getString();
            case PrincipalId.Device d -> GuiText.SecurityDeviceRow.text(row.description()).getString();
            case PrincipalId.Player p -> row.description();
        };
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        var trimmed = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("...")));
        return trimmed + "...";
    }

    private void drawCentered(GuiGraphicsExtractor guiGraphics, Component text, int y) {
        var x = (imageWidth - font.width(text)) / 2;
        guiGraphics.text(font, text, x, y, 0xFFFFFF, false);
    }

    private void drawWrapped(GuiGraphicsExtractor guiGraphics, Component text, int x, int y, int maxWidth) {
        var lines = font.split(text, maxWidth);
        var lineY = y;
        for (var line : lines) {
            guiGraphics.text(font, line, x, lineY, 0xC0C0C0, false);
            lineY += font.lineHeight + 1;
        }
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (status != null && status.canEdit()) {
            for (var hit : rowHits) {
                for (var p = 0; p < TOGGLE_PERMISSIONS.length; p++) {
                    if (isHovering(COL_PERM[p], hit.y(), 8, LIST_ROW_HEIGHT, event.x(), event.y())) {
                        var updated = EnumSet.noneOf(SecurityPermissions.class);
                        updated.addAll(hit.row().permissions());
                        var perm = TOGGLE_PERMISSIONS[p];
                        if (updated.contains(perm)) {
                            updated.remove(perm);
                        } else {
                            updated.add(perm);
                        }
                        menu.sendGrant(hit.row().principal(), toPermissionSet(updated));
                        return true;
                    }
                }
                if (isHovering(COL_REVOKE, hit.y(), 8, LIST_ROW_HEIGHT, event.x(), event.y())) {
                    menu.sendRevoke(hit.row().principal());
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }
}
