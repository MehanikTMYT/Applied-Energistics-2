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

import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A named group of players, defined within a single {@link SecurityDomain}. Referenced from a domain's ACL via
 * {@code PrincipalId.Group(id())}.
 */
public record SecurityGroup(UUID id, String name, Set<UUID> members) {

    public static final Codec<SecurityGroup> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SecurityGroup::id),
            Codec.STRING.fieldOf("name").forGetter(SecurityGroup::name),
            UUIDUtil.CODEC.listOf().xmap(Set::copyOf, java.util.List::copyOf).fieldOf("members")
                    .forGetter(SecurityGroup::members))
            .apply(builder, SecurityGroup::new));

    public static SecurityGroup create(String name) {
        return new SecurityGroup(UUID.randomUUID(), name, Set.of());
    }

    public SecurityGroup withMember(UUID player) {
        var newMembers = new java.util.HashSet<>(members);
        newMembers.add(player);
        return new SecurityGroup(id, name, Set.copyOf(newMembers));
    }

    public SecurityGroup withoutMember(UUID player) {
        var newMembers = new java.util.HashSet<>(members);
        newMembers.remove(player);
        return new SecurityGroup(id, name, Set.copyOf(newMembers));
    }
}
