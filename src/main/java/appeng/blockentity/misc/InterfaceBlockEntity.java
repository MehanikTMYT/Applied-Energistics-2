/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.IPriorityHost;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.PersistentDeviceIdentity;

/**
 * Note on {@link IDeviceIdentity} (Round 25, classification <b>MIXED</b>, DEVICE for the autonomous paths): an
 * Interface has more than one security role and the brief explicitly allows that (§8.2/§23).
 * <ul>
 * <li><b>Autonomous stocking</b> - {@code InterfaceLogic#acquireFromNetwork} runs off the Interface's own tick and
 * calls {@code StorageHelper#poweredExtraction} on the network's secured storage with {@code interfaceRequestSource}.
 * New unattended intent -> {@code Device} principal, permission {@code EXTRACT}.</li>
 * <li><b>Autonomous crafting</b> - {@code InterfaceLogic#handleCrafting} submits a job with {@code actionSource} when a
 * Crafting Card is installed. Same principal, permission {@code CRAFT}.</li>
 * <li><b>Local inventory exposure</b> - {@code InterfaceLogic.InterfaceInventory} is a plain pass-through
 * {@code MEStorage} mount. It is handed the <em>caller's</em> {@code IActionSource} and must keep using it; this
 * identity is deliberately never substituted there (brief §23, classification C - PROPAGATED_ACTOR).</li>
 * <li><b>Player GUI operations</b> - go through {@code AEBaseMenu}'s {@code PlayerSource}, and
 * {@code SecurityService#resolvePrincipal} gives the player precedence over any machine, so a player acting at an
 * Interface is checked as that player and never as this device.</li>
 * </ul>
 * A device grant therefore opens exactly the Interface's own unattended work and nothing a caller does through it.
 */
public class InterfaceBlockEntity extends AENetworkedBlockEntity
        implements IPriorityHost, IUpgradeableObject, IConfigurableObject, InterfaceLogicHost, IDeviceIdentity {

    private final PersistentDeviceIdentity deviceIdentity = new PersistentDeviceIdentity();

    private static final IGridNodeListener<InterfaceBlockEntity> NODE_LISTENER = new BlockEntityNodeListener<>() {
        @Override
        public void onGridChanged(InterfaceBlockEntity nodeOwner, IGridNode node) {
            nodeOwner.logic.gridChanged();
        }
    };

    private final InterfaceLogic logic = createLogic();

    public InterfaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    protected InterfaceLogic createLogic() {
        return new InterfaceLogic(getMainNode(), this, getItemFromBlockEntity().asItem());
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    /**
     * Round 29 prototype (brief §32): the Interface is the first device to take its identity from
     * {@link DevicePrincipalRegistry} instead of from its own saved NBT.
     */
    @Override
    public void onReady() {
        super.onReady();

        if (getLevel() instanceof ServerLevel serverLevel) {
            var key = MachineKey.of(this);
            if (key != null) {
                var claimed = this.deviceIdentity.get();
                var authenticated = DevicePrincipalRegistry.get(serverLevel).resolveOrRegister(key, claimed);
                if (!authenticated.equals(claimed)) {
                    this.deviceIdentity.adoptAuthenticatedId(authenticated);
                    this.saveChanges();
                }
            }
        }
    }

    /**
     * Round 30: permanent destruction retires this machine's registry binding.
     * <p/>
     * This is the half of the lifecycle Round 29 left open, and it was not cosmetic. The block {@code MachineKey} is
     * {@code dimension + position + "block"}, so without retirement a freshly placed Interface at the same coordinates
     * would find the dead machine's binding and silently inherit its ACL - a replacement quietly acquiring the grants
     * of whatever stood there before.
     * <p/>
     * {@code preRemoveSideEffects} is the correct signal because it is what vanilla calls when the block entity is
     * actually being removed from the world - it is where AE2 already drops the machine's contents. A chunk unload does
     * not call it (nothing is dropped when a chunk unloads), so the two cases the brief demands be distinguished
     * distinguish themselves, without the registry having to guess from activity or timestamps.
     * <p/>
     * Retirement frees the position for a future machine while tombstoning the identity itself, so the id is gone for
     * good: neither the replacement nor a stale copy of this machine's NBT can ever authenticate as it again.
     */
    @Override
    public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
        super.preRemoveSideEffects(blockPos, blockState);

        if (getLevel() instanceof ServerLevel serverLevel) {
            var key = MachineKey.of(this);
            if (key != null) {
                DevicePrincipalRegistry.get(serverLevel).retire(key);
            }
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (getMainNode().hasGridBooted()) {
            this.logic.notifyNeighbors();
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        this.logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.clearContent();
    }

    @Override
    public void saveAdditional(ValueOutput data) {
        super.saveAdditional(data);
        this.deviceIdentity.write(data);
        this.logic.writeToNBT(data);
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        if (this.deviceIdentity.read(data)) {
            setChanged();
        }
        this.logic.readFromNBT(data);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return this.logic.getCableConnectionType(dir);
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return AEBlocks.INTERFACE.stack();
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(Identifier id) {
        if (id.equals(UPGRADES)) {
            return logic.getUpgrades();
        }
        return super.getSubInventory(id);
    }

    @Override
    public UUID getDeviceId() {
        return this.deviceIdentity.get();
    }
}
