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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import appeng.api.networking.security.DeviceRegistration;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.parts.IPartHost;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.AELog;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.SecurityDomain;
import appeng.me.security.SecurityDomainRegistry;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

/**
 * Round 32.1 - the whole restart persistence contract, in one controlled world.
 * <p/>
 * Round 32 proved that a real server restart preserves a <em>block</em> device's stored id. That is one third of what
 * the registry promises. It did not prove that a <em>part</em> keeps its identity, that an ACL grant issued to a device
 * still authorises it after a restart, or that a tombstone survives at all - the world it used had an empty tombstone
 * set, so there was nothing to survive. Calling the registry "proven" on that evidence was too broad.
 * <p/>
 * This plot closes the remaining three. It is deliberately self-staging: the GameTest server's world persists between
 * runs, so the first run finds nothing at the site and builds it, and every later run finds the site standing and
 * checks what survived. Two consecutive runs are therefore two real JVMs over one world - each records its own
 * {@code ProcessHandle.current().pid()}, so the restart is evidenced rather than asserted.
 * <p/>
 * The site sits far outside any plot region, because plot regions are rebuilt on every run and nothing there could
 * survive a restart by construction.
 *
 * <ul>
 * <li><b>SETUP</b> builds a secured network holding a block DEVICE (X) and a part DEVICE (Y), grants each of them a
 * real permission, checks the grant actually authorises them through the production source, then creates a third device
 * (Z), grants it the same permission and destroys it - leaving a tombstone and, deliberately, Z's grant still standing
 * in the ACL. That standing grant is the resurrection bait.</li>
 * <li><b>VERIFY</b> asserts X and Y came back with the same identities and are still authorised, that Z is still
 * tombstoned, and that a fresh machine claiming Z as its legacy identity is issued something else and is refused by Z's
 * grant.</li>
 * </ul>
 */
@TestPlotClass
public final class Round321RestartPersistencePlots {
    private Round321RestartPersistencePlots() {
    }

    /** Far outside the plot area, so nothing here is rebuilt by the test harness between runs. */
    private static final BlockPos SITE = new BlockPos(7000, 70, 7000);

    private static final BlockPos ENERGY = SITE.below();
    private static final BlockPos STATION = SITE.north();
    private static final BlockPos BLOCK_DEVICE = SITE.east();
    private static final BlockPos PART_HOST = SITE.west();
    private static final Direction PART_SIDE = Direction.WEST;
    private static final BlockPos DOOMED_DEVICE = SITE.south();

    private static final String STATE_FILE = "round32_1_state.properties";

    /** A fixed, obviously synthetic owner. The point of this plot is device principals, not player ones. */
    private static final UUID OWNER = UUID.fromString("32100000-0000-4000-8000-000000000001");

    private static final Set<SecurityPermissions> GRANT = EnumSet.of(SecurityPermissions.EXTRACT);

    @TestPlot("round32_1_restart_persistence")
    public static void restartPersistence(PlotBuilder plot) {
        plot.test(helper -> {
            var level = helper.getLevel();
            var state = readState(level);
            if (state == null) {
                setup(helper, level);
            } else {
                verify(helper, level, state);
            }
        });
    }

    // ==================================================================
    // SETUP - the state that has to survive
    // ==================================================================

