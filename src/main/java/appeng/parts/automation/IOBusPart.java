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

package appeng.parts.automation;

import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ISubMenuHost;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigManagerBuilder;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.helpers.IConfigInvHost;
import appeng.me.helpers.MachineSource;
import appeng.me.security.DevicePrincipalRegistry;
import appeng.me.security.MachineKey;
import appeng.me.security.PersistentDeviceIdentity;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.ConfigInventory;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;

/**
 * Note on {@link IDeviceIdentity} (Round 25, classification <b>B - DEVICE</b>): an I/O bus is an <em>autonomous</em>
 * actor. Nothing but its own {@link IGridTickable} tick drives {@code doBusWork}; the player who configured its filter
 * is long gone and is deliberately not the actor (brief §22: a player's own {@code EXTRACT} must not silently open an
 * Export Bus). The bus therefore creates a genuinely new, security-sensitive intent every tick and needs a principal of
 * its own.
 * <p/>
 * The identity lives on <em>this</em> object - the part instance - not on the {@code CableBus} host (brief §16), so two
 * buses on the same cable are two different {@code Device} principals. That falls out of the field being an instance
 * field and of {@code source} being {@code new MachineSource(this)}: {@code SecurityService#resolveDeviceId} finds this
 * part directly on its first branch, without ever consulting the host.
 * <p/>
 * Required permissions differ per subclass and are deliberately <em>not</em> unified here (brief §19): an
 * {@link ImportBusPart} only ever calls {@code MEStorage#insert} on the network ({@code INJECT}), an
 * {@link ExportBusPart} only ever calls {@code MEStorage#extract} ({@code EXTRACT}), plus {@code CRAFT} when a Crafting
 * Card is installed. Neither ever needs {@code BUILD}, {@code SECURITY} or any {@code QUANTUM_*} permission.
 */
