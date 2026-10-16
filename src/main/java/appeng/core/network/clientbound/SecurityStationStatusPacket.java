
package appeng.core.network.clientbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityPermissions;
import appeng.core.network.ClientboundPacket;
import appeng.core.network.CustomAppEngPayload;

/**
 * Periodic snapshot of a {@link appeng.menu.implementations.SecurityStationMenu}'s edited domain, sent from server to
 * client so {@code SecurityStationScreen} has something to render - mirrors the {@code NetworkStatusPacket} pattern
 * used by the network tool, since the ACL/group list is variable-length data that doesn't fit the fixed-field
 * {@code @GuiSync} mechanism.
 * <p/>
 * All the actual permission checking happens server-side ({@code SecurityStationMenu#hasSecurityPermission} gates every
 * mutation); this packet only ever carries display data plus a {@link #canEdit()} hint the screen uses to grey out
 * controls it knows the server would reject anyway.
 */
public record SecurityStationStatusPacket(
        int stateOrdinal,
        boolean hasDomain,
        UUID domainId,
        boolean hasOwner,
        UUID ownerId,
        String ownerName,
        boolean canEdit,
        List<AclRow> rows,
        List<GroupRow> groups) implements ClientboundPacket {

    public record AclRow(PrincipalId principal, String description, Set<SecurityPermissions> permissions) {
    }

    public record GroupRow(UUID id, String name) {
    }

    private static final StreamCodec<ByteBuf, PrincipalId> PRINCIPAL_STREAM_CODEC = ByteBufCodecs
            .fromCodec(PrincipalId.CODEC);

    public static final Type<SecurityStationStatusPacket> TYPE = CustomAppEngPayload
            .createType("security_station_status");

    public static final StreamCodec<RegistryFriendlyByteBuf, SecurityStationStatusPacket> STREAM_CODEC = StreamCodec
            .ofMember(SecurityStationStatusPacket::write, SecurityStationStatusPacket::decode);

    @Override
    public Type<SecurityStationStatusPacket> type() {
        return TYPE;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(stateOrdinal);
        buf.writeBoolean(hasDomain);
        UUIDUtil.STREAM_CODEC.encode(buf, domainId);
        buf.writeBoolean(hasOwner);
        UUIDUtil.STREAM_CODEC.encode(buf, ownerId);
        ByteBufCodecs.STRING_UTF8.encode(buf, ownerName);
        buf.writeBoolean(canEdit);

        buf.writeVarInt(rows.size());
        for (var row : rows) {
            PRINCIPAL_STREAM_CODEC.encode(buf, row.principal());
            ByteBufCodecs.STRING_UTF8.encode(buf, row.description());
            buf.writeVarInt(row.permissions().size());
            for (var perm : row.permissions()) {
                buf.writeVarInt(perm.ordinal());
            }
        }

        buf.writeVarInt(groups.size());
        for (var group : groups) {
            UUIDUtil.STREAM_CODEC.encode(buf, group.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, group.name());
        }
    }

    public static SecurityStationStatusPacket decode(RegistryFriendlyByteBuf buf) {
        var stateOrdinal = buf.readVarInt();
        var hasDomain = buf.readBoolean();
        var domainId = UUIDUtil.STREAM_CODEC.decode(buf);
        var hasOwner = buf.readBoolean();
        var ownerId = UUIDUtil.STREAM_CODEC.decode(buf);
        var ownerName = ByteBufCodecs.STRING_UTF8.decode(buf);
        var canEdit = buf.readBoolean();

        var rowCount = buf.readVarInt();
        List<AclRow> rows = new ArrayList<>(rowCount);
        var permissionValues = SecurityPermissions.values();
        for (var i = 0; i < rowCount; i++) {
            var principal = PRINCIPAL_STREAM_CODEC.decode(buf);
            var description = ByteBufCodecs.STRING_UTF8.decode(buf);
            var permCount = buf.readVarInt();
            var permissions = java.util.EnumSet.noneOf(SecurityPermissions.class);
            for (var p = 0; p < permCount; p++) {
                permissions.add(permissionValues[buf.readVarInt()]);
            }
            rows.add(new AclRow(principal, description, permissions));
        }

        var groupCount = buf.readVarInt();
        List<GroupRow> groups = new ArrayList<>(groupCount);
        for (var i = 0; i < groupCount; i++) {
            var id = UUIDUtil.STREAM_CODEC.decode(buf);
            var name = ByteBufCodecs.STRING_UTF8.decode(buf);
            groups.add(new GroupRow(id, name));
        }

        return new SecurityStationStatusPacket(stateOrdinal, hasDomain, domainId, hasOwner, ownerId, ownerName,
                canEdit, rows, groups);
    }
}
