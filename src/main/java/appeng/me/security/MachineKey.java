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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.parts.AEBasePart;

/**
 * Canonically identifies one physical <em>security actor</em> - not merely one block.
 * <p/>
 * Round 29 makes the device registry, rather than host NBT, the authority for {@code PrincipalId.Device}. That only
 * works if a machine can be recognised again after a reload by something the host does not get to invent. This key is
 * that something: it is derived from where the device physically <em>is</em>, read by AE2 from vanilla state, never
 * declared by the device.
 *
 * <h2>Why it is not just dimension + position</h2>
 *
 * A cable bus hosts up to seven independent parts ({@code IPartHost#getPart(Direction)} accepts each of the six sides
 * plus {@code null} for the centre), and several of them - Import/Export Bus, Annihilation Plane, Interface, Pattern
 * Provider - are autonomous devices in their own right. Two parts on one block must never collide, so the key carries a
 * slot discriminator: {@link #BLOCK_SLOT} for a block entity device, the side name for a part, {@link #CENTER_SLOT} for
 * a centre part.
 *
 * <h2>What it deliberately does not include</h2>
 *
 * Not the grid, not the security domain, not the network. A device may be disconnected, moved between grids, or sit on
 * a split network without ceasing to be the same physical machine (brief §42). Identity is physical; authorization is
 * applied afterwards, by the domain.
 *
 * <h2>Consequences that follow from the choice</h2>
 *
 * <ul>
 * <li><b>Reload</b> - same dimension, same position, same slot, so the same key and the same identity.</li>
 * <li><b>Clone</b> - a copy placed anywhere else has a different key. It cannot present the original's identity, which
 * is exactly what closes the blueprint collision Round 28 reproduced.</li>
 * <li><b>Relocation</b> - also a different key. Round 29 deliberately treats an unsignalled relocation as a new device
 * (brief §24): "old place empty, same id appeared elsewhere" is indistinguishable from a clone, and guessing in favour
 * of convenience is exactly how a clone would steal an identity.</li>
 * </ul>
 */
public record MachineKey(Identifier dimension, BlockPos pos, String slot) {

    /** Slot discriminator for a device that is a block entity rather than a part. */
    public static final String BLOCK_SLOT = "block";
    /** Slot discriminator for a part in a cable bus's centre position ({@code getPart(null)}). */
    public static final String CENTER_SLOT = "center";

    public static final Codec<MachineKey> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(
                    Identifier.CODEC.fieldOf("dim").forGetter(MachineKey::dimension),
                    BlockPos.CODEC.fieldOf("pos").forGetter(MachineKey::pos),
                    Codec.STRING.fieldOf("slot").forGetter(MachineKey::slot))
            .apply(builder, MachineKey::new));

    /**
     * Derives the key of whatever owns a grid node, or {@code null} if that owner is not an in-world device this
     * registry can identify.
     * <p/>
     * The two recognised shapes are the two AE2 actually has: a {@link BlockEntity} (Interface, Pattern Provider, IO
     * Port, and any addon block entity that owns a node) and an {@link AEBasePart} (IO Bus, Annihilation Plane, and the
     * part forms of Interface and Pattern Provider). In both cases the position comes from vanilla/AE2 state - the
     * block entity's own {@code getBlockPos()}, or the part host's - and not from anything the device reports about
     * itself.
     *
     * @return the key, or {@code null} when the owner has no in-world position yet (not placed, client side, or a
     *         non-device owner such as a plain test object). A {@code null} key means "cannot be authenticated as a
     *         device", which callers must treat as fail-closed rather than as permission.
     */
    @Nullable
    public static MachineKey of(@Nullable Object nodeOwner) {
        if (nodeOwner instanceof BlockEntity be) {
            return ofBlockEntity(be, BLOCK_SLOT);
        }
        if (nodeOwner instanceof AEBasePart part) {
            var hostBe = part.getBlockEntity();
            if (hostBe == null) {
                return null;
            }
            var side = part.getSide();
            return ofBlockEntity(hostBe, side == null ? CENTER_SLOT : side.getSerializedName());
        }
        return null;
    }

    @Nullable
    private static MachineKey ofBlockEntity(BlockEntity be, @Nullable String slot) {
        var level = be.getLevel();
        if (level == null || slot == null) {
            return null;
        }
        return new MachineKey(level.dimension().identifier(), be.getBlockPos().immutable(), slot);
    }

    @Override
    public String toString() {
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "/" + slot;
    }
}
