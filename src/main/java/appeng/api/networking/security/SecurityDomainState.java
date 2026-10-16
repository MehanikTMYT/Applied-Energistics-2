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

/**
 * The security state of a single {@link appeng.api.networking.IGrid}, as computed by {@link ISecurityService}.
 */
public enum SecurityDomainState {
    /**
     * LEGACY/UNSECURED: no {@link appeng.me.security.SecurityDomain} has ever been bound to this network. The network
     * behaves exactly like vanilla AE2: fully open, no permission checks are enforced. A Security Station is not
     * required for the network to function.
     */
    LEGACY,
    /**
     * SECURED: exactly one {@link appeng.me.security.SecurityDomain} is bound to this network. Its ACL is enforced,
     * even if every Security Station belonging to the domain has since been destroyed, unloaded, or is offline - the
     * network never silently reverts to {@link #LEGACY}.
     */
    SECURED,
    /**
     * CONFLICT: two (or more) previously separate SECURED networks with different {@link SecurityDomainId}s were
     * physically merged into one grid (e.g. by connecting a cable between them). This is a hard invariant violation:
     * AE2 never silently merges or picks a "winning" domain. Every permission check fails (fail-closed) until the
     * conflicting physical connection is undone (which naturally splits the grid back apart and restores each side's
     * own domain state) or until an operator resolves it via {@code /ae2 security recover}.
     */
    CONFLICT
}
