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

package appeng.blockentity.misc;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.security.SecurityDomain;
import appeng.me.security.SecurityDomainRegistry;

/**
 * A Security Station is an editor/access point for a {@link SecurityDomain} - it is <strong>not</strong> where the
 * domain's data actually lives (see {@link SecurityDomainRegistry}). Multiple Security Stations can be placed on the
 * same network, all editing the same domain; destroying every one of them does not revert the network to
 * {@link SecurityDomainState#LEGACY}, nor does it lose the ACL.
 * <p/>
 * Bootstrap semantics: when a Security Station joins a network that has never had a domain bound to it
 * ({@link SecurityDomainState#LEGACY}), it bootstraps a brand-new domain owned by whichever player placed it (via
 * {@link ISecurityService#bootstrapDomain}), which immediately starts being enforced across the whole network. If the
 * network already has a domain (placed on top of an already-secured network, or a second station added to one), this is
 * a no-op - the station just becomes another editor for the existing domain. If the network is in
 * {@link SecurityDomainState#CONFLICT}, bootstrapping is refused; the conflict must be resolved first (by splitting the
 * grid back apart, or via {@code /ae2 security recover}).
 */
public class SecurityStationBlockEntity extends AENetworkedBlockEntity {
    public SecurityStationBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    @Override
    public void onReady() {
        super.onReady();
        tryBootstrapDomain();
    }

    private void tryBootstrapDomain() {
        getMainNode().ifPresent((grid, node) -> {
            var security = getSecurityService(grid);
            if (security.getState() == SecurityDomainState.LEGACY) {
                var owner = node.getOwningPlayerProfileId();
                if (owner != null) {
                    security.bootstrapDomain(owner);
                }
            }
        });
    }

    /**
     * @return the domain this station is currently editing, or {@code null} if the network is not currently online, or
     *         is {@link SecurityDomainState#LEGACY}/{@link SecurityDomainState#CONFLICT}.
     */
    @Nullable
    public SecurityDomainId getEditedDomainId() {
        if (!getMainNode().isReady()) {
            return null;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        return getSecurityService(grid).getDomainId().orElse(null);
    }

    @Nullable
    public SecurityDomainState getDomainState() {
        if (!getMainNode().isReady()) {
            return null;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        return getSecurityService(grid).getState();
    }

    /**
     * Attempts to bootstrap a fresh domain owned by the given player, if the network is currently
     * {@link SecurityDomainState#LEGACY}. Exposed for the menu's "activate security" action - stations don't have to
     * bootstrap only from {@link #onReady()}, a player may want to secure a previously-open network on demand.
     */
    @Nullable
    public SecurityDomainId activate(UUID owner) {
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        return getSecurityService(grid).bootstrapDomain(owner);
    }

    private static ISecurityService getSecurityService(IGrid grid) {
        return grid.getService(ISecurityService.class);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(orientation.getSide(RelativeSide.BACK));
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }
}
