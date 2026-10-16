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

package appeng.items.tools;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import appeng.api.ids.AEComponents;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.items.AEBaseItem;

/**
 * "Encoded Quantum Key" — the physical credential card a Quantum Key Imprinter produces from a blank (see
 * {@code appeng.core.definitions.AEItems#QUANTUM_KEY_BLANK}/{@code #QUANTUM_KEY}). Carries only
 * {@link appeng.api.implementations.items.QuantumKeyCardData} (see that class's own javadoc for why it is deliberately
 * minimal and never a source of truth) — this item class itself holds no logic beyond displaying that data; every
 * activation/issue/revoke/suspend/rebind decision is made server-side by
 * {@code appeng.me.quantum.QuantumCredentialService} against the live
 * {@code appeng.me.quantum.QuantumCredentialRegistry}, never by this item.
 */
public class QuantumKeyItem extends AEBaseItem {
    public QuantumKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
            Consumer<Component> lines, TooltipFlag tooltipFlags) {
        var data = stack.get(AEComponents.QUANTUM_KEY_CARD_DATA);
        if (data == null) {
            lines.accept(Tooltips.of(GuiText.QuantumKeyUnencoded.text()));
            return;
        }
        lines.accept(Tooltips.of(GuiText.QuantumKeyProfile.text(shortId(data.profileId()))));
        lines.accept(Tooltips.of(GuiText.QuantumKeyCredential.text(shortId(data.credentialId()))));
        lines.accept(Tooltips.of(GuiText.QuantumKeyRevision.text(data.revision())
                .copy().withStyle(ChatFormatting.GRAY)));
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }
}
