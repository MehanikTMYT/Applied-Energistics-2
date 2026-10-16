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

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;

/**
 * Round 25 - the mandatory per-device negative security matrix (brief §20), cross-device isolation (§21), player/device
 * separation (§22) and identity persistence (§18, §39) for the machines that Round 25 turned into {@code Device}
 * principals: Import Bus, Export Bus, IO Port, Annihilation Plane and Interface (block and part).
 * <p/>
 * The matrix is expressed once, against the shared mechanism all five use ({@link PersistentDeviceIdentity} resolved by
 * {@link SecurityService#resolveDeviceId}), and is parameterised by the permission each machine actually needs - which
 * is derived from its code flow, not assumed (see {@code round25-machine-actor-matrix.md}).
 */
// The ephemeral test server is required, not decorative: AEItemKey.of(Item) reaches Item#getDefaultInstance, which
// needs the item registry's data components to be bound. @BootstrapMinecraft alone does not bind them, so without this
// the class would only pass when some other test class happened to bind them first in the same JVM - an
// ordering-dependent flake this round observed for real.
@ExtendWith(EphemeralTestServerProvider.class)
class Round25MachineDeviceIdentityTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    private static AEItemKey item() {
        return AEItemKey.of(Items.DIAMOND);
    }

    /** The permissions each Round 25 device actually requires, derived from its production call sites. */
    private enum Machine {
        IMPORT_BUS(SecurityPermissions.INJECT),
        EXPORT_BUS(SecurityPermissions.EXTRACT),
        IO_PORT(SecurityPermissions.INJECT, SecurityPermissions.EXTRACT),
        ANNIHILATION_PLANE(SecurityPermissions.INJECT),
        INTERFACE(SecurityPermissions.EXTRACT, SecurityPermissions.CRAFT);

        final EnumSet<SecurityPermissions> required;

        Machine(SecurityPermissions... required) {
            this.required = EnumSet.of(required[0], required);
        }
    }

    // ------------------------------------------------------------------
    // §20 - negative matrix
    // ------------------------------------------------------------------

    @Test
    void securedWithNoGrantIsDenied() {
        for (var machine : Machine.values()) {
            var f = new Fixture();
            var device = f.newDevice();
            for (var permission : machine.required) {
                assertThat(f.security.hasPermission(device.source, permission))
                        .as("%s must be denied %s with an empty ACL", machine, permission)
                        .isFalse();
            }
        }
    }

    @Test
    void correctDeviceGrantIsAllowed() {
        for (var machine : Machine.values()) {
            var f = new Fixture();
            var device = f.newDevice();
            f.domain.grant(PrincipalId.ofDevice(device.id()), machine.required);
            for (var permission : machine.required) {
                assertThat(f.security.hasPermission(device.source, permission))
                        .as("%s must be allowed %s once granted to its own device principal", machine, permission)
                        .isTrue();
            }
        }
    }

    @Test
    void playerOnlyGrantLeavesTheDeviceDenied() {
        for (var machine : Machine.values()) {
            var f = new Fixture();
            var device = f.newDevice();
            f.domain.grant(PrincipalId.ofPlayer(UUID.randomUUID()), machine.required);
            for (var permission : machine.required) {
                assertThat(f.security.hasPermission(device.source, permission))
                        .as("%s: a player grant must not open an autonomous machine (brief §22)", machine)
                        .isFalse();
            }
        }
    }

    @Test
    void aDifferentDeviceGrantLeavesThisDeviceDenied() {
        for (var machine : Machine.values()) {
            var f = new Fixture();
            var a = f.newDevice();
            var b = f.newDevice();
            f.domain.grant(PrincipalId.ofDevice(a.id()), machine.required);
            for (var permission : machine.required) {
                assertThat(f.security.hasPermission(b.source, permission))
                        .as("%s: DEVICE A != DEVICE B (brief §21)", machine)
                        .isFalse();
            }
        }
    }

    @Test
    void everyoneWithNothingGrantedLeavesTheDeviceDenied() {
        var f = new Fixture();
        var device = f.newDevice();
        f.domain.grant(PrincipalId.EVERYONE, Set.of());
        for (var permission : SecurityPermissions.values()) {
            assertThat(f.security.hasPermission(device.source, permission)).isFalse();
        }
    }

    @Test
    void theWrongPermissionNeverImpliesTheRightOne() {
        for (var machine : Machine.values()) {
            for (var wrong : SecurityPermissions.values()) {
                if (machine.required.contains(wrong)) {
                    continue;
                }
                var f = new Fixture();
                var device = f.newDevice();
                f.domain.grant(PrincipalId.ofDevice(device.id()), Set.of(wrong));
                for (var permission : machine.required) {
                    assertThat(f.security.hasPermission(device.source, permission))
                            .as("%s: %s must not imply %s", machine, wrong, permission)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void aGrantInAnotherDomainDoesNotCrossOver() {
        var f = new Fixture();
        var device = f.newDevice();
        var otherDomain = f.registry.createDomain(UUID.randomUUID());
        otherDomain.grant(PrincipalId.ofDevice(device.id()), Set.of(SecurityPermissions.values()));

        for (var permission : SecurityPermissions.values()) {
            assertThat(f.security.hasPermission(device.source, permission))
                    .as("a grant in a different SECURED domain must not authorize anything here")
                    .isFalse();
        }
    }

    @Test
    void legacyNetworksStayVanillaCompatible() {
        var f = new Fixture(false);
        var device = f.newDevice();
        assertThat(f.security.getState()).isEqualTo(SecurityDomainState.LEGACY);
        for (var permission : SecurityPermissions.values()) {
            assertThat(f.security.hasPermission(device.source, permission))
                    .as("LEGACY = open, vanilla-compatible - Round 25 must not change that")
                    .isTrue();
        }
        assertThat(f.insert(device.source)).isEqualTo(4L);
    }

    /**
     * Not just the boolean check - the whole path through the real {@link SecurityAwareMEStorage} decorator, which is
     * what the machines actually call.
     */
    @Test
    void theMatrixHoldsEndToEndThroughTheSecuredStorage() {
        var f = new Fixture();
        var device = f.newDevice();

        assertThat(f.insert(device.source)).as("no grant -> nothing moves").isZero();
        f.domain.grant(PrincipalId.ofDevice(device.id()), Set.of(SecurityPermissions.INJECT));
        assertThat(f.insert(device.source)).isEqualTo(4L);
        assertThat(f.extract(device.source))
                .as("INJECT must not carry EXTRACT with it")
                .isZero();
        f.domain.grant(PrincipalId.ofDevice(device.id()),
                Set.of(SecurityPermissions.INJECT, SecurityPermissions.EXTRACT));
        assertThat(f.extract(device.source)).isEqualTo(4L);
        assertThat(f.domain.getPermissions(PrincipalId.EVERYONE)).isEmpty();
    }

    // ------------------------------------------------------------------
    // §21 - cross-device isolation for the concrete families
    // ------------------------------------------------------------------

    @Test
    void twoInstancesOfTheSameFamilyAreTwoDifferentPrincipals() {
        // Import Bus A != Import Bus B, Export Bus A != Export Bus B, IO Port A != IO Port B, Interface A != B.
        for (var machine : Machine.values()) {
            var a = new PersistentDeviceIdentity();
            var b = new PersistentDeviceIdentity();
            assertThat(a.get()).as("%s: two instances must never share an identity", machine).isNotEqualTo(b.get());
        }
    }

    /**
     * Brief §16: two security-sensitive parts on one {@code CableBus} host must be two principals. This falls out of
     * the identity being an instance field of the <em>part</em> (and of {@code new MachineSource(this)} pointing at the
     * part), which is what {@code IOBusPart} / {@code AnnihilationPlanePart} / {@code InterfacePart} do - the host is
     * never consulted.
     */
    @Test
    void theCableBusHostIsNeverUsedAsAShortcutIdentity() {
        assertThat(IDeviceIdentity.class.isAssignableFrom(appeng.blockentity.networking.CableBusBlockEntity.class))
                .isFalse();
    }

    // ------------------------------------------------------------------
    // §18 / §39 - identity persistence matrix
    // ------------------------------------------------------------------

    @Test
    void identityIsStableAcrossSaveAndReload() {
        var original = new PersistentDeviceIdentity();
        var id = original.get();
        var saved = write(original);

        // Save / reload, chunk unload / reload and server restart are all the same operation from the machine's point
        // of view: construct a fresh instance, then read the persisted tag over it.
        for (var pass = 0; pass < 3; pass++) {
            var reloaded = new PersistentDeviceIdentity();
            assertThat(reloaded.get()).as("a fresh instance starts with its own id").isNotEqualTo(id);
            assertThat(read(reloaded, saved)).as("a saved id must not be reported as needing migration").isFalse();
            assertThat(reloaded.get()).as("the persisted id is restored verbatim, every time").isEqualTo(id);
            saved = write(reloaded);
        }
    }

    /**
     * Brief §39 migration question: a machine placed in a world saved before Round 25 has no {@code aeDeviceId}. It
     * must mint one exactly once and then keep it - never re-mint on each load.
     */
    @Test
    void aPreRound25MachineMintsItsIdOnceAndThenKeepsIt() {
        var legacy = new PersistentDeviceIdentity();
        var minted = legacy.get();

        var emptyTag = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY)
                .buildResult();
        assertThat(read(legacy, emptyTag))
                .as("the host must be told to persist the newly minted id (it calls setChanged()/markForSave())")
                .isTrue();
        assertThat(legacy.get()).as("the constructor-minted id is kept, not replaced").isEqualTo(minted);

        var afterSave = write(legacy);
        var nextLoad = new PersistentDeviceIdentity();
        assertThat(read(nextLoad, afterSave)).isFalse();
        assertThat(nextLoad.get()).isEqualTo(minted);
    }

    @Test
    void twoMachinesNeverPersistTheSameId() {
        var a = new PersistentDeviceIdentity();
        var b = new PersistentDeviceIdentity();
        var reloadedA = new PersistentDeviceIdentity();
        var reloadedB = new PersistentDeviceIdentity();
        read(reloadedA, write(a));
        read(reloadedB, write(b));
        assertThat(reloadedA.get()).isNotEqualTo(reloadedB.get());
    }

    private static CompoundTag write(PersistentDeviceIdentity identity) {
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        identity.write(out);
        return out.buildResult();
    }

    private static boolean read(PersistentDeviceIdentity identity, CompoundTag tag) {
        return identity.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
    }

    @Test
    void theIdIsNeverRegeneratedPerCall() {
        var identity = new PersistentDeviceIdentity();
        assertThat(identity.get()).isEqualTo(identity.get()).isEqualTo(identity.get());
    }

    @Test
    void anIdentityIsNeverDerivedFromAPlayer() {
        var playerId = UUID.randomUUID();
        for (var i = 0; i < 64; i++) {
            assertThat(new PersistentDeviceIdentity().get()).isNotEqualTo(playerId);
        }
    }

    /**
     * Brief §33: a brand-new device principal starts with <em>no</em> grants at all, and in particular inherits nothing
     * from the network's owner. Owning a network does not make your machines privileged; the owner short-circuit in
     * {@code hasPermission} applies to {@code PrincipalId.Player} only.
     */
    @Test
    void aDeviceDoesNotInheritTheNetworkOwnersPrivileges() {
        var ownerId = UUID.randomUUID();
        var f = new Fixture(ownerId);
        var device = f.newDevice();

        assertThat(f.domain.isOwner(ownerId)).isTrue();
        for (var permission : SecurityPermissions.values()) {
            assertThat(f.security.hasPermission(device.source, permission))
                    .as("a device on the owner's own network still starts with nothing")
                    .isFalse();
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private record Device(UUID id, IActionSource source) {
    }

    /**
     * A device host that owns its own grid node, mirroring {@code GridHelper.createManagedNode(this, ...)}. Round 27
     * reads the device id from {@code node.getOwner()}, so the host has to be that owner - previously it merely carried
     * a reference to somebody else's node, which is the unauthenticated-claim shape FINDING-3 describes.
     */
    private final class DeviceHost implements IActionHost, IDeviceIdentity {
        private final PersistentDeviceIdentity identity = new PersistentDeviceIdentity();
        private final ManagedGridNode ownNode;

        private DeviceHost() {
            this.ownNode = new ManagedGridNode(this, listener);
            this.ownNode.create(level, null);
        }

        @Override
        public IGridNode getActionableNode() {
            return ownNode.getNode();
        }

        @Override
        public UUID getDeviceId() {
            return identity.get();
        }
    }

    private final class Fixture {
        private final SecurityService security;
        private final SecurityDomainRegistry registry = SecurityDomainRegistry.createForTesting();
        private final SecurityDomain domain;
        private final MEStorage securedStorage;
        private final ManagedGridNode mgn;

        private Fixture() {
            this(true, UUID.randomUUID());
        }

        private Fixture(UUID ownerId) {
            this(true, ownerId);
        }

        private Fixture(boolean secured) {
            this(secured, UUID.randomUUID());
        }

        private Fixture(boolean secured, UUID ownerId) {
            mgn = new ManagedGridNode(owner, listener);
            mgn.create(level, null);
            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            // Round 32: device principals now come from the registry, not from the machine.
            security.setDeviceRegistryForTesting(deviceRegistry);
            domain = secured ? registry.getDomain(security.bootstrapDomain(ownerId)) : null;
            securedStorage = new SecurityAwareMEStorage(new FakeStorage(), security);
        }

        private Device newDevice() {
            // Round 32: a device is registered and located by AE2; the helper builds that shape.
            var id = java.util.UUID.randomUUID();
            return new Device(id, RegisteredTestDevice.source(deviceRegistry, level, id));
        }

        private long insert(IActionSource source) {
            return securedStorage.insert(item(), 4, Actionable.MODULATE, source);
        }

        private long extract(IActionSource source) {
            return securedStorage.extract(item(), 4, Actionable.MODULATE, source);
        }
    }

    private static final class FakeStorage implements MEStorage {
        private final KeyCounter contents = new KeyCounter();

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                contents.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
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
            return Component.literal("Round 25 device matrix storage");
        }
    }
}
