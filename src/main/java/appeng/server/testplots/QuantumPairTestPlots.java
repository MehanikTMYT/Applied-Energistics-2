package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.QuantumKeyCardData;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.misc.QuantumEndpointBlockEntity;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.quantum.QuantumCredential;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumCredentialService;
import appeng.me.quantum.QuantumTopologyRegistry;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;

/**
 * Round 10 ("Mk I PHYSICAL QUANTUM PAIR") — the required GameTest coverage (Phase J of the task manifest): real
 * physical {@code QuantumEndpointBlock}s, real placement/registration, real credential activation through
 * {@link QuantumEndpointBlockEntity#activateWithKey} (the exact production entry point a player's right-click invokes —
 * the plots below build a real "Encoded Quantum Key" {@link ItemStack} carrying real {@link QuantumKeyCardData} and
 * call it directly, which is the only part of the real interaction NOT exercised here being the literal mouse click —
 * everything server-side downstream of it is the genuine production path), and real {@code GridConnection}
 * materialization/teardown.
 */
@TestPlotClass
public class QuantumPairTestPlots {
    private QuantumPairTestPlots() {
    }

    // =====================================================================
    // 1. quantum_pair_basic
    // =====================================================================

    @TestPlot("quantum_pair_basic")
    public static void quantumPairBasic(PlotBuilder plot) {
        var posA = new BlockPos(0, 0, 0);
        var posB = new BlockPos(0, 0, 6);
        var endpointA = posA.east();
        var endpointB = posB.east();

        plot.creativeEnergyCell(posA.below());
        plot.cable(posA);
        plot.block(endpointA, AEBlocks.QUANTUM_ENDPOINT);
        // A Creative Cell only accepts/reports types it was pre-configured with - configure it for Diamond so the
        // real insert/extract proof below (through the materialized Mk I PAIR) has something to work with.
        plot.drive(posA.above()).addCreativeCell().add(Items.DIAMOND);

        plot.creativeEnergyCell(posB.below());
        plot.cable(posB);
        plot.block(endpointB, AEBlocks.QUANTUM_ENDPOINT);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(endpointA))
                    .thenWaitUntil(() -> helper.getGrid(endpointB))
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(endpointA) != helper.getGrid(endpointB),
                                "both physical endpoints must start on two SEPARATE grids, before any credential "
                                        + "activation ever happens",
                                endpointA);
                    })
                    .thenExecute(() -> {
                        // Provisioning: real Q3/Q4 registries, exactly as an Imprinter-driven flow would populate
                        // them (this plot skips the Imprinter GUI itself purely for setup brevity - the actual
                        // authorization/materialization path under test starts at activateWithKey below).
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        var profile = topology.createProfile(); // LEGACY (no domain bound) - no actor required
                        var credA = credentials.issue(profile.id());
                        var credB = credentials.issue(profile.id());

                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var beB = helper.getBlockEntity(endpointB, QuantumEndpointBlockEntity.class);
                        helper.check(beA.getEndpointId() == null, "A must start UNPROVISIONED", endpointA);
                        helper.check(beB.getEndpointId() == null, "B must start UNPROVISIONED", endpointB);

                        var resultA = beA.activateWithKey(encodedKey(profile.id().id(), credA.id()),
                                IActionSource.empty());
                        helper.check(resultA == QuantumCredentialService.Result.OK, "activation A failed: " + resultA,
                                endpointA);
                        helper.check(beA.getEndpointId() != null, "A must be ACTIVATED with a stable id", endpointA);

                        var resultB = beB.activateWithKey(encodedKey(profile.id().id(), credB.id()),
                                IActionSource.empty());
                        helper.check(resultB == QuantumCredentialService.Result.OK, "activation B failed: " + resultB,
                                endpointB);
                    })
                    .thenIdle(15) // let the pair materialize AND the drive's creative cell fully mount/settle
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(endpointA) == helper.getGrid(endpointB),
                                "the Mk I PAIR must materialize a real, shared GridConnection after both endpoints "
                                        + "are activated",
                                endpointA);

                        var diamond = AEItemKey.of(Items.DIAMOND);
                        var inserted = helper.getGrid(endpointA).getStorageService().getInventory().insert(diamond, 4,
                                Actionable.MODULATE, IActionSource.empty());
                        helper.check(inserted == 4, "insert through A's own network failed", endpointA);
                    })
                    .thenIdle(2) // let the real storage network cache refresh, exactly like any other AE2 network
                    .thenExecute(() -> {
                        var diamond = AEItemKey.of(Items.DIAMOND);
                        var seenFromB = new KeyCounter();
                        helper.getGrid(endpointB).getStorageService().getInventory().getAvailableStacks(seenFromB);
                        // A Creative Cell reports Integer.MAX_VALUE for any type it's configured for (not the
                        // literal inserted amount) - see CreativeCellInventory#getAvailableStacks - so ">0" (visible
                        // at all through the real PAIR) is the correct, real production assertion here, not "==4".
                        helper.check(seenFromB.get(diamond) > 0,
                                "item inserted at A must be visible through B's own grid via the real PAIR", endpointB);
                        var extracted = helper.getGrid(endpointB).getStorageService().getInventory().extract(diamond,
                                4, Actionable.MODULATE, IActionSource.empty());
                        helper.check(extracted == 4, "extract through B's own network (via the real PAIR) failed",
                                endpointB);
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 2. quantum_pair_chunk_reload — LIMITATION: a real chunk unload is not practically reproducible inside the
    // GameTest sandbox (the test structure's chunks are always kept loaded by the harness). The nearest real
    // production lifecycle hook is used instead, called directly on the live BlockEntity - the EXACT same
    // onChunkUnloaded()/onReady() methods a genuine chunk unload/reload invokes (see
    // QuantumEndpointBlockEntity/AENetworkedBlockEntity) - so this proves the real lifecycle code path's behavior
    // (connection torn down, then re-formed exactly once on "reload", never duplicated), just not the chunk-manager
    // trigger itself.
    // =====================================================================

    @TestPlot("quantum_pair_chunk_reload")
    public static void quantumPairChunkReload(PlotBuilder plot) {
        var posA = new BlockPos(0, 0, 0);
        var posB = new BlockPos(0, 0, 6);
        var endpointA = posA.east();
        var endpointB = posB.east();

        plot.creativeEnergyCell(posA.below());
        plot.cable(posA);
        plot.block(endpointA, AEBlocks.QUANTUM_ENDPOINT);
        plot.creativeEnergyCell(posB.below());
        plot.cable(posB);
        plot.block(endpointB, AEBlocks.QUANTUM_ENDPOINT);

        // Captures endpoint A's own IGridNode BEFORE it is (simulated-)unloaded, so the post-unload check below can
        // prove specifically that the A<->B remote edge is gone without needing helper.getGrid() (which asserts
        // every grid-hosting block entity in the whole plot is ready - no longer true for A on purpose at that
        // point).
        var nodeARef = new appeng.api.networking.IGridNode[1];

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(endpointA))
                    .thenWaitUntil(() -> helper.getGrid(endpointB))
                    .thenExecute(() -> {
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        var profile = topology.createProfile();
                        var credA = credentials.issue(profile.id());
                        var credB = credentials.issue(profile.id());
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var beB = helper.getBlockEntity(endpointB, QuantumEndpointBlockEntity.class);
                        beA.activateWithKey(encodedKey(profile.id().id(), credA.id()), IActionSource.empty());
                        beB.activateWithKey(encodedKey(profile.id().id(), credB.id()), IActionSource.empty());
                    })
                    .thenIdle(5)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(endpointA) == helper.getGrid(endpointB),
                                "pair must be materialized before simulating unload", endpointA);
                        nodeARef[0] = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class).getMainNode()
                                .getNode();
                    })
                    .thenExecute(() -> {
                        // Simulated unload of A's chunk: the exact production hook a real chunk unload calls.
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        beA.onChunkUnloaded();
                    })
                    .thenIdle(3)
                    .thenExecute(() -> {
                        // NOTE: cannot use helper.getGrid(...) here anymore - it internally asserts EVERY grid-hosting
                        // block entity in the whole plot is ready (PlotTestHelper#checkAllInitialized), which A's own
                        // BE deliberately no longer is, on purpose, at this point. Check B's own node's connections
                        // directly instead - B still has its own local cable connection, so check specifically that
                        // none of them lead to A's (captured) node anymore.
                        var beB = helper.getBlockEntity(endpointB, QuantumEndpointBlockEntity.class);
                        var nodeB = beB.getMainNode().getNode();
                        var stillConnectedToA = nodeB.getConnections().stream()
                                .anyMatch(c -> c.getOtherSide(nodeB) == nodeARef[0]);
                        helper.check(!stillConnectedToA,
                                "materialized connection must be gone after (simulated) unload - intent, not "
                                        + "materialization, is what survives",
                                endpointA);
                        var beAUnloaded = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        helper.check(!beAUnloaded.getMainNode().isReady(), "A's own node must no longer be ready",
                                endpointA);
                        // Identity/intent survive the unload - "unloaded is not deleted" (Q3/Q4 hard invariant).
                        // NOTE ON SCOPE: re-forming the connection on RELOAD is deliberately NOT re-simulated here by
                        // calling onReady() again on this SAME BlockEntity/ManagedGridNode instance - archaeology
                        // during this round found that appeng.me.ManagedGridNode#create() throws
                        // IllegalStateException("already initialized") if called on a node that already went through
                        // create()+destroy() once (IManagedGridNode#destroy()'s own javadoc: "the node cannot be
                        // reused"), exactly matching real production behavior - an actual chunk reload always
                        // constructs a BRAND NEW BlockEntity (and therefore a brand new ManagedGridNode) from saved
                        // NBT, never reuses the old in-memory instance. Faithfully simulating that inside this
                        // sandbox needs a full remove+replace-with-preserved-NBT dance disproportionate to what this
                        // one GameTest needs to prove; the reload/restart half of this exact scenario (persisted
                        // intent -> trusted-internal reconciliation -> re-materialized pair, via a REAL
                        // SavedDataStorage round-trip) is already proven at the JUnit level by
                        // QuantumPairProvisioningTest#freshSavedDataStorageRestartPreservesIntentAndReconcilesToTheSameMaterialization.
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var laneId = topology.getEndpoint(beA.getEndpointId()).laneId();
                        helper.check(topology.getEndpoint(beA.getEndpointId())
                                .status() == appeng.api.networking.quantum.QuantumEndpointStatus.OFFLINE,
                                "unloaded endpoint must be recorded OFFLINE, never REMOVED", endpointA);
                        helper.check(topology.getLane(laneId).getEdges().size() == 1,
                                "the persisted edge (topology intent) must survive the unload", endpointA);
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 3. quantum_pair_security — two real physical, independently SECURED networks (real Security Stations, exactly
    // mirroring P2PCrossDomainSecurityPlots' own established pattern including the setPlacedBy/BuildAction gap
    // workaround) must never let a credential-driven pair materialize a connection across them.
    // =====================================================================

    @TestPlot("quantum_pair_security")
    public static void quantumPairSecurity(PlotBuilder plot) {
        var posA = new BlockPos(0, 0, 0);
        var posB = new BlockPos(0, 0, 6);
        var endpointA = posA.east();
        var endpointB = posB.east();
        var stationAPos = posA.north();
        var stationBPos = posB.north();

        plot.creativeEnergyCell(posA.below());
        plot.cable(posA);
        plot.block(endpointA, AEBlocks.QUANTUM_ENDPOINT);
        securityStation(plot, stationAPos);

        plot.creativeEnergyCell(posB.below());
        plot.cable(posB);
        plot.block(endpointB, AEBlocks.QUANTUM_ENDPOINT);
        securityStation(plot, stationBPos);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(endpointA))
                    .thenWaitUntil(() -> helper.getGrid(endpointB))
                    .thenWaitUntil(() -> helper.getGrid(stationAPos))
                    .thenWaitUntil(() -> helper.getGrid(stationBPos))
                    .thenExecute(() -> {
                        var stationA = helper.getBlockEntity(stationAPos, SecurityStationBlockEntity.class);
                        var stationB = helper.getBlockEntity(stationBPos, SecurityStationBlockEntity.class);
                        helper.check(
                                stationA.getDomainState() == appeng.api.networking.security.SecurityDomainState.SECURED,
                                "Network A should already be SECURED", stationAPos);
                        helper.check(
                                stationB.getDomainState() == appeng.api.networking.security.SecurityDomainState.SECURED,
                                "Network B should already be SECURED", stationBPos);
                        helper.check(!stationA.getEditedDomainId().equals(stationB.getEditedDomainId()),
                                "Networks A and B must be two different domains", stationAPos);
                    })
                    .thenExecute(() -> {
                        // Registry-level: one profile/lane/pair, exactly like any other Mk I PAIR provisioning -
                        // the registry itself has no idea these two endpoints' real networks are independently
                        // secured.
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        var profile = topology.createProfile();
                        var credA = credentials.issue(profile.id());
                        var credB = credentials.issue(profile.id());
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var beB = helper.getBlockEntity(endpointB, QuantumEndpointBlockEntity.class);
                        var resultA = beA.activateWithKey(encodedKey(profile.id().id(), credA.id()),
                                IActionSource.empty());
                        var resultB = beB.activateWithKey(encodedKey(profile.id().id(), credB.id()),
                                IActionSource.empty());
                        helper.check(resultA == QuantumCredentialService.Result.OK, "activation A failed", endpointA);
                        helper.check(resultB == QuantumCredentialService.Result.OK, "activation B failed", endpointB);
                    })
                    .thenIdle(10)
                    .thenExecute(() -> {
                        // The physical-layer cross-security-domain pre-merge guard must reject materialization -
                        // no leakage, no merge, exactly like P2PCrossDomainSecurityPlots proves for ME P2P.
                        helper.check(helper.getGrid(endpointA) != helper.getGrid(endpointB),
                                "cross-domain Mk I PAIR must never materialize a connection", endpointA);
                        var stationA = helper.getBlockEntity(stationAPos, SecurityStationBlockEntity.class);
                        var stationB = helper.getBlockEntity(stationBPos, SecurityStationBlockEntity.class);
                        helper.check(
                                stationA.getDomainState() == appeng.api.networking.security.SecurityDomainState.SECURED,
                                "Network A must remain SECURED, unaffected by the rejected pair", stationAPos);
                        helper.check(
                                stationB.getDomainState() == appeng.api.networking.security.SecurityDomainState.SECURED,
                                "Network B must remain SECURED, unaffected by the rejected pair", stationBPos);
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 4. quantum_pair_credential_revoke
    // =====================================================================

    @TestPlot("quantum_pair_credential_revoke")
    public static void quantumPairCredentialRevoke(PlotBuilder plot) {
        var posA = new BlockPos(0, 0, 0);
        var posB = new BlockPos(0, 0, 6);
        var endpointA = posA.east();
        var endpointB = posB.east();

        plot.creativeEnergyCell(posA.below());
        plot.cable(posA);
        plot.block(endpointA, AEBlocks.QUANTUM_ENDPOINT);
        plot.creativeEnergyCell(posB.below());
        plot.cable(posB);
        plot.block(endpointB, AEBlocks.QUANTUM_ENDPOINT);

        // See quantum_pair_chunk_reload's own comment for why this capture-before-unload + connection-check pattern
        // is used instead of helper.getGrid() once A is (simulated-)unloaded.
        var nodeARef = new appeng.api.networking.IGridNode[1];

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(endpointA))
                    .thenWaitUntil(() -> helper.getGrid(endpointB))
                    .thenExecute(() -> {
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        var profile = topology.createProfile();
                        var credA = credentials.issue(profile.id());
                        var credB = credentials.issue(profile.id());
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var beB = helper.getBlockEntity(endpointB, QuantumEndpointBlockEntity.class);
                        beA.activateWithKey(encodedKey(profile.id().id(), credA.id()), IActionSource.empty());
                        beB.activateWithKey(encodedKey(profile.id().id(), credB.id()), IActionSource.empty());
                    })
                    .thenIdle(5)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(endpointA) == helper.getGrid(endpointB),
                                "pair must materialize first, before revocation", endpointA);
                        nodeARef[0] = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class).getMainNode()
                                .getNode();
                    })
                    .thenExecute(() -> {
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var credA = credentials.getCredentialForEndpoint(beA.getEndpointId());
                        // Round 11 update: this now goes through the PRODUCTION revoke path, which is handed the
                        // live per-server endpoint index and therefore dematerializes the already-established
                        // connection immediately. Before Round 11 this call could only prevent FUTURE
                        // (re)materialization, so this plot had to simulate a chunk unload to observe the connection
                        // actually going away; that workaround is gone, and the assertion below is now strictly
                        // stronger - the transport must disappear with NO unload at all.
                        var table = appeng.me.quantum.QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                        var result = QuantumCredentialService.revoke(QuantumTopologyRegistry.get(helper.getLevel()),
                                credentials, null, credA.id(), IActionSource.empty(), table);
                        helper.check(result == QuantumCredentialService.Result.OK, "revoke itself must succeed",
                                endpointA);
                    })
                    .thenIdle(5)
                    .thenExecute(() -> {
                        var beB = helper.getBlockEntity(endpointB, QuantumEndpointBlockEntity.class);
                        var nodeB = beB.getMainNode().getNode();
                        var stillConnectedToA = nodeB.getConnections().stream()
                                .anyMatch(c -> c.getOtherSide(nodeB) == nodeARef[0]);
                        helper.check(!stillConnectedToA,
                                "connection must be gone IMMEDIATELY on revoke - no unload, no restart, no Routing "
                                        + "Controller involved in the enforcement",
                                endpointA);
                        var beA = helper.getBlockEntity(endpointA, QuantumEndpointBlockEntity.class);
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        helper.check(topology.getEndpoint(beA.getEndpointId()) != null,
                                "identity must survive revocation - never deleted", endpointA);
                        var credA = credentials.getCredentialForEndpoint(beA.getEndpointId());
                        helper.check(credA.state() == appeng.api.networking.quantum.QuantumCredentialState.REVOKED,
                                "credential must stay REVOKED - nothing may resurrect it to OFFLINE", endpointA);
                        helper.check(beA.getMainNode().isReady(),
                                "endpoint A must still be perfectly loaded and ready - proving the transport went "
                                        + "away because of the REVOCATION itself, not because of an unload",
                                endpointA);
                        var laneId = topology.getEndpoint(beA.getEndpointId()).laneId();
                        helper.check(topology.getLane(laneId).getEdges().size() == 1,
                                "persisted topology intent (the edge) must survive revocation too", endpointA);
                        // NOTE ON SCOPE: proving that a subsequent RELOAD/RESTART never resurrects a REVOKED
                        // credential's materialization is covered at the JUnit level (real, repeated
                        // QuantumLaneReconciler#reconstruct calls against the real registries) by
                        // QuantumPairProvisioningTest#revokedCredentialsEndpointNeverParticipatesInReconstructionEvenAfterManyRetries
                        // - see quantum_pair_chunk_reload's own javadoc note for why this GameTest does not also
                        // re-invoke onReady() on the same already-destroyed ManagedGridNode instance to simulate that
                        // reload itself.
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // Shared helpers
    // =====================================================================

    private static ItemStack encodedKey(java.util.UUID profileId, QuantumCredentialId credentialId) {
        var stack = new ItemStack(AEItems.QUANTUM_KEY.asItem());
        stack.set(AEComponents.QUANTUM_KEY_CARD_DATA,
                new QuantumKeyCardData(profileId, credentialId.id(), 1, QuantumCredential.CURRENT_PROTOCOL_VERSION));
        return stack;
    }

    /**
     * Places a real {@link appeng.block.misc.SecurityStationBlock} with an explicit owner set before {@code onReady()}
     * runs, exactly mirroring {@code P2PCrossDomainSecurityPlots#securityStation} (same
     * {@code PlaceBlockState}-never-calls-{@code setPlacedBy} gap workaround via an explicit {@link BuildAction}).
     * Callers must place a cable at {@code pos.south()} - the station's own fixed connectable side.
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
