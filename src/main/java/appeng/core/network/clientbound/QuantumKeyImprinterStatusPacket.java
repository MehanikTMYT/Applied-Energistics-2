package appeng.core.network.clientbound;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import appeng.core.network.ClientboundPacket;
import appeng.core.network.CustomAppEngPayload;

/**
 * Periodic snapshot of a {@code appeng.menu.implementations.QuantumKeyImprinterMenu}'s server-side state, sent from
 * server to client so {@code QuantumKeyImprinterScreen} has something to render — mirrors
 * {@code SecurityStationStatusPacket} exactly (see that class's own javadoc). All actual authorization happens
 * server-side ({@code QuantumCredentialService} gates every mutation); this packet only ever carries display data.
 */
public record QuantumKeyImprinterStatusPacket(
        int domainStateOrdinal,
        boolean hasProfile,
        UUID profileId,
        UUID domainId,
        String heldItemDescription,
        String lastResultMessage) implements ClientboundPacket {

    public static final Type<QuantumKeyImprinterStatusPacket> TYPE = CustomAppEngPayload
            .createType("quantum_key_imprinter_status");

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumKeyImprinterStatusPacket> STREAM_CODEC = StreamCodec
            .composite(
                    ByteBufCodecs.VAR_INT, QuantumKeyImprinterStatusPacket::domainStateOrdinal,
                    ByteBufCodecs.BOOL, QuantumKeyImprinterStatusPacket::hasProfile,
                    UUIDUtil.STREAM_CODEC, QuantumKeyImprinterStatusPacket::profileId,
                    UUIDUtil.STREAM_CODEC, QuantumKeyImprinterStatusPacket::domainId,
                    ByteBufCodecs.STRING_UTF8, QuantumKeyImprinterStatusPacket::heldItemDescription,
                    ByteBufCodecs.STRING_UTF8, QuantumKeyImprinterStatusPacket::lastResultMessage,
                    QuantumKeyImprinterStatusPacket::new);

    @Override
    public Type<QuantumKeyImprinterStatusPacket> type() {
        return TYPE;
    }
}
