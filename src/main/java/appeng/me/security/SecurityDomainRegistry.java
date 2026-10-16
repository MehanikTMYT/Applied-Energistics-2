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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import appeng.api.networking.security.SecurityDomainId;
import appeng.core.AppEng;

/**
 * World-scoped (overworld-attached, like {@link appeng.api.features.PlayerRegistryInternal}) persistent storage for
 * every {@link SecurityDomain} that has ever been bootstrapped on the server.
 * <p/>
 * This is deliberately kept independent from any single {@link appeng.api.networking.IGrid}: a domain's ACL must keep
 * being enforced even while its network is fully unloaded, split into pieces that haven't rejoined a grid yet, or has
 * had all of its Security Stations destroyed. Individual {@link appeng.me.GridNode}s only carry the
 * {@link SecurityDomainId} they're bound to (see {@link SecurityService}); the actual owner/ACL/group data always lives
 * here.
 */
public final class SecurityDomainRegistry extends SavedData {
    private static final Identifier ID = AppEng.makeId("security_domains");

    private final Map<UUID, SecurityDomain> domains = new HashMap<>();

    private record RegistryData(List<SecurityDomain> domains) {
        static final Codec<RegistryData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                SecurityDomain.CODEC.listOf().fieldOf("domains").forGetter(RegistryData::domains))
                .apply(builder, RegistryData::new));
    }

    /**
     * Package-private (not {@code private}) purely so unit tests can exercise the real on-disk
     * {@link SavedDataStorage}/{@link SavedDataType} round-trip directly (via {@code storage.computeIfAbsent(TYPE)})
     * without needing a full {@link ServerLevel#OVERWORLD} to exist, which
     * {@link #get(Level)}/{@link #get(MinecraftServer)} require - the ephemeral JUnit test server has no loaded worlds
     * at all. This does not change what gets persisted or how; it's the same {@link SavedDataType} either way.
     */
    static final SavedDataType<SecurityDomainRegistry> TYPE = new SavedDataType<>(
            ID,
            level -> new SecurityDomainRegistry(),
            level -> RegistryData.CODEC.xmap(
                    data -> {
                        var registry = new SecurityDomainRegistry();
                        for (var domain : data.domains()) {
                            registry.domains.put(domain.id().id(), domain);
                        }
                        return registry;
                    },
                    registry -> new RegistryData(List.copyOf(registry.domains.values()))));

    private SecurityDomainRegistry() {
    }

    /**
     * Creates a plain, in-memory registry that is not attached to any {@link Level}/{@link SavedData} at all. Intended
     * for unit tests exercising {@link SecurityService}'s ACL logic without needing a running Minecraft server; see
     * {@code SecurityService#setRegistryForTesting}.
     */
    public static SecurityDomainRegistry createForTesting() {
        return new SecurityDomainRegistry();
    }

    public static SecurityDomainRegistry get(MinecraftServer server) {
        var overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Cannot retrieve security domain data for a server that has no overworld.");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    @Nullable
    public static SecurityDomainRegistry get(Level level) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return get(serverLevel.getServer());
    }

    @Nullable
    public SecurityDomain getDomain(SecurityDomainId id) {
        return domains.get(id.id());
    }

    public SecurityDomain createDomain(UUID owner) {
        var domain = new SecurityDomain(SecurityDomainId.create(), owner);
        domain.audit("domain created, owner=" + owner);
        domains.put(domain.id().id(), domain);
        setDirty();
        return domain;
    }

    public Collection<SecurityDomain> getAllDomains() {
        return List.copyOf(domains.values());
    }

    /**
     * Marks this registry as needing to be saved. Call after mutating a {@link SecurityDomain} obtained from this
     * registry.
     */
    public void markDirty() {
        setDirty();
    }
}