public abstract class IOBusPart extends UpgradeablePart
        implements IGridTickable, IConfigInvHost, ISubMenuHost, IDeviceIdentity {
    private final PersistentDeviceIdentity deviceIdentity = new PersistentDeviceIdentity();
    private final ConfigInventory config;
    // Filter derived from the config
    @Nullable
    private IPartitionList filter;
    private final TickRates tickRates;
    protected final IActionSource source;
    private boolean lastRedstone = false;
    /**
     * Indicates that an I/O bus in redstone pulse mode has observed a low to high redstone transition and is waiting to
     * act on this during its next tick.
     */
    private boolean pendingPulse = false;

    public IOBusPart(TickRates tickRates, Set<AEKeyType> supportedKeyTypes, IPartItem<?> partItem) {
        super(partItem);
        this.tickRates = tickRates;
        this.source = new MachineSource(this);
        this.config = ConfigInventory.configTypes(63).supportedTypes(supportedKeyTypes)
                .changeListener(this::updateState).build();
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    protected void registerSettings(IConfigManagerBuilder builder) {
        super.registerSettings(builder);
        builder.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        builder.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public RedstoneMode getRSMode() {
        return this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
    }

    private boolean isInPulseMode() {
        return getRSMode() == RedstoneMode.SIGNAL_PULSE;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    /**
     * All export and import bus parts have a configuration ui.
     */
    protected abstract MenuType<?> getMenuType();

    @Override
    public void upgradesChanged() {
        this.updateState();
    }

    @Override
    public void readFromNBT(ValueInput extra) {
        super.readFromNBT(extra);
        if (this.deviceIdentity.read(extra)) {
            // Pre-Round-25 bus (or a freshly placed one): keep the id minted in the constructor and persist it now so
            // it is stable from here on, instead of drifting on every reload and invalidating its ACL grants.
            getHost().markForSave();
        }
        config.readFromChildTag(extra, "config");
        // Ensure the filter is rebuilt
        filter = null;
        pendingPulse = isInPulseMode() && extra.getBooleanOr("pendingPulse", false);
    }

    @Override
    public void writeToNBT(ValueOutput extra) {
        super.writeToNBT(extra);
        this.deviceIdentity.write(extra);
        config.writeToChildTag(extra, "config");
        if (isInPulseMode() && pendingPulse) {
            extra.putBoolean("pendingPulse", true);
        }
    }

    @Override
    public UUID getDeviceId() {
        return this.deviceIdentity.get();
    }

    @Override
    public ConfigInventory getConfig() {
        return config;
    }

    protected final IPartitionList getFilter() {
        if (filter == null) {
            var filterBuilder = IPartitionList.builder();
            filterBuilder.addAll(getConfig().keySet());
            if (isUpgradedWith(AEItems.FUZZY_CARD)) {
                filterBuilder.fuzzyMode(this.getConfigManager().getSetting(Settings.FUZZY_MODE));
            }
            filter = filterBuilder.build();
        }
        return filter;
    }

    @Override
    public void onRedstoneLevelMayHaveChanged() {
        if (isInPulseMode()) {
            var hostIsPowered = this.getHost().hasRedstone();
            if (this.lastRedstone != hostIsPowered) {
                this.lastRedstone = hostIsPowered;
                if (this.lastRedstone && !this.pendingPulse) {
                    // Perform the action based on the pulse on the next tick
                    this.pendingPulse = true;
                    getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
                }
            }
        } else {
            // This handles waking up the bus if the adjacent redstone has changed
            updateRedstoneState();
        }
    }

    protected int availableSlots() {
        return Math.min(18 + getInstalledUpgrades(AEItems.CAPACITY_CARD) * 9, this.getConfig().size());
    }

    protected int getOperationsPerTick() {
        return switch (getInstalledUpgrades(AEItems.SPEED_CARD)) {
            default -> 1;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
        };
    }

    @Override
    public final TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        // Sometimes between being woken up and actually doing work, the config/redstone mode may have changed
        // put us back to sleep if that was the case
        if (isSleeping()) {
            return TickRateModulation.SLEEP;
        }

        if (!canDoBusWork()) {
            return TickRateModulation.IDLE;
        }

        // Reset a potential redstone pulse trigger
        this.pendingPulse = false;

        var hasDoneWork = this.doBusWork(node.getGrid());

        // We may be back to sleep (i.e. in pulse mode)
        if (isSleeping()) {
            return TickRateModulation.SLEEP;
        } else {
            return hasDoneWork ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
        }
    }

    /**
     * Checks if the bus can actually do something.
     * <p>
     * Currently this tests if the chunk for the target is actually loaded, and if the main node has it's channel and
     * power requirements fulfilled.
     *
     * @return true, if the the bus should do its work.
     */
    protected final boolean canDoBusWork() {
        if (!getMainNode().isActive()) {
            return false;
        }

        var self = this.getHost().getBlockEntity();
        var targetPos = self.getBlockPos().relative(getSide());

        return Platform.areBlockEntitiesTicking(self.getLevel(), targetPos);
    }

    private void updateState() {
        filter = null; // rebuild the filter

        updateRedstoneState();
    }

    @Override
    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        super.onSettingChanged(manager, setting);

        updateRedstoneState();

        // Ensure we have an up-to-date last redstone state when pulse mode is activated to
        // correctly detect subsequent pulses
        if (isInPulseMode()) {
            this.lastRedstone = getHost().hasRedstone();
        }
    }

    private void updateRedstoneState() {
        // Clear the pending pulse flag if the upgrade is removed or the config is toggled off
        if (!this.isInPulseMode()) {
            this.pendingPulse = false;
        }

        getMainNode().ifPresent((grid, node) -> {
            if (!this.isSleeping()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    @Override
    public final boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) {
            MenuOpener.open(getMenuType(), player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public final TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(tickRates.getMin(), tickRates.getMax(), isSleeping());
    }

    @Override
    protected boolean isSleeping() {
        if (isInPulseMode() && this.pendingPulse) {
            return false;
        } else {
            return super.isSleeping();
        }
    }

    protected abstract boolean doBusWork(IGrid grid);

    @Override
    public void addToWorld() {
        super.addToWorld();
        registerDeviceIdentity();

        // To correctly detect pulses (changes from low to high), we need to know the current state when we
        // are added to the world.
        this.lastRedstone = this.getHost().hasRedstone();
        // We may have observed a redstone pulse before, but were unable to act on it due to being unloaded before
        // we ticked again. Ensure that we do act on this pulse as soon as possible.
        if (pendingPulse) {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.open(getMenuType(), player, subMenu.getLocator(), true);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return getPartItem().asItem().getDefaultInstance();
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
