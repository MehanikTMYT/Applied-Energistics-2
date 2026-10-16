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

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.networking.security.SecurityDomainState;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.core.localization.GuiText;
import appeng.core.network.clientbound.QuantumKeyImprinterStatusPacket;
import appeng.menu.implementations.QuantumKeyImprinterMenu;

/**
 * Client screen for {@link QuantumKeyImprinterMenu}. Purely a view + input surface — every button here just calls the
 * menu's client-action senders, which are re-validated for permission on the server regardless of what this screen
 * displays (see {@link QuantumKeyImprinterMenu}'s own javadoc). Deliberately minimal — text status lines plus four
 * buttons, no fancy layout, no profile-picker widget (this Imprinter only ever has ONE profile to show — see
 * {@code QuantumKeyImprinterBlockEntity#getProfileId()}) — mirrors {@code SecurityStationScreen}'s own documented
 * "functional, not yet polished" precedent. HUMAN VISUAL DESIGN PENDING.
 */
public class QuantumKeyImprinterScreen extends AEBaseScreen<QuantumKeyImprinterMenu> {
    private final AE2Button issueButton;
    private final AE2Button revokeButton;
    private final AE2Button suspendButton;
    private final AE2Button resumeButton;

    @Nullable
    private QuantumKeyImprinterStatusPacket status;

    public QuantumKeyImprinterScreen(QuantumKeyImprinterMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.issueButton = widgets.addButton("issue_button", GuiText.QuantumImprinterIssue.text(), this::doIssue);
        this.revokeButton = widgets.addButton("revoke_button", GuiText.QuantumImprinterRevoke.text(), this::doRevoke);
        this.suspendButton = widgets.addButton("suspend_button", GuiText.QuantumImprinterSuspend.text(),
                this::doSuspend);
        this.resumeButton = widgets.addButton("resume_button", GuiText.QuantumImprinterResume.text(), this::doResume);
    }

    public void processServerUpdate(QuantumKeyImprinterStatusPacket packet) {
        this.status = packet;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var state = status != null ? SecurityDomainState.values()[status.domainStateOrdinal()]
                : SecurityDomainState.LEGACY;
        var hasProfile = status != null && status.hasProfile();

        issueButton.active = hasProfile && status != null && status.heldItemDescription().equals("blank");
        var holdingEncoded = status != null && status.heldItemDescription().startsWith("encoded:");
        revokeButton.active = hasProfile && holdingEncoded;
        suspendButton.active = hasProfile && holdingEncoded;
        resumeButton.active = hasProfile && holdingEncoded;

        issueButton.visible = state != SecurityDomainState.CONFLICT;
        revokeButton.visible = issueButton.visible;
        suspendButton.visible = issueButton.visible;
        resumeButton.visible = issueButton.visible;
    }

    private void doIssue() {
        menu.sendIssue();
    }

    private void doRevoke() {
        menu.sendRevoke();
    }

    private void doSuspend() {
        menu.sendSuspend();
    }

    private void doResume() {
        menu.sendResume();
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var textColor = 0xFFFFFF;

        var state = status != null ? SecurityDomainState.values()[status.domainStateOrdinal()]
                : SecurityDomainState.LEGACY;

        var stateText = switch (state) {
            case CONFLICT -> GuiText.SecurityStateConflict.text().getString();
            case LEGACY -> GuiText.SecurityStateLegacy.text().getString();
            case SECURED -> GuiText.SecurityStateSecured.text().getString();
        };
        guiGraphics.text(font, stateText, 8, 18, textColor, false);

        if (status == null) {
            return;
        }

        if (status.hasProfile()) {
            guiGraphics.text(font,
                    GuiText.QuantumImprinterProfileLabel.text(shortId(status.profileId())).getString(), 8, 30,
                    0xB0B0B0, false);
            guiGraphics.text(font,
                    GuiText.QuantumImprinterDomainLabel.text(shortId(status.domainId())).getString(), 8, 40, 0xB0B0B0,
                    false);
        } else {
            guiGraphics.text(font, GuiText.QuantumImprinterNoProfile.text(), 8, 30, 0xFFFF60, false);
        }

        guiGraphics.text(font, GuiText.QuantumImprinterHeldItem.text(describeHeld(status.heldItemDescription()))
                .getString(), 8, 52, 0xE0E0E0, false);

        if (!status.lastResultMessage().isEmpty()) {
            guiGraphics.text(font,
                    GuiText.QuantumImprinterLastResult.text(status.lastResultMessage()).getString(), 8, 64, 0xC0C0C0,
                    false);
        }
    }

    private String describeHeld(String raw) {
        if (raw.equals("blank")) {
            return GuiText.QuantumKeyUnencoded.text().getString();
        }
        if (raw.startsWith("encoded:")) {
            var parts = raw.split(":");
            return parts.length >= 4 ? ("profile " + parts[1].substring(0, 8) + " rev " + parts[3]) : raw;
        }
        return GuiText.QuantumImprinterHeldNothingUseful.text().getString();
    }

    private String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
