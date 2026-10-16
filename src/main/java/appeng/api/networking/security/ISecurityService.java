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

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGridService;

/**
 * Grid-wide service tracking the {@link appeng.me.security.SecurityDomain} that this network's nodes are bound to, and
 * enforcing its ACL.
 * <p/>
 * See {@link SecurityDomainState} for the three states a network can be in. Every enforcement point in AE2 (storage
 * insert/extract, crafting submission, the Security Station's own ACL editor, etc.) should call
 * {@link #hasPermission(IActionSource, SecurityPermissions)} rather than re-implementing this logic.
 * <p/>
 * Three categories of {@link IActionSource} are treated distinctly:
 * <ul>
 * <li><b>Player actions</b> ({@link IActionSource#player()} present): resolved to a {@code PrincipalId.Player} and
 * checked against the domain's ACL (and, for the owner, always allowed).</li>
 * <li><b>Machine actions</b> ({@link IActionSource#player()} absent, {@link IActionSource#machine()} present): resolved
 * to a {@code PrincipalId.Device} <em>only</em> if the machine (or its block entity/part) implements
 * {@link IDeviceIdentity}; otherwise the machine has no identity of its own and can only rely on whatever is granted to
 * {@link PrincipalId#EVERYONE}. A machine is never granted the owner's permissions automatically.</li>
 * <li><b>Trusted-internal actions</b> (neither present, i.e. {@link IActionSource#empty()}): AE2's own internal
 * bookkeeping (e.g. moving items produced by a pattern into the same provider's own buffer). These bypass the ACL
 * entirely, since they are not attributable to any external actor and do not cross the network's security
 * boundary.</li>
 * </ul>
 */
public interface ISecurityService extends IGridService {
    /**
     * @return the current security state of this network.
     */
    SecurityDomainState getState();

    /**
     * @return the id of the domain this network is bound to, if any (i.e. {@link #getState()} is not
     *         {@link SecurityDomainState#LEGACY}). Present even while {@link SecurityDomainState#CONFLICT}, in which
     *         case it refers to just one of the (at least two) conflicting domains, for diagnostic purposes only.
     */
    Optional<SecurityDomainId> getDomainId();

    /**
     * Checks whether the given action source is allowed to perform the given permission on this network.
     * <p/>
     * Always {@code true} while {@link #getState()} is {@link SecurityDomainState#LEGACY} (no domain was ever bound -
     * vanilla AE2 semantics), always {@code false} while {@link SecurityDomainState#CONFLICT} (fail-closed), and
     * otherwise resolved against the bound domain's ACL as described in the class documentation.
     */
    boolean hasPermission(IActionSource source, SecurityPermissions permission);

    /**
     * Binds a brand-new {@link appeng.me.security.SecurityDomain} (owned by the given player) to this network and marks
     * every node currently in the grid as belonging to it, transitioning the network from
     * {@link SecurityDomainState#LEGACY} to {@link SecurityDomainState#SECURED}.
     * <p/>
     * Called by the Security Station when it joins a grid that has never had a domain bound to it. A no-op (simply
     * returns the existing id) if the network is already {@link SecurityDomainState#SECURED}. Not usable while
     * {@link SecurityDomainState#CONFLICT} - a conflict must be resolved (by physically splitting the grid, or via the
     * {@code /ae2 security recover} command) first.
     *
     * @return the id of the domain now bound to this network, or {@code null} if the network is in
     *         {@link SecurityDomainState#CONFLICT} and no domain could be bound.
     */
    @Nullable
    SecurityDomainId bootstrapDomain(UUID ownerId);
}
