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

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumCredentialState;
import appeng.api.networking.quantum.QuantumEndpointId;

/**
 * The persistent record of one Quantum provisioning credential: the {@link NetworkProfileId} it proves provisioning
 * for, its lifecycle {@link QuantumCredentialState}, a revision counter (the stale-copied-card defense — see
 * {@link QuantumCredentialRegistry#supersede}), and the single {@link QuantumEndpointId} it is currently bound to, if
 * any.
 * <p/>
 * <b>Registry is the sole source of truth — the physical card/item is never authoritative.</b> A card (see
 * {@code appeng.items.tools.QuantumKeyItem} in a later phase) carries only a copy of this credential's {@link #id()},
 * {@link #profileId()} and {@link #revision()} at the moment it was issued/last synced; every activation attempt
 * re-reads this live record and compares against it (see {@code QuantumCredentialService#activate}) — the card's own
 * NBT/components are never trusted as-is. This is what makes ItemStack duplication/NBT-cloning harmless: cloning the
 * card duplicates the *claim*, not the *identity* — the registry still holds exactly one canonical
 * {@link #boundEndpoint()} for a given {@link #id()}, so a second, distinct physical claim of the same credential is
 * rejected by {@link #bind} rather than silently creating a second active endpoint (mirrors
 * {@link QuantumEndpointOnlineTable}'s "duplicate physical claim of one identity becomes CONFLICT, never a silent
 * second copy" precedent, adapted from transient online-node tracking to persistent credential-to-endpoint binding).
 * <p/>
 * <b>Hard invariant: at most one bound endpoint at a time.</b> There is no "move this credential to a different
 * endpoint" side effect of {@link #bind} — an already-bound credential can only move to a different endpoint via the
 * explicit {@link #rebind} operation (always an authorized, deliberate administrative action — see
 * {@code QuantumCredentialService#rebind} — never a side effect of {@link #bind} being called again with a different
 * id). This is the same "no silent re-parenting" shape {@link QuantumEndpointRecord#laneId()} already enforces for
 * endpoints belonging to a lane.
 * <p/>
 * Mutable; callers go through {@link QuantumCredentialRegistry}, which calls
 * {@link QuantumCredentialRegistry#markDirty()} after a mutation — mirroring {@link NetworkProfile}'s and
 * {@link QuantumEndpointRecord}'s own relationship to {@link QuantumTopologyRegistry}.
 */
public final class QuantumCredential {
    /** Bumped whenever the on-disk schema of {@link Data} changes; see {@link #upgrade(Data)}. */
    public static final int CURRENT_VERSION = 1;

    /** Bumped whenever the credential/card wire protocol changes; independent of {@link #CURRENT_VERSION}. */
    public static final int CURRENT_PROTOCOL_VERSION = 1;

    private final QuantumCredentialId id;
    private final NetworkProfileId profileId;
    private QuantumCredentialState state;
    private int revision;
    @Nullable
    private QuantumEndpointId boundEndpoint;
    private final int protocolVersion = CURRENT_PROTOCOL_VERSION;

    QuantumCredential(QuantumCredentialId id, NetworkProfileId profileId) {
        this.id = id;
        this.profileId = profileId;
        this.state = QuantumCredentialState.ISSUED;
        this.revision = 1;
    }

    public QuantumCredentialId id() {
        return id;
    }

    public NetworkProfileId profileId() {
        return profileId;
    }

    public QuantumCredentialState state() {
        return state;
    }

