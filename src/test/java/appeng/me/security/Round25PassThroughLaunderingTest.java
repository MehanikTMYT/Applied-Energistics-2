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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.storage.NetworkStorage;

/**
 * Round 25 - the Storage Bus / ME Chest privilege-laundering gate (brief §10, §11, STOP-6, STOP-7).
 * <p/>
 * <b>The architecture being asserted.</b> This project's ACL is enforced at exactly one place on the storage path:
 * {@code StorageService} wraps its {@link NetworkStorage} in a {@link SecurityAwareMEStorage} and only ever hands that
 * wrapper out ({@code StorageService.java:105} and {@code :205} - {@code getInventory()} is the sole public accessor
 * and returns the secured wrapper, never the raw {@code NetworkStorage}). Storage Buses and ME Chests are
 * {@code IStorageProvider}s that {@code mount} their inventory <em>inside</em> that {@code NetworkStorage}
 * ({@code StorageBusPart#mountInventories}, {@code MEChestBlockEntity#mountInventories}). They therefore sit
 * <em>behind</em> the check, are reached only after it has already passed, and receive the original caller's
 * {@link IActionSource} unchanged.
 * <p/>
 * Two consequences, both tested below:
 * <ol>
 * <li>A denied caller never reaches the pass-through component at all, so a device grant held by that component cannot
 * launder anything (the tests here go further and give the component a full grant, including {@code SECURITY}).</li>
 * <li>Giving these components a {@code Device} identity of their own would not just be unnecessary, it would be
 * architecturally wrong: they never author an intent, they only carry someone else's. Round 25 therefore deliberately
 * leaves {@code StorageBusPart} and {@code MEChestBlockEntity} without {@code IDeviceIdentity} - classification <b>E -
 * PASSIVE_STORAGE_ENDPOINT</b> - and that is a complete result for those components, not a gap.</li>
 * </ol>
 * Supporting code evidence for the Storage Bus specifically: its {@code protected final IActionSource source} field
 * ({@code StorageBusPart.java:93} / {@code :127}) is <em>never read anywhere in the codebase</em> and the class has no
 * subclasses - the Storage Bus is not an action source at all today. For the ME Chest, its {@code mySrc}
 * ({@code MEChestBlockEntity.java:107}) is only ever passed to {@code this.cellHandler}, its own local cell, and never
 * to the network's secured storage.
 */
