/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.parts.misc;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.PersistentDeviceIdentity;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;

/**
 * Note on {@link IDeviceIdentity}: see {@code InterfaceBlockEntity}'s class comment for the full MIXED actor model. The
 * identity belongs to this <em>part</em>, not to its {@code CableBus} host (brief §16), so two Interface parts on one
 * cable are two different {@code Device} principals, and an Interface part is never the same principal as an Interface
 * block.
 */
public class InterfacePart extends AEBasePart implements InterfaceLogicHost, IDeviceIdentity {

    private final PersistentDeviceIdentity deviceIdentity = new PersistentDeviceIdentity();

    @Override
    public void addToWorld() {
        super.addToWorld();
        registerDeviceIdentity();
    }

    private static final IGridNodeListener<InterfacePart> NODE_LISTENER = new NodeListener<>() {
        @Override
        public void onGridChanged(InterfacePart nodeOwner, IGridNode node) {
            super.onGridChanged(nodeOwner, node);
            nodeOwner.getInterfaceLogic().gridChanged();
        }
    };

    private final InterfaceLogic logic = createLogic();

    public InterfacePart(IPartItem<?> partItem) {
        super(partItem);
    }

    protected InterfaceLogic createLogic() {
        return new InterfaceLogic(getMainNode(), this, getPartItem().asItem());
    }

    @Override
    public void saveChanges() {
        getHost().markForSave();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().hasGridBooted()) {
            this.logic.notifyNeighbors();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void readFromNBT(ValueInput input) {
        super.readFromNBT(input);
        if (this.deviceIdentity.read(input)) {
            saveChanges();
        }
        this.logic.readFromNBT(input);
    }

    @Override
    public void writeToNBT(ValueOutput data) {
        super.writeToNBT(data);
        this.deviceIdentity.write(data);
        this.logic.writeToNBT(data);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        this.logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.logic.clearContent();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.logic.getConfigManager();
    }

    @Override
    public boolean onUseWithoutItem(Player p, Vec3 pos) {
        if (!p.level().isClientSide()) {
            openMenu(p, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public int getPriority() {
        return this.logic.getPriority();
    }

    @Override
    public void setPriority(int newValue) {
        this.logic.setPriority(newValue);
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
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getPartItem());
    }

    @Override
    public UUID getDeviceId() {
        return this.deviceIdentity.get();
    }

    /**
     * Round 31: this part takes its identity from {@link DevicePrincipalRegistry} rather than from its own NBT.
     * <p/>
     * {@code addToWorld} is the part-side counterpart of a block entity's {@code onReady}: the first moment the part
     * has a host, a position and a side, i.e. a resolvable {@link MachineKey}. Retirement is deliberately <em>not</em>
     * done here or in {@code removeFromWorld}, because that method is called both when the cable bus is destroyed and
     * when its chunk merely unloads (see {@code CableBusBlockEntity#setRemoved} / {@code #onChunkUnloaded}); using it
     * would free an identity on every unload. The two genuine removal signals live in the host instead:
     * {@code CableBusContainer#removePartFromSide} for a single part and
     * {@code CableBusBlockEntity#preRemoveSideEffects} for the whole block.
     */
    private void registerDeviceIdentity() {
        if (getBlockEntity() != null && getBlockEntity().getLevel() instanceof ServerLevel serverLevel) {
            var key = MachineKey.of(this);
            if (key != null) {
                var claimed = this.deviceIdentity.get();
                var authenticated = DevicePrincipalRegistry.get(serverLevel).resolveOrRegister(key, claimed);
                if (!authenticated.equals(claimed)) {
                    this.deviceIdentity.adoptAuthenticatedId(authenticated);
                    this.getHost().markForSave();
                }
            }
        }
    }

}