    public int revision() {
        return revision;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public Optional<QuantumEndpointId> boundEndpoint() {
        return Optional.ofNullable(boundEndpoint);
    }

    // ---------------------------------------------------------------------
    // Mutators — package-private, structural only. Actor-aware authorization and cross-registry validation
    // (profile existence, endpoint/lane compatibility, revision match on the CALLER-supplied card, ...) is
    // performed by QuantumCredentialService BEFORE any of these are invoked, mirroring exactly how
    // QuantumTopologyRegistry#addEdge's cross-record checks live in the registry (one level up from
    // QuantumLane#addEdge) rather than here.
    // ---------------------------------------------------------------------

    /**
     * Binds this credential to {@code endpointId}, if it is currently unbound or already bound to that SAME endpoint
     * (idempotent re-activation/reattach — e.g. a repeated activation attempt on the same physical endpoint). Refused
     * for a {@link QuantumCredentialState#REVOKED} credential, and refused (returns {@code false}, no state change) if
     * this credential is already bound to a DIFFERENT endpoint — that requires the explicit {@link #rebind}.
     */
    boolean bind(QuantumEndpointId endpointId) {
        if (state == QuantumCredentialState.REVOKED) {
            return false;
        }
        if (boundEndpoint != null && !boundEndpoint.equals(endpointId)) {
            return false;
        }
        boundEndpoint = endpointId;
        if (state == QuantumCredentialState.ISSUED || state == QuantumCredentialState.OFFLINE) {
            state = QuantumCredentialState.ACTIVATED;
        }
        return true;
    }

    /** Marks this credential's bound endpoint online. Requires an existing binding and a non-terminal state. */
    boolean markOnline() {
        if (boundEndpoint == null) {
            return false;
        }
        if (state == QuantumCredentialState.REVOKED || state == QuantumCredentialState.SUSPENDED) {
            return false;
        }
        state = QuantumCredentialState.ONLINE;
        return true;
    }

    /** Marks this credential's bound endpoint offline. The binding is kept — offline is not unbound. */
    boolean markOffline() {
        if (state != QuantumCredentialState.ONLINE) {
            return false;
        }
        state = QuantumCredentialState.OFFLINE;
        return true;
    }

    /**
     * Explicitly suspends this credential — the binding and revision are kept, but it is refused by every
     * activation/route check until {@link #resume}d. Refused only if already {@link QuantumCredentialState#REVOKED}.
     */
    boolean suspend() {
        if (state == QuantumCredentialState.REVOKED) {
            return false;
        }
        state = QuantumCredentialState.SUSPENDED;
        return true;
    }

    /**
     * Resumes a {@link QuantumCredentialState#SUSPENDED} credential. It does not automatically become
     * {@link QuantumCredentialState#ONLINE} again — the physical endpoint must re-announce itself, exactly as an
     * ordinary reload would (see {@link #markOnline}) — it becomes {@link QuantumCredentialState#OFFLINE} if bound, or
     * {@link QuantumCredentialState#ISSUED} if it was suspended before ever being bound.
     */
    boolean resume() {
        if (state != QuantumCredentialState.SUSPENDED) {
            return false;
        }
        state = boundEndpoint != null ? QuantumCredentialState.OFFLINE : QuantumCredentialState.ISSUED;
        return true;
    }

    /** Explicitly, permanently revokes this credential. Terminal — refused only if already REVOKED. */
    boolean revoke() {
        if (state == QuantumCredentialState.REVOKED) {
            return false;
        }
        state = QuantumCredentialState.REVOKED;
        return true;
    }

    /**
     * Explicit, authorized rebind: retires whatever endpoint this credential was previously bound to (if any) and binds
     * it to {@code newEndpointId} instead — never a side effect of {@link #bind}. Does not bump {@link #revision()} (a
     * rebind is a topology change the SAME already-authorized physical card should keep working through, not a "this
     * card should now be considered stale" security event — see {@link QuantumCredentialRegistry#supersede} for the
     * latter). Refused only if already {@link QuantumCredentialState#REVOKED}.
     */
    boolean rebind(QuantumEndpointId newEndpointId) {
        if (state == QuantumCredentialState.REVOKED) {
            return false;
        }
        boundEndpoint = newEndpointId;
        state = QuantumCredentialState.ACTIVATED;
        return true;
    }

    /** Bumps the revision counter — see {@link QuantumCredentialRegistry#supersede}. */
    void bumpRevision() {
        revision++;
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private record Data(int version, UUID id, UUID profileId, QuantumCredentialState state, int revision,
            Optional<UUID> boundEndpoint, int protocolVersion) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.INT.fieldOf("version").forGetter(Data::version),
                UUIDUtil.CODEC.fieldOf("id").forGetter(Data::id),
                UUIDUtil.CODEC.fieldOf("profile_id").forGetter(Data::profileId),
                QuantumCredentialState.CODEC.fieldOf("state").forGetter(Data::state),
                Codec.INT.fieldOf("revision").forGetter(Data::revision),
                UUIDUtil.CODEC.optionalFieldOf("bound_endpoint").forGetter(Data::boundEndpoint),
                Codec.INT.fieldOf("protocol_version").forGetter(Data::protocolVersion))
                .apply(builder, Data::new));
    }

    public static final Codec<QuantumCredential> CODEC = Data.CODEC.xmap(QuantumCredential::upgrade,
            QuantumCredential::toData);

    /**
     * Converts a possibly-old-version {@link Data} DTO into a live {@link QuantumCredential}. There is only one version
     * so far; future schema changes should branch on {@code data.version()} here, mirroring
     * {@code NetworkProfile#upgrade}/{@code SecurityDomain#upgrade}.
     */
    private static QuantumCredential upgrade(Data data) {
        var credential = new QuantumCredential(new QuantumCredentialId(data.id()), new NetworkProfileId(
                data.profileId()));
        credential.state = data.state();
        credential.revision = data.revision();
        credential.boundEndpoint = data.boundEndpoint().map(QuantumEndpointId::new).orElse(null);
        return credential;
    }

    private static Data toData(QuantumCredential credential) {
        return new Data(CURRENT_VERSION, credential.id.id(), credential.profileId.id(), credential.state,
                credential.revision, Optional.ofNullable(credential.boundEndpoint).map(QuantumEndpointId::id),
                credential.protocolVersion);
    }
}
