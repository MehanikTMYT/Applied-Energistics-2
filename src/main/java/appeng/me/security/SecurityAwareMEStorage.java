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

package appeng.me.security;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

/**
 * Wraps the network's real {@link MEStorage} (e.g. {@link appeng.me.storage.NetworkStorage}) and gates
 * {@link #insert}/{@link #extract} on {@link SecurityPermissions#INJECT}/{@link SecurityPermissions#EXTRACT}.
 * <p/>
 * Installed by {@code StorageService.getInventory()} - this decorates rather than modifies {@code NetworkStorage} in
 * place, matching the pre-2023 precedent of wrapping the network storage rather than threading permission checks
 * through its mount/priority bookkeeping.
 * <p/>
 * Reads ({@link #getAvailableStacks}) are intentionally <em>not</em> gated: browsing what a secured network contains
 * without extract rights is consistent with how terminals have always worked (visibility != access), and every other
 * AE2 subsystem (crafting calculation, JEI/REI integration, autocrafting simulation) depends on being able to see the
 * full stack list regardless of who's asking.
 */
public class SecurityAwareMEStorage implements MEStorage {
    private final MEStorage delegate;
    private final ISecurityService security;

    public SecurityAwareMEStorage(MEStorage delegate, ISecurityService security) {
        this.delegate = delegate;
        this.security = security;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!security.hasPermission(source, SecurityPermissions.INJECT)) {
            return 0;
        }
        return delegate.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!security.hasPermission(source, SecurityPermissions.EXTRACT)) {
            return 0;
        }
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        delegate.getAvailableStacks(out);
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }
}
