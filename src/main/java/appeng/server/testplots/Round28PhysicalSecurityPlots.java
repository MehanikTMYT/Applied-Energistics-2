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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.security.SecurityDomainRegistry;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

/**
 * Round 28 Phase A - <b>physical closure of what Round 27 could only assert in unit tests.</b>
 * <p/>
 * Round 27 fixed FINDING-2 by wrapping {@code MEChestBlockEntity#getMEStorage(Direction)} in
 * {@code SecurityAwareMEStorage}, but its regression test <em>mirrored</em> that wrapper in a fixture rather than
 * calling the real block entity - a live level is needed for that. The report said so plainly and listed it as a gap.
 * These plots close it: a real ME Chest, a real storage cell, a real network, and the capability fetched exactly the
 * way a neighbouring block or an addon fetches it, through
 * {@code level.getCapability(AECapabilities.ME_STORAGE, pos, side)}.
 * <p/>
 * Every assertion below therefore exercises the production path end to end. If the wrapper were removed from
 * {@code getMEStorage}, {@link #meChestSecuredDenies} would fail - which is what makes it a real negative control
 * rather than a restatement of the fix.
 *
 * <ul>
 * <li><b>PA1</b> {@code round28_me_chest_secured} - on a SECURED network with no public grant, an external capability
 * holder can neither extract nor insert.</li>
 * <li><b>PA2</b> {@code round28_me_chest_legacy} - on a LEGACY network the very same call still works, so existing
 * external automation is untouched (owner Decision 3A, second half).</li>
 * <li><b>PA3</b> {@code round28_me_chest_everyone} - an explicit {@code EVERYONE} grant re-opens exactly what it
 * grants, proving "unidentified" rather than "denied outright".</li>
 * <li><b>PA4</b> {@code round28_interface_capability_secured} - the same for an Interface, and carrying the mandatory
 * privilege-laundering assertion: the Interface's own device holds {@code EXTRACT} and an external holder of its
 * capability still gets nothing.</li>
 * <li><b>PA5</b> {@code round28_interface_capability_legacy} - LEGACY interoperability through an Interface.</li>
 * <li><b>PA6</b> {@code round28_device_id_full_nbt_copy} - the blueprint mechanism reproduced without Structurize,
 * using the same two vanilla calls it makes, together with the ACL leakage that follows from it.</li>
 * </ul>
 */
@TestPlotClass
public class Round28PhysicalSecurityPlots {
    private Round28PhysicalSecurityPlots() {
    }

    private static final int STORED_AMOUNT = 64;

    /**
     * Resolved lazily, never in a static initializer: plot classes are loaded during scanning, before the item registry
     * is populated, and an {@code AEItemKey.of(...)} field would abort class init.
     */
    private static AEItemKey stored() {
        return AEItemKey.of(Items.DIAMOND);
    }

    // ==================================================================
    // PA1 - SECURED: external capability is denied
    // ==================================================================

    @TestPlot("round28_me_chest_secured")
    public static void meChestSecuredDenies(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var chestPos = origin.east();

        buildChestNetwork(plot, origin, chestPos);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var station = helper.getBlockEntity(stationPos, SecurityStationBlockEntity.class);
                    helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                            "network must be SECURED or this plot proves nothing", stationPos);
                    helper.check(acl(helper, origin).getPermissions(PrincipalId.EVERYONE).isEmpty(),
                            "EVERYONE must hold nothing here - a blanket grant would make the denial vacuous",
                            origin);

                    var external = capability(helper, chestPos);

                    // The fixture is real: the cell genuinely holds the item, so a zero below is a security
                    // decision and not an empty inventory.
                    var visible = new appeng.api.stacks.KeyCounter();
                    external.getAvailableStacks(visible);
                    helper.check(visible.get(stored()) == STORED_AMOUNT,
                            "the capability must actually see the cell contents for this test to mean anything",
                            chestPos);

