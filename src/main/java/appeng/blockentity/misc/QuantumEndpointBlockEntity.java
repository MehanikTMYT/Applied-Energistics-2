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

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.ids.AEComponents;
import appeng.api.networking.GridFlags;
import appeng.api.networking.pathing.CapacityTier;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumEndpointLocation;
import appeng.api.networking.security.IActionSource;
import appeng.block.misc.QuantumEndpointBlock;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.quantum.QuantumCredentialRegistry;
import appeng.me.quantum.QuantumCredentialService;
import appeng.me.quantum.QuantumEndpointOnlineTable;
import appeng.me.quantum.QuantumLaneReconciler;
import appeng.me.quantum.QuantumTopologyRegistry;
import appeng.me.security.SecurityDomainRegistry;

/**
 * Round 10 ("Mk I PHYSICAL QUANTUM PAIR"): the first physical block that materializes Q3's persisted topology intent
 * and Q4's credential authority into a real, in-world {@link appeng.api.networking.IGridConnection}.
 * <p/>
 * <b>Ownership model — one node, no main/outer split, no multiblock.</b> Unlike an ME P2P tunnel (which has two
 * distinct roles on one device: a channel-consuming local side and a capacity-carrying transport side), a Quantum PAIR
 * endpoint has exactly one role — it IS its own remote link's local attachment point, exactly like each side of the
 * existing vanilla {@code QuantumBridgeBlockEntity}/{@code QuantumCluster} pair (see Round 10's own archaeology
 * report). This class deliberately mirrors {@code AENetworkedBlockEntity}'s ordinary single-node
 * create-on-{@link #onReady()}/destroy-on-unload lifecycle — the SAME shape dozens of other single-node AE2 blocks
 * already use (e.g. {@link QuantumKeyImprinterBlockEntity} itself) — with {@link GridFlags#DENSE_CAPACITY} plus an
 * explicit {@link CapacityTier#STANDARD} (Mk I's 32-channel transport ceiling; mirrors exactly how
 * {@code QuantumMEP2PTunnelPart}/{@code SingularityMEP2PTunnelPart}/{@code CosmicMEP2PTunnelPart} call
 * {@code setCapacityTier} on their own transport node — here it is a documented no-op against the field's own default,
 * kept explicit purely so "this transport ceiling is an intentional STANDARD/32, not an accidental default" is a
 * literal, greppable fact).
 * <p/>
 * <b>Identity is stable and never regenerated.</b> {@link #endpointId} is assigned exactly once — the first time this
 * physical block is successfully activated with a valid credential (see {@link #activateWithKey}) — and persisted in
 * this block entity's own NBT via {@link QuantumEndpointId#CODEC} from then on; a chunk reload/server restart never
 * mints a new one (see {@link #loadTag}/{@link #saveAdditional}). {@code BlockPos} is never used as, or derived into,
 * this identity (see {@link QuantumEndpointId} class javadoc) — only ever recorded as this endpoint's CURRENT
 * {@link QuantumEndpointLocation}, an ordinary status attribute.
 * <p/>
 * <b>Intent vs. materialization stays exactly as Q3/Q4 left it.</b> This class never itself decides whether a
 * {@link appeng.api.networking.IGridConnection} should exist — {@link QuantumLaneReconciler#reconstruct} (unmodified)
 * is the only thing that ever forms one, driven by the SAME persisted {@code QuantumLane} edge Q3 already treats as
 * authoritative. {@link #onReady()} (covers both a fresh chunk load AND an ordinary server restart — there is no
 * separate "restart" code path) marks the endpoint online, folds its credential's OFFLINE→ONLINE transition through
 * {@link QuantumCredentialRegistry#markOnline} directly (a trusted-internal state bump, no {@link IActionSource}
 * involved — restart reconstruction remains authentication-free per Q3/Q4's own hard invariant), registers this node
 * into the server's {@link QuantumEndpointOnlineTable}, and asks the reconciler to (re)attempt the lane's persisted
 * edge — which is a safe, idempotent no-op if the connection already exists, and simply waits if the peer endpoint is
 * not currently online (never substitutes a different edge).
 */
public class QuantumEndpointBlockEntity extends AENetworkedBlockEntity {
    private static final String NBT_ENDPOINT_ID = "quantumEndpointId";

    @Nullable
    private QuantumEndpointId endpointId;

