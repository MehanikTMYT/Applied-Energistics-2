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

package appeng.menu.implementations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.network.clientbound.SecurityStationStatusPacket;
import appeng.me.security.SecurityDomain;
import appeng.me.security.SecurityDomainRegistry;
import appeng.me.security.SecurityGroup;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.guisync.GuiSync;

/**
 * Server-authoritative editor for a {@link SecurityDomain}'s ACL, opened by interacting with a
 * {@link SecurityStationBlockEntity}. All mutating actions are gated on {@link SecurityPermissions#SECURITY} (except
 * {@link #ACTIVATE}, which is only meaningful - and only needs no explicit grant - while the network is still
 * {@link SecurityDomainState#LEGACY}, where every permission check trivially passes).
 * <p/>
 * Note: this menu implements the full server-side ACL mutation API and keeps the client informed of the domain's
 * high-level state via {@link #domainStateOrdinal}, but does not yet ship a polished widget-based screen (player/ group
 * list, buttons, etc.) - that is tracked as a follow-up to this same subsystem, not a different feature. The
 * server-side logic here is fully functional and covered by tests independent of any screen.
 */
public class SecurityStationMenu extends AEBaseMenu {
    public static final MenuType<SecurityStationMenu> TYPE = MenuTypeBuilder
            .create(SecurityStationMenu::new, SecurityStationBlockEntity.class)
            .build("security_station");

    private static final StreamCodec<ByteBuf, PrincipalId> PRINCIPAL_CODEC = ByteBufCodecs.fromCodec(PrincipalId.CODEC);
    private static final StreamCodec<ByteBuf, UUID> UUID_CODEC = ByteBufCodecs.fromCodec(UUIDUtil.CODEC);

