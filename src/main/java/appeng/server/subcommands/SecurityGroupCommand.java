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

package appeng.server.subcommands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.me.security.SecurityDomain;
import appeng.me.security.SecurityDomainRegistry;
import appeng.me.security.SecurityGroup;
import appeng.server.ISubCommand;

/**
 * {@code /ae2 securitygroup <addmember|removemember|listmembers> <groupName> [player]} (while looking at a Security
 * Station): manages a {@link SecurityGroup}'s <em>membership</em> (who is in the group), as opposed to the group's ACL
 * permissions or its existence, both of which are already fully editable from the Security Station screen
 * ({@code CREATE_GROUP}/{@code DELETE_GROUP}/{@code GRANT}/{@code REVOKE}). {@code SET_GROUP_MEMBERS} - the server
 * action that actually mutates membership - has existed, server-authoritative and tested, since the domain model was
 * built; this command is the first player-reachable entry point for it, since the screen never grew a membership widget
 * (see the session report's honest UI-debt list).
 * <p/>
 * <b>Deliberately registered as a <em>separate</em> top-level {@code /ae2} command, not nested under
 * {@code /ae2 security}</b>: that node is gated at {@code LEVEL_OWNERS} (operator-only) for its one existing
 * subcommand, {@code recover} - an explicit anti-lockout tool that must stay independent of the in-game ACL (see
 * {@link SecurityRecoverCommand}'s own doc comment). Brigadier's {@code .requires(...)} gate applies to the entire
 * subtree under a node, so nesting a normal ACL-gated command there would either (a) also be operator-only, defeating
 * the point, or (b) require loosening the existing operator gate for the whole {@code security} namespace, which would
 * be an unrelated, unwanted change to an already-shipped, already-tested command. This command is therefore registered
 * at {@code LEVEL_ALL} (any player can attempt it, exactly like the Security Station screen's buttons are always
 * clickable) - the real, server-authoritative gate is the {@link SecurityPermissions#SECURITY} ACL check performed
 * inside {@link #run}, identical in spirit to {@code SecurityStationMenu#hasSecurityPermission}.
 */
public class SecurityGroupCommand implements ISubCommand {
    private static final double REACH_DISTANCE = 32.0;