    public QuantumEndpointBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
        this.getMainNode().setFlags(GridFlags.DENSE_CAPACITY);
        var tier = blockState.getBlock() instanceof QuantumEndpointBlock endpointBlock
                ? endpointBlock.getCapacityTier()
                : CapacityTier.STANDARD;
        this.getMainNode().setCapacityTier(tier);
    }

    /**
     * @return this endpoint's stable identity, or {@code null} if this physical block has never been successfully
     *         activated with a valid credential yet (UNPROVISIONED — see the Round 10 task manifest's Phase H).
     */
    @Nullable
    public QuantumEndpointId getEndpointId() {
        return endpointId;
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        this.endpointId = data.read(NBT_ENDPOINT_ID, QuantumEndpointId.CODEC).orElse(null);
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        if (this.endpointId != null) {
            data.store(NBT_ENDPOINT_ID, QuantumEndpointId.CODEC, this.endpointId);
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        goOnlineIfProvisioned();
    }

    @Override
    public void onChunkUnloaded() {
        goOffline();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        goOffline();
        super.setRemoved();
    }

    /**
     * Server-authoritative activation entry point (called from {@code QuantumEndpointBlock#useItemOn}): reads the held
     * "Encoded Quantum Key" card's {@link appeng.api.implementations.items.QuantumKeyCardData} and re-validates it
     * against the live registries — the card's component data is never trusted on its own (see
     * {@link appeng.me.quantum.QuantumCredential} class javadoc). If this physical endpoint has no stable identity yet,
     * delegates to {@link QuantumCredentialService#activateFreshPhysicalEndpoint} (which itself performs the
     * find-or-create-lane + register-endpoint provisioning under the exact same {@code BUILD} gate before ever calling
     * the ordinary, unmodified {@link QuantumCredentialService#activate}); otherwise re-activates through
     * {@link QuantumCredentialService#activate} directly (idempotent re-activation/reattach of an already-provisioned
     * endpoint — e.g. a repeated activation attempt with the same card).
     */
    public QuantumCredentialService.Result activateWithKey(ItemStack heldKey, IActionSource actor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return QuantumCredentialService.Result.NOT_FOUND;
        }
        var data = heldKey.get(AEComponents.QUANTUM_KEY_CARD_DATA);
        if (data == null) {
            return QuantumCredentialService.Result.NOT_FOUND;
        }
        var server = serverLevel.getServer();
        var topology = QuantumTopologyRegistry.get(server);
        var credentials = QuantumCredentialRegistry.get(server);
        var security = SecurityDomainRegistry.get(server);
        var credentialId = new QuantumCredentialId(data.credentialId());

        QuantumCredentialService.Result result;
        if (this.endpointId == null) {
            var label = "endpoint@" + getBlockPos().toShortString();
            var outcome = QuantumCredentialService.activateFreshPhysicalEndpoint(topology, credentials, security,
                    credentialId, data.revision(), label, actor);
            result = outcome.result();
            if (result == QuantumCredentialService.Result.OK) {
                this.endpointId = outcome.endpointId();
                setChanged();
            }
        } else {
            result = QuantumCredentialService.activate(topology, credentials, security, credentialId, data.revision(),
                    this.endpointId, actor);
        }

        if (result == QuantumCredentialService.Result.OK) {
            goOnlineIfProvisioned();
        }
        return result;
    }

    /** Activates this fresh endpoint as one explicit arm of the anchor selected by a second encoded key. */
    public QuantumCredentialService.Result activateAsHubMember(ItemStack memberKey, ItemStack anchorKey,
            IActionSource actor) {
        if (this.endpointId != null || !(level instanceof ServerLevel serverLevel)) {
            return QuantumCredentialService.Result.CONFLICT;
        }
        var memberData = memberKey.get(AEComponents.QUANTUM_KEY_CARD_DATA);
        var anchorData = anchorKey.get(AEComponents.QUANTUM_KEY_CARD_DATA);
        if (memberData == null || anchorData == null) {
            return QuantumCredentialService.Result.NOT_FOUND;
        }
        var server = serverLevel.getServer();
        var outcome = QuantumCredentialService.activateFreshHubMember(
                QuantumTopologyRegistry.get(server), QuantumCredentialRegistry.get(server),
                SecurityDomainRegistry.get(server), new QuantumCredentialId(memberData.credentialId()),
                memberData.revision(), new QuantumCredentialId(anchorData.credentialId()), anchorData.revision(),
                "endpoint@" + getBlockPos().toShortString(), actor);
        if (outcome.result() == QuantumCredentialService.Result.OK) {
            this.endpointId = outcome.endpointId();
            setChanged();
            goOnlineIfProvisioned();
        }
        return outcome.result();
    }

    private void goOnlineIfProvisioned() {
        if (endpointId == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var node = getMainNode().getNode();
        if (node == null) {
            return;
        }
        var server = serverLevel.getServer();
        var topology = QuantumTopologyRegistry.get(server);
        var credentials = QuantumCredentialRegistry.get(server);

        var location = new QuantumEndpointLocation(serverLevel.dimension(), getBlockPos().immutable());
        topology.setEndpointOnline(endpointId, location);
        var credential = credentials.getCredentialForEndpoint(endpointId);
        if (credential != null) {
            // Trusted-internal OFFLINE(bound) -> ONLINE bump (no IActionSource) - matches QuantumLaneReconciler's own
            // "restart reconstruction stays TRUSTED INTERNAL" invariant exactly. A safe no-op if the credential is
            // currently SUSPENDED/REVOKED (see QuantumCredential#markOnline) - it does NOT resurrect either.
            credentials.markOnline(credential.id());
        }

        var table = QuantumEndpointOnlineTable.get(server);
        table.register(endpointId, node);
        reconcileLane(topology, credentials, table);
    }

    private void goOffline() {
        if (endpointId == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var server = serverLevel.getServer();
        var topology = QuantumTopologyRegistry.get(server);
        var credentials = QuantumCredentialRegistry.get(server);
        // Sanctioned Q4 offline path, entirely unmodified - "unloaded/removed is not deleted", never actor-gated
        // (ordinary chunk unload/block removal is not a deliberate player mutation-intent, mirrors deactivate's own
        // javadoc). Covers BOTH chunk unload and physical block destruction identically - neither ever revokes the
        // credential, deletes the profile/lane, or invents a substitute peer.
        QuantumCredentialService.deactivate(topology, credentials, endpointId);
        var node = getMainNode().getNode();
        if (node != null) {
            QuantumEndpointOnlineTable.get(server).unregister(endpointId, node);
        }
    }

    private void reconcileLane(QuantumTopologyRegistry topology, QuantumCredentialRegistry credentials,
            QuantumEndpointOnlineTable table) {
        var record = topology.getEndpoint(endpointId);
        if (record == null) {
            return;
        }
        var lane = topology.getLane(record.laneId());
        if (lane == null) {
            return;
        }
        var filtered = QuantumCredentialService.filterByCredentialState(table.onlineNodes(), credentials);
        QuantumLaneReconciler.reconstruct(lane, filtered);
    }
}
