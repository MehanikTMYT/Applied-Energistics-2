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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.UUIDUtil;

import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainId;
import appeng.api.networking.security.SecurityPermissions;

/**
 * The persistent, authoritative record of a network's security configuration: who owns it, its ACL (which
 * {@link PrincipalId}s hold which {@link SecurityPermissions}), and its {@link SecurityGroup}s.
 * <p/>
 * A {@link appeng.blockentity.misc.SecurityStationBlockEntity Security Station} is merely an editor/access point for a
 * domain - the domain itself is stored independently (see {@link SecurityDomainRegistry}) and keeps enforcing its ACL
 * even after every Security Station belonging to it has been destroyed.
 * <p/>
 * Mutable; callers are responsible for calling {@link SecurityDomainRegistry#markDirty()} (or going through one of its
 * mutation helpers) after changing a domain so it gets persisted.
 */
public final class SecurityDomain {
    /**
     * Bumped whenever the on-disk schema of {@link Data} changes. {@link #upgrade(Data)} is the single place that knows
     * how to migrate older versions forward.
     */
    public static final int CURRENT_VERSION = 1;

    /**
     * Maximum number of entries kept in the in-memory/persisted audit trail (see {@link #audit(String)}). Older entries
     * are dropped on a FIFO basis; this is a lightweight breadcrumb trail for admins, not a full log.
     */
    private static final int MAX_AUDIT_ENTRIES = 64;

    private final SecurityDomainId id;
    private UUID owner;
    private final Map<PrincipalId, EnumSet<SecurityPermissions>> acl = new HashMap<>();
    private final Map<UUID, SecurityGroup> groups = new HashMap<>();
    private final Deque<String> auditLog = new ArrayDeque<>();

    public SecurityDomain(SecurityDomainId id, UUID owner) {
        this.id = id;
        this.owner = owner;
    }

    public SecurityDomainId id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public boolean isOwner(UUID playerId) {
        return owner.equals(playerId);
    }

    /**
     * @return an unmodifiable view of the raw ACL. Does not resolve group membership - use
     *         {@link #getEffectivePermissions} for that.
     */
    public Map<PrincipalId, Set<SecurityPermissions>> getAcl() {
        return Collections.unmodifiableMap(acl);
    }

