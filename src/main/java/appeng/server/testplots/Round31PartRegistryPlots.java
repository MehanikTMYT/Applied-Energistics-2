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

package appeng.server.testplots;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.SecurityDomainRegistry;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

/**
 * Round 31 - the part half of the device registry, proven physically.
 * <p/>
 * Round 30 migrated the three block DEVICE families and stopped at the parts, because the obvious retirement signal
 * ({@code IPart#removeFromWorld()}) fires on chunk unload as well as on destruction. Round 31 uses the two signals that
 * do distinguish themselves - {@code CableBusContainer#removePartFromSide} for one part,
 * {@code CableBusBlockEntity#preRemoveSideEffects} for the whole block - and these plots hold that wiring to account.
 *
 * <ul>
 * <li><b>PT-P1</b> {@code round31_two_parts_same_host} - two DEVICE parts on one cable bus are separate principals, and
 * a grant to one does nothing for the other. This is what the slot discriminator in {@link MachineKey} exists for.</li>
 * <li><b>PT-P3</b> {@code round31_part_same_slot_replacement} - removing a part and putting a fresh one in the same
 * slot yields a new principal; the removed part's grant does not carry over.</li>
 * <li><b>PT-P4</b> {@code round31_whole_host_rebuild} - destroying the entire cable bus retires every part it carried,
 * so a rebuilt host with the same parts inherits nothing.</li>
 * </ul>
 */
@TestPlotClass
public class Round31PartRegistryPlots {
    private Round31PartRegistryPlots() {
    }

    private static AEItemKey stored() {
        return AEItemKey.of(Items.DIAMOND);
    }

    // ==================================================================
    // PT-P1 - two parts on one host are two principals
    // ==================================================================

    @TestPlot("round31_two_parts_same_host")
    public static void twoPartsOnOneHost(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var busPos = origin.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.drive(origin.above()).addItemCell64k().add(stored(), 64);
        plot.cable(busPos)
                .part(Direction.EAST, AEParts.EXPORT_BUS)
                .part(Direction.UP, AEParts.IMPORT_BUS);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    requireSecured(helper, stationPos);

                    var east = deviceAt(helper, busPos, Direction.EAST);
                    var up = deviceAt(helper, busPos, Direction.UP);

                    helper.check(east != null && up != null,
                            "both parts must carry device identities", busPos);
                    helper.check(!east.getDeviceId().equals(up.getDeviceId()),
                            "PT-P1: two parts on one host must be separate principals - this is exactly what the "
                                    + "slot discriminator in the machine key is for",
                            busPos);

                    // A grant to one is not a grant to the other.
                    acl(helper, origin).grant(PrincipalId.ofDevice(east.getDeviceId()),
                            Set.of(SecurityPermissions.EXTRACT));
                    helper.check(
                            acl(helper, origin).getEffectivePermissions(PrincipalId.ofDevice(up.getDeviceId()))
                                    .isEmpty(),
                            "PT-P1: granting one part must leave its neighbour with nothing", busPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PT-P3 - a replacement in the same slot is a new device
    // ==================================================================

    @TestPlot("round31_part_same_slot_replacement")
    public static void samePartSlotReplacement(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var busPos = origin.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.drive(origin.above()).addItemCell64k().add(stored(), 64);
        plot.cable(busPos).part(Direction.EAST, AEParts.EXPORT_BUS);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    requireSecured(helper, stationPos);

                    var first = deviceAt(helper, busPos, Direction.EAST);
                    helper.check(first != null, "the part must carry a device identity", busPos);
                    var idFirst = first.getDeviceId();
                    acl(helper, origin).grant(PrincipalId.ofDevice(idFirst), Set.of(SecurityPermissions.EXTRACT));

                    // Remove that part for good, then put a fresh one back in the very same slot.
                    host(helper, busPos).removePartFromSide(Direction.EAST);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    host(helper, busPos).addPart(AEParts.EXPORT_BUS.asItem(), Direction.EAST, null);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    var second = deviceAt(helper, busPos, Direction.EAST);
                    helper.check(second != null, "the replacement part must carry an identity", busPos);

                    helper.check(
                            acl(helper, origin).getEffectivePermissions(PrincipalId.ofDevice(second.getDeviceId()))
                                    .isEmpty(),
                            "PT-P3: a fresh part in the slot of a removed one must not inherit its grant - same "
                                    + "slot, different device",
                            busPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PT-P4 - destroying the whole host retires everything it carried
    // ==================================================================

    @TestPlot("round31_whole_host_rebuild")
    public static void wholeHostRebuild(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var busPos = origin.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.drive(origin.above()).addItemCell64k().add(stored(), 64);
        plot.cable(busPos).part(Direction.EAST, AEParts.EXPORT_BUS);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    requireSecured(helper, stationPos);

                    var before = deviceAt(helper, busPos, Direction.EAST);
                    helper.check(before != null, "the part must carry a device identity", busPos);
                    var idBefore = before.getDeviceId();
                    acl(helper, origin).grant(PrincipalId.ofDevice(idBefore), Set.of(SecurityPermissions.EXTRACT));

                    // Destroy the entire cable bus, parts and all.
                    helper.getLevel().destroyBlock(helper.absolutePos(busPos), false);

                    var registry = DevicePrincipalRegistry.get(helper.getLevel());
                    helper.check(registry.isTombstoned(idBefore),
                            "PT-P4: destroying the host must retire the identities of the parts it carried",
                            busPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static IPartHost host(PlotTestHelper helper, BlockPos relative) {
        return helper.getBlockEntity(relative, appeng.blockentity.networking.CableBusBlockEntity.class);
    }

    private static IDeviceIdentity deviceAt(PlotTestHelper helper, BlockPos relative, Direction side) {
        var part = host(helper, relative).getPart(side);
        return part instanceof IDeviceIdentity identity ? identity : null;
    }

    private static void requireSecured(PlotTestHelper helper, BlockPos stationPos) {
        var station = helper.getBlockEntity(stationPos, SecurityStationBlockEntity.class);
        helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                "network must be SECURED or this plot proves nothing", stationPos);
    }

    private static appeng.me.security.SecurityDomain acl(PlotTestHelper helper, BlockPos gridPos) {
        var grid = helper.getGrid(gridPos);
        var security = grid.getService(ISecurityService.class);
        var domainId = security.getDomainId().orElseThrow(
                () -> new IllegalStateException("network is not SECURED, so it has no ACL to edit"));
        return SecurityDomainRegistry.get(helper.getLevel()).getDomain(domainId);
    }

    private static void securityStation(PlotBuilder plot, BlockPos pos) {
        plot.block(pos, AEBlocks.SECURITY_STATION);
        plot.addBuildAction(new BuildAction() {
            @Override
            public BoundingBox getBoundingBox() {
                return new BoundingBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
            }

            @Override
            public void build(ServerLevel level, Player player, BlockPos plotOrigin) {
                var absPos = pos.offset(plotOrigin.getX(), plotOrigin.getY(), plotOrigin.getZ());
                if (level.getBlockEntity(absPos) instanceof SecurityStationBlockEntity station) {
                    station.setOwner(player);
                }
            }
        });
    }
}