// The ephemeral test server is required, not decorative: AEItemKey.of(Item) reaches Item#getDefaultInstance, which
// needs the item registry's data components to be bound. @BootstrapMinecraft alone does not bind them, so without this
// the class would only pass when some other test class happened to bind them first in the same JVM - an
// ordering-dependent flake this round observed for real.
@ExtendWith(EphemeralTestServerProvider.class)
class Round25PassThroughLaunderingTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    private static AEItemKey item() {
        return AEItemKey.of(Items.DIAMOND);
    }

    // ------------------------------------------------------------------
    // §10 - Storage Bus
    // ------------------------------------------------------------------

    @Test
    void deniedActorCannotGainAccessThroughAllowedStorageBusDevice() {
        var f = new Fixture();
        var deniedPlayer = UUID.randomUUID();
        var storageBusDevice = UUID.randomUUID();

        // The pass-through component is granted everything, including super-admin. The caller is granted nothing.
        f.domain.grant(PrincipalId.ofDevice(storageBusDevice), Set.of(SecurityPermissions.values()));

        assertThat(f.extractAs(playerSource(deniedPlayer)))
                .as("STOP-6: a denied player must not extract through a fully-granted Storage Bus")
                .isZero();
        assertThat(f.insertAs(playerSource(deniedPlayer)))
                .as("STOP-6: ... nor inject through it")
                .isZero();
        assertThat(f.busSawCalls)
                .as("the denied caller must never even reach the mounted Storage Bus inventory - the check happens "
                        + "at the network boundary, in front of the mount")
                .isZero();
    }

    @Test
    void allowedActorStillWorksThroughStorageBus() {
        var f = new Fixture();
        var allowedPlayer = UUID.randomUUID();
        f.domain.grant(PrincipalId.ofPlayer(allowedPlayer),
                Set.of(SecurityPermissions.INJECT, SecurityPermissions.EXTRACT));

        assertThat(f.insertAs(playerSource(allowedPlayer))).isEqualTo(4L);
        assertThat(f.extractAs(playerSource(allowedPlayer))).isEqualTo(4L);
        assertThat(f.busSawCalls).as("the allowed caller does reach the mounted inventory").isGreaterThan(0);
        assertThat(f.busSawSources)
                .as("brief §23 / STOP-5: the mount must be handed the ORIGINAL caller's principal, never the "
                        + "pass-through component's own identity")
                .containsOnly(PrincipalId.ofPlayer(allowedPlayer));
    }

    /**
     * The device-actor direction of the same rule: an authorized <em>machine</em> (e.g. the Round 25 Import Bus) also
     * keeps its own identity all the way down to the mount. The Storage Bus does not overwrite it either.
     */
    @Test
    void deviceCallerIdentityIsPreservedThroughStorageBus() {
        var f = new Fixture();
        var importBus = UUID.randomUUID();
        f.domain.grant(PrincipalId.ofDevice(importBus), Set.of(SecurityPermissions.INJECT));

        assertThat(f.insertAs(deviceSource(f.mgn, importBus))).isEqualTo(4L);
        assertThat(f.busSawSources).containsOnly(PrincipalId.ofDevice(importBus));
    }

    // ------------------------------------------------------------------
    // §11 - ME Chest
    // ------------------------------------------------------------------

    @Test
    void playerWithoutExtractCannotReadThroughDeviceGrantedMeChest() {
        var f = new Fixture();
        var deniedPlayer = UUID.randomUUID();
        var meChestDevice = UUID.randomUUID();

        // The ME Chest's own hypothetical device principal is given full rights.
        f.domain.grant(PrincipalId.ofDevice(meChestDevice), Set.of(SecurityPermissions.values()));
        // The player is given INJECT but explicitly NOT EXTRACT, to show the two are decided independently.
        f.domain.grant(PrincipalId.ofPlayer(deniedPlayer), Set.of(SecurityPermissions.INJECT));

        assertThat(f.insertAs(playerSource(deniedPlayer))).as("INJECT is granted, so this works").isEqualTo(4L);
        assertThat(f.extractAs(playerSource(deniedPlayer)))
                .as("STOP-7: EXTRACT is not granted, and a fully-granted ME Chest device must not supply it")
                .isZero();
    }

    // ------------------------------------------------------------------
    // §22 - player and device grants are separate universes
    // ------------------------------------------------------------------

    @Test
    void playerGrantDoesNotOpenAnAutonomousMachineAndViceVersa() {
        var f = new Fixture();
        var player = UUID.randomUUID();
        var device = UUID.randomUUID();

        f.domain.grant(PrincipalId.ofPlayer(player), Set.of(SecurityPermissions.EXTRACT));
        assertThat(f.extractAs(playerSource(player))).isEqualTo(0L); // nothing stored yet
        assertThat(f.security.hasPermission(playerSource(player), SecurityPermissions.EXTRACT)).isTrue();
        assertThat(f.security.hasPermission(deviceSource(f.mgn, device), SecurityPermissions.EXTRACT))
                .as("PLAYER_PERMISSION != DEVICE_PERMISSION")
                .isFalse();

        f.domain.grant(PrincipalId.ofDevice(device), Set.of(SecurityPermissions.INJECT));
        assertThat(f.security.hasPermission(deviceSource(f.mgn, device), SecurityPermissions.INJECT)).isTrue();
        assertThat(f.security.hasPermission(playerSource(player), SecurityPermissions.INJECT))
                .as("a device's INJECT must not leak to a player")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private IActionSource playerSource(UUID playerId) {
        var player = org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        org.mockito.Mockito.when(player.getUUID()).thenReturn(playerId);
        return IActionSource.ofPlayer(player);
    }

    /**
     * Builds the source of a real device. Round 27 resolves the device id from the owner of the node the source hands
     * back, so the host must own a node of its own rather than borrow the fixture's - borrowing was the
     * unauthenticated-claim shape FINDING-3 describes.
     */
    private IActionSource deviceSource(ManagedGridNode unusedFixtureNode, UUID deviceId) {
        // Round 32: routed through the registry, because a machine no longer authenticates itself.
        return RegisteredTestDevice.source(deviceRegistry, level, deviceId);
    }

    private final class DeviceHost
            implements
            appeng.api.networking.security.IActionHost,
            appeng.api.networking.security.IDeviceIdentity {
        private final UUID deviceId;
        private final ManagedGridNode ownNode;

        private DeviceHost(UUID deviceId) {
            this.deviceId = deviceId;
            this.ownNode = new ManagedGridNode(this, listener);
            this.ownNode.create(level, null);
        }

        @Override
        public appeng.api.networking.IGridNode getActionableNode() {
            return ownNode.getNode();
        }

        @Override
        public UUID getDeviceId() {
            return deviceId;
        }
    }

    private final class Fixture {
        private final ManagedGridNode mgn;
        private final SecurityService security;
        private final SecurityDomainRegistry registry = SecurityDomainRegistry.createForTesting();
        private final SecurityDomain domain;
        private final MEStorage securedStorage;

        private int busSawCalls = 0;
        private final java.util.Set<PrincipalId> busSawSources = new java.util.HashSet<>();

        private Fixture() {
            mgn = new ManagedGridNode(owner, listener);
            mgn.create(level, null);
            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            // Round 32: device principals now come from the registry, not from the machine.
            security.setDeviceRegistryForTesting(deviceRegistry);
            domain = registry.getDomain(security.bootstrapDomain(UUID.randomUUID()));

            // The real production topology: a real NetworkStorage with the pass-through component mounted INSIDE it,
            // and the SecurityAwareMEStorage decorator in front of the whole thing - i.e. in front of the mount.
            var network = new NetworkStorage();
            network.mount(0, new PassThroughMount());
            securedStorage = new SecurityAwareMEStorage(network, security);
        }

        private long insertAs(IActionSource source) {
            return securedStorage.insert(item(), 4, Actionable.MODULATE, source);
        }

        private long extractAs(IActionSource source) {
            return securedStorage.extract(item(), 4, Actionable.MODULATE, source);
        }

        /**
         * Stands in for {@code StorageBusPart.StorageBusInventory} / {@code MEChestBlockEntity}'s mounted cell: a plain
         * {@link MEStorage} mounted into the network, which records which principal the network handed it.
         */
        private final class PassThroughMount implements MEStorage {
            private final KeyCounter contents = new KeyCounter();

            private void record(IActionSource source) {
                busSawCalls++;
                source.player().ifPresent(p -> busSawSources.add(PrincipalId.ofPlayer(p.getUUID())));
                // Round 32: read the identity the way SecurityService reads it - off the node's owner, not off the
                // wrapper handed to ofMachine(). Production has both shapes (a part passes `this`, an Interface passes
                // a lambda), so inspecting the wrapper only ever saw half of them.
                source.machine().ifPresent(m -> {
                    var node = m.getActionableNode();
                    var owner = node == null ? null : node.getOwner();
                    if (owner instanceof appeng.api.networking.security.IDeviceIdentity id) {
                        busSawSources.add(PrincipalId.ofDevice(id.getDeviceId()));
                    }
                });
            }

            @Override
            public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                record(source);
                if (mode == Actionable.MODULATE) {
                    contents.add(what, amount);
                }
                return amount;
            }

            @Override
            public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                record(source);
                var extracted = Math.min(contents.get(what), amount);
                if (mode == Actionable.MODULATE && extracted > 0) {
                    contents.remove(what, extracted);
                }
                return extracted;
            }

            @Override
            public void getAvailableStacks(KeyCounter out) {
                out.addAll(contents);
            }

            @Override
            public Component getDescription() {
                return Component.literal("Round 25 pass-through mount");
            }
        }
    }
}
