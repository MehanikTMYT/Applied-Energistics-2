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

package appeng.api.networking.quantum;

import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;

/**
 * Stable, persistent identity for one {@link appeng.me.quantum.NetworkProfile} — the logical identity of a single
 * player-facing ME network/backbone context, independent of any particular {@link appeng.api.networking.IGrid} instance
 * (grids are transient, compute-on-demand connected components; a profile is meant to survive splits, merges, restarts,
 * and chunk unloads).
 * <p/>
 * Deliberately a <strong>different</strong> typed id from {@link appeng.api.networking.security.SecurityDomainId}, even
 * though today exactly one {@link appeng.me.quantum.NetworkProfile} is bound to at most one {@code SecurityDomainId} at
 * a time (see {@link appeng.me.quantum.NetworkProfile#getSecurityDomainId()}). A future profile-migration /
 * authorized-merge / recovery / profile-recreation / security-policy-lifecycle workflow may need the profile identity
 * and the domain identity to evolve independently — e.g. a profile keeping its lanes and endpoints while its security
 * domain is recovered or recreated via {@code /ae2 security recover}, or a domain conceptually surviving a profile
 * split. Reusing a single UUID/type for both would make that distinction impossible to express later; keeping them as
 * two distinct record types costs nothing now and preserves the option.
 */
public record NetworkProfileId(UUID id) {
    public static final Codec<NetworkProfileId> CODEC = UUIDUtil.CODEC.xmap(NetworkProfileId::new,
            NetworkProfileId::id);

    public static NetworkProfileId create() {
        return new NetworkProfileId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
