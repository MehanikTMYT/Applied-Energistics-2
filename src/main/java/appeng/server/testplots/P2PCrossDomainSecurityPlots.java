package appeng.server.testplots;

import static appeng.server.testplots.P2PPlotHelper.linkTunnels;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import appeng.api.networking.security.SecurityDomainState;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.service.P2PService;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;

/**
 * Regression coverage for the Round 9 {@code MEP2PTunnelPart#updateConnections()} fix: when
 * {@link appeng.api.networking.GridHelper#createConnection} rejects an ME P2P link because the two tunnels' outer
 * (transport) nodes already belong to two independently {@link SecurityDomainState#SECURED} networks bound to different
 * domains, it returns {@code null} - and the fix only stores the resulting {@code IGridConnection} in
 * {@code connections} when it is non-null. Before the fix, a {@code null} was stored unconditionally, and the very next
 * sweep of {@code connections.values()} (either the "sever stale connections" loop later in the same method, or a full
 * {@code DISCONNECT}) called {@code .destroy()} on it, throwing an NPE.
 * <p/>
 * This plot builds the real topology needed to exercise that exact null-check line through the actual production class
 * - not a synthetic {@link appeng.api.networking.IGridNode} fixture like {@code RecursiveP2PTest}/
 * {@code HighTierP2PTest} use for their (frequency-manually-wired, no {@link P2PService} discovery involved) tests:
 * <ul>
 * <li>One small hub network ("Grid X") hosts the <em>main</em> (local/channel) node of both the input and the output ME
 * P2P tunnel. This is required for {@code P2PTunnelPart#getOutputStream()} - which looks up the paired tunnel via
 * {@code P2PService.get(getMainNode().getGrid())} - to find the pair at all; without it, {@code updateConnections()}
 * would never even attempt {@code GridHelper.createConnection}, and the fixed line would never run.</li>
 * <li>Each tunnel's <em>outer/transport</em> node (the one {@code GridHelper.createConnection} actually operates on,
 * via {@code getExternalFacingNode()}) is wired - via ordinary in-world adjacency, not the P2P link itself - into its
 * own separate, physically independent network ("Network A" / "Network B"), each with its own
 * {@link appeng.blockentity.misc.SecurityStationBlockEntity} that independently bootstraps its own
 * {@link SecurityDomainState#SECURED} domain. These are the two domains the security guard actually compares.</li>
 * </ul>
 * Linking the tunnels to the same frequency must not merge Network A and Network B, and must never throw - neither on
 * the first {@code updateConnections()} pass (which discovers the pairing and gets the rejected {@code null} back from
 * {@code GridHelper.createConnection}) nor on a later repath forced by changing the input's frequency again (which
 * sweeps the - empty, under the fix - {@code connections} map).
 */
@TestPlotClass
public class P2PCrossDomainSecurityPlots {
    private P2PCrossDomainSecurityPlots() {
    }