    public Set<SecurityPermissions> getPermissions(PrincipalId principal) {
        var set = acl.get(principal);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /**
     * Grants the given permissions to the given principal, in addition to any it already holds. The
     * {@link PrincipalId.Player} owner does not need (and cannot lose) an explicit grant - it always implicitly holds
     * every permission.
     */
    public void grant(PrincipalId principal, Set<SecurityPermissions> permissions) {
        acl.computeIfAbsent(principal, k -> EnumSet.noneOf(SecurityPermissions.class)).addAll(permissions);
    }

    /**
     * Replaces the entire permission set for a principal. An empty set removes the principal's ACL entry entirely.
     */
    public void setPermissions(PrincipalId principal, Set<SecurityPermissions> permissions) {
        if (permissions.isEmpty()) {
            acl.remove(principal);
        } else {
            acl.put(principal, EnumSet.copyOf(permissions));
        }
    }

    /**
     * Revokes all permissions from the given principal, removing its ACL entry entirely.
     */
    public void revokeAll(PrincipalId principal) {
        acl.remove(principal);
    }

    public Map<UUID, SecurityGroup> getGroups() {
        return Collections.unmodifiableMap(groups);
    }

    @Nullable
    public SecurityGroup getGroup(UUID groupId) {
        return groups.get(groupId);
    }

    public void putGroup(SecurityGroup group) {
        groups.put(group.id(), group);
    }

    public void removeGroup(UUID groupId) {
        groups.remove(groupId);
        acl.remove(PrincipalId.ofGroup(groupId));
    }

    /**
     * Resolves the full effective permission set for a principal, i.e. its direct grant, plus (for players) any group
     * it belongs to, plus whatever is granted to {@link PrincipalId#EVERYONE}. Does <strong>not</strong> apply the
     * "owner always has everything" rule - callers should check {@link #isOwner} first.
     */
    public EnumSet<SecurityPermissions> getEffectivePermissions(PrincipalId principal) {
        var result = EnumSet.noneOf(SecurityPermissions.class);

        var direct = acl.get(principal);
        if (direct != null) {
            result.addAll(direct);
        }

        if (principal instanceof PrincipalId.Player player) {
            for (var group : groups.values()) {
                if (group.members().contains(player.id())) {
                    var groupPerms = acl.get(PrincipalId.ofGroup(group.id()));
                    if (groupPerms != null) {
                        result.addAll(groupPerms);
                    }
                }
            }
        }

        var everyone = acl.get(PrincipalId.EVERYONE);
        if (everyone != null) {
            result.addAll(everyone);
        }

        return result;
    }

    /**
     * Appends a short, human-readable entry to this domain's audit trail (e.g. "owner reassigned by /ae2 security
     * recover"). Bounded to {@link #MAX_AUDIT_ENTRIES}; oldest entries are dropped first.
     */
    public void audit(String entry) {
        auditLog.addLast(entry);
        while (auditLog.size() > MAX_AUDIT_ENTRIES) {
            auditLog.removeFirst();
        }
    }

    public List<String> getAuditLog() {
        return List.copyOf(auditLog);
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private record AclEntryData(PrincipalId principal, List<SecurityPermissions> permissions) {
        static final Codec<AclEntryData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                PrincipalId.CODEC.fieldOf("principal").forGetter(AclEntryData::principal),
                SecurityPermissions.CODEC.listOf().fieldOf("permissions").forGetter(AclEntryData::permissions))
                .apply(builder, AclEntryData::new));
    }

    private record Data(
            int version,
            UUID id,
            UUID owner,
            List<AclEntryData> acl,
            List<SecurityGroup> groups,
            List<String> auditLog) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.INT.fieldOf("version").forGetter(Data::version),
                UUIDUtil.CODEC.fieldOf("id").forGetter(Data::id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Data::owner),
                AclEntryData.CODEC.listOf().optionalFieldOf("acl", List.of()).forGetter(Data::acl),
                SecurityGroup.CODEC.listOf().optionalFieldOf("groups", List.of()).forGetter(Data::groups),
                Codec.STRING.listOf().optionalFieldOf("audit_log", List.of()).forGetter(Data::auditLog))
                .apply(builder, Data::new));
    }

    public static final Codec<SecurityDomain> CODEC = Data.CODEC.xmap(SecurityDomain::upgrade, SecurityDomain::toData);

    /**
     * Converts a possibly-old-version {@link Data} DTO into a live {@link SecurityDomain}, applying schema migrations
     * as needed. There is only one version so far (this subsystem shipped {@code version} from day one specifically so
     * this method has somewhere to grow into); future schema changes should branch on {@code data.version()} here
     * rather than anywhere else.
     */
    private static SecurityDomain upgrade(Data data) {
        // No migrations needed yet - CURRENT_VERSION is the only version that has ever existed.
        var domain = new SecurityDomain(new SecurityDomainId(data.id()), data.owner());
        for (var entry : data.acl()) {
            if (!entry.permissions().isEmpty()) {
                domain.acl.put(entry.principal(), EnumSet.copyOf(entry.permissions()));
            }
        }
        for (var group : data.groups()) {
            domain.groups.put(group.id(), group);
        }
        domain.auditLog.addAll(data.auditLog());
        return domain;
    }

    private static Data toData(SecurityDomain domain) {
        var aclData = domain.acl.entrySet().stream()
                .map(e -> new AclEntryData(e.getKey(), List.copyOf(e.getValue())))
                .toList();
        return new Data(CURRENT_VERSION, domain.id.id(), domain.owner, aclData, List.copyOf(domain.groups.values()),
                List.copyOf(domain.auditLog));
    }
}
