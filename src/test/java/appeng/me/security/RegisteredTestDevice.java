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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.me.helpers.MachineSource;

/**
 * Builds the shape a device actually has in the world, for tests.
 *
 * <h2>Why fixtures had to change</h2>
 *
 * Until Round 32 a test could model a device as a bare object implementing {@code IDeviceIdentity}, because
 * {@code SecurityService} would fall back to whatever id such an object reported. That fallback was the last place a
 * caller could assert its own identity, and it existed only for these fixtures - no production owner in AE2 is anything
 * other than a block entity or a part. Removing it means fixtures must model what production really looks like:
 * something AE2 can physically locate, whose identity the registry - not the object - holds.
 *
 * <h2>What this helper produces</h2>
 *
 * A source whose node's owner is a block entity at a real position, registered in the given registry under the id the
 * test wants to grant. Nothing here weakens the check: the id still comes from the registry, keyed by the machine's
 * physical position, exactly as it does for a placed Interface. What the helper removes is only the ceremony.
 */
final class RegisteredTestDevice {
    private static final AtomicInteger NEXT_POS = new AtomicInteger();

    /**
     * One machine per identity. Asking twice for "the source of device X" must give the same physical machine, the way
     * a placed Interface stays where it is - without this, a second call would land on a fresh position and the
     * registry would rightly refuse to hand the same id to a different machine.
     */
    private static final java.util.Map<UUID, BlockPos> POSITIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private RegisteredTestDevice() {
    }

    /**
     * Registers a device at a fresh position and returns the source it would use.
     *
     * @param registry the in-memory registry the service under test was given
     * @param level    the (mocked) level the node reports
     * @param deviceId the identity the test wants this machine to hold - registered as a legacy claim, which is free in
     *                 a fresh registry and therefore adopted verbatim
     */
    static IActionSource source(DevicePrincipalRegistry registry, ServerLevel level, UUID deviceId) {
        stubDimension(level);
        var pos = POSITIONS.computeIfAbsent(deviceId, id -> new BlockPos(NEXT_POS.incrementAndGet(), 64, 0));
        // The owner is a block entity that also carries an identity - exactly what every real AE2 device is. The
        // registry remains the authority; this only lets fixtures that inspect the machine itself keep working, the
        // way they would against a placed Interface.
        var owner = mock(BlockEntity.class, withSettings().extraInterfaces(IDeviceIdentity.class));
        when(owner.getLevel()).thenReturn(level);
        when(owner.getBlockPos()).thenReturn(pos);
        when(((IDeviceIdentity) owner).getDeviceId()).thenReturn(deviceId);

        var key = new MachineKey(dimensionOf(level), pos, MachineKey.BLOCK_SLOT);
        var authenticated = registry.resolveOrRegister(key, deviceId);
        if (!authenticated.equals(deviceId)) {
            throw new IllegalStateException(
                    "the registry refused the requested id, which means this fixture's assumption is wrong: "
                            + deviceId + " -> " + authenticated);
        }

        var node = mock(IGridNode.class);
        when(node.getOwner()).thenReturn(owner);
        when(node.getLevel()).thenReturn(level);

        return new MachineSource(() -> node);
    }

    /** A device that owns a real node but was never registered - the "unregistered addon" case. */
    static IActionSource unregisteredSource(ServerLevel level) {
        stubDimension(level);
        var pos = new BlockPos(NEXT_POS.incrementAndGet(), 64, 0);
        var owner = mock(BlockEntity.class);
        when(owner.getLevel()).thenReturn(level);
        when(owner.getBlockPos()).thenReturn(pos);

        var node = mock(IGridNode.class);
        when(node.getOwner()).thenReturn(owner);
        when(node.getLevel()).thenReturn(level);

        return new MachineSource(() -> node);
    }

    /**
     * The shared level fixture is a bare mock, so it has no dimension until something gives it one. Production always
     * has one; stubbing it here keeps {@code MachineKey.of} on the same code path it takes in the world instead of
     * making the helper special-case a missing dimension.
     */
    private static void stubDimension(ServerLevel level) {
        if (level.dimension() == null) {
            when(level.dimension()).thenReturn(Level.OVERWORLD);
        }
    }

    private static Identifier dimensionOf(Level level) {
        return level.dimension().identifier();
    }
}
