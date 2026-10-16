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

package appeng.me.quantum;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.UUIDUtil;

import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.api.networking.quantum.QuantumEndpointLocation;
import appeng.api.networking.quantum.QuantumEndpointStatus;
import appeng.api.networking.quantum.QuantumLaneId;

/**
 * The persistent record of one physical Quantum Lane endpoint ("Base"/"Factory"/"Mine" in the Q2 prototype's own
 * terminology): its stable {@link QuantumEndpointId}, the exactly-one {@link QuantumLaneId} it belongs to (an endpoint
 * is never silently re-parented to a different lane — see {@link QuantumTopologyRegistry#addEdge}), a human-readable
 * label, its {@link QuantumEndpointStatus} lifecycle state, its current {@link QuantumEndpointLocation} if known, and a
 * revision counter bumped on every status/location change (so a caller can detect a stale read without needing a full
 * audit trail).
 * <p/>
 * {@code laneId} is intentionally {@code final} — this is the structural enforcement of "an endpoint belongs to exactly
 * one lane/profile for its whole lifetime"; there is no setter. A physical endpoint that should genuinely move to a
 * different lane is represented by retiring this record ({@link #markRemoved()}) and registering a new one, never by
 * mutating an existing identity's lane membership.
 */
public final class QuantumEndpointRecord {
    private final QuantumEndpointId id;
    private final QuantumLaneId laneId;
    private String label;
    private QuantumEndpointStatus status;
    @Nullable
    private QuantumEndpointLocation location;
    private int revision;

    QuantumEndpointRecord(QuantumEndpointId id, QuantumLaneId laneId, String label) {
        this.id = id;
        this.laneId = laneId;
        this.label = label;
        this.status = QuantumEndpointStatus.REGISTERED;
        this.revision = 0;
    }

    public QuantumEndpointId id() {
        return id;
    }

    public QuantumLaneId laneId() {
        return laneId;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public QuantumEndpointStatus status() {
        return status;
    }

    public Optional<QuantumEndpointLocation> location() {
        return Optional.ofNullable(location);
    }

    public int revision() {
        return revision;
    }

    /**
     * Marks this endpoint online at the given (optional) location — used both for a brand-new endpoint's first
     * appearance and for a reattach/rebind (an already-known {@link QuantumEndpointId} coming back online, possibly at
     * a new {@link QuantumEndpointLocation}, e.g. after the block was broken and replaced one tile over). No-op
     * (returns {@code false}) if this record is {@link QuantumEndpointStatus#REMOVED} — a retired identity does not
     * resurrect itself just by a stale reference coming back online.
     */
    boolean markOnline(@Nullable QuantumEndpointLocation location) {
        if (status == QuantumEndpointStatus.REMOVED) {
            return false;
        }
        this.status = QuantumEndpointStatus.ONLINE;
        this.location = location;
        this.revision++;
        return true;
    }

    /**
     * Marks this endpoint offline (chunk unloaded, block temporarily gone, server stopped, ...). The record itself, and
     * its last known {@link #location()}, are kept — unloaded is not deleted.
     */
    void markOffline() {
        if (status != QuantumEndpointStatus.REMOVED) {
            this.status = QuantumEndpointStatus.OFFLINE;
            this.revision++;
        }
    }

    /** Explicitly, permanently retires this endpoint. Terminal — see {@link #markOnline}. */
    void markRemoved() {
        this.status = QuantumEndpointStatus.REMOVED;
        this.location = null;
        this.revision++;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private record Data(UUID id, UUID laneId, String label, QuantumEndpointStatus status,
            Optional<QuantumEndpointLocation> location, int revision) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Data::id),
                UUIDUtil.CODEC.fieldOf("lane_id").forGetter(Data::laneId),
                Codec.STRING.fieldOf("label").forGetter(Data::label),
                QuantumEndpointStatus.CODEC.fieldOf("status").forGetter(Data::status),
                QuantumEndpointLocation.CODEC.optionalFieldOf("location").forGetter(Data::location),
                Codec.INT.fieldOf("revision").forGetter(Data::revision))
                .apply(builder, Data::new));
    }

    public static final Codec<QuantumEndpointRecord> CODEC = Data.CODEC.xmap(QuantumEndpointRecord::fromData,
            QuantumEndpointRecord::toData);

    private static QuantumEndpointRecord fromData(Data data) {
        var record = new QuantumEndpointRecord(new QuantumEndpointId(data.id()), new QuantumLaneId(data.laneId()),
                data.label());
        record.status = data.status();
        record.location = data.location().orElse(null);
        record.revision = data.revision();
        return record;
    }

    private static Data toData(QuantumEndpointRecord record) {
        return new Data(record.id.id(), record.laneId.id(), record.label, record.status,
                Optional.ofNullable(record.location), record.revision);
    }
}