    @TestPlot("p2p_cross_domain_security")
    public static void crossDomainSecurity(PlotBuilder plot) {
        var origin = BlockPos.ZERO;

        // Hub network ("Grid X"): physically hosts the *main* node of both ME P2P tunnels, so they can discover
        // each other by frequency through P2PService (see class javadoc). Not itself security-relevant.
        plot.creativeEnergyCell(origin.below());
        plot.cable(origin);
        plot.cable(origin.west()).part(Direction.WEST, AEParts.ME_P2P_TUNNEL);
        plot.cable(origin.east()).part(Direction.EAST, AEParts.ME_P2P_TUNNEL);

        // Network A: physically independent from Network B - only touches the rest of this plot through the west
        // tunnel's *outer* node (the world-facing side GridHelper.createConnection() actually links). Bootstraps
        // its own SECURED domain via its own Security Station.
        var networkAHub = origin.west().west();
        var stationAPos = networkAHub.north();
        plot.cable(networkAHub);
        plot.creativeEnergyCell(networkAHub.below());
        securityStation(plot, stationAPos);

        // Network B: same shape, mirrored on the east tunnel, independently bootstraps a DIFFERENT SECURED domain.
        var networkBHub = origin.east().east();
        var stationBPos = networkBHub.north();
        plot.cable(networkBHub);
        plot.creativeEnergyCell(networkBHub.below());
        securityStation(plot, stationBPos);

        var inputPos = origin.west();
        var outputPos = origin.east();

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(origin))
                    .thenWaitUntil(() -> helper.getGrid(stationAPos))
                    .thenWaitUntil(() -> helper.getGrid(stationBPos))
                    .thenExecute(() -> {
                        var stationA = helper.getBlockEntity(stationAPos, SecurityStationBlockEntity.class);
                        var stationB = helper.getBlockEntity(stationBPos, SecurityStationBlockEntity.class);
                        helper.check(stationA.getDomainState() == SecurityDomainState.SECURED,
                                "Network A should already be SECURED", stationAPos);
                        helper.check(stationB.getDomainState() == SecurityDomainState.SECURED,
                                "Network B should already be SECURED", stationBPos);
                        helper.check(!stationA.getEditedDomainId().equals(stationB.getEditedDomainId()),
                                "Networks A and B must be two different domains", stationAPos);
                        helper.check(helper.getGrid(stationAPos) != helper.getGrid(stationBPos),
                                "Networks A and B must still be two different grids before linking", stationAPos);
                    })
                    .thenExecute(() -> {
                        // Link the two tunnels to the same frequency. This is what makes updateConnections()
                        // discover the pairing on the next tick and attempt GridHelper.createConnection() between
                        // the two tunnels' outer nodes - i.e. between Network A and Network B.
                        var grid = helper.getGrid(origin);
                        linkTunnels(grid, MEP2PTunnelPart.class, helper.absolutePos(inputPos),
                                helper.absolutePos(outputPos));
                    })
                    // Give MEP2PTunnelPart's IGridTickable/TickHandler-deferred updateConnections() several ticks
                    // to run. This is exactly where the unfixed code would silently store a null IGridConnection in
                    // its `connections` map without throwing yet.
                    .thenIdle(20)
                    .thenExecute(() -> {
                        // The connection must have been REJECTED, not formed: the two independently-secured
                        // networks stay two separate grids - this is the intended, non-error outcome.
                        helper.check(helper.getGrid(stationAPos) != helper.getGrid(stationBPos),
                                "Cross-domain P2P link must not merge the two SECURED networks", stationAPos);
                    })
                    .thenExecute(() -> {
                        // Force another updateConnections() pass by re-pointing the input to a fresh frequency.
                        // Under the pre-fix code, this is exactly the kind of follow-up sweep ("sever stale
                        // connections" / DISCONNECT) that called .destroy() on the null stored earlier and threw an
                        // NPE. Under the fix, `connections` was never populated for the rejected pairing, so this is
                        // a no-op.
                        var grid = helper.getGrid(origin);
                        var p2p = P2PService.get(grid);
                        var inputTunnel = helper.getPart(inputPos, Direction.WEST, MEP2PTunnelPart.class);
                        p2p.updateFreq(inputTunnel, p2p.newFrequency());
                    })
                    .thenIdle(20)
                    .thenExecute(() -> {
                        // Still two separate, independently secured networks - and getting here at all (no
                        // exception thrown anywhere above) is the actual regression proof.
                        helper.check(helper.getGrid(stationAPos) != helper.getGrid(stationBPos),
                                "Networks A and B must remain independent", stationAPos);
                    })
                    .thenSucceed();
        });
    }

    /**
     * Places a real {@link appeng.block.misc.SecurityStationBlock} and makes sure it has a non-null owning player
     * before its {@link SecurityStationBlockEntity#onReady()} runs (which happens on the first tick after placement,
     * i.e. strictly after every build action below has already executed synchronously) - otherwise
     * {@code tryBootstrapDomain()} silently no-ops (it requires {@code node.getOwningPlayerProfileId() != null}) and
     * the network would stay {@link SecurityDomainState#LEGACY} forever. Test-plot block placement
     * ({@code PlaceBlockState}) calls {@code level.setBlock} directly and never triggers {@code Block#setPlacedBy}
     * (unlike a real player placing the block from the item form), so the owner has to be set explicitly here.
     * <p/>
     * The station's own connectable side is fixed to south ({@link appeng.block.misc.SecurityStationBlock} does not
     * override {@code getOrientationStrategy()}, so its orientation - and thus
     * {@code SecurityStationBlockEntity#getGridConnectableSides()}'s {@code RelativeSide.BACK} - is always the default,
     * unrotated one) - callers must place a cable at {@code pos.south()}.
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
}
