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

package appeng.api.networking.security;

import java.util.UUID;

/**
 * Implemented by a machine ({@link IActionHost} or its underlying block entity/part) that wants to be recognized as its
 * own {@link PrincipalId.Device} principal in a {@link appeng.me.security.SecurityDomain}'s ACL, independent of
 * whichever player built or currently owns it.
 * <p/>
 * A device identity is a deliberate, explicit opt-in: machines that do <strong>not</strong> implement this interface
 * are never treated as the network's owner and never inherit any permissions automatically - they can only act within
 * whatever is granted to the {@link PrincipalId#EVERYONE} principal (or nothing at all, in a secured domain that hasn't
 * granted anything to {@code EVERYONE}). This mirrors the design decision that "a machine does not get full owner
 * permissions automatically".
 * <p/>
 * The returned id must be stable for the lifetime of the device (i.e. persisted to NBT), so that ACL entries granted to
 * a specific device survive server restarts, chunk unloads, and the device being picked up and placed again in the same
 * location - as long as the implementation persists and restores the same {@link UUID}.
 */
public interface IDeviceIdentity {
    /**
     * @return the stable, persistent identity of this device, used as a {@link PrincipalId.Device} principal.
     */
    UUID getDeviceId();
}
