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

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.QuantumKeyCardData;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.SecurityDomainState;
import appeng.blockentity.misc.QuantumKeyImprinterBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.network.clientbound.QuantumKeyImprinterStatusPacket;
import appeng.me.quantum.QuantumCredentialService;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;

/**
 * Server-authoritative editor/issuer for the Quantum Key Imprinter (see {@link QuantumKeyImprinterBlockEntity}'s own
 * javadoc for the full provisioning-device shape this mirrors from {@code SecurityStationMenu}). Deliberately zero
 * inventory slots — exactly like {@code SecurityStationMenu} and {@code NetworkStatusMenu} before it (see
 * {@code SecurityStationMenu}'s own javadoc for why that is an accepted, precedented shape in this codebase, not a
 * corner being cut): every action here reads/writes the ITEM CURRENTLY IN THE ACTING PLAYER'S MAIN HAND at the moment
 * the button is pressed, resolved fresh server-side on every single action — never a slot the client could desync from
 * what the server actually holds authoritative.
 * <p/>
 * Every mutating action re-checks permission via {@link QuantumCredentialService} on the server, regardless of what
 * this menu/its screen displayed — the screen is a pure view + input surface, never authoritative (mirrors
 * {@code SecurityStationScreen}'s own documented relationship to {@code SecurityStationMenu}).
 */
public class QuantumKeyImprinterMenu extends AEBaseMenu {
    public static final MenuType<QuantumKeyImprinterMenu> TYPE = MenuTypeBuilder
            .create(QuantumKeyImprinterMenu::new, QuantumKeyImprinterBlockEntity.class)
            .build("quantum_key_imprinter");

    public static final ClientActionKey<Void> ISSUE = new ClientActionKey<>("quantum_key_imprinter_issue");
    public static final ClientActionKey<Void> REVOKE = new ClientActionKey<>("quantum_key_imprinter_revoke");
    public static final ClientActionKey<Void> SUSPEND = new ClientActionKey<>("quantum_key_imprinter_suspend");
    public static final ClientActionKey<Void> RESUME = new ClientActionKey<>("quantum_key_imprinter_resume");

    private final QuantumKeyImprinterBlockEntity imprinter;
    private String lastResultMessage = "";

    public QuantumKeyImprinterMenu(int id, Inventory ip, QuantumKeyImprinterBlockEntity host) {
        super(TYPE, id, ip, host);
        this.imprinter = host;

        registerClientAction(ISSUE, this::doIssue);
        registerClientAction(REVOKE, this::doRevoke);
        registerClientAction(SUSPEND, this::doSuspend);
        registerClientAction(RESUME, this::doResume);

        // Deliberately no player inventory slots - see class javadoc.
    }

    private void doIssue() {
        var player = getPlayer();
        if (player == null) {
            return;
        }
        var stack = player.getMainHandItem();
        if (!stack.is(AEItems.QUANTUM_KEY_BLANK.asItem())) {
            lastResultMessage = "not-holding-blank";
            return;
        }
        var outcome = imprinter.issueCredential(IActionSource.ofPlayer(player));
        lastResultMessage = outcome.result().name();
        if (outcome.result() != QuantumCredentialService.Result.OK || outcome.credential() == null) {
            return;
        }
        var credential = outcome.credential();
        var data = new QuantumKeyCardData(credential.profileId().id(), credential.id().id(), credential.revision(),
                credential.protocolVersion());

        var encoded = new ItemStack(AEItems.QUANTUM_KEY.asItem());
        encoded.set(AEComponents.QUANTUM_KEY_CARD_DATA, data);

        // Consume exactly ONE blank from the stack in hand and hand back exactly one encoded key - never a bulk
        // transform, mirroring how a physical imprinting device would work one card at a time.
        stack.shrink(1);
        if (!player.getInventory().add(encoded)) {
            player.drop(encoded, false);
        }
    }

    private void doRevoke() {
        withHeldCredential((actor, credentialId) -> {
            var result = imprinter.revokeCredential(actor, credentialId);
            lastResultMessage = result.name();
        });
    }

    private void doSuspend() {
        withHeldCredential((actor, credentialId) -> {
            var result = imprinter.suspendCredential(actor, credentialId);
            lastResultMessage = result.name();
        });
    }

    private void doResume() {
        withHeldCredential((actor, credentialId) -> {
            var result = imprinter.resumeCredential(actor, credentialId);
            lastResultMessage = result.name();
        });
    }

    private interface CredentialAction {
        void run(IActionSource actor, QuantumCredentialId credentialId);
    }

    private void withHeldCredential(CredentialAction action) {
        var player = getPlayer();
        if (player == null) {
            return;
        }
        var stack = player.getMainHandItem();
        var data = stack.get(AEComponents.QUANTUM_KEY_CARD_DATA);
        if (data == null) {
            lastResultMessage = "not-holding-encoded-key";
            return;
        }
        action.run(IActionSource.ofPlayer(player), new QuantumCredentialId(data.credentialId()));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && getPlayer() instanceof ServerPlayer serverPlayer) {
            sendPacketToClient(buildStatusPacket(serverPlayer));
        }
    }

    private QuantumKeyImprinterStatusPacket buildStatusPacket(ServerPlayer viewer) {
        var state = imprinter.getDomainState();
        var stateOrdinal = (state != null ? state : SecurityDomainState.LEGACY).ordinal();
        var profileId = imprinter.getProfileId();
        var domainId = imprinter.getDomainId();

        var heldStack = viewer.getMainHandItem();
        var heldDescription = describeHeld(heldStack);

        return new QuantumKeyImprinterStatusPacket(
                stateOrdinal,
                profileId != null,
                profileId != null ? profileId.id() : new UUID(0, 0),
                domainId != null ? domainId.id() : new UUID(0, 0),
                heldDescription,
                lastResultMessage);
    }

    private String describeHeld(ItemStack stack) {
        if (stack.is(AEItems.QUANTUM_KEY_BLANK.asItem())) {
            return "blank";
        }
        var data = stack.get(AEComponents.QUANTUM_KEY_CARD_DATA);
        if (data != null) {
            return "encoded:" + data.profileId() + ":" + data.credentialId() + ":" + data.revision();
        }
        return "";
    }

    // ---------------------------------------------------------------------
    // Client-side entry points, called by QuantumKeyImprinterScreen.
    // ---------------------------------------------------------------------

    public void sendIssue() {
        if (isClientSide()) {
            sendClientAction(ISSUE);
        }
    }

    public void sendRevoke() {
        if (isClientSide()) {
            sendClientAction(REVOKE);
        }
    }

    public void sendSuspend() {
        if (isClientSide()) {
            sendClientAction(SUSPEND);
        }
    }

    public void sendResume() {
        if (isClientSide()) {
            sendClientAction(RESUME);
        }
    }
}
