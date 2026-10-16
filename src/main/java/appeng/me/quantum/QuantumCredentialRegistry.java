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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import appeng.api.networking.quantum.NetworkProfileId;
import appeng.api.networking.quantum.QuantumCredentialId;
import appeng.api.networking.quantum.QuantumEndpointId;
import appeng.core.AppEng;

/**
 * World-scoped (overworld-attached), persistent storage for every {@link QuantumCredential} that has ever been issued
 * on the server — the canonical provisioning/credential registry for Quantum Q4.
 * <p/>
 * Architecturally a direct sibling of {@link appeng.me.security.SecurityDomainRegistry} and
 * {@link QuantumTopologyRegistry}: same {@code SavedData}/{@code SavedDataType} ownership pattern, same package-private
 * {@code TYPE} field so unit tests can exercise the real on-disk round-trip via
 * {@code SavedDataStorage.computeIfAbsent(TYPE)} without a running server, and the same {@link #createForTesting()}
 * escape hatch for a plain in-memory instance.
 * <p/>
 * <b>Deliberately a SEPARATE {@code SavedData} from {@link QuantumTopologyRegistry}</b>, not a new map folded into it —
 * a credential is a distinct entity (see {@link QuantumCredentialId}'s own javadoc), and keeping the two registries
 * independent is what makes it structurally impossible for this registry to become "just another place topology data is
 * duplicated": this class has no field that could ever hold a {@link appeng.me.quantum.QuantumLane.Edge} or a lane
 * membership set, only a {@link NetworkProfileId} (which profile a credential proves provisioning for) and, per
 * credential, AT MOST ONE currently-bound {@link QuantumEndpointId}. There is no per-credential route/edge/history list
 * anywhere in this class or in {@link QuantumCredential} — see that class's own javadoc for the "one canonical current
 * binding, no silent second copy" invariant this enforces.
 * <p/>
 * <b>What this registry deliberately does NOT do</b> (mirroring {@link QuantumTopologyRegistry}'s own documented
 * non-goals):
 * <ul>
 * <li>It performs NO actor/permission checks itself — every method here is a structural-only primitive, exactly like
 * {@link QuantumTopologyRegistry#addEdge}. Actor-aware authorization (SECURITY-tier
 * {@code QUANTUM_ISSUE}/{@code QUANTUM_REVOKE}/{@code QUANTUM_REWRITE}, or BUILD-tier physical activation) is entirely
 * {@code QuantumCredentialService}'s responsibility, one level up — this keeps the "single server-side source of truth
 * for authorization" choke point in one orchestrating class instead of scattered across every registry method and every
 * GUI/menu action.</li>
 * <li>It never validates that a credential's {@link NetworkProfileId} actually names an existing {@link NetworkProfile}
 * in {@link QuantumTopologyRegistry} — the two registries are independent {@code SavedData} instances and neither
 * references the other. That cross-registry validation ("does this profile exist") is
 * {@code QuantumCredentialService}'s job (see its {@code issue}/{@code activate} methods), exactly mirroring why
 * {@link QuantumTopologyRegistry#addEdge} itself validates endpoint/lane consistency but nothing about
 * {@code SecurityDomainRegistry}.</li>
 * <li>It never itself calls {@code QuantumLaneReconciler.reconstruct} or otherwise forms a live {@code IGridConnection}
 * — a credential's {@link QuantumCredentialState#ONLINE}/{@link QuantumCredentialState#OFFLINE} bookkeeping here is a
 * parallel, independent piece of state to the endpoint's own
 * {@link appeng.api.networking.quantum.QuantumEndpointStatus} in {@link QuantumTopologyRegistry} — see
 * {@code QuantumCredentialService} for how the two are kept in sync without either registry reaching into the other's
 * internals.</li>
 * </ul>
 */
public final class QuantumCredentialRegistry extends SavedData {
    private static final Identifier ID = AppEng.makeId("quantum_credentials");

    private final Map<QuantumCredentialId, QuantumCredential> credentials = new HashMap<>();

