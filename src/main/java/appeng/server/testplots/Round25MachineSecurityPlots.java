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
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.MachineSource;
import appeng.me.security.SecurityDomainRegistry;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.storagebus.StorageBusPart;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;

/**
 * Round 25 - the mandatory physical security matrix (brief §28).
 * <p/>
 * These are real in-world networks: a real {@link SecurityStationBlockEntity} bootstrapping a real
 * {@link SecurityDomainState#SECURED} domain, real Import/Export Bus parts ticking on their own, and the real
 * {@code SecurityAwareMEStorage} in the path. Nothing is mocked or stubbed - in particular the device id read here is
 * the one the part actually persisted, and the grant is written into the real {@link SecurityDomainRegistry}.
 *
 * <ul>
 * <li><b>PT1</b> {@code round25_export_bus_device} - an Export Bus with no grant exports nothing; granting
 * {@code EXTRACT} to <em>that part's own device principal</em> makes it work.</li>
 * <li><b>PT2</b> {@code round25_import_bus_device} - the same for an Import Bus and {@code INJECT}.</li>
 * <li><b>PT3</b> {@code round25_pass_through_no_laundering} - a Storage Bus, which deliberately has no identity of its
 * own, cannot be used to launder access: an unrelated fully-granted device principal does not open the network, and the
 * Storage Bus itself is not an {@link IDeviceIdentity} at all.</li>
 * </ul>
 */
@TestPlotClass
public class Round25MachineSecurityPlots {
    private Round25MachineSecurityPlots() {
    }

    // ==================================================================
    // PT1 - Export Bus
    // ==================================================================

