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

package appeng.menu.implementations;

import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.quantum.QuantumRouteReason;
import appeng.api.networking.quantum.QuantumRouteState;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.misc.QuantumRoutingControllerBlockEntity;
import appeng.core.network.clientbound.QuantumRoutingControllerStatusPacket;
import appeng.me.quantum.QuantumRoutingService;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;

/**
 * Round 11 ("Routing Controller foundation"): server-authoritative control surface for the Quantum Routing Controller.
 * <p/>
 * Follows {@code QuantumKeyImprinterMenu}'s established shape exactly — zero inventory slots, every action resolved
 * server-side at the moment the button is pressed, and a periodic status packet as the only thing the client ever
 * learns. <b>This menu contains no authorization logic of its own.</b> It never calls
 * {@code QuantumTopologyRegistry.addEdge}/{@code removeEdge}, never calls {@code GridHelper.createConnection}, never
 * destroys a {@code GridConnection}, and never touches the credential registry: each of the three mutating actions is
 * one forwarding call into {@link QuantumRoutingService} via the block entity, and that service performs the
 * {@code QUANTUM_ROUTE} check from scratch every single time. The {@code canView}/{@code canRoute} flags in the status
 * packet are a display courtesy only — a modified client that presses a "disabled" button gets a plain
 * {@code FORBIDDEN} from the server, because the button's state was never what was being trusted.
 */
public class QuantumRoutingControllerMenu extends AEBaseMenu {
    public static final MenuType<QuantumRoutingControllerMenu> TYPE = MenuTypeBuilder
            .create(QuantumRoutingControllerMenu::new, QuantumRoutingControllerBlockEntity.class)
            .build("quantum_routing_controller");

    public static final ClientActionKey<Void> REPAIR_ROUTE = new ClientActionKey<>("quantum_routing_repair");
    public static final ClientActionKey<Void> NEXT_PAIR = new ClientActionKey<>("quantum_routing_next_pair");

    private final QuantumRoutingControllerBlockEntity controller;
    private String lastResultMessage = "";

    public QuantumRoutingControllerMenu(int id, Inventory ip, QuantumRoutingControllerBlockEntity host) {
        super(TYPE, id, ip, host);
        this.controller = host;

        registerClientAction(REPAIR_ROUTE, this::doRepair);
        registerClientAction(NEXT_PAIR, this::doNextPair);

        // Deliberately no player inventory slots - same precedent as QuantumKeyImprinterMenu/SecurityStationMenu.
    }

    private void doRepair() {
        var player = getPlayer();
        if (player == null) {
            return;
        }
        lastResultMessage = controller.repairSelectedRoute(IActionSource.ofPlayer(player)).name();
    }

    /**
     * Purely cosmetic: moves the display cursor to the next pair of the bound profile. Requires no permission because
     * it grants no information by itself — the actual status read that follows is separately gated by
     * {@code QUANTUM_VIEW} inside the service, and returns nothing at all if the viewer lacks it.
     */
    private void doNextPair() {
        controller.cycleSelection();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && getPlayer() instanceof ServerPlayer serverPlayer) {
            sendPacketToClient(buildStatusPacket(serverPlayer));
        }
    }

    private QuantumRoutingControllerStatusPacket buildStatusPacket(ServerPlayer viewer) {
        var actor = IActionSource.ofPlayer(viewer);
        var profileId = controller.getProfileId();
        var pairs = controller.listPairs();
        var outcome = controller.viewSelectedLane(actor);

        var canView = outcome.result() == QuantumRoutingService.Result.OK;
        var status = outcome.status();

        // A viewer without QUANTUM_VIEW is told nothing whatsoever about the pair - not the endpoint ids, not the
        // dimensions, not even whether a route intent exists. "Forbidden" must not be a side channel.
        if (!canView || status == null) {
            return new QuantumRoutingControllerStatusPacket(
                    profileId != null,
                    profileId != null ? profileId.id() : new UUID(0, 0),
                    pairs.size(),
                    controller.getSelectedLaneIndex(),
                    false,
                    "", "", false, false, "", "",
                    false, false,
                    0, 0, 0, List.of(), List.of(),
                    QuantumRouteState.INACTIVE.ordinal(),
                    (profileId == null ? QuantumRouteReason.CONTROLLER_UNBOUND : QuantumRouteReason.NO_INTENT)
                            .ordinal(),
                    false,
                    false,
                    lastResultMessage);
        }

        var endpointA = status.endpoints().isEmpty() ? null : status.endpoints().getFirst();
        var endpointB = status.endpoints().size() < 2 ? null : status.endpoints().get(1);
        var endpointSummaries = status.endpoints().stream()
                .map(endpoint -> shortId(endpoint.endpointId()) + " " + (endpoint.online() ? "ONLINE" : "OFFLINE"))
                .toList();
        var edgeSummaries = status.edges().stream()
                .map(edge -> shortId(edge.a()) + "-" + shortId(edge.b()) + " "
                        + edge.state().name() + "/" + edge.reason().name())
                .toList();
        return new QuantumRoutingControllerStatusPacket(
                profileId != null,
                profileId != null ? profileId.id() : new UUID(0, 0),
                pairs.size(),
                controller.getSelectedLaneIndex(),
                !status.endpoints().isEmpty(),
                endpointA != null ? shortId(endpointA.endpointId()) : "",
                endpointB != null ? shortId(endpointB.endpointId()) : "",
                endpointA != null && endpointA.online(),
                endpointB != null && endpointB.online(),
                endpointA != null && endpointA.dimension() != null ? endpointA.dimension().identifier().toString() : "",
                endpointB != null && endpointB.dimension() != null ? endpointB.dimension().identifier().toString() : "",
                status.intendedEdgeCount() > 0,
                status.intendedEdgeCount() > 0 && status.materializedEdgeCount() == status.intendedEdgeCount(),
                status.endpoints().size(),
                status.intendedEdgeCount(),
                status.materializedEdgeCount(),
                endpointSummaries,
                edgeSummaries,
                status.state().ordinal(),
                status.reason().ordinal(),
                true,
                controller.hasRoutePermission(actor),
                lastResultMessage);
    }

    private static String shortId(
            @org.jetbrains.annotations.Nullable appeng.api.networking.quantum.QuantumEndpointId id) {
        if (id == null) {
            return "";
        }
        var text = id.id().toString();
        return text.substring(0, Math.min(8, text.length()));
    }

    // ---------------------------------------------------------------------
    // Client-side entry points, called by QuantumRoutingControllerScreen.
    // ---------------------------------------------------------------------

    public void sendRepairRoute() {
        if (isClientSide()) {
            sendClientAction(REPAIR_ROUTE);
        }
    }

    public void sendNextPair() {
        if (isClientSide()) {
            sendClientAction(NEXT_PAIR);
        }
    }
}
