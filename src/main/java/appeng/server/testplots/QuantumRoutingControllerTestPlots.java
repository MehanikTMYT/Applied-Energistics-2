package appeng.server.testplots;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.QuantumKeyCardData;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumRouteState;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.misc.QuantumEndpointBlockEntity;
import appeng.blockentity.misc.QuantumRoutingControllerBlockEntity;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.quantum.QuantumCredential;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumCredentialService;
import appeng.me.quantum.QuantumEndpointOnlineTable;
import appeng.me.quantum.QuantumRouteMaterializer;
import appeng.me.quantum.QuantumRoutingService;
import appeng.me.quantum.QuantumTopologyRegistry;
import appeng.server.testworld.BuildAction;
import appeng.server.testworld.PlotBuilder;

/**
 * Round 11 ("Routing Controller foundation") — the required production GameTest coverage.
 * <p/>
 * Real physical {@code QuantumRoutingControllerBlock}s, real {@code QuantumEndpointBlock}s, real Security Stations,
 * real credential activation through the production {@code activateWithKey} entry point, real materialized
 * {@code GridConnection}s, and real storage visibility across them. The routing operations under test are invoked
 * through the very same block-entity methods the menu's buttons call — so what is exercised here is the genuine
 * server-side path, minus only the literal mouse click.
 */
@TestPlotClass
public class QuantumRoutingControllerTestPlots {
    private QuantumRoutingControllerTestPlots() {
    }

    // =====================================================================
    // 1. quantum_routing_controller_status — a real bound controller observes a real ACTIVE pair.
    // =====================================================================

