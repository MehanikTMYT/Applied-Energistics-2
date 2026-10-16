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

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Identifies a principal that can be granted permissions in a {@link appeng.me.security.SecurityDomain}'s ACL.
 * <p/>
 * Four kinds of principals exist:
 * <ul>
 * <li>{@link Player} - an individual Minecraft player, identified by their profile UUID.</li>
 * <li>{@link Group} - a named group of players, defined within a {@link appeng.me.security.SecurityDomain}.</li>
 * <li>{@link Device} - a machine/block with a persistent device identity (see {@link IDeviceIdentity}), independent of
 * the player that built or owns it.</li>
 * <li>{@link Everyone} - the built-in catch-all principal representing "anyone with physical access", used to grant
 * baseline permissions on partially-public networks.</li>
 * </ul>
 */
public sealed interface PrincipalId {

    record Player(UUID id) implements PrincipalId {
    }

    record Group(UUID id) implements PrincipalId {
    }

    record Device(UUID id) implements PrincipalId {
    }

    record Everyone() implements PrincipalId {
    }

    /**
     * The singleton instance of the built-in "everyone" principal.
     */
    Everyone EVERYONE = new Everyone();

    static PrincipalId ofPlayer(UUID id) {
        return new Player(id);
    }

    static PrincipalId ofGroup(UUID id) {
        return new Group(id);
    }

    static PrincipalId ofDevice(UUID id) {
        return new Device(id);
    }

    static PrincipalId everyone() {
        return EVERYONE;
    }

    record Data(String type, Optional<UUID> id) {
    }

    Codec<Data> DATA_CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.STRING.fieldOf("type").forGetter(Data::type),
            UUIDUtil.CODEC.optionalFieldOf("id").forGetter(Data::id)).apply(builder, Data::new));

    Codec<PrincipalId> CODEC = DATA_CODEC.comapFlatMap(PrincipalId::fromData, PrincipalId::toData);

    private static DataResult<PrincipalId> fromData(Data data) {
        return switch (data.type()) {
            case "player" -> data.id().<DataResult<PrincipalId>>map(id -> DataResult.success(new Player(id)))
                    .orElseGet(() -> DataResult.error(() -> "player principal is missing an id"));
            case "group" -> data.id().<DataResult<PrincipalId>>map(id -> DataResult.success(new Group(id)))
                    .orElseGet(() -> DataResult.error(() -> "group principal is missing an id"));
            case "device" -> data.id().<DataResult<PrincipalId>>map(id -> DataResult.success(new Device(id)))
                    .orElseGet(() -> DataResult.error(() -> "device principal is missing an id"));
            case "everyone" -> DataResult.success(EVERYONE);
            default -> DataResult.error(() -> "unknown principal type: " + data.type());
        };
    }

    private static Data toData(PrincipalId principal) {
        return switch (principal) {
            case Player p -> new Data("player", Optional.of(p.id()));
            case Group g -> new Data("group", Optional.of(g.id()));
            case Device d -> new Data("device", Optional.of(d.id()));
            case Everyone ignored -> new Data("everyone", Optional.empty());
        };
    }

    /**
     * A short, stable string key useful for maps/sets that cannot use records as keys reliably (e.g. some NBT/JSON
     * round-trips), and for the recovery command's audit log.
     */
    default String asKey() {
        return switch (this) {
            case Player p -> "player:" + p.id();
            case Group g -> "group:" + g.id();
            case Device d -> "device:" + d.id();
            case Everyone ignored -> "everyone";
        };
    }
}
