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

package appeng.api.networking.quantum;

import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;

/**
 * Stable, persistent identity for one {@link appeng.me.quantum.QuantumCredential} — a provisioning/authorization token
 * that a {@link NetworkProfileId} owner (or a delegate holding
 * {@link appeng.api.networking.security.SecurityPermissions#QUANTUM_ISSUE}) has issued, and which a physical
 * {@link appeng.me.quantum.QuantumEndpointRecord} can be bound to in order to prove it is legitimately provisioned for
 * that profile.
 * <p/>
 * Deliberately a <strong>different</strong> typed id from {@link NetworkProfileId}, {@link QuantumLaneId} and
 * {@link QuantumEndpointId} — see those classes' own javadoc for why this codebase never reuses one UUID type/class for
 * multiple distinct entities. A credential is not a profile, not a lane, not an endpoint: it is the separate "proof of
 * provisioning" token that binds an endpoint to a profile, and its own lifecycle (issued, activated, online/offline,
 * suspended, revoked — see {@link QuantumCredentialState}) is independent of all three.
 */
public record QuantumCredentialId(UUID id) {
    public static final Codec<QuantumCredentialId> CODEC = UUIDUtil.CODEC.xmap(QuantumCredentialId::new,
            QuantumCredentialId::id);

    public static QuantumCredentialId create() {
        return new QuantumCredentialId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
