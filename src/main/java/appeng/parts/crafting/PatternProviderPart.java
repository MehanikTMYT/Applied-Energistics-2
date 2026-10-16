/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.parts.crafting;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.PersistentDeviceIdentity;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;

/**
 * Note on {@link IDeviceIdentity}: see {@code PatternProviderBlockEntity}'s class comment for why a Pattern Provider is
 * its own security principal. Each part owns its own grid node, so two Pattern Provider parts on the same
 * {@code CableBus} host are two distinct devices with two distinct identities - a part never borrows its host's
 * identity.
 */
public class PatternProviderPart extends AEBasePart implements PatternProviderLogicHost, IDeviceIdentity {

    protected final PatternProviderLogic logic = createLogic();

    private final PersistentDeviceIdentity deviceIdentity = new PersistentDeviceIdentity();

    public PatternProviderPart(IPartItem<?> partItem) {
        super(partItem);
    }

    protected PatternProviderLogic createLogic() {
        return new PatternProviderLogic(this.getMainNode(), this);
    }

    @Override
    public void saveChanges() {
        getHost().markForSave();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(getPartItem());
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.logic.onMainNodeStateChanged();
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
            // Placed before device identities existed (or freshly placed): keep the id minted in the constructor and
            // persist it now, so this provider keeps the same Device principal across every future load.
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
    public UUID getDeviceId() {
        return this.deviceIdentity.get();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        registerDeviceIdentity();
        this.logic.updatePatterns();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        this.logic.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);

        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.exportSettings(builder);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);

        if (mode == SettingsFrom.MEMORY_CARD) {
            logic.importSettings(input, player);
        }
    }

    @Override
    public void onRedstoneLevelMayHaveChanged() {
        logic.updateRedstoneState();
    }

    @Override
    public boolean onUseWithoutItem(Player p, Vec3 pos) {
        if (!p.level().isClientSide()) {
            openMenu(p, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.of(this.getSide());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return AEParts.PATTERN_PROVIDER.stack();
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
