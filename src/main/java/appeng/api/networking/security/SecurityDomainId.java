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

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;

/**
 * A persistent, unique identifier for a {@link appeng.me.security.SecurityDomain}.
 * <p/>
 * Exactly one logical Network Profile (connected ME network, as it evolves over time through grid splits/merges) is
 * bound to at most one {@link SecurityDomainId} at a time. Two independently-created networks always get different ids,
 * even if they were once part of the same network and later split.
 */
public record SecurityDomainId(UUID id) {
    public static final Codec<SecurityDomainId> CODEC = UUIDUtil.CODEC.xmap(SecurityDomainId::new,
            SecurityDomainId::id);

    public static SecurityDomainId create() {
        return new SecurityDomainId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