    private record RegistryData(List<QuantumCredential> credentials) {
        static final Codec<RegistryData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                QuantumCredential.CODEC.listOf().fieldOf("credentials").forGetter(RegistryData::credentials))
                .apply(builder, RegistryData::new));
    }

    /**
     * Package-private (not {@code private}) purely so unit tests can exercise the real on-disk
     * {@code SavedDataStorage}/{@link SavedDataType} round-trip directly, exactly mirroring
     * {@link QuantumTopologyRegistry#TYPE}'s own documented reason.
     */
    static final SavedDataType<QuantumCredentialRegistry> TYPE = new SavedDataType<>(
            ID,
            level -> new QuantumCredentialRegistry(),
            level -> RegistryData.CODEC.xmap(
                    data -> {
                        var registry = new QuantumCredentialRegistry();
                        for (var credential : data.credentials()) {
                            registry.credentials.put(credential.id(), credential);
                        }
                        return registry;
                    },
                    registry -> new RegistryData(List.copyOf(registry.credentials.values()))));

    private QuantumCredentialRegistry() {
    }

    /**
     * Creates a plain, in-memory registry that is not attached to any {@link Level}/{@link SavedData} at all — exactly
     * like {@link QuantumTopologyRegistry#createForTesting()}.
     */
    public static QuantumCredentialRegistry createForTesting() {
        return new QuantumCredentialRegistry();
    }

    public static QuantumCredentialRegistry get(MinecraftServer server) {
        var overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException(
                    "Cannot retrieve quantum credential data for a server that has no overworld.");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    @Nullable
    public static QuantumCredentialRegistry get(Level level) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return get(serverLevel.getServer());
    }

    // =====================================================================
    // Structural-only primitives — no ACL. See class javadoc.
    // =====================================================================

    /**
     * Mints a brand-new credential for {@code profileId}, in {@link QuantumCredentialState#ISSUED}, unbound, at
     * revision 1. Does NOT validate that {@code profileId} names an existing profile — see class javadoc.
     */
    public QuantumCredential issue(NetworkProfileId profileId) {
        var credential = new QuantumCredential(QuantumCredentialId.create(), profileId);
        credentials.put(credential.id(), credential);
        setDirty();
        return credential;
    }

    @Nullable
    public QuantumCredential get(QuantumCredentialId id) {
        return credentials.get(id);
    }

    public Collection<QuantumCredential> getAllCredentials() {
        return List.copyOf(credentials.values());
    }

    public List<QuantumCredential> getCredentialsForProfile(NetworkProfileId profileId) {
        var result = new ArrayList<QuantumCredential>();
        for (var credential : credentials.values()) {
            if (credential.profileId().equals(profileId)) {
                result.add(credential);
            }
        }
        return result;
    }

    /**
     * @return the credential currently bound to {@code endpointId}, or {@code null} if none is. O(n) in the number of
     *         credentials — acceptable at this registry's expected scale, exactly like
     *         {@link QuantumTopologyRegistry#getEndpointsForLane} is O(lane size) rather than indexed.
     */
    @Nullable
    public QuantumCredential getCredentialForEndpoint(QuantumEndpointId endpointId) {
        for (var credential : credentials.values()) {
            if (credential.boundEndpoint().isPresent() && credential.boundEndpoint().get().equals(endpointId)) {
                return credential;
            }
        }
        return null;
    }

    /**
     * Structural bind: see {@link QuantumCredential#bind}. Returns {@code false} (no state change) if the credential
     * does not exist, is {@link QuantumCredentialState#REVOKED}, or is already bound to a DIFFERENT endpoint (the
     * duplicate-claim/CONFLICT case — see class and {@link QuantumCredential} javadoc). The caller (normally
     * {@code QuantumCredentialService}) is responsible for every ACL/cross-registry check beforehand.
     */
    public boolean bind(QuantumCredentialId id, QuantumEndpointId endpointId) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.bind(endpointId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean markOnline(QuantumCredentialId id) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.markOnline();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean markOffline(QuantumCredentialId id) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.markOffline();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean suspend(QuantumCredentialId id) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.suspend();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean resume(QuantumCredentialId id) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.resume();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean revoke(QuantumCredentialId id) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.revoke();
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /**
     * Structural rebind: see {@link QuantumCredential#rebind}. Unlike {@link #bind}, this unconditionally retires
     * whatever endpoint the credential was previously bound to (if any) in favor of {@code newEndpointId} — the caller
     * is responsible for having already authorized this as a deliberate rebind operation (never called as a fallback
     * when {@link #bind} returns {@code false}).
     */
    public boolean rebind(QuantumCredentialId id, QuantumEndpointId newEndpointId) {
        var credential = credentials.get(id);
        if (credential == null) {
            return false;
        }
        var changed = credential.rebind(newEndpointId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /**
     * Bumps a credential's revision counter without changing its state or binding — the stale-copied-card defense.
     * After this call, any physical card whose own {@code revision} component still reads the OLD value is refused by
     * {@code QuantumCredentialService#activate}'s revision-match check; a freshly (re-)issued card reflecting the new
     * revision is accepted. Reserved as the mechanism a future full {@link QuantumCredentialState#SUPERSEDED} workflow
     * would build on (see that enum constant's javadoc) — today it is exposed directly as this one minimal primitive,
     * not a full reissue UX.
     *
     * @return the updated credential, or empty if {@code id} does not exist.
     */
    public Optional<QuantumCredential> supersede(QuantumCredentialId id) {
        var credential = credentials.get(id);
        if (credential == null) {
            return Optional.empty();
        }
        credential.bumpRevision();
        setDirty();
        return Optional.of(credential);
    }

    /**
     * Marks this registry as needing to be saved. Callers normally don't need this directly — every mutation method
     * above already calls it — but it's exposed for symmetry with {@link QuantumTopologyRegistry#markDirty()}.
     */
    public void markDirty() {
        setDirty();
    }
}