    @Override
    public void addArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("addmember")
                .then(Commands.argument("group", StringArgumentType.string())
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    run(ctx.getSource(), StringArgumentType.getString(ctx, "group"),
                                            Action.ADD, EntityArgument.getPlayer(ctx, "player"));
                                    return 1;
                                }))));
        builder.then(Commands.literal("removemember")
                .then(Commands.argument("group", StringArgumentType.string())
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    run(ctx.getSource(), StringArgumentType.getString(ctx, "group"),
                                            Action.REMOVE, EntityArgument.getPlayer(ctx, "player"));
                                    return 1;
                                }))));
        builder.then(Commands.literal("listmembers")
                .then(Commands.argument("group", StringArgumentType.string())
                        .executes(ctx -> {
                            run(ctx.getSource(), StringArgumentType.getString(ctx, "group"), Action.LIST, null);
                            return 1;
                        })));
    }

    @Override
    public void call(MinecraftServer srv, CommandContext<CommandSourceStack> ctx, CommandSourceStack sender) {
        sender.sendSystemMessage(Component.literal(
                "Usage: /ae2 securitygroup <addmember|removemember|listmembers> <group> [player] "
                        + "(while looking at a Security Station)"));
    }

    private enum Action {
        ADD,
        REMOVE,
        LIST
    }

    private void run(CommandSourceStack sender, String groupName, Action action, ServerPlayer targetPlayer)
            throws CommandSyntaxException {
        var executor = sender.getPlayerOrException();

        var station = findTargetedSecurityStation(executor);
        if (station == null) {
            throw new SimpleCommandExceptionType(
                    Component.literal("You must be looking at a Security Station within " + (int) REACH_DISTANCE
                            + " blocks."))
                    .create();
        }

        var grid = station.getMainNode().getGrid();
        if (grid == null) {
            throw new SimpleCommandExceptionType(Component.literal("That Security Station's network is offline."))
                    .create();
        }

        var security = grid.getService(ISecurityService.class);
        var state = security.getState();
        if (state != SecurityDomainState.SECURED) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "That network is not SECURED (state: " + state + "); there is no group to manage."))
                    .create();
        }

        if (!security.hasPermission(IActionSource.ofPlayer(executor), SecurityPermissions.SECURITY)) {
            throw new SimpleCommandExceptionType(
                    Component.literal("You do not have the SECURITY permission on this network."))
                    .create();
        }

        var domainId = security.getDomainId().orElseThrow();
        var registry = SecurityDomainRegistry.get(executor.level());
        if (registry == null) {
            throw new SimpleCommandExceptionType(Component.literal("Security domain storage is unavailable.")).create();
        }
        var domain = registry.getDomain(domainId);
        if (domain == null) {
            throw new SimpleCommandExceptionType(Component.literal("That security domain no longer exists.")).create();
        }

        var group = findGroupByName(domain, groupName);
        if (group == null) {
            throw new SimpleCommandExceptionType(
                    Component.literal("No group named '" + groupName + "' exists on this network.")).create();
        }

        switch (action) {
            case ADD -> {
                if (group.members().contains(targetPlayer.getUUID())) {
                    sender.sendSystemMessage(Component.literal(
                            targetPlayer.getGameProfile().name() + " is already a member of '" + group.name() + "'."));
                    return;
                }
                var updated = group.withMember(targetPlayer.getUUID());
                domain.putGroup(updated);
                domain.audit("added " + targetPlayer.getUUID() + " to group '" + group.name() + "' by "
                        + executor.getGameProfile().name() + " (" + executor.getUUID() + ") via /ae2 securitygroup");
                registry.markDirty();
                sender.sendSuccess(() -> Component.literal(
                        "Added " + targetPlayer.getGameProfile().name() + " to group '" + group.name() + "'."), true);
            }
            case REMOVE -> {
                if (!group.members().contains(targetPlayer.getUUID())) {
                    sender.sendSystemMessage(Component.literal(
                            targetPlayer.getGameProfile().name() + " is not a member of '" + group.name() + "'."));
                    return;
                }
                var updated = group.withoutMember(targetPlayer.getUUID());
                domain.putGroup(updated);
                domain.audit("removed " + targetPlayer.getUUID() + " from group '" + group.name() + "' by "
                        + executor.getGameProfile().name() + " (" + executor.getUUID() + ") via /ae2 securitygroup");
                registry.markDirty();
                sender.sendSuccess(() -> Component.literal(
                        "Removed " + targetPlayer.getGameProfile().name() + " from group '" + group.name() + "'."),
                        true);
            }
            case LIST -> {
                if (group.members().isEmpty()) {
                    sender.sendSystemMessage(Component.literal("Group '" + group.name() + "' has no members."));
                    return;
                }
                var server = executor.level().getServer();
                var names = new StringBuilder();
                for (var memberId : group.members()) {
                    if (!names.isEmpty()) {
                        names.append(", ");
                    }
                    var online = server.getPlayerList().getPlayer(memberId);
                    names.append(online != null ? online.getGameProfile().name() : memberId.toString());
                }
                sender.sendSystemMessage(
                        Component.literal("Group '" + group.name() + "' members: " + names));
            }
        }
    }

    /**
     * Case-insensitive group name lookup, matching {@code SecurityStationScreen#resolveTargetPrincipal}'s GROUP mode
     * (the same convention players already use in the Security Station screen to reference a group by name).
     * Package-private so it can be unit-tested directly against a real {@link SecurityDomain} fixture, without needing
     * a live server/command dispatch (mirrors {@code PartPlacement#findExistingGridSecurity}'s and
     * {@code CablePart#hasBuildPermissionToRecolor}'s own reasoning for factoring out routing/lookup logic this way).
     */
    static SecurityGroup findGroupByName(SecurityDomain domain, String name) {
        for (var group : domain.getGroups().values()) {
            if (group.name().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    private SecurityStationBlockEntity findTargetedSecurityStation(Player player) {
        var hit = player.pick(REACH_DISTANCE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        var blockEntity = player.level().getBlockEntity(blockHit.getBlockPos());
        return blockEntity instanceof SecurityStationBlockEntity station ? station : null;
    }
}
