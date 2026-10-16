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

package appeng.block.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.networking.pathing.CapacityTier;
import appeng.api.networking.security.IActionSource;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.misc.QuantumEndpointBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.me.quantum.QuantumCredentialService;

/**
 * Round 10 ("Mk I PHYSICAL QUANTUM PAIR"): the physical Quantum Endpoint block. Right-clicking it with an "Encoded
 * Quantum Key" (see {@link appeng.items.tools.QuantumKeyItem}) triggers server-authoritative activation via
 * {@link QuantumEndpointBlockEntity#activateWithKey} — every actual authorization/state decision happens there and,
 * beneath it, in {@link QuantumCredentialService}; this class only reads the held item and reports the outcome.
 */
public class QuantumEndpointBlock extends AEBaseEntityBlock<QuantumEndpointBlockEntity> {
    private final CapacityTier capacityTier;

    public QuantumEndpointBlock(Properties p) {
        this(p, CapacityTier.STANDARD);
    }

    public QuantumEndpointBlock(Properties p, CapacityTier capacityTier) {
        super(metalProps(p));
        this.capacityTier = capacityTier;
    }

    public CapacityTier getCapacityTier() {
        return capacityTier;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel && heldItem.is(AEItems.QUANTUM_KEY.asItem())) {
            var be = this.getBlockEntity(level, pos);
            if (be != null) {
                var anchorKey = player.getOffhandItem();
                var result = hand == InteractionHand.MAIN_HAND && anchorKey.is(AEItems.QUANTUM_KEY.asItem())
                        ? be.activateAsHubMember(heldItem, anchorKey, IActionSource.ofPlayer(player))
                        : be.activateWithKey(heldItem, IActionSource.ofPlayer(player));
                player.sendSystemMessage(feedbackFor(result));
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    private static net.minecraft.network.chat.Component feedbackFor(QuantumCredentialService.Result result) {
        return switch (result) {
            case OK -> GuiText.QuantumEndpointActivated.text();
            case NOT_FOUND -> GuiText.QuantumEndpointNotEncoded.text();
            case FORBIDDEN -> GuiText.QuantumEndpointForbidden.text();
            case REVOKED -> GuiText.QuantumEndpointRevoked.text();
            case SUSPENDED -> GuiText.QuantumEndpointSuspended.text();
            case CONFLICT -> GuiText.QuantumEndpointConflict.text();
            case STALE_REVISION -> GuiText.QuantumEndpointStaleRevision.text();
            case WRONG_PROFILE -> GuiText.QuantumEndpointWrongProfile.text();
        };
    }
}