    @TestPlot("round25_export_bus_device")
    public static void exportBusDevice(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var busPos = origin.east();
        var chestPos = busPos.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        securityStation(plot, stationPos);
        plot.drive(origin.above()).addItemCell64k().add(AEItemKey.of(Items.DIAMOND), 64);
        plot.cable(busPos).part(Direction.EAST, AEParts.EXPORT_BUS, bus -> {
            bus.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
        });
        plot.chest(chestPos);

        plot.test(helper -> {
            var probe = new Object() {
                UUID deviceId;
            };
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(origin))
                    .thenExecute(() -> {
                        var station = helper.getBlockEntity(stationPos, SecurityStationBlockEntity.class);
                        helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                                "network must be SECURED for this test to mean anything", stationPos);
                        var bus = helper.getPart(busPos, Direction.EAST, ExportBusPart.class);
                        helper.check(bus instanceof IDeviceIdentity,
                                "Round 25: an Export Bus must carry its own device identity", busPos);
                        probe.deviceId = ((IDeviceIdentity) bus).getDeviceId();
                        helper.check(probe.deviceId != null, "device id must not be null", busPos);
                        helper.check(acl(helper, origin).getPermissions(PrincipalId.EVERYONE).isEmpty(),
                                "EVERYONE must have no grants - a blanket grant would invalidate this test", origin);
                    })
                    // Phase 1: no grant at all. The bus ticks repeatedly and must move nothing.
                    .thenIdle(60)
                    .thenExecute(() -> helper.check(
                            helper.countContainerContentAt(chestPos).isEmpty(),
                            "PT1: an Export Bus with no EXTRACT grant must export nothing on a SECURED network",
                            chestPos))
                    // Phase 2: grant EXTRACT to this exact device - and nothing else.
                    .thenExecute(() -> acl(helper, origin).grant(PrincipalId.ofDevice(probe.deviceId),
                            Set.of(SecurityPermissions.EXTRACT)))
                    .thenIdle(60)
                    .thenExecute(() -> helper.check(
                            helper.countContainerContentAt(chestPos).get(AEItemKey.of(Items.DIAMOND)) > 0,
                            "PT1: once its own device principal holds EXTRACT, the Export Bus works", chestPos))
                    .thenSucceed();
        });
    }

    // ==================================================================
    // PT2 - Import Bus
    // ==================================================================

    @TestPlot("round25_import_bus_device")
    public static void importBusDevice(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var busPos = origin.east();
        var chestPos = busPos.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        securityStation(plot, stationPos);
        plot.drive(origin.above()).addItemCell64k();
        plot.cable(busPos).part(Direction.EAST, AEParts.IMPORT_BUS);
        plot.chest(chestPos, new ItemStack(Items.DIAMOND, 16));

        plot.test(helper -> {
            var probe = new Object() {
                UUID deviceId;
            };
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(origin))
                    .thenExecute(() -> {
                        var station = helper.getBlockEntity(stationPos, SecurityStationBlockEntity.class);
                        helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                                "network must be SECURED", stationPos);
                        var bus = helper.getPart(busPos, Direction.EAST, ImportBusPart.class);
                        helper.check(bus instanceof IDeviceIdentity,
                                "Round 25: an Import Bus must carry its own device identity", busPos);
                        probe.deviceId = ((IDeviceIdentity) bus).getDeviceId();
                    })
                    .thenIdle(60)
                    .thenExecute(() -> {
                        helper.check(
                                helper.countContainerContentAt(chestPos).get(AEItemKey.of(Items.DIAMOND)) == 16,
                                "PT2: an Import Bus with no INJECT grant must import nothing", chestPos);
                        helper.assertNetworkContainsNot(origin, Items.DIAMOND);
                    })
                    .thenExecute(() -> acl(helper, origin).grant(PrincipalId.ofDevice(probe.deviceId),
                            Set.of(SecurityPermissions.INJECT)))
                    .thenIdle(60)
                    .thenExecute(() -> helper.assertNetworkContains(origin, Items.DIAMOND))
                    .thenSucceed();
        });
    }

    // ==================================================================
    // PT3 - pass-through must not launder
    // ==================================================================

    @TestPlot("round25_pass_through_no_laundering")
    public static void passThroughNoLaundering(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var busPos = origin.east();
        var chestPos = busPos.east();

        plot.creativeEnergyCell(origin.below());
        // A real, grid-connected device to act as the legitimate principal in the positive control below. It is not
        // required to export anything - it is here purely because Round 27 only accepts a device principal that is
        // backed by an actual registered grid node on this grid.
        plot.cable(origin).part(Direction.UP, AEParts.EXPORT_BUS);
        securityStation(plot, stationPos);
        plot.cable(busPos).part(Direction.EAST, AEParts.STORAGE_BUS);
        plot.chest(chestPos, new ItemStack(Items.DIAMOND, 16));

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(origin))
                    .thenIdle(20)
                    .thenExecute(() -> {
                        var grid = helper.getGrid(origin);
                        var storageBus = helper.getPart(busPos, Direction.EAST, StorageBusPart.class);

                        // Classification E: the Storage Bus is deliberately NOT an actor of its own.
                        helper.check(!(storageBus instanceof IDeviceIdentity),
                                "Round 25 classification E: a Storage Bus must NOT have a device identity - it only "
                                        + "mounts foreign storage and carries the caller's own principal",
                                busPos);

                        // The chest's contents really are reachable through the storage bus, i.e. the mount works
                        // and the test below is testing the security decision, not an empty inventory.
                        var raw = new appeng.api.stacks.KeyCounter();
                        storageBus.getInternalHandler().getAvailableStacks(raw);
                        helper.check(raw.get(AEItemKey.of(Items.DIAMOND)) == 16,
                                "the Storage Bus must actually see the chest for this test to be meaningful",
                                busPos);

                        // An unrelated device principal is granted EVERY permission there is...
                        var strangerId = UUID.randomUUID();
                        acl(helper, origin).grant(PrincipalId.ofDevice(strangerId),
                                Set.of(SecurityPermissions.values()));

                        // ... and a *different*, ungranted device still cannot pull those items out of the network
                        // through the Storage Bus. The check happens at the network boundary, in front of the mount.
                        var deniedId = UUID.randomUUID();
                        var denied = new MachineSource(new StandaloneDevice(deniedId));
                        var extracted = grid.getStorageService().getInventory()
                                .extract(AEItemKey.of(Items.DIAMOND), 16, Actionable.MODULATE, denied);
                        helper.check(extracted == 0,
                                "PT3/STOP-6: a denied actor must not reach the chest through the Storage Bus, even "
                                        + "when another device holds every permission",
                                busPos);

                        // Round 27 / GT7 - the forged-device regression, in a physical network.
                        //
                        // Granting EXTRACT to the *claimed* id of a fabricated stand-in must not authorize it. Before
                        // Round 27 this very call returned 16: SecurityService took the id straight off any object
                        // implementing the public IDeviceIdentity, so a two-method object with no grid node at all was
                        // authorized as whatever device it named. That is FINDING-3, and this assertion is now its
                        // permanent regression test.
                        acl(helper, origin).grant(PrincipalId.ofDevice(deniedId),
                                Set.of(SecurityPermissions.EXTRACT));
                        var forged = grid.getStorageService().getInventory()
                                .extract(AEItemKey.of(Items.DIAMOND), 16, Actionable.MODULATE, denied);
                        helper.check(forged == 0,
                                "Round 27/FINDING-3: a fabricated device with no grid node must NOT be authorized even "
                                        + "when the id it claims holds EXTRACT - a claimed id is not provenance",
                                busPos);

                        // The positive control, now carried by a *real* device: the Export Bus part placed on this
                        // very grid. Granting EXTRACT to its device principal makes the same call through the same
                        // Storage Bus mount succeed, which proves the two zeroes above are security decisions and not
                        // a broken fixture - and that Round 27 did not simply deny everything.
                        var realDevice = helper.getPart(origin, Direction.UP, ExportBusPart.class);
                        helper.check(realDevice instanceof IDeviceIdentity,
                                "the positive control needs a real device principal", origin);
                        var realId = ((IDeviceIdentity) realDevice).getDeviceId();
                        acl(helper, origin).grant(PrincipalId.ofDevice(realId), Set.of(SecurityPermissions.EXTRACT));
                        var allowed = grid.getStorageService().getInventory()
                                .extract(AEItemKey.of(Items.DIAMOND), 16, Actionable.MODULATE,
                                        new MachineSource(realDevice));
                        helper.check(allowed == 16,
                                "PT3 positive control: a real grid-connected device holding EXTRACT still reaches the "
                                        + "chest through the Storage Bus",
                                busPos);
                    })
                    .thenSucceed();
        });
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A minimal standalone {@link IDeviceIdentity} action host used to play the part of "some other machine on this
     * network" without having to place one.
     */
    private record StandaloneDevice(UUID id)
            implements
                appeng.api.networking.security.IActionHost,
                IDeviceIdentity {
        @Override
        public appeng.api.networking.IGridNode getActionableNode() {
            return null;
        }

        @Override
        public UUID getDeviceId() {
            return id;
        }
    }

    private static appeng.me.security.SecurityDomain acl(appeng.server.testworld.PlotTestHelper helper,
            BlockPos gridPos) {
        var grid = helper.getGrid(gridPos);
        var security = grid.getService(ISecurityService.class);
        var domainId = security.getDomainId().orElseThrow(
                () -> new IllegalStateException("network is not SECURED, so it has no ACL to edit"));
        return SecurityDomainRegistry.get(helper.getLevel()).getDomain(domainId);
    }

    /**
     * Same helper as {@code P2PCrossDomainSecurityPlots}: test-plot placement never calls {@code Block#setPlacedBy}, so
     * the station's owning player has to be set explicitly or {@code tryBootstrapDomain()} silently no-ops and the
     * network stays LEGACY - which would make every assertion in this file vacuous.
     */
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

    @SuppressWarnings("unused")
    private static IActionSource unusedImportAnchor() {
        return IActionSource.empty();
    }
}
