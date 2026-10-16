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

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.parts.IPartHost;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.server.testworld.PlotBuilder;

/**
 * Round 32 - the chunk unload half of the retirement contract, proven physically.
 * <p/>
 * Round 31 could only argue this from the code: retirement is wired to {@code removePartFromSide} and
 * {@code preRemoveSideEffects}, neither of which a chunk unload calls, so an unload was assumed not to retire anything.
 * An assumption is not evidence, and the whole point of the registry is that a device which goes away and comes back is
 * the same device - a machine that lost its grants every time a player walked out of render distance would be worse
 * than no registry at all.
 * <p/>
 * The plot region itself is held loaded for the duration of a GameTest, so this test does not use it. It builds a
 * device in a chunk far outside the test area, holds that chunk with its own forced-chunk ticket, drops the ticket, and
 * drives the chunk source until the chunk is genuinely gone. Then it asks the registry what it remembers, and loads the
 * chunk back to confirm the returning part resolves to the principal it left with.
 *
 * <ul>
 * <li><b>PT-P5</b> {@code round32_part_chunk_unload_keeps_principal} - unloading the chunk a DEVICE part lives in
 * neither retires nor tombstones its principal, and reloading returns the same one.</li>
 * </ul>
 */
@TestPlotClass
public final class Round32ChunkUnloadPlots {
    private Round32ChunkUnloadPlots() {
    }

    /**
     * Far enough from the plot area that it shares no chunk with any test, and outside the spawn chunks that the server
     * keeps loaded regardless of tickets.
     */
    private static final BlockPos REMOTE = new BlockPos(6000, 70, 6000);

    @TestPlot("round32_part_chunk_unload_keeps_principal")
    public static void chunkUnloadKeepsPrincipal(PlotBuilder plot) {
        plot.test(helper -> {
            var level = helper.getLevel();
            var cx = REMOTE.getX() >> 4;
            var cz = REMOTE.getZ() >> 4;
            var deviceId = new UUID[1];

            helper.startSequence()
                    .thenExecute(() -> {
                        level.setChunkForced(cx, cz, true);
                        level.setBlockAndUpdate(REMOTE, AEBlocks.CABLE_BUS.block().defaultBlockState());
                        var host = (IPartHost) level.getBlockEntity(REMOTE);
                        helper.check(host != null, "the remote cable bus must exist before a part is added to it");
                        host.addPart(AEParts.EXPORT_BUS.asItem(), Direction.EAST, null);
                    })
                    .thenIdle(20)
                    .thenExecute(() -> {
                        var identity = remoteDevice(level);
                        helper.check(identity != null, "the remote part must carry a device identity");
                        deviceId[0] = identity.getDeviceId();
                        var registry = DevicePrincipalRegistry.get(level);
                        helper.check(registry.isBound(deviceId[0]),
                                "PT-P5 precondition: the remote part must hold a registered principal before unload");

                        // Drop the only ticket holding the chunk, then make the server actually process the unload.
                        level.setChunkForced(cx, cz, false);
                    })
                    .thenWaitUntil(() -> {
                        level.getChunkSource().tick(() -> true, true);
                        level.getChunkSource().save(true);
                        helper.check(level.getChunkSource().getChunkNow(cx, cz) == null,
                                "PT-P5 setup: the remote chunk is still loaded; the unload never happened");
                    })
                    .thenExecute(() -> {
                        var registry = DevicePrincipalRegistry.get(level);
                        helper.check(!registry.isTombstoned(deviceId[0]),
                                "PT-P5: a chunk unload must not retire a device - only destruction does");
                        helper.check(registry.isBound(deviceId[0]),
                                "PT-P5: the principal must survive the unload, or a machine would lose its grants "
                                        + "whenever nobody was standing near it");

                        // Bring it back: the returning part must be the same principal, not a new one.
                        level.setChunkForced(cx, cz, true);
                    })
                    .thenIdle(20)
                    .thenExecute(() -> {
                        var identity = remoteDevice(level);
                        helper.check(identity != null, "the part must still be there after the chunk came back");
                        helper.check(deviceId[0].equals(identity.getDeviceId()),
                                "PT-P5: a device that comes back from an unload is the same device");

                        cleanUp(level, cx, cz);
                    })
                    .thenSucceed();
        });
    }

    @org.jetbrains.annotations.Nullable
    private static IDeviceIdentity remoteDevice(ServerLevel level) {
        if (!(level.getBlockEntity(REMOTE) instanceof CableBusBlockEntity bus)) {
            return null;
        }
        return bus.getPart(Direction.EAST) instanceof IDeviceIdentity identity ? identity : null;
    }

    private static void cleanUp(ServerLevel level, int cx, int cz) {
        level.destroyBlock(REMOTE, false);
        level.setChunkForced(cx, cz, false);
    }
}