    @TestPlot("quantum_routing_controller_status")
    public static void quantumRoutingControllerStatus(PlotBuilder plot) {
        var layout = layout(plot);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointA()))
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointB()))
                    .thenWaitUntil(() -> helper.getGrid(layout.station()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenExecute(() -> provision(helper, layout))
                    .thenIdle(15)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "the Mk I PAIR must materialize before the controller can observe it",
                                layout.endpointA());

                        var controller = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        helper.check(controller.getProfileId() != null,
                                "controller must resolve exactly ONE profile from its own SECURED network",
                                layout.controller());
                        helper.check(controller.listPairs().size() == 1,
                                "controller must see exactly its own profile's single lane", layout.controller());

                        var outcome = controller.viewSelectedPair(IActionSource.empty());
                        helper.check(outcome.result() == QuantumRoutingService.Result.OK,
                                "status read failed: " + outcome.result(), layout.controller());
                        helper.check(outcome.status().state() == QuantumRouteState.ACTIVE,
                                "controller must report ACTIVE for a healthy materialized pair, got "
                                        + outcome.status().state(),
                                layout.controller());
                        helper.check(outcome.status().intentExists() && outcome.status().materialized(),
                                "both intent AND materialization must be reported", layout.controller());
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // Round 12 pre-gate: two controllers on one profile are equivalent stateless control-plane clients.
    // =====================================================================

    @TestPlot("routing_controller_multiple_same_profile")
    public static void routingControllerMultipleSameProfile(PlotBuilder plot) {
        var layout = layout(plot);
        var controllerBPos = layout.posA().south();
        plot.block(controllerBPos, AEBlocks.QUANTUM_ROUTING_CONTROLLER);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointA()))
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointB()))
                    .thenWaitUntil(() -> helper.getGrid(layout.station()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenWaitUntil(() -> helper.getGrid(controllerBPos))
                    .thenExecute(() -> provision(helper, layout))
                    .thenIdle(15)
                    .thenExecute(() -> {
                        var controllerA = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        var controllerB = helper.getBlockEntity(controllerBPos,
                                QuantumRoutingControllerBlockEntity.class);
                        helper.check(controllerA.getProfileId() != null
                                && controllerA.getProfileId().equals(controllerB.getProfileId()),
                                "both controllers must resolve the same physically connected profile",
                                layout.controller());
                        helper.check(controllerA.viewSelectedPair(IActionSource.empty()).status()
                                .equals(controllerB.viewSelectedPair(IActionSource.empty()).status()),
                                "both controllers must derive the same route status", controllerBPos);

                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var lane = topology.getLane(controllerA.getSelectedLane());
                        var edge = lane.getEdges().getFirst();
                        var table = QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                        helper.check(QuantumRouteMaterializer.dematerializeEdge(table, edge.a(), edge.b()),
                                "test setup must remove the shared runtime connection", layout.controller());
                        helper.check(
                                controllerA
                                        .repairSelectedRoute(IActionSource.empty()) == QuantumRoutingService.Result.OK,
                                "controller A must repair existing intent",
                                layout.controller());
                        helper.check(
                                controllerB
                                        .repairSelectedRoute(IActionSource.empty()) == QuantumRoutingService.Result.OK,
                                "controller B repair must be idempotent",
                                controllerBPos);

                        var nodeA = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class)
                                .getMainNode().getNode();
                        var nodeB = helper.getBlockEntity(layout.endpointB(), QuantumEndpointBlockEntity.class)
                                .getMainNode().getNode();
                        var between = nodeA.getConnections().stream().filter(c -> c.getOtherSide(nodeA) == nodeB)
                                .count();
                        helper.check(between == 1, "two controllers must not duplicate a materialized connection",
                                layout.endpointA());
                    })
                    .thenExecute(() -> helper.setBlock(layout.controller(), net.minecraft.world.level.block.Blocks.AIR))
                    .thenIdle(10)
                    .thenExecute(() -> {
                        var controllerB = helper.getBlockEntity(controllerBPos,
                                QuantumRoutingControllerBlockEntity.class);
                        helper.check(controllerB.viewSelectedPair(IActionSource.empty()).status().materialized(),
                                "controller B must remain operational after controller A is destroyed", controllerBPos);
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "destroying controller A must not affect transport", layout.endpointA());
                    })
                    .thenExecute(
                            () -> helper.setBlock(layout.controller(), AEBlocks.QUANTUM_ROUTING_CONTROLLER.block()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenIdle(10)
                    .thenExecute(() -> {
                        var controllerA = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        var controllerB = helper.getBlockEntity(controllerBPos,
                                QuantumRoutingControllerBlockEntity.class);
                        helper.check(controllerA.getProfileId().equals(controllerB.getProfileId()),
                                "re-placed controller A must converge on controller B's profile", layout.controller());
                        helper.check(controllerA.viewSelectedPair(IActionSource.empty()).status()
                                .equals(controllerB.viewSelectedPair(IActionSource.empty()).status()),
                                "re-placed controller A must converge on the same derived status", layout.controller());
                    })
                    .thenExecute(() -> {
                        helper.setBlock(layout.controller(), net.minecraft.world.level.block.Blocks.AIR);
                        helper.setBlock(controllerBPos, net.minecraft.world.level.block.Blocks.AIR);
                    })
                    .thenIdle(10)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "destroying both controllers must not affect healthy transport", layout.endpointA());
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var endpoint = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class)
                                .getEndpointId();
                        helper.check(topology.getLane(topology.getEndpoint(endpoint).laneId()).getEdges().size() == 1,
                                "destroying both controllers must not delete exact persisted intent",
                                layout.endpointA());
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 2. quantum_routing_manual_reconcile — a missing materialization keeps exact persisted intent, and the
    // authorized controller repair restores that same edge without creating or rewriting topology intent.
    // =====================================================================

    @TestPlot("quantum_routing_manual_reconcile")
    public static void quantumRoutingManualReconcile(PlotBuilder plot) {
        var layout = layout(plot);
        // A creative cell on network A, so remote storage visibility from B is a real, observable fact.
        plot.drive(layout.posA().above()).addCreativeCell().add(Items.DIAMOND);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointA()))
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointB()))
                    .thenWaitUntil(() -> helper.getGrid(layout.station()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenExecute(() -> provision(helper, layout))
                    .thenIdle(15)
                    .thenExecute(() -> {
                        var diamond = AEItemKey.of(Items.DIAMOND);
                        helper.getGrid(layout.endpointA()).getStorageService().getInventory().insert(diamond, 4,
                                Actionable.MODULATE, IActionSource.empty());
                    })
                    .thenIdle(3)
                    .thenExecute(() -> {
                        var seen = new KeyCounter();
                        helper.getGrid(layout.endpointB()).getStorageService().getInventory().getAvailableStacks(seen);
                        helper.check(seen.get(AEItemKey.of(Items.DIAMOND)) > 0,
                                "remote storage must be visible through the materialized PAIR before dematerialization",
                                layout.endpointB());
                    })
                    .thenExecute(() -> {
                        var controller = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var lane = topology.getLane(controller.getSelectedLane());
                        var edge = lane.getEdges().getFirst();
                        var table = QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                        helper.check(QuantumRouteMaterializer.dematerializeEdge(table, edge.a(), edge.b()),
                                "test setup must remove the existing runtime connection", layout.controller());
                    })
                    .thenIdle(10)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) != helper.getGrid(layout.endpointB()),
                                "missing materialization must split the two grids apart",
                                layout.endpointA());
                        var seen = new KeyCounter();
                        helper.getGrid(layout.endpointB()).getStorageService().getInventory().getAvailableStacks(seen);
                        helper.check(seen.get(AEItemKey.of(Items.DIAMOND)) == 0,
                                "remote storage must disappear while the route is not materialized",
                                layout.endpointB());

                        var controller = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        var status = controller.viewSelectedPair(IActionSource.empty()).status();
                        helper.check(status.intentExists() && !status.materialized(),
                                "intent must survive while runtime materialization is absent",
                                layout.controller());
                    })
                    .thenExecute(() -> {
                        var controller = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        var result = controller.repairSelectedRoute(IActionSource.empty());
                        helper.check(result == QuantumRoutingService.Result.OK,
                                "authorized existing-intent reconcile failed: " + result, layout.controller());
                    })
                    .thenIdle(10)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "manual reconcile must re-materialize the same PAIR", layout.endpointA());
                        var seen = new KeyCounter();
                        helper.getGrid(layout.endpointB()).getStorageService().getInventory().getAvailableStacks(seen);
                        helper.check(seen.get(AEItemKey.of(Items.DIAMOND)) > 0,
                                "remote storage must come back once existing intent is reconciled", layout.endpointB());

                        // Repair on an already-active route is idempotent - no duplicate connection.
                        var controller = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        for (int i = 0; i < 5; i++) {
                            controller.repairSelectedRoute(IActionSource.empty());
                        }
                        var nodeA = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class)
                                .getMainNode().getNode();
                        var nodeB = helper.getBlockEntity(layout.endpointB(), QuantumEndpointBlockEntity.class)
                                .getMainNode().getNode();
                        var between = nodeA.getConnections().stream().filter(c -> c.getOtherSide(nodeA) == nodeB)
                                .count();
                        helper.check(between == 1, "repeated repair must never duplicate the connection, found "
                                + between, layout.endpointA());
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 3. quantum_routing_router_destruction — the control plane is never load-bearing for transport.
    // =====================================================================

    @TestPlot("quantum_routing_router_destruction")
    public static void quantumRoutingRouterDestruction(PlotBuilder plot) {
        var layout = layout(plot);
        plot.drive(layout.posA().above()).addCreativeCell().add(Items.DIAMOND);

        var endpointIds = new appeng.api.networking.quantum.QuantumEndpointId[2];

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointA()))
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointB()))
                    .thenWaitUntil(() -> helper.getGrid(layout.station()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenExecute(() -> provision(helper, layout))
                    .thenIdle(15)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "pair must be healthy before the router is destroyed", layout.endpointA());
                        endpointIds[0] = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class)
                                .getEndpointId();
                        endpointIds[1] = helper.getBlockEntity(layout.endpointB(), QuantumEndpointBlockEntity.class)
                                .getEndpointId();
                        var diamond = AEItemKey.of(Items.DIAMOND);
                        helper.getGrid(layout.endpointA()).getStorageService().getInventory().insert(diamond, 4,
                                Actionable.MODULATE, IActionSource.empty());
                    })
                    .thenExecute(() -> {
                        // Physically destroy the Routing Controller block - the real production removal path.
                        helper.setBlock(layout.controller(), net.minecraft.world.level.block.Blocks.AIR);
                    })
                    .thenIdle(15)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "destroying the Routing Controller must NOT break a healthy existing PAIR",
                                layout.endpointA());

                        var seen = new KeyCounter();
                        helper.getGrid(layout.endpointB()).getStorageService().getInventory().getAvailableStacks(seen);
                        helper.check(seen.get(AEItemKey.of(Items.DIAMOND)) > 0,
                                "remote storage visibility must survive the router's destruction", layout.endpointB());

                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        helper.check(topology.getEndpoint(endpointIds[0]) != null
                                && topology.getEndpoint(endpointIds[1]) != null,
                                "endpoint identities must survive the router's destruction", layout.endpointA());
                        var laneId = topology.getEndpoint(endpointIds[0]).laneId();
                        helper.check(topology.getLane(laneId).getEdges().size() == 1,
                                "persisted route intent must survive the router's destruction", layout.endpointA());
                        var credential = credentials.getCredentialForEndpoint(endpointIds[0]);
                        helper.check(credential != null && credential.state().permitsActiveRoute(),
                                "credentials must survive the router's destruction, un-revoked", layout.endpointA());
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 4. quantum_routing_unauthorized — a real player without QUANTUM_ROUTE mutates nothing at all.
    // =====================================================================

    @TestPlot("quantum_routing_unauthorized")
    public static void quantumRoutingUnauthorized(PlotBuilder plot) {
        var layout = layout(plot);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointA()))
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointB()))
                    .thenWaitUntil(() -> helper.getGrid(layout.station()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenExecute(() -> provision(helper, layout))
                    .thenIdle(15)
                    .thenExecute(() -> {
                        var controller = helper.getBlockEntity(layout.controller(),
                                QuantumRoutingControllerBlockEntity.class);
                        // A real, freshly-made player who is neither the domain owner nor granted anything.
                        var intruder = helper.makeMockPlayer(GameType.SURVIVAL);
                        var actor = IActionSource.ofPlayer(intruder);

                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var laneId = controller.getSelectedLane();
                        var edgesBefore = topology.getLane(laneId).getEdges().size();

                        helper.check(controller.repairSelectedRoute(actor) == QuantumRoutingService.Result.FORBIDDEN,
                                "an actor without QUANTUM_ROUTE must not be able to repair a route",
                                layout.controller());
                        helper.check(controller.viewSelectedPair(actor)
                                .result() == QuantumRoutingService.Result.FORBIDDEN,
                                "an actor without QUANTUM_VIEW must not be able to read route status",
                                layout.controller());

                        helper.check(topology.getLane(laneId).getEdges().size() == edgesBefore,
                                "no registry mutation may have occurred", layout.controller());
                    })
                    .thenIdle(5)
                    .thenExecute(() -> helper.check(
                            helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                            "no connection mutation may have occurred either", layout.endpointA()))
                    .thenSucceed();
        });
    }

    // =====================================================================
    // 5. quantum_routing_credential_revoke_live — the Round 11 security closure, at the production block level:
    // revoking an ACTIVE pair's credential must remove the remote transport IMMEDIATELY, with no unload, no
    // restart and no Routing Controller involved in the enforcement.
    // =====================================================================

    @TestPlot("quantum_routing_credential_revoke_live")
    public static void quantumRoutingCredentialRevokeLive(PlotBuilder plot) {
        var layout = layout(plot);
        plot.drive(layout.posA().above()).addCreativeCell().add(Items.DIAMOND);

        plot.test(helper -> {
            helper.startSequence()
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointA()))
                    .thenWaitUntil(() -> helper.getGrid(layout.endpointB()))
                    .thenWaitUntil(() -> helper.getGrid(layout.station()))
                    .thenWaitUntil(() -> helper.getGrid(layout.controller()))
                    .thenExecute(() -> provision(helper, layout))
                    .thenIdle(15)
                    .thenExecute(() -> {
                        helper.check(helper.getGrid(layout.endpointA()) == helper.getGrid(layout.endpointB()),
                                "pair must be materialized and active before revocation", layout.endpointA());
                        var diamond = AEItemKey.of(Items.DIAMOND);
                        helper.getGrid(layout.endpointA()).getStorageService().getInventory().insert(diamond, 4,
                                Actionable.MODULATE, IActionSource.empty());
                    })
                    .thenIdle(3)
                    .thenExecute(() -> {
                        var seen = new KeyCounter();
                        helper.getGrid(layout.endpointB()).getStorageService().getInventory().getAvailableStacks(seen);
                        helper.check(seen.get(AEItemKey.of(Items.DIAMOND)) > 0,
                                "remote storage must be visible before revocation", layout.endpointB());
                    })
                    .thenExecute(() -> {
                        // The production revoke path, exactly as the Imprinter's menu drives it: the live per-server
                        // endpoint index is passed, so the already-materialized connection is torn down at once.
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                        var table = appeng.me.quantum.QuantumEndpointOnlineTable
                                .get(helper.getLevel().getServer());
                        var beA = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class);
                        var credA = credentials.getCredentialForEndpoint(beA.getEndpointId());
                        var result = QuantumCredentialService.revoke(topology, credentials, null, credA.id(),
                                IActionSource.empty(), table);
                        helper.check(result == QuantumCredentialService.Result.OK, "revoke failed: " + result,
                                layout.endpointA());
                    })
                    .thenIdle(10)
                    .thenExecute(() -> {
                        // NO unload, NO restart, NO controller action happened in between.
                        helper.check(helper.getGrid(layout.endpointA()) != helper.getGrid(layout.endpointB()),
                                "a revoked credential must not keep the two networks merged", layout.endpointA());
                        var seen = new KeyCounter();
                        helper.getGrid(layout.endpointB()).getStorageService().getInventory().getAvailableStacks(seen);
                        helper.check(seen.get(AEItemKey.of(Items.DIAMOND)) == 0,
                                "remote storage must be gone immediately after revocation", layout.endpointB());

                        // Identity and intent deliberately survive - revocation is a credential fact, not a
                        // topology edit.
                        var topology = QuantumTopologyRegistry.get(helper.getLevel());
                        var beA = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class);
                        helper.check(topology.getEndpoint(beA.getEndpointId()) != null,
                                "endpoint identity must survive revocation", layout.endpointA());
                        var laneId = topology.getEndpoint(beA.getEndpointId()).laneId();
                        helper.check(topology.getLane(laneId).getEdges().size() == 1,
                                "persisted route intent must survive revocation", layout.endpointA());
                    })
                    .thenSucceed();
        });
    }

    // =====================================================================
    // Round 12 production HUB vertical slice.
    // =====================================================================

    @TestPlot("quantum_hub_three_endpoint_basic")
    public static void quantumHubThreeEndpointBasic(PlotBuilder plot) {
        var hub = hubLayout(plot, false);
        plot.drive(hub.base().posA().above()).addCreativeCell().add(Items.DIAMOND);
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointA()))
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointB()))
                .thenWaitUntil(() -> helper.getGrid(hub.endpointC()))
                .thenWaitUntil(() -> helper.getGrid(hub.base().controller()))
                .thenExecute(() -> provisionHub(helper, hub))
                .thenIdle(15)
                .thenExecute(() -> {
                    var a = helper.getBlockEntity(hub.base().endpointA(), QuantumEndpointBlockEntity.class);
                    var b = helper.getBlockEntity(hub.base().endpointB(), QuantumEndpointBlockEntity.class);
                    var c = helper.getBlockEntity(hub.endpointC(), QuantumEndpointBlockEntity.class);
                    var topology = QuantumTopologyRegistry.get(helper.getLevel());
                    var lane = topology.getLane(topology.getEndpoint(a.getEndpointId()).laneId());
                    helper.check(lane.getEndpoints().size() == 3 && lane.getEdges().size() == 2,
                            "three physical endpoints must persist exactly two HUB edges", hub.base().endpointA());
                    helper.check(lane.hasEdge(a.getEndpointId(), b.getEndpointId())
                            && lane.hasEdge(a.getEndpointId(), c.getEndpointId())
                            && !lane.hasEdge(b.getEndpointId(), c.getEndpointId()),
                            "HUB must be exact star A-B/A-C, never the B-C full-mesh edge", hub.base().endpointA());
                    helper.check(helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.base().endpointB())
                            && helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.endpointC()),
                            "all three real endpoint grids must materialize into one grid", hub.base().endpointA());
                    var controller = helper.getBlockEntity(hub.base().controller(),
                            QuantumRoutingControllerBlockEntity.class);
                    var status = controller.viewSelectedLane(IActionSource.empty()).status();
                    helper.check(status.endpoints().size() == 3 && status.intendedEdgeCount() == 2
                            && status.materializedEdgeCount() == 2,
                            "Router must derive 3 endpoints / 2 intent / 2 materialized", hub.base().controller());
                })
                .thenSucceed());
    }

    @TestPlot("quantum_hub_partial_member_unload")
    public static void quantumHubPartialMemberUnload(PlotBuilder plot) {
        var hub = hubLayout(plot, false);
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointA()))
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointB()))
                .thenWaitUntil(() -> helper.getGrid(hub.endpointC()))
                .thenExecute(() -> provisionHub(helper, hub))
                .thenIdle(15)
                .thenExecute(() -> {
                    var c = helper.getBlockEntity(hub.endpointC(), QuantumEndpointBlockEntity.class);
                    var table = QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                    var topology = QuantumTopologyRegistry.get(helper.getLevel());
                    QuantumRouteMaterializer.dematerializeEndpoint(topology, table, c.getEndpointId());
                    table.unregister(c.getEndpointId(), c.getMainNode().getNode());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.check(helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.base().endpointB()),
                            "A-B arm must remain healthy while C is unavailable", hub.base().endpointA());
                    helper.check(helper.getGrid(hub.base().endpointA()) != helper.getGrid(hub.endpointC()),
                            "only A-C arm must be unavailable", hub.endpointC());
                    var topology = QuantumTopologyRegistry.get(helper.getLevel());
                    var a = helper.getBlockEntity(hub.base().endpointA(), QuantumEndpointBlockEntity.class);
                    helper.check(
                            topology.getLane(topology.getEndpoint(a.getEndpointId()).laneId()).getEdges().size() == 2,
                            "member unavailability must not delete intent", hub.base().endpointA());
                    var c = helper.getBlockEntity(hub.endpointC(), QuantumEndpointBlockEntity.class);
                    var table = QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                    table.register(c.getEndpointId(), c.getMainNode().getNode());
                    QuantumRouteMaterializer.materializeLane(topology, QuantumCredentialRegistry.get(helper.getLevel()),
                            table, topology.getEndpoint(a.getEndpointId()).laneId());
                })
                .thenIdle(5)
                .thenExecute(() -> helper.check(
                        helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.endpointC()),
                        "returning C must restore only its exact persisted arm", hub.endpointC()))
                .thenSucceed());
    }

    @TestPlot("quantum_hub_anchor_unavailable")
    public static void quantumHubAnchorUnavailable(PlotBuilder plot) {
        var hub = hubLayout(plot, false);
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointA()))
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointB()))
                .thenWaitUntil(() -> helper.getGrid(hub.endpointC()))
                .thenExecute(() -> provisionHub(helper, hub))
                .thenIdle(15)
                .thenExecute(() -> {
                    var a = helper.getBlockEntity(hub.base().endpointA(), QuantumEndpointBlockEntity.class);
                    var table = QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                    QuantumRouteMaterializer.dematerializeEndpoint(QuantumTopologyRegistry.get(helper.getLevel()),
                            table,
                            a.getEndpointId());
                    table.unregister(a.getEndpointId(), a.getMainNode().getNode());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.check(helper.getGrid(hub.base().endpointB()) != helper.getGrid(hub.endpointC()),
                            "members must never auto-connect when explicit anchor is unavailable",
                            hub.base().endpointB());
                    var topology = QuantumTopologyRegistry.get(helper.getLevel());
                    var a = helper.getBlockEntity(hub.base().endpointA(), QuantumEndpointBlockEntity.class);
                    var laneId = topology.getEndpoint(a.getEndpointId()).laneId();
                    helper.check(topology.getLane(laneId).getEdges().size() == 2,
                            "anchor unavailability must retain both exact edges", hub.base().endpointA());
                    var table = QuantumEndpointOnlineTable.get(helper.getLevel().getServer());
                    table.register(a.getEndpointId(), a.getMainNode().getNode());
                    QuantumRouteMaterializer.materializeLane(topology, QuantumCredentialRegistry.get(helper.getLevel()),
                            table, laneId);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.check(
                        helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.base().endpointB())
                                && helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.endpointC()),
                        "anchor return must restore the same two exact edges", hub.base().endpointA()))
                .thenSucceed());
    }

    @TestPlot("quantum_hub_credential_revoke_member")
    public static void quantumHubCredentialRevokeMember(PlotBuilder plot) {
        var hub = hubLayout(plot, false);
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointA()))
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointB()))
                .thenWaitUntil(() -> helper.getGrid(hub.endpointC()))
                .thenExecute(() -> provisionHub(helper, hub))
                .thenIdle(15)
                .thenExecute(() -> {
                    var c = helper.getBlockEntity(hub.endpointC(), QuantumEndpointBlockEntity.class);
                    var topology = QuantumTopologyRegistry.get(helper.getLevel());
                    var credentials = QuantumCredentialRegistry.get(helper.getLevel());
                    var result = QuantumCredentialService.revoke(topology, credentials, null,
                            credentials.getCredentialForEndpoint(c.getEndpointId()).id(), IActionSource.empty(),
                            QuantumEndpointOnlineTable.get(helper.getLevel().getServer()));
                    helper.check(result == QuantumCredentialService.Result.OK, "member revoke failed", hub.endpointC());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.check(helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.base().endpointB()),
                            "revoking C must not tear down healthy A-B", hub.base().endpointB());
                    helper.check(helper.getGrid(hub.base().endpointA()) != helper.getGrid(hub.endpointC()),
                            "revoking C must tear down only A-C", hub.endpointC());
                })
                .thenSucceed());
    }

    @TestPlot("quantum_hub_security_partial_rejection")
    public static void quantumHubSecurityPartialRejection(PlotBuilder plot) {
        var hub = hubLayout(plot, true);
        plot.test(helper -> helper.startSequence()
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointA()))
                .thenWaitUntil(() -> helper.getGrid(hub.base().endpointB()))
                .thenWaitUntil(() -> helper.getGrid(hub.endpointC()))
                .thenWaitUntil(() -> helper.getGrid(hub.stationC()))
                .thenExecute(() -> {
                    var stationA = helper.getBlockEntity(hub.base().station(), SecurityStationBlockEntity.class);
                    var stationC = helper.getBlockEntity(hub.stationC(), SecurityStationBlockEntity.class);
                    helper.check(stationA.getDomainState() == SecurityDomainState.SECURED
                            && stationC.getDomainState() == SecurityDomainState.SECURED,
                            "both physical networks must be SECURED before HUB provisioning", hub.stationC());
                    helper.check(!stationA.getEditedDomainId().equals(stationC.getEditedDomainId()),
                            "the rejected HUB arm must use a genuinely different Security Domain", hub.stationC());
                    helper.check(helper.getGrid(hub.base().station()) != helper.getGrid(hub.stationC()),
                            "the two domains must start on separate grids", hub.stationC());
                })
                .thenExecute(() -> provisionHub(helper, hub))
                .thenIdle(15)
                .thenExecute(() -> {
                    helper.check(helper.getGrid(hub.base().endpointA()) == helper.getGrid(hub.base().endpointB()),
                            "same-domain A-B must materialize", hub.base().endpointB());
                    helper.check(helper.getGrid(hub.base().endpointA()) != helper.getGrid(hub.endpointC()),
                            "cross-domain A-C must be rejected before merge", hub.endpointC());
                    var controller = helper.getBlockEntity(hub.base().controller(),
                            QuantumRoutingControllerBlockEntity.class);
                    var status = controller.viewSelectedLane(IActionSource.empty()).status();
                    helper.check(status.intendedEdgeCount() == 2 && status.materializedEdgeCount() == 1,
                            "partial security rejection must report one healthy and one blocked arm",
                            hub.base().controller());
                })
                .thenSucceed());
    }

    // =====================================================================
    // Shared layout / provisioning helpers
    // =====================================================================

    /**
     * Two separate physical networks: A (cable + endpoint + Security Station + Routing Controller) and B (cable +
     * endpoint). The controller deliberately lives on a SECURED network, because that is the only situation in which it
     * can deterministically resolve exactly one Network Profile.
     */
    private record Layout(BlockPos posA, BlockPos posB, BlockPos endpointA, BlockPos endpointB, BlockPos station,
            BlockPos controller) {
    }

    private record HubLayout(Layout base, BlockPos posC, BlockPos endpointC, BlockPos stationC) {
    }

    private static HubLayout hubLayout(PlotBuilder plot, boolean secureCSeparately) {
        var base = layout(plot);
        var posC = new BlockPos(6, 0, 0);
        var endpointC = posC.west();
        // Security Station connects on its south face, so the cable must be directly south of it.
        var stationC = posC.north();
        plot.creativeEnergyCell(posC.below());
        plot.cable(posC);
        plot.block(endpointC, AEBlocks.QUANTUM_ENDPOINT_MK2);
        if (secureCSeparately) {
            securityStation(plot, stationC);
        }
        return new HubLayout(base, posC, endpointC, stationC);
    }

    private static Layout layout(PlotBuilder plot) {
        var posA = new BlockPos(0, 0, 0);
        var posB = new BlockPos(0, 0, 6);
        var endpointA = posA.east();
        var endpointB = posB.east();
        var stationPos = posA.north();
        var controllerPos = posA.west();

        plot.creativeEnergyCell(posA.below());
        plot.cable(posA);
        plot.block(endpointA, AEBlocks.QUANTUM_ENDPOINT);
        securityStation(plot, stationPos);
        plot.block(controllerPos, AEBlocks.QUANTUM_ROUTING_CONTROLLER);

        plot.creativeEnergyCell(posB.below());
        plot.cable(posB);
        plot.block(endpointB, AEBlocks.QUANTUM_ENDPOINT);

        return new Layout(posA, posB, endpointA, endpointB, stationPos, controllerPos);
    }

    /**
     * Binds a brand-new profile to network A's live security domain — exactly what a Quantum Key Imprinter's own
     * {@code tryBootstrapProfile} does in production — then provisions and activates both physical endpoints through
     * the real {@code activateWithKey} entry point.
     */
    private static void provision(appeng.server.testworld.PlotTestHelper helper, Layout layout) {
        var topology = QuantumTopologyRegistry.get(helper.getLevel());
        var credentials = QuantumCredentialRegistry.get(helper.getLevel());

        var station = helper.getBlockEntity(layout.station(), SecurityStationBlockEntity.class);
        helper.check(station.getDomainState() == SecurityDomainState.SECURED,
                "network A must be SECURED for the controller to bind to a profile", layout.station());
        var domainId = station.getEditedDomainId();

        var profile = topology.createProfile();
        topology.bindSecurityDomain(profile.id(), domainId);

        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var beA = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class);
        var beB = helper.getBlockEntity(layout.endpointB(), QuantumEndpointBlockEntity.class);
        var resultA = beA.activateWithKey(encodedKey(profile.id().id(), credA.id()), IActionSource.empty());
        var resultB = beB.activateWithKey(encodedKey(profile.id().id(), credB.id()), IActionSource.empty());
        helper.check(resultA == QuantumCredentialService.Result.OK, "activation A failed: " + resultA,
                layout.endpointA());
        helper.check(resultB == QuantumCredentialService.Result.OK, "activation B failed: " + resultB,
                layout.endpointB());
    }

    private static void provisionHub(appeng.server.testworld.PlotTestHelper helper, HubLayout hub) {
        var layout = hub.base();
        var topology = QuantumTopologyRegistry.get(helper.getLevel());
        var credentials = QuantumCredentialRegistry.get(helper.getLevel());
        var station = helper.getBlockEntity(layout.station(), SecurityStationBlockEntity.class);
        var profile = topology.createProfile();
        topology.bindSecurityDomain(profile.id(), station.getEditedDomainId());
        var credA = credentials.issue(profile.id());
        var credB = credentials.issue(profile.id());
        var credC = credentials.issue(profile.id());
        var keyA = encodedKey(profile.id().id(), credA.id());
        var beA = helper.getBlockEntity(layout.endpointA(), QuantumEndpointBlockEntity.class);
        var beB = helper.getBlockEntity(layout.endpointB(), QuantumEndpointBlockEntity.class);
        var beC = helper.getBlockEntity(hub.endpointC(), QuantumEndpointBlockEntity.class);
        helper.check(beA.activateWithKey(keyA, IActionSource.empty()) == QuantumCredentialService.Result.OK,
                "anchor activation failed", layout.endpointA());
        helper.check(
                beB.activateWithKey(encodedKey(profile.id().id(), credB.id()),
                        IActionSource.empty()) == QuantumCredentialService.Result.OK,
                "first member activation failed", layout.endpointB());
        helper.check(
                beC.activateAsHubMember(encodedKey(profile.id().id(), credC.id()), keyA,
                        IActionSource.empty()) == QuantumCredentialService.Result.OK,
                "explicit HUB member activation failed", hub.endpointC());
    }

    private static ItemStack encodedKey(java.util.UUID profileId, QuantumCredentialId credentialId) {
        var stack = new ItemStack(AEItems.QUANTUM_KEY.asItem());
        stack.set(AEComponents.QUANTUM_KEY_CARD_DATA,
                new QuantumKeyCardData(profileId, credentialId.id(), 1, QuantumCredential.CURRENT_PROTOCOL_VERSION));
        return stack;
    }

    /**
     * Places a real Security Station with an explicit owner set before {@code onReady()} — identical to
     * {@code QuantumPairTestPlots#securityStation}/{@code P2PCrossDomainSecurityPlots#securityStation}.
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
