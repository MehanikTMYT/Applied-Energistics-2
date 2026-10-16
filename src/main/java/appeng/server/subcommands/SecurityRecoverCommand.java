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

import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainState;
import appeng.blockentity.misc.SecurityStationBlockEntity;
import appeng.core.AELog;
import appeng.me.security.SecurityDomainRegistry;
import appeng.server.ISubCommand;

/**
 * {@code /ae2 security recover <newOwner>}: explicit, operator-triggered anti-lockout tool for the security subsystem.
 * There is deliberately no automatic reset of a domain's ACL - a domain keeps enforcing its permissions even if every
 * player who could edit it is gone (quit the server, had their account deleted, etc.), and the only way out is this
 * command, run by someone with operator-level permission, targeting a Security Station they're looking at.
 * <p/>
 * Reassigns the domain's owner to {@code newOwner} (who then, as owner, implicitly has every permission and can fix up
 * the rest of the ACL through the Security Station UI) and appends an entry to the domain's audit trail as well as the
 * server log - it never silently wipes the ACL.
 */
public class SecurityRecoverCommand implements ISubCommand {
    private static final double REACH_DISTANCE = 32.0;

    @Override
    public void addArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("recover")
                .then(Commands.argument("newOwner", EntityArgument.player())
                        .executes(ctx -> {
                            recover(ctx.getSource(), EntityArgument.getPlayer(ctx, "newOwner"));
                            return 1;
                        })));
    }

    @Override
    public void call(MinecraftServer srv, CommandContext<CommandSourceStack> ctx, CommandSourceStack sender) {
        sender.sendSystemMessage(Component.literal("Usage: /ae2 security recover <newOwner> (while looking at a "
                + "Security Station)"));
    }

    private void recover(CommandSourceStack sender, ServerPlayer newOwner) throws CommandSyntaxException {
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
        if (state == SecurityDomainState.CONFLICT) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "That network is in a security CONFLICT state (two secured networks were merged). "
                            + "Physically split the network apart to resolve this; ownership recovery does not apply here."))
                    .create();
        }
        if (state == SecurityDomainState.LEGACY) {
            throw new SimpleCommandExceptionType(
                    Component.literal("That network has never been secured; there is no owner to recover.")).create();
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

        var previousOwner = domain.owner();
        domain.setOwner(newOwner.getUUID());
        var auditEntry = "owner reassigned from " + previousOwner + " to " + newOwner.getUUID()
                + " by operator " + executor.getGameProfile().name() + " (" + executor.getUUID()
                + ") via /ae2 security recover";
        domain.audit(auditEntry);
        registry.markDirty();

        AELog.info("[Security] %s", auditEntry);
        sender.sendSuccess(() -> Component.literal(
                "Recovered domain " + domainId + ": owner is now " + newOwner.getGameProfile().name() + "."), true);
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
