package appeng.core.network.clientbound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import appeng.core.network.ClientboundPacket;
import appeng.core.network.CustomAppEngPayload;

/**
 * Round 11 ("Routing Controller foundation"): periodic snapshot of a
 * {@code appeng.menu.implementations.QuantumRoutingControllerMenu}'s server-side state, so
 * {@code QuantumRoutingControllerScreen} has something to render. Mirrors {@code QuantumKeyImprinterStatusPacket}
 * exactly.
 * <p/>
 * <b>Display data only.</b> Every authorization decision is made server-side by {@code QuantumRoutingService} on each
 * action; nothing in this packet is ever trusted as a permission grant. In particular {@code canView}/{@code canRoute}
 * exist purely so the screen can grey out buttons as a courtesy — a client that ignores them and presses anyway gets
 * the exact same server-side {@code FORBIDDEN}.
 * <p/>
 * <b>Coordinate privacy.</b> Endpoint identity is carried as short display ids plus a dimension name only. The exact
 * remote {@code BlockPos} is deliberately never transmitted — Round 11 does not decide that {@code QUANTUM_VIEW}
 * implies the right to see precise coordinates of an entire infrastructure; that remains a separate, explicit
 * authorization decision for a later round.
 */
public record QuantumRoutingControllerStatusPacket(
        boolean bound,
        UUID profileId,
        int pairCount,
        int selectedIndex,
        boolean hasPair,
        String endpointAShortId,
        String endpointBShortId,
        boolean endpointAOnline,
        boolean endpointBOnline,
        String endpointADimension,
        String endpointBDimension,
        boolean intentExists,
        boolean materialized,
        int endpointCount,
        int intendedEdgeCount,
        int materializedEdgeCount,
        List<String> endpointSummaries,
        List<String> edgeSummaries,
        int routeStateOrdinal,
        int routeReasonOrdinal,
        boolean canView,
        boolean canRoute,
        String lastResultMessage) implements ClientboundPacket {

    public static final Type<QuantumRoutingControllerStatusPacket> TYPE = CustomAppEngPayload
            .createType("quantum_routing_controller_status");

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumRoutingControllerStatusPacket> STREAM_CODEC = StreamCodec
            .of(QuantumRoutingControllerStatusPacket::write, QuantumRoutingControllerStatusPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, QuantumRoutingControllerStatusPacket packet) {
        buf.writeBoolean(packet.bound());
        UUIDUtil.STREAM_CODEC.encode(buf, packet.profileId());
        ByteBufCodecs.VAR_INT.encode(buf, packet.pairCount());
        ByteBufCodecs.VAR_INT.encode(buf, packet.selectedIndex());
        buf.writeBoolean(packet.hasPair());
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.endpointAShortId());
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.endpointBShortId());
        buf.writeBoolean(packet.endpointAOnline());
        buf.writeBoolean(packet.endpointBOnline());
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.endpointADimension());
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.endpointBDimension());
        buf.writeBoolean(packet.intentExists());
        buf.writeBoolean(packet.materialized());
        ByteBufCodecs.VAR_INT.encode(buf, packet.endpointCount());
        ByteBufCodecs.VAR_INT.encode(buf, packet.intendedEdgeCount());
        ByteBufCodecs.VAR_INT.encode(buf, packet.materializedEdgeCount());
        writeStrings(buf, packet.endpointSummaries());
        writeStrings(buf, packet.edgeSummaries());
        ByteBufCodecs.VAR_INT.encode(buf, packet.routeStateOrdinal());
        ByteBufCodecs.VAR_INT.encode(buf, packet.routeReasonOrdinal());
        buf.writeBoolean(packet.canView());
        buf.writeBoolean(packet.canRoute());
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.lastResultMessage());
    }

    private static QuantumRoutingControllerStatusPacket read(RegistryFriendlyByteBuf buf) {
        return new QuantumRoutingControllerStatusPacket(
                buf.readBoolean(),
                UUIDUtil.STREAM_CODEC.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                readStrings(buf),
                readStrings(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
        ByteBufCodecs.VAR_INT.encode(buf, values.size());
        for (var value : values) {
            ByteBufCodecs.STRING_UTF8.encode(buf, value);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        var result = new ArrayList<String>();
        var count = ByteBufCodecs.VAR_INT.decode(buf);
        for (int i = 0; i < count; i++) {
            result.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        }
        return List.copyOf(result);
    }

    @Override
    public Type<QuantumRoutingControllerStatusPacket> type() {
        return TYPE;
    }
}