                    helper.check(
                            external.extract(stored(), 8, Actionable.MODULATE, IActionSource.empty()) == 0,
                            "PA1: an unidentified external capability holder must not extract from a SECURED chest",
                            chestPos);
                    helper.check(
                            external.insert(AEItemKey.of(Items.EMERALD), 4, Actionable.MODULATE,
                                    IActionSource.empty()) == 0,
                            "PA1: nor insert into it",
                            chestPos);

                    // And nothing actually moved.
                    var after = new appeng.api.stacks.KeyCounter();
                    external.getAvailableStacks(after);
                    helper.check(after.get(stored()) == STORED_AMOUNT,
                            "PA1: the cell contents must be unchanged after the denied calls", chestPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PA2 - LEGACY: unchanged interoperability
    // ==================================================================

    @TestPlot("round28_me_chest_legacy")
    public static void meChestLegacyStaysOpen(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var chestPos = origin.east();

        // Deliberately no Security Station: this network is LEGACY.
        buildChestNetwork(plot, origin, chestPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var security = helper.getGrid(origin).getService(ISecurityService.class);
                    helper.check(security.getState() == SecurityDomainState.LEGACY,
                            "this plot must run on a LEGACY network", origin);

                    var external = capability(helper, chestPos);
                    helper.check(
                            external.extract(stored(), 8, Actionable.MODULATE, IActionSource.empty()) == 8,
                            "PA2: LEGACY interoperability is preserved - external automation still works",
                            chestPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PA3 - SECURED + EVERYONE: exactly what was granted
    // ==================================================================

    @TestPlot("round28_me_chest_everyone")
    public static void meChestEveryoneGrantApplies(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var chestPos = origin.east();

        buildChestNetwork(plot, origin, chestPos);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    acl(helper, origin).grant(PrincipalId.EVERYONE, Set.of(SecurityPermissions.EXTRACT));

                    var external = capability(helper, chestPos);
                    helper.check(
                            external.extract(stored(), 8, Actionable.MODULATE, IActionSource.empty()) == 8,
                            "PA3: an explicit EVERYONE grant deliberately opens the network and must be honoured",
                            chestPos);
                    helper.check(
                            external.insert(AEItemKey.of(Items.EMERALD), 4, Actionable.MODULATE,
                                    IActionSource.empty()) == 0,
                            "PA3: EXTRACT was granted, INJECT was not - the grant is honoured exactly, not broadly",
                            chestPos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PA4 - Interface capability, SECURED: denied, and no privilege laundering
    // ==================================================================

    /**
     * Round 27 proved the Interface case at unit level, against a {@code SecurityAwareMEStorage} built in a fixture.
     * This plot goes through the real thing: a real Interface block on a real network, and the capability taken from a
     * neighbouring position, which is what {@code InitCapabilityProviders#initInterface} actually publishes.
     * <p/>
     * It also carries the mandatory privilege-laundering assertion in physical form: the Interface's own device
     * principal is granted {@code EXTRACT} - its autonomous stocking is entitled to that - and an external holder of
     * its capability still gets nothing. The caller is the actor; the Interface is not a gateway.
     */
    @TestPlot("round28_interface_capability_secured")
    public static void interfaceCapabilitySecuredDenies(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var interfacePos = origin.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.drive(origin.above()).addItemCell64k().add(stored(), STORED_AMOUNT);
        plot.block(interfacePos, AEBlocks.INTERFACE);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var station = helper.getBlockEntity(stationPos, SecurityStationBlockEntity.class);
                    helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                            "network must be SECURED or this plot proves nothing", stationPos);
                    helper.check(acl(helper, origin).getPermissions(PrincipalId.EVERYONE).isEmpty(),
                            "EVERYONE must hold nothing here", origin);

                    // Give the Interface's own device principal EXTRACT: this is what makes the next assertion a
                    // laundering test rather than a plain denial.
                    var itf = helper.getBlockEntity(interfacePos, InterfaceBlockEntity.class);
                    var itfDeviceId = itf.getDeviceId();
                    helper.check(itfDeviceId != null, "the Interface must carry a device identity", interfacePos);
                    acl(helper, origin).grant(PrincipalId.ofDevice(itfDeviceId),
                            Set.of(SecurityPermissions.EXTRACT));

                    var external = capability(helper, interfacePos);
                    var visible = new appeng.api.stacks.KeyCounter();
                    external.getAvailableStacks(visible);
                    helper.check(visible.get(stored()) == STORED_AMOUNT,
                            "the capability must actually see the network for this test to mean anything",
                            interfacePos);

                    helper.check(
                            external.extract(stored(), 8, Actionable.MODULATE, IActionSource.empty()) == 0,
                            "PA4: an unidentified external capability holder must not extract from a SECURED network "
                                    + "through an Interface - even though the Interface itself holds EXTRACT",
                            interfacePos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PA5 - Interface capability, LEGACY: unchanged
    // ==================================================================

    @TestPlot("round28_interface_capability_legacy")
    public static void interfaceCapabilityLegacyStaysOpen(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var interfacePos = origin.east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.drive(origin.above()).addItemCell64k().add(stored(), STORED_AMOUNT);
        plot.block(interfacePos, AEBlocks.INTERFACE);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var security = helper.getGrid(origin).getService(ISecurityService.class);
                    helper.check(security.getState() == SecurityDomainState.LEGACY,
                            "this plot must run on a LEGACY network", origin);

                    var external = capability(helper, interfacePos);
                    helper.check(
                            external.extract(stored(), 8, Actionable.MODULATE, IActionSource.empty()) == 8,
                            "PA5: LEGACY interoperability through an Interface is preserved", interfacePos);
                })
                .thenSucceed());
    }

    // ==================================================================
    // PA6 - device identity across a full-NBT copy (the blueprint mechanism)
    // ==================================================================

    /**
     * Round 26 traced statically that Structurize copies AE2's {@code aeDeviceId} through a blueprint, and Round 27
     * could not reproduce it because no runtime with both mods exists here. This plot reproduces the part that actually
     * decides the outcome - <b>the AE2 side of the mechanism</b> - without Structurize, by performing the very same two
     * vanilla calls Structurize performs:
     * <ul>
     * <li>{@code BlueprintUtil:107} captures with {@code BlockEntity#saveWithFullMetadata(...)};</li>
     * <li>{@code PlacementHandlers:1414} restores with {@code BlockEntity#loadWithComponents(...)}.</li>
     * </ul>
     * Two real Interfaces are placed, then A's full NBT is loaded into B exactly as a blueprint placement would, and
     * the security consequence is measured against the real ACL.
     * <p/>
     * <b>Scope, stated honestly:</b> this proves what AE2 does when its block entity is copied wholesale. It does not
     * execute Structurize's own capture/placement code, so it cannot rule out that Structurize filters something on its
     * side. What it does establish is whether AE2 alone is enough to make the collision - and therefore whether a fix
     * is owed by AE2, by Structurize, or by both.
     */
    @TestPlot("round28_device_id_full_nbt_copy")
    public static void deviceIdSurvivesFullNbtCopy(PlotBuilder plot) {
        var origin = BlockPos.ZERO;
        var stationPos = origin.north();
        var itfA = origin.east();
        var itfB = origin.east().east();

        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.cable(itfA.above());
        plot.drive(origin.above()).addItemCell64k().add(stored(), STORED_AMOUNT);
        plot.block(itfA, AEBlocks.INTERFACE);
        plot.block(itfB, AEBlocks.INTERFACE);
        securityStation(plot, stationPos);

        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(origin))
                .thenIdle(20)
                .thenExecute(() -> {
                    var a = helper.getBlockEntity(itfA, InterfaceBlockEntity.class);
                    var b = helper.getBlockEntity(itfB, InterfaceBlockEntity.class);

                    var idA = a.getDeviceId();
                    var idBFresh = b.getDeviceId();
                    helper.check(idA != null && idBFresh != null, "both Interfaces must carry identities", itfA);
                    helper.check(!idA.equals(idBFresh),
                            "PA6 precondition: two freshly placed devices must have different identities", itfB);

                    // Exactly the blueprint mechanism: capture A wholesale, restore it into B.
                    var registries = helper.getLevel().registryAccess();
                    var capturedA = a.saveWithFullMetadata(registries);
                    b.loadWithComponents(
                            TagValueInput.create(ProblemReporter.DISCARDING, registries, capturedA));

                    // The copy really did carry A's id: B's own cached claim now reads as A's. Round 28 stopped here
                    // and recorded the leak; Round 29 continues, because the claim is no longer the authority.
                    var idBAfterCopy = b.getDeviceId();
                    helper.check(idA.equals(idBAfterCopy),
                            "PA6 precondition: the full-NBT copy must genuinely carry A's stored id, otherwise the "
                                    + "isolation asserted below would be proving nothing",
                            itfB);

                    // The security consequence - inverted in Round 29, exactly as this plot said it would have to be
                    // once AE2 gained copy-aware identity. B sits at its own position, so DevicePrincipalRegistry
                    // resolves it to its own binding no matter what its NBT claims, and A's grant does not reach it.
                    acl(helper, origin).grant(PrincipalId.ofDevice(idA), Set.of(SecurityPermissions.EXTRACT));
                    var bSource = IActionSource.ofMachine(() -> b.getMainNode().getNode());
                    var extractedByB = helper.getGrid(origin).getStorageService().getInventory()
                            .extract(stored(), 8, Actionable.MODULATE, bSource);
                    helper.check(extractedByB == 0,
                            "PA6/Round 29: a clone carrying A's id must NOT act with A's rights - the registry binds "
                                    + "identity to physical position, so the copy authenticates as itself or not at all",
                            itfB);

                    // ...while the genuine A, whose binding the registry holds, still works.
                    var aSource = IActionSource.ofMachine(() -> a.getMainNode().getNode());
                    var extractedByA = helper.getGrid(origin).getStorageService().getInventory()
                            .extract(stored(), 8, Actionable.MODULATE, aSource);
                    helper.check(extractedByA == 8,
                            "PA6/Round 29: the original device keeps its identity and its grant - the fix is isolation, "
                                    + "not a blanket denial",
                            itfA);
                })
                .thenSucceed());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A minimal but entirely real setup: cable, power, and an ME Chest holding a 1k item cell with a known stack in it.
     */
    private static void buildChestNetwork(PlotBuilder plot, BlockPos origin, BlockPos chestPos) {
        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.blockEntity(chestPos, AEBlocks.ME_CHEST, chest -> {
            var cellItem = AEItems.ITEM_CELL_1K.stack();
            var cellInv = StorageCells.getCellInventory(cellItem, null);
            cellInv.insert(stored(), STORED_AMOUNT, Actionable.MODULATE, IActionSource.empty());
            chest.setCell(cellItem);
        });
    }

    /**
     * Fetches the storage exactly the way an external mod would: through the block capability, from a side that is not
     * the chest's front (the front is the terminal face and deliberately exposes nothing).
     */
    private static appeng.api.storage.MEStorage capability(PlotTestHelper helper, BlockPos relativeChestPos) {
        var absolute = helper.absolutePos(relativeChestPos);
        var storage = helper.getLevel().getCapability(AECapabilities.ME_STORAGE, absolute, Direction.DOWN);
        helper.check(storage != null,
                "the ME_STORAGE capability must be present - a null here would make every assertion vacuous",
                relativeChestPos);
        return storage;
    }

    private static appeng.me.security.SecurityDomain acl(PlotTestHelper helper, BlockPos gridPos) {
        var grid = helper.getGrid(gridPos);
        var security = grid.getService(ISecurityService.class);
        var domainId = security.getDomainId().orElseThrow(
                () -> new IllegalStateException("network is not SECURED, so it has no ACL to edit"));
        return SecurityDomainRegistry.get(helper.getLevel()).getDomain(domainId);
    }

    /**
     * Test-plot placement never calls {@code Block#setPlacedBy}, so the station's owning player has to be set
     * explicitly or {@code tryBootstrapDomain()} silently no-ops and the network stays LEGACY.
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
    private static UUID unusedImportAnchor() {
        return UUID.randomUUID();
    }
}
