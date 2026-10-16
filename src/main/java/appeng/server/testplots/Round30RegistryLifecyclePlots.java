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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.SecurityDomainRegistry;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

/**
 * Round 30 - the registry's <b>lifecycle</b>, proven on a real device in a real world.
 * <p/>
 * Round 29 established the registry as the authority and proved clone isolation, but it left the other half of the
 * lifecycle open: nothing ever retired a binding. Because a block's {@code MachineKey} is
 * {@code dimension + position + "block"}, that gap had teeth - break a machine, build a new one on the same spot, and
 * the newcomer would find the dead machine's binding and inherit its grants.
 * <p/>
 * These plots close it and, just as importantly, pin the distinction the fix depends on: <b>permanent destruction
 * retires an identity, chunk unload must not</b>.
 *
 * <ul>
 * <li><b>L1</b> {@code round30_break_replace_isolation} - a replacement at the exact same position gets its own
 * identity and does not inherit the destroyed machine's grant.</li>
 * <li><b>L2</b> {@code round30_tombstone_not_resurrected} - the retired identity cannot be brought back by a machine
 * elsewhere that still carries it in its NBT, which is what a stale blueprint or an old backup looks like.</li>
 * </ul>
 */
@TestPlotClass
public class Round30RegistryLifecyclePlots {
    private Round30RegistryLifecyclePlots() {
    }

    private static AEItemKey stored() {
        return AEItemKey.of(Items.DIAMOND);
    }

    // ==================================================================
    // L1 - a replacement must not inherit a destroyed device's rights
    // ==================================================================

    @TestPlot("round30_break_replace_isolation")
    public static void breakAndReplaceIsolation(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var itfPos = origin.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.drive(origin.above()).addItemCell64k().add(stored(), 64);
        plot.block(itfPos, AEBlocks.INTERFACE);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var station = helper.getBlockEntity(stationPos, SecurityStationBlockEntity.class);
                    helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                            "network must be SECURED or this plot proves nothing", stationPos);

                    var a = helper.getBlockEntity(itfPos, InterfaceBlockEntity.class);
                    var idA = a.getDeviceId();

                    // Grant the original device EXTRACT and prove it really works, so the denial below is a security
                    // decision rather than a broken fixture.
                    acl(helper, origin).grant(PrincipalId.ofDevice(idA), Set.of(SecurityPermissions.EXTRACT));
                    helper.check(extractAs(helper, origin, a) == 8,
                            "L1 precondition: the original device must actually be able to extract", itfPos);
                })
                // Destroy it for good, then build a fresh one on the very same block.
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(itfPos), false))
                .thenIdle(5)
                .thenExecute(() -> helper.getLevel().setBlockAndUpdate(helper.absolutePos(itfPos),
                        AEBlocks.INTERFACE.block().defaultBlockState()))
                .thenIdle(20)
                .thenExecute(() -> {
                    var b = helper.getBlockEntity(itfPos, InterfaceBlockEntity.class);
                    var idB = b.getDeviceId();

                    helper.check(idB != null, "the replacement must have an identity of its own", itfPos);
                    helper.check(extractAs(helper, origin, b) == 0,
                            "L1: a machine built where another was destroyed must NOT inherit its grant - the "
                                    + "position is the same, the device is not",
                            itfPos);

                    // ...and it works once granted in its own right, so this is isolation and not a lockout.
                    acl(helper, origin).grant(PrincipalId.ofDevice(idB), Set.of(SecurityPermissions.EXTRACT));
                    helper.check(extractAs(helper, origin, b) == 8,
                            "L1: the replacement works under its own grant", itfPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // L2 - a retired identity stays retired
    // ==================================================================

    @TestPlot("round30_tombstone_not_resurrected")
    public static void tombstoneIsNotResurrected(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var itfPos = origin.east();
        var elsewhere = origin.east().east().east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.cable(itfPos.above());
        plot.cable(elsewhere.above());
        plot.drive(origin.above()).addItemCell64k().add(stored(), 64);
        plot.block(itfPos, AEBlocks.INTERFACE);
        plot.block(elsewhere, AEBlocks.INTERFACE);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var a = helper.getBlockEntity(itfPos, InterfaceBlockEntity.class);
                    var idA = a.getDeviceId();
                    acl(helper, origin).grant(PrincipalId.ofDevice(idA), Set.of(SecurityPermissions.EXTRACT));

                    // Destroy A. Its identity is now retired - not merely unbound.
                    var registry = DevicePrincipalRegistry.get(helper.getLevel());
                    helper.getLevel().destroyBlock(helper.absolutePos(itfPos), false);
                    helper.check(registry.isTombstoned(idA),
                            "L2 precondition: destroying a device must tombstone its identity, not free it",
                            itfPos);

                    // A different, living machine now presents A's identity - what a stale blueprint or an old
                    // backup looks like from the registry's side.
                    var other = helper.getBlockEntity(elsewhere, InterfaceBlockEntity.class);
                    var key = MachineKey.of(other);
                    helper.check(key != null, "the second machine must have a resolvable key", elsewhere);
                    var resolved = registry.resolveOrRegister(key, idA);

                    helper.check(!resolved.equals(idA),
                            "L2: a retired identity must never be handed back out, however it is claimed",
                            elsewhere);
                    helper.check(extractAs(helper, origin, other) == 0,
                            "L2: and the dead device's grant must not reach the claimant", elsewhere);
                })
                .thenSucceed());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static long extractAs(PlotTestHelper helper, BlockPos gridPos, InterfaceBlockEntity device) {
        var source = IActionSource.ofMachine(() -> device.getMainNode().getNode());
        return helper.getGrid(gridPos).getStorageService().getInventory()
                .extract(stored(), 8, Actionable.MODULATE, source);
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
