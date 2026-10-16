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

import appeng.api.networking.quantum.QuantumRouteReason;
import appeng.api.networking.quantum.QuantumRouteState;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.core.localization.GuiText;
import appeng.core.network.clientbound.QuantumRoutingControllerStatusPacket;
import appeng.menu.implementations.QuantumRoutingControllerMenu;

/**
 * Round 11 ("Routing Controller foundation"): client screen for {@link QuantumRoutingControllerMenu}.
 * <p/>
 * A pure view + input surface, exactly like {@link QuantumKeyImprinterScreen}. Buttons reflect the server's reported
 * permissions as a courtesy, but they are never a security mechanism — the server re-checks {@code QUANTUM_ROUTE} on
 * every action regardless of what was rendered here.
 * <p/>
 * Deliberately minimal per the round's own scope: text status lines and two buttons. No topology map, no draggable
 * graph, no world map, no coordinate browser and no global profile browser — and, by construction, no exact remote
 * {@code BlockPos} anywhere, because the status packet never carries one. HUMAN VISUAL DESIGN PENDING.
 */
public class QuantumRoutingControllerScreen extends AEBaseScreen<QuantumRoutingControllerMenu> {
    private final AE2Button repairButton;
    private final AE2Button nextPairButton;

    @Nullable
    private QuantumRoutingControllerStatusPacket status;

    public QuantumRoutingControllerScreen(QuantumRoutingControllerMenu menu, Inventory playerInventory,
            Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.repairButton = widgets.addButton("repair_button", GuiText.QuantumRoutingRepair.text(), this::doRepair);
        this.nextPairButton = widgets.addButton("next_pair_button", GuiText.QuantumRoutingNextPair.text(),
                this::doNextPair);
    }

    public void processServerUpdate(QuantumRoutingControllerStatusPacket packet) {
        this.status = packet;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        var bound = status != null && status.bound();
        var hasPair = status != null && status.hasPair();
        var canRoute = status != null && status.canRoute();
        var intentExists = status != null && status.intentExists();

        repairButton.active = bound && hasPair && canRoute && intentExists;
        nextPairButton.active = bound && status != null && status.pairCount() > 1;
    }

    private void doRepair() {
        menu.sendRepairRoute();
    }

    private void doNextPair() {
        menu.sendNextPair();
    }

    @Override
    public void drawFG(GuiGraphicsExtractor guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (status == null) {
            return;
        }

        if (!status.bound()) {
            guiGraphics.text(font, GuiText.QuantumRoutingUnbound.text(), 8, 18, 0xFFFF60, false);
            return;
        }

        guiGraphics.text(font, GuiText.QuantumRoutingProfileLabel.text(shortId(status.profileId())).getString(), 8, 18,
                0xB0B0B0, false);

        if (!status.canView()) {
            guiGraphics.text(font, GuiText.QuantumRoutingNoView.text(), 8, 30, 0xFF8080, false);
            return;
        }

        if (status.pairCount() == 0 || !status.hasPair()) {
            guiGraphics.text(font, GuiText.QuantumRoutingNoPairs.text(), 8, 30, 0xFFFF60, false);
            return;
        }

        guiGraphics.text(font,
                GuiText.QuantumRoutingPairLabel.text(status.selectedIndex() + 1, status.pairCount()).getString(), 8, 30,
                0xE0E0E0, false);
        guiGraphics.text(font,
                GuiText.QuantumRoutingEndpointA
                        .text(status.endpointAShortId(), onlineText(status.endpointAOnline())).getString(),
                8, 42, 0xC0C0C0, false);
        guiGraphics.text(font,
                GuiText.QuantumRoutingEndpointB
                        .text(status.endpointBShortId(), onlineText(status.endpointBOnline())).getString(),
                8, 52, 0xC0C0C0, false);

        guiGraphics.text(font, Component.literal("Endpoints " + status.endpointCount() + "  Edges "
                + status.materializedEdgeCount() + "/" + status.intendedEdgeCount()), 8, 62, 0xC0C0C0, false);

        var state = QuantumRouteState.values()[status.routeStateOrdinal()];
        guiGraphics.text(font, GuiText.QuantumRoutingStateLabel.text(stateText(state)).getString(), 8, 76,
                stateColor(state), false);
        guiGraphics.text(font,
                GuiText.QuantumRoutingReasonLabel
                        .text(reasonText(QuantumRouteReason.values()[status.routeReasonOrdinal()])).getString(),
                8, 86, 0xB0B0B0, false);

        for (int i = 0; i < Math.min(2, status.edgeSummaries().size()); i++) {
            guiGraphics.text(font, Component.literal(status.edgeSummaries().get(i)), 8, 98 + i * 10, 0x909090,
                    false);
        }

        if (!status.lastResultMessage().isEmpty()) {
            guiGraphics.text(font, GuiText.QuantumRoutingLastResult.text(status.lastResultMessage()).getString(), 8,
                    118, 0xC0C0C0, false);
        }
    }

    private String onlineText(boolean online) {
        return (online ? GuiText.QuantumRoutingEndpointOnline : GuiText.QuantumRoutingEndpointOffline).text()
                .getString();
    }

    private static String stateText(QuantumRouteState state) {
        return switch (state) {
            case ACTIVE -> GuiText.QuantumRoutingStateActive.text().getString();
            case DEGRADED -> GuiText.QuantumRoutingStateDegraded.text().getString();
            case BLOCKED -> GuiText.QuantumRoutingStateBlocked.text().getString();
            case INACTIVE -> GuiText.QuantumRoutingStateInactive.text().getString();
        };
    }

    private static int stateColor(QuantumRouteState state) {
        return switch (state) {
            case ACTIVE -> 0x60FF60;
            case DEGRADED -> 0xFFFF60;
            case BLOCKED -> 0xFF8080;
            case INACTIVE -> 0xB0B0B0;
        };
    }

    private static String reasonText(QuantumRouteReason reason) {
        return switch (reason) {
            case NONE -> GuiText.QuantumRoutingReasonNone.text().getString();
            case NO_INTENT -> GuiText.QuantumRoutingReasonNoIntent.text().getString();
            case ENDPOINT_UNLOADED -> GuiText.QuantumRoutingReasonEndpointUnloaded.text().getString();
            case PEER_MISSING -> GuiText.QuantumRoutingReasonPeerMissing.text().getString();
            case CREDENTIAL_SUSPENDED -> GuiText.QuantumRoutingReasonCredentialSuspended.text().getString();
            case CREDENTIAL_REVOKED -> GuiText.QuantumRoutingReasonCredentialRevoked.text().getString();
            case SECURITY_DOMAIN_MISMATCH -> GuiText.QuantumRoutingReasonSecurityMismatch.text().getString();
            case CONNECTION_MISSING -> GuiText.QuantumRoutingReasonConnectionMissing.text().getString();
            case ENDPOINT_CONFLICT -> GuiText.QuantumRoutingReasonEndpointConflict.text().getString();
            case CONTROLLER_UNBOUND -> GuiText.QuantumRoutingReasonControllerUnbound.text().getString();
            case UNSUPPORTED_IN_ROUND_11 -> GuiText.QuantumRoutingReasonUnsupported.text().getString();
        };
    }

    private String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
