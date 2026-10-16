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

package appeng.api.implementations.items;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The complete data an "Encoded Quantum Key" item stack carries (see
 * {@code appeng.items.tools.QuantumKeyItem}/{@code appeng.core.definitions.AEItems#QUANTUM_KEY}) — the physical
 * credential card produced by a Quantum Key Imprinter.
 * <p/>
 * <b>Deliberately minimal — the card is never the source of truth.</b> Per the Q4 provisioning design (see
 * {@code appeng.me.quantum.QuantumCredential}'s own class javadoc), this is exactly the manifest's minimum
 * "card/credential semantics" list and nothing more: which {@code NetworkProfileId} it claims to provision for, which
 * {@code QuantumCredentialId} it claims to be, and the {@code revision} it was last synced at (the stale-copied-card
 * defense — see {@code QuantumCredentialRegistry#supersede}). There is intentionally NO owner, no ACL, no
 * {@code SecurityDomain}, no endpoint coordinates, no route/topology graph, no online state, and no Routing Controller
 * data on this record — every activation re-reads the LIVE {@code QuantumCredentialRegistry} record and never trusts
 * this data on its own (see {@code QuantumCredentialService#activate}). Duplicating this record onto a second
 * `ItemStack` (creative-mode copy, NBT clone, ...) duplicates only the CLAIM, never a second valid endpoint — the
 * registry's own duplicate/CONFLICT rejection is what actually enforces uniqueness, not anything about this data being
 * hard to copy.
 */
public record QuantumKeyCardData(UUID profileId, UUID credentialId, int revision, int protocolVersion) {
    public static final Codec<QuantumKeyCardData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            UUIDUtil.CODEC.fieldOf("profile_id").forGetter(QuantumKeyCardData::profileId),
            UUIDUtil.CODEC.fieldOf("credential_id").forGetter(QuantumKeyCardData::credentialId),
            Codec.INT.fieldOf("revision").forGetter(QuantumKeyCardData::revision),
            Codec.INT.fieldOf("protocol_version").forGetter(QuantumKeyCardData::protocolVersion))
            .apply(builder, QuantumKeyCardData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumKeyCardData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, QuantumKeyCardData::profileId,
            UUIDUtil.STREAM_CODEC, QuantumKeyCardData::credentialId,
            ByteBufCodecs.VAR_INT, QuantumKeyCardData::revision,
            ByteBufCodecs.VAR_INT, QuantumKeyCardData::protocolVersion,
            QuantumKeyCardData::new);
}
