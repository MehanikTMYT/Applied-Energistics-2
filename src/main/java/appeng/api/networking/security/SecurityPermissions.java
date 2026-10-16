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

import com.mojang.serialization.Codec;

/**
 * The set of permissions that can be granted to a {@link PrincipalId} within a
 * {@link appeng.me.security.SecurityDomain}.
 * <p/>
 * {@code INJECT}, {@code EXTRACT}, {@code CRAFT}, {@code BUILD} and {@code SECURITY} are the classic permissions
 * inherited from AE2's original (pre-2023) security system. The remaining permissions are new and gate features that
 * did not previously require any access control.
 * <p/>
 * {@link #SECURITY} is treated as a super-admin permission: it is the only permission required to edit a domain's
 * ACL/groups. It does <strong>not</strong> implicitly grant any of the other permissions - those must still be granted
 * explicitly. The domain owner always implicitly holds every permission, regardless of ACL contents.
 */
public enum SecurityPermissions {
    /**
     * Required to insert items into the network, e.g. via a terminal.
     */
    INJECT,
    /**
     * Required to extract items from the network, e.g. via a terminal.
     */
    EXTRACT,
    /**
     * Required to request crafting from the network, e.g. via a terminal.
     */
    CRAFT,
    /**
     * Required to modify automation and the network's physical layout (placing/removing cables, parts, etc.).
     */
    BUILD,
    /**
     * Super-admin permission. Required to edit a domain's ACL, groups, and other security settings. Does not implicitly
     * grant any other permission.
     */
    SECURITY,
    /**
     * Required to access this network through a wireless access point.
     */
    WIRELESS_ACCESS,
    /**
     * Required to access this network across dimensions (e.g. through a quantum link).
     */
    DIMENSIONAL_ACCESS,
    /**
     * Required to view quantum network configuration.
     */
    QUANTUM_VIEW,
    /**
     * Required to issue new quantum network links/keys.
     */
    QUANTUM_ISSUE,
    /**
     * Required to rewrite existing quantum network configuration.
     */
    QUANTUM_REWRITE,
    /**
     * Required to revoke quantum network links/keys.
     */
    QUANTUM_REVOKE,
    /**
     * Required to change quantum network routing.
     */
    QUANTUM_ROUTE;

    public static final Codec<SecurityPermissions> CODEC = Codec.STRING.xmap(
            SecurityPermissions::valueOf, Enum::name);
}