    private static void setup(PlotTestHelper helper, ServerLevel level) {
        var ids = new UUID[3]; // X, Y, Z

        helper.startSequence()
                .thenExecute(() -> {
                    level.setChunkForced(SITE.getX() >> 4, SITE.getZ() >> 4, true);

                    level.setBlockAndUpdate(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    cableAt(level, SITE);
                    level.setBlockAndUpdate(STATION, AEBlocks.SECURITY_STATION.block().defaultBlockState());
                    level.setBlockAndUpdate(BLOCK_DEVICE, AEBlocks.INTERFACE.block().defaultBlockState());
                    level.setBlockAndUpdate(DOOMED_DEVICE, AEBlocks.INTERFACE.block().defaultBlockState());
                    cableAt(level, PART_HOST);
                })
                .thenIdle(20)
                .thenExecute(() -> partHost(level, PART_HOST).addPart(AEParts.EXPORT_BUS.asItem(), PART_SIDE, null))
                .thenIdle(20)
                .thenExecute(() -> {
                    // Secure the network. Without this every check below would pass for the wrong reason.
                    var station = (SecurityStationBlockEntity) level.getBlockEntity(STATION);
                    helper.check(station != null, "the security station must exist before the network can be secured");
                    station.activate(OWNER);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.check(security(helper, level).getState() == SecurityDomainState.SECURED,
                            "SETUP: the network must be SECURED or none of these grants mean anything");

                    ids[0] = deviceIdOf(helper, level, BLOCK_DEVICE, "block device X");
                    ids[1] = partDeviceId(helper, level);
                    ids[2] = deviceIdOf(helper, level, DOOMED_DEVICE, "doomed device Z");

                    helper.check(!ids[0].equals(ids[1]), "SETUP: the block and the part must be separate principals");
                    helper.check(!ids[0].equals(ids[2]) && !ids[1].equals(ids[2]),
                            "SETUP: the doomed device must be a third principal");

                    var acl = domain(helper, level);
                    acl.grant(PrincipalId.ofDevice(ids[0]), GRANT);
                    acl.grant(PrincipalId.ofDevice(ids[1]), GRANT);
                    acl.grant(PrincipalId.ofDevice(ids[2]), GRANT);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    // Not "the ACL map contains the id" - the actual permission decision, through the same source
                    // SecurityAwareMEStorage builds when a machine asks it for something.
                    helper.check(allowed(helper, level, actionHost(level, BLOCK_DEVICE)),
                            "SETUP: the block device's grant must actually authorise it before the restart");
                    helper.check(allowed(helper, level, (IActionHost) partOf(level)),
                            "SETUP: the part device's grant must actually authorise it before the restart");
                    helper.check(allowed(helper, level, actionHost(level, DOOMED_DEVICE)),
                            "SETUP: the doomed device must be authorised while it is alive, or its tombstone proves "
                                    + "nothing later");

                    // And a control: an unregistered machine on the same secured network gets nothing. If this ever
                    // passes, the checks above are passing for a reason that has nothing to do with the ACL.
                    level.setBlockAndUpdate(SITE.above(), AEBlocks.DRIVE.block().defaultBlockState());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.check(!allowed(helper, level, actionHost(level, SITE.above())),
                            "SETUP: a machine with no grant must be refused, or this plot cannot tell a grant from a "
                                    + "network that allows everything");

                    // Retire Z through the real lifecycle path, and leave its grant standing in the ACL on purpose.
                    level.destroyBlock(DOOMED_DEVICE, false);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    var registry = DevicePrincipalRegistry.get(level);
                    helper.check(registry.isTombstoned(ids[2]),
                            "SETUP: destroying the doomed device must tombstone it, or there is no tombstone to "
                                    + "carry across the restart");
                    helper.check(domain(helper, level).getPermissions(PrincipalId.ofDevice(ids[2])).contains(
                            SecurityPermissions.EXTRACT),
                            "SETUP: the retired device's grant must stay in the ACL - that standing grant is exactly "
                                    + "what a resurrected machine would try to inherit");

                    writeState(helper, level, ids[0], ids[1], ids[2]);
                    AELog.info("[R321] phase=SETUP pid=%d X=%s Y=%s Z=%s", ProcessHandle.current().pid(),
                            ids[0], ids[1], ids[2]);
                })
                .thenSucceed();
    }

    // ==================================================================
    // VERIFY - what a second JVM finds
    // ==================================================================

    private static void verify(PlotTestHelper helper, ServerLevel level, Properties state) {
        var x = UUID.fromString(state.getProperty("blockDeviceId"));
        var y = UUID.fromString(state.getProperty("partDeviceId"));
        var z = UUID.fromString(state.getProperty("tombstonedDeviceId"));
        var run = Integer.parseInt(state.getProperty("verifyRun", "0")) + 1;
        var probePos = SITE.offset(0, 0, 3 + run);

        helper.startSequence()
                .thenExecute(() -> level.setChunkForced(SITE.getX() >> 4, SITE.getZ() >> 4, true))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.check(security(helper, level).getState() == SecurityDomainState.SECURED,
                            "VERIFY: the network must still be SECURED after the restart");

                    // 1. The block device is the same device.
                    var xAfter = deviceIdOf(helper, level, BLOCK_DEVICE, "block device");
                    helper.check(x.equals(xAfter),
                            "BLOCK_RESTART_ID_STABLE: the block device came back as " + xAfter + ", not " + x);

                    // 2. The part device is the same device. This is the proof Round 32 never ran.
                    var yAfter = partDeviceId(helper, level);
                    helper.check(y.equals(yAfter),
                            "PART_RESTART_ID_STABLE: the part came back as " + yAfter + ", not " + y);

                    // 3. Both grants still decide the same way, through the production source.
                    helper.check(allowed(helper, level, actionHost(level, BLOCK_DEVICE)),
                            "BLOCK_ACL_RESTART_PERSISTENCE: the block device's grant did not survive the restart");
                    helper.check(allowed(helper, level, (IActionHost) partOf(level)),
                            "PART_ACL_RESTART_PERSISTENCE: the part device's grant did not survive the restart");

                    // 4. The tombstone survived.
                    var registry = DevicePrincipalRegistry.get(level);
                    helper.check(registry.isTombstoned(z),
                            "TOMBSTONE_RESTART_PERSISTENCE: " + z + " is no longer tombstoned after the restart");
                    helper.check(domain(helper, level).getPermissions(PrincipalId.ofDevice(z)).contains(
                            SecurityPermissions.EXTRACT),
                            "VERIFY: the retired device's grant must still stand, or the resurrection test below has "
                                    + "nothing to try to inherit");

                    // 5. A fresh machine at a location that has never registered, claiming Z as its legacy identity -
                    // the shape a cloned or restored machine takes. A drive is used because it is a real machine with
                    // a real MachineKey that AE2 does not register on its own, so the claim goes through the public
                    // addon contract and reaches the registry's tombstone check with an unbound key.
                    level.setBlockAndUpdate(probePos, AEBlocks.DRIVE.block().defaultBlockState());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    var probe = level.getBlockEntity(probePos);
                    helper.check(probe != null, "the resurrection probe must exist");
                    var assigned = DeviceRegistration.register(probe, z);

                    helper.check(assigned != null, "the probe must be issued some principal");
                    helper.check(!z.equals(assigned),
                            "TOMBSTONE_REUSE_AFTER_RESTART: a machine claiming a retired id was handed that very id ("
                                    + z + ")");
                    helper.check(!allowed(helper, level, actionHost(level, probePos)),
                            "TOMBSTONE_REUSE_AFTER_RESTART: the retired device's standing grant authorised a "
                                    + "different machine");

                    state.setProperty("verifyRun", Integer.toString(run));
                    state.setProperty("lastAssignedProbeId", assigned.toString());
                    saveState(helper, level, state);
                    AELog.info("[R321] phase=VERIFY pid=%d X=%s Y=%s Z=%s probe=%s", ProcessHandle.current().pid(),
                            x, y, z, assigned);
                })
                .thenSucceed();
    }

    // ==================================================================
    // Site helpers
    // ==================================================================

    private static void cableAt(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        partHost(level, pos).addPart(AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, null);
    }

    private static IPartHost partHost(ServerLevel level, BlockPos pos) {
        return (IPartHost) level.getBlockEntity(pos);
    }

    private static appeng.parts.AEBasePart partOf(ServerLevel level) {
        var host = (CableBusBlockEntity) level.getBlockEntity(PART_HOST);
        return (appeng.parts.AEBasePart) host.getPart(PART_SIDE);
    }

    private static IActionHost actionHost(ServerLevel level, BlockPos pos) {
        return (IActionHost) level.getBlockEntity(pos);
    }

    private static UUID deviceIdOf(PlotTestHelper helper, ServerLevel level, BlockPos pos, String what) {
        var be = level.getBlockEntity(pos);
        helper.check(be instanceof IDeviceIdentity, what + " must carry a device identity");
        var id = ((IDeviceIdentity) be).getDeviceId();
        helper.check(id != null, what + " must have a device id");
        return id;
    }

    private static UUID partDeviceId(PlotTestHelper helper, ServerLevel level) {
        var part = partOf(level);
        helper.check(part instanceof IDeviceIdentity, "the export bus must carry a device identity");
        var id = ((IDeviceIdentity) part).getDeviceId();
        helper.check(id != null, "the export bus must have a device id");
        return id;
    }

    private static ISecurityService security(PlotTestHelper helper, ServerLevel level) {
        var host = actionHost(level, BLOCK_DEVICE);
        var node = host.getActionableNode();
        helper.check(node != null, "the block device must be on a grid");
        return node.getGrid().getService(ISecurityService.class);
    }

    private static SecurityDomain domain(PlotTestHelper helper, ServerLevel level) {
        var domainId = security(helper, level).getDomainId()
                .orElseThrow(() -> new IllegalStateException("network is not SECURED, so it has no ACL"));
        return SecurityDomainRegistry.get(level).getDomain(domainId);
    }

    private static boolean allowed(PlotTestHelper helper, ServerLevel level, IActionHost machine) {
        return security(helper, level).hasPermission(IActionSource.ofMachine(machine), SecurityPermissions.EXTRACT);
    }

    // ==================================================================
    // Harness state - identifiers only; the registry itself is never written here
    // ==================================================================

    private static Path statePath(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
    }

    @Nullable
    private static Properties readState(ServerLevel level) {
        var path = statePath(level);
        if (!Files.exists(path)) {
            return null;
        }
        var props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Round 32.1 state file exists but cannot be read: " + path, e);
        }
        return props;
    }

    private static void writeState(PlotTestHelper helper, ServerLevel level, UUID x, UUID y, UUID z) {
        var props = new Properties();
        props.setProperty("blockDeviceId", x.toString());
        props.setProperty("partDeviceId", y.toString());
        props.setProperty("tombstonedDeviceId", z.toString());
        props.setProperty("setupPid", Long.toString(ProcessHandle.current().pid()));
        props.setProperty("verifyRun", "0");
        saveState(helper, level, props);
    }

    private static void saveState(PlotTestHelper helper, ServerLevel level, Properties props) {
        props.setProperty("lastPid", Long.toString(ProcessHandle.current().pid()));
        try (OutputStream out = Files.newOutputStream(statePath(level))) {
            props.store(out, "Round 32.1 restart persistence harness - identifiers under test, not registry state");
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the Round 32.1 state file", e);
        }
    }

    /** MachineKey is used only to spell out in the log what the part's key actually is. */
    static String describePartKey(ServerLevel level) {
        var key = MachineKey.of(partOf(level));
        return key == null ? "<none>" : key.toString();
    }
}
