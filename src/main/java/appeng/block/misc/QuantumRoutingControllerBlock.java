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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.misc.QuantumRoutingControllerBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.QuantumRoutingControllerMenu;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;

/**
 * Round 11 ("Routing Controller foundation"). Deliberately identical in shape to {@link QuantumKeyImprinterBlock}: a
 * plain, single-node machine block whose only interaction is opening its server-authoritative menu. All authorization
 * lives in {@code QuantumRoutingService}; breaking this block never affects any existing Quantum PAIR (see
 * {@link QuantumRoutingControllerBlockEntity}'s class javadoc).
 */
public class QuantumRoutingControllerBlock extends AEBaseEntityBlock<QuantumRoutingControllerBlockEntity> {
    public QuantumRoutingControllerBlock(Properties p) {
        super(metalProps(p));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            var be = this.getBlockEntity(level, pos);
            if (be != null && !InteractionUtil.isInAlternateUseMode(player)) {
                MenuOpener.open(QuantumRoutingControllerMenu.TYPE, player, MenuLocators.forBlockEntity(be));
            }
        }

        return InteractionResult.SUCCESS;
    }
}