    private record GrantMessage(PrincipalId principal, Set<SecurityPermissions> permissions) {
        static final Codec<GrantMessage> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                PrincipalId.CODEC.fieldOf("principal").forGetter(GrantMessage::principal),
                permissionSetCodec().fieldOf("permissions").forGetter(GrantMessage::permissions))
                .apply(builder, GrantMessage::new));
        static final StreamCodec<ByteBuf, GrantMessage> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    private record GroupMembersMessage(UUID groupId, Set<UUID> members) {
        static final Codec<GroupMembersMessage> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                UUIDUtil.CODEC.fieldOf("groupId").forGetter(GroupMembersMessage::groupId),
                UUIDUtil.CODEC.listOf().xmap(Set::copyOf, java.util.List::copyOf).fieldOf("members")
                        .forGetter(GroupMembersMessage::members))
                .apply(builder, GroupMembersMessage::new));
        static final StreamCodec<ByteBuf, GroupMembersMessage> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    public static final ClientActionKey<GrantMessage> GRANT = new ClientActionKey<>("security_station_grant");
    public static final ClientActionKey<PrincipalId> REVOKE = new ClientActionKey<>("security_station_revoke");
    public static final ClientActionKey<String> CREATE_GROUP = new ClientActionKey<>("security_station_create_group");
    public static final ClientActionKey<UUID> DELETE_GROUP = new ClientActionKey<>("security_station_delete_group");
    public static final ClientActionKey<GroupMembersMessage> SET_GROUP_MEMBERS = new ClientActionKey<>(
            "security_station_set_group_members");
    public static final ClientActionKey<Void> ACTIVATE = new ClientActionKey<>("security_station_activate");

    private final SecurityStationBlockEntity station;

    @GuiSync(1)
    public int domainStateOrdinal = SecurityDomainState.LEGACY.ordinal();

    public SecurityStationMenu(int id, Inventory ip, SecurityStationBlockEntity host) {
        super(TYPE, id, ip, host);
        this.station = host;

        registerClientAction(GRANT, GrantMessage.STREAM_CODEC,
                msg -> withDomainForEdit(domain -> {
                    domain.setPermissions(msg.principal(), msg.permissions());
                    domain.audit("granted " + msg.permissions() + " to " + msg.principal().asKey() + " by "
                            + actorDescription());
                }));
        registerClientAction(REVOKE, PRINCIPAL_CODEC,
                principal -> withDomainForEdit(domain -> {
                    domain.revokeAll(principal);
                    domain.audit("revoked all permissions from " + principal.asKey() + " by " + actorDescription());
                }));
        registerClientAction(CREATE_GROUP, ByteBufCodecs.STRING_UTF8,
                name -> withDomainForEdit(domain -> {
                    var group = SecurityGroup.create(name);
                    domain.putGroup(group);
                    domain.audit("created group '" + name + "' by " + actorDescription());
                }));
        registerClientAction(DELETE_GROUP, UUID_CODEC,
                groupId -> withDomainForEdit(domain -> {
                    domain.removeGroup(groupId);
                    domain.audit("deleted group " + groupId + " by " + actorDescription());
                }));
        registerClientAction(SET_GROUP_MEMBERS, GroupMembersMessage.STREAM_CODEC,
                msg -> withDomainForEdit(domain -> {
                    var existing = domain.getGroup(msg.groupId());
                    if (existing != null) {
                        domain.putGroup(new SecurityGroup(existing.id(), existing.name(), msg.members()));
                        domain.audit("updated members of group '" + existing.name() + "' by " + actorDescription());
                    }
                }));
        registerClientAction(ACTIVATE, () -> {
            if (!hasSecurityPermission()) {
                return;
            }
            var player = getPlayer();
            if (player != null) {
                station.activate(player.getUUID());
            }
        });

        // Deliberately no player inventory slots: this menu is a pure ACL editor (text/buttons/list), not an
        // item-handling GUI - matches the precedent set by NetworkStatusMenu (also zero slots).
    }

    private int broadcastDelay;

    @Override
    public void broadcastChanges() {
        var state = station.getDomainState();
        this.domainStateOrdinal = (state != null ? state : SecurityDomainState.LEGACY).ordinal();

        if (isServerSide() && getPlayer() instanceof ServerPlayer serverPlayer) {
            // Throttle like NetworkStatusMenu does - the full ACL snapshot is more expensive to build than a
            // handful of @GuiSync primitives, and the screen doesn't need it every single tick.
            if (broadcastDelay++ > 10) {
                broadcastDelay = 0;
                sendPacketToClient(buildStatusPacket(serverPlayer));
            }
        }

        super.broadcastChanges();
    }

    private SecurityStationStatusPacket buildStatusPacket(ServerPlayer viewer) {
        var state = station.getDomainState();
        var stateOrdinal = (state != null ? state : SecurityDomainState.LEGACY).ordinal();
        var domain = getEditedDomain();
        var canEdit = hasSecurityPermission();

        if (domain == null) {
            return new SecurityStationStatusPacket(stateOrdinal, false, ZERO_UUID, false, ZERO_UUID, "", canEdit,
                    List.of(), List.of());
        }

        var server = viewer.level().getServer();

        List<SecurityStationStatusPacket.AclRow> rows = new ArrayList<>();
        // Always show an EVERYONE row (even empty) so the screen has somewhere to grant baseline access.
        rows.add(new SecurityStationStatusPacket.AclRow(PrincipalId.EVERYONE, "",
                domain.getPermissions(PrincipalId.EVERYONE)));
        for (var entry : domain.getAcl().entrySet()) {
            if (entry.getKey() instanceof PrincipalId.Everyone) {
                continue; // already added above
            }
            rows.add(new SecurityStationStatusPacket.AclRow(entry.getKey(), describePrincipal(entry.getKey(), domain,
                    server), entry.getValue()));
        }

        List<SecurityStationStatusPacket.GroupRow> groups = new ArrayList<>();
        for (var group : domain.getGroups().values()) {
            groups.add(new SecurityStationStatusPacket.GroupRow(group.id(), group.name()));
        }

        var ownerName = resolvePlayerName(server, domain.owner());
        return new SecurityStationStatusPacket(stateOrdinal, true, domain.id().id(), true, domain.owner(),
                ownerName != null ? ownerName : "", canEdit, rows, groups);
    }

    private String describePrincipal(PrincipalId principal, SecurityDomain domain,
            net.minecraft.server.MinecraftServer server) {
        return switch (principal) {
            case PrincipalId.Player p -> {
                var name = resolvePlayerName(server, p.id());
                yield name != null ? name : shortUuid(p.id());
            }
            case PrincipalId.Group g -> {
                var group = domain.getGroup(g.id());
                yield group != null ? group.name() : shortUuid(g.id());
            }
            case PrincipalId.Device d -> shortUuid(d.id());
            case PrincipalId.Everyone ignored -> "";
        };
    }

    /**
     * Resolves a UUID to a player name if that player is currently online. Offline players cannot be resolved to a name
     * by this menu (no persistent name cache is queried) - a known, documented limitation of the current screen; the
     * underlying ACL storage keys everything by UUID regardless, so this only affects the friendliness of the display,
     * never correctness or enforcement.
     */
    @Nullable
    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        return online != null ? online.getGameProfile().name() : null;
    }

    private static String shortUuid(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static final UUID ZERO_UUID = new UUID(0, 0);

    // ---------------------------------------------------------------------
    // Client-side entry points, called by SecurityStationScreen. Wrap the protected sendClientAction so the screen
    // (a different package) can trigger the same server-authoritative mutation API described above. The server
    // still re-checks SECURITY on every one of these regardless of what the client sends/thinks it can do.
    // ---------------------------------------------------------------------

    public void sendGrant(PrincipalId principal, Set<SecurityPermissions> permissions) {
        if (isClientSide()) {
            sendClientAction(GRANT, new GrantMessage(principal, permissions));
        }
    }

    public void sendRevoke(PrincipalId principal) {
        if (isClientSide()) {
            sendClientAction(REVOKE, principal);
        }
    }

    public void sendCreateGroup(String name) {
        if (isClientSide()) {
            sendClientAction(CREATE_GROUP, name);
        }
    }

    public void sendDeleteGroup(UUID groupId) {
        if (isClientSide()) {
            sendClientAction(DELETE_GROUP, groupId);
        }
    }

    public void sendActivate() {
        if (isClientSide()) {
            sendClientAction(ACTIVATE);
        }
    }

    private String actorDescription() {
        var player = getPlayer();
        return player != null ? player.getGameProfile().name() + " (" + player.getUUID() + ")" : "unknown";
    }

    private boolean hasSecurityPermission() {
        var grid = station.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        var security = grid.getService(ISecurityService.class);
        return security.hasPermission(getActionSource(), SecurityPermissions.SECURITY);
    }

    @Nullable
    private SecurityDomain getEditedDomain() {
        var grid = station.getMainNode().getGrid();
        if (grid == null) {
            return null;
        }
        var domainId = grid.getService(ISecurityService.class).getDomainId().orElse(null);
        if (domainId == null) {
            return null;
        }
        var player = getPlayer();
        if (player == null) {
            return null;
        }
        var registry = SecurityDomainRegistry.get(player.level());
        return registry == null ? null : registry.getDomain(domainId);
    }

    private void withDomainForEdit(Consumer<SecurityDomain> action) {
        if (!hasSecurityPermission()) {
            return;
        }
        var domain = getEditedDomain();
        if (domain == null) {
            return;
        }
        action.accept(domain);
        var player = getPlayer();
        var registry = player != null ? SecurityDomainRegistry.get(player.level()) : null;
        if (registry != null) {
            registry.markDirty();
        }
    }

    private static Codec<Set<SecurityPermissions>> permissionSetCodec() {
        return SecurityPermissions.CODEC.listOf().xmap(
                list -> list.isEmpty() ? java.util.EnumSet.noneOf(SecurityPermissions.class)
                        : java.util.EnumSet.copyOf(list),
                java.util.List::copyOf);
    }
}
