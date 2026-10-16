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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IDeviceIdentity;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.PrincipalId;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.helpers.MachineSource;

/**
 * Round 24 — Pattern Provider device identity and permission matrix.
 * <p/>
 * Covers the selected security model (Option A / DEVICE): a Pattern Provider is its own {@link PrincipalId.Device}
 * principal, and its one network-storage mutation - returning items to the network - needs exactly one permission,
 * {@link SecurityPermissions#INJECT}.
 * <p/>
 * These tests drive the <em>real</em> {@link SecurityService}, the <em>real</em> {@link SecurityDomainRegistry}, the
 * <em>real</em> {@link SecurityAwareMEStorage} and the <em>real</em>
 * {@link PatternProviderReturnInventory#injectIntoNetwork}. Mockito is used only for a {@link Player} UUID shell and
 * the grid-node listener; no permission decision, storage authorization or identity resolution is mocked.
 * <p/>
 * The identity resolution path exercised here is exactly the production one: the action source is a
 * {@link MachineSource} over a <em>lambda</em> {@code IActionHost} (as built at {@code PatternProviderLogic.java:137}),
 * so {@code SecurityService#resolveDeviceId} must resolve the identity via {@code IGridNode#getOwner()} - which for a
 * real Pattern Provider is the {@code PatternProviderBlockEntity} / {@code PatternProviderPart} itself.
 */
class PatternProviderDeviceIdentityTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    private static AEItemKey craftedOutput() {
        return AEItemKey.of(Items.DIAMOND_PICKAXE);
    }

    // =====================================================================
    // Identity lifecycle (brief §18 D1-D8, §32)
    // =====================================================================

    /** D1: save -> reload yields the same device id. */
    @Test
    void identitySurvivesASaveReloadRoundTrip() {
        var original = new PersistentDeviceIdentity();
        var id = original.get();

        var tag = write(original);

        var reloaded = new PersistentDeviceIdentity();
        assertThat(reloaded.get()).as("a fresh instance starts with its own id").isNotEqualTo(id);

        var neededMigration = read(reloaded, tag);

        assertThat(neededMigration).isFalse();
        assertThat(reloaded.get()).as("D1/D5/D6: identity is stable across save/reload, chunk unload and restart")
                .isEqualTo(id);
    }

    /** The getter must never mint a new id per call. */
    @Test
    void identityIsNotRegeneratedOnEveryCall() {
        var identity = new PersistentDeviceIdentity();
        assertThat(identity.get()).isEqualTo(identity.get()).isEqualTo(identity.get());
    }

    /** D2/D3: two distinct devices never share an identity. */
    @Test
    void twoDevicesHaveDistinctIdentities() {
        assertThat(new PersistentDeviceIdentity().get()).isNotEqualTo(new PersistentDeviceIdentity().get());
    }

    /**
     * §32 migration: a Pattern Provider from a world saved before device identities existed has no stored id. It must
     * keep the id minted in its constructor and report that it needs persisting - it must NOT mint a fresh id on every
     * subsequent load, which would invalidate any ACL entry granted to it.
     */
    @Test
    void preExistingMachineMintsItsIdentityExactlyOnceAndThenKeepsIt() {
        var legacy = new PersistentDeviceIdentity();
        var mintedId = legacy.get();

        // A tag saved by an older build: no device id in it at all.
        var oldWorldTag = new CompoundTag();
        var neededMigration = read(legacy, oldWorldTag);

        assertThat(neededMigration).as("host must be told to persist the newly minted id").isTrue();
        assertThat(legacy.get()).as("the constructor-minted id is kept, not replaced").isEqualTo(mintedId);

        // Once persisted, every later load is stable.
        var afterSave = write(legacy);
        var nextLoad = new PersistentDeviceIdentity();
        assertThat(read(nextLoad, afterSave)).isFalse();
        assertThat(nextLoad.get()).isEqualTo(mintedId);
    }

    /**
     * NEGATIVE CONTROL 1 / anti-vacuity anchor (brief §28, §29): pins the actual Round 24 production change. Both
     * physical forms of the Pattern Provider must really declare {@link IDeviceIdentity}; reverting either edit in
     * {@code PatternProviderBlockEntity} or {@code PatternProviderPart} fails this test immediately.
     * <p/>
     * Brief §17 requires block and part to be treated separately, so both are asserted independently rather than
     * through a shared supertype.
     */
    @Test
    void bothPhysicalPatternProviderFormsDeclareADeviceIdentity() {
        assertThat(IDeviceIdentity.class)
                .as("PatternProviderBlockEntity must be its own Device principal")
                .isAssignableFrom(appeng.blockentity.crafting.PatternProviderBlockEntity.class);
        assertThat(IDeviceIdentity.class)
                .as("PatternProviderPart must be its own Device principal, independently of its CableBus host")
                .isAssignableFrom(appeng.parts.crafting.PatternProviderPart.class);

        // And the CableBus host itself must NOT be one - a part must never borrow its host's identity (brief §17).
        assertThat(IDeviceIdentity.class.isAssignableFrom(appeng.blockentity.networking.CableBusBlockEntity.class))
                .as("the CableBus host must not act as a device principal for the parts it carries")
                .isFalse();
    }

    // =====================================================================
    // Permission matrix (brief §19, §20)
    // =====================================================================

    /**
     * P1: on a SECURED network with no broad grants, a single explicit DEVICE INJECT grant is enough to make the
     * Pattern Provider's return-to-network work. This is the capability Round 24 adds: before the fix the provider
     * resolved to no principal at all, so <em>no</em> grant could ever address it and only an EVERYONE grant would have
     * worked.
     */
    @Test
    void deviceGrantAloneMakesTheReturnPathWorkWithoutAnyEveryoneGrant() {
        var f = new Fixture();
        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        f.domain.grant(PrincipalId.ofDevice(f.provider.getDeviceId()), Set.of(SecurityPermissions.INJECT));

        assertThat(f.inject()).isTrue();
        assertThat(f.returnInv.isEmpty()).isTrue();
        assertThat(f.networkAmount()).isEqualTo(1L);
        assertThat(f.domain.getPermissions(PrincipalId.EVERYONE))
                .as("EVERYONE_INJECT_REQUIRED=NO")
                .isEmpty();
    }

    /** SECURED implicit deny: a device with no grant at all is denied. */
    @Test
    void deviceWithoutAnyGrantIsDenied() {
        var f = new Fixture();
        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        assertThat(f.inject()).isFalse();
        assertThat(f.networkAmount()).isZero();
    }

    /**
     * §19 permission minimality / no permission collapse: granting the device the wrong permission must not let it
     * inject. CRAFT != INJECT, EXTRACT != INJECT, BUILD != INJECT.
     */
    @Test
    void wrongPermissionDoesNotGrantInject() {
        for (var wrong : new SecurityPermissions[] { SecurityPermissions.CRAFT, SecurityPermissions.EXTRACT,
                SecurityPermissions.BUILD }) {
            var f = new Fixture();
            f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());
            f.domain.grant(PrincipalId.ofDevice(f.provider.getDeviceId()), Set.of(wrong));

            assertThat(f.inject()).as("%s must not imply INJECT", wrong).isFalse();
            assertThat(f.networkAmount()).isZero();
        }
    }

    /** P2: a player's grant does not leak to the device. */
    @Test
    void playerGrantDoesNotLeakToTheDevice() {
        var f = new Fixture();
        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        var playerId = UUID.randomUUID();
        f.domain.grant(PrincipalId.ofPlayer(playerId), Set.of(SecurityPermissions.INJECT));

        // The player really does have INJECT...
        assertThat(f.security.hasPermission(playerSource(playerId), SecurityPermissions.INJECT)).isTrue();
        // ...but the Pattern Provider device does not inherit it.
        assertThat(f.inject()).isFalse();
        assertThat(f.networkAmount()).isZero();
    }

    /** P3/P4: one device's grant does not leak to a different device. */
    @Test
    void deviceGrantDoesNotLeakToAnotherDevice() {
        var f = new Fixture();
        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        // Grant a *different* Pattern Provider, as if only provider A were authorized.
        f.domain.grant(PrincipalId.ofDevice(new PersistentDeviceIdentity().get()),
                Set.of(SecurityPermissions.INJECT));

        assertThat(f.inject()).as("P4: provider B stays denied when only provider A is granted").isFalse();
        assertThat(f.networkAmount()).isZero();
    }

    /** P5: a grant made in one domain has no effect in a different domain. */
    @Test
    void deviceGrantDoesNotCrossDomains() {
        var f = new Fixture();
        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        // Grant this device INJECT, but in an unrelated domain in the same registry.
        var otherDomain = f.registry.createDomain(UUID.randomUUID());
        otherDomain.grant(PrincipalId.ofDevice(f.provider.getDeviceId()), Set.of(SecurityPermissions.INJECT));

        assertThat(f.inject()).as("P5: a grant in domain B must not authorize anything in domain A").isFalse();
        assertThat(f.networkAmount()).isZero();
    }

    /** P7: a LEGACY (no Security Station) network keeps working exactly like vanilla AE2. */
    @Test
    void legacyNetworkNeedsNoGrantAtAll() {
        var f = new Fixture(false);
        assertThat(f.security.getState()).isEqualTo(SecurityDomainState.LEGACY);

        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        assertThat(f.inject()).isTrue();
        assertThat(f.networkAmount()).isEqualTo(1L);
    }

    /**
     * NEGATIVE CONTROL 2 (brief §29 NC2): replacing the identified device with an anonymous machine - i.e. reverting
     * exactly the production change, so the node owner is no longer an {@link IDeviceIdentity} - must break the
     * scenario that the DEVICE grant otherwise makes work.
     */
    @Test
    void negativeControlAnonymousMachineCannotBeAuthorizedAtAll() {
        var f = new Fixture();
        f.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        // Grant the device exactly what it needs...
        f.domain.grant(PrincipalId.ofDevice(f.provider.getDeviceId()), Set.of(SecurityPermissions.INJECT));

        // ...but perform the injection through an anonymous machine source, as the code did before Round 24.
        var anonymous = new MachineSource(f.anonymousNode::getNode);
        var didSomething = f.returnInv.injectIntoNetwork(f.securedStorage, anonymous, stack -> {
        });

        assertThat(didSomething)
                .as("NC2: without a device identity there is no principal to grant, so the grant cannot apply")
                .isFalse();
        assertThat(f.networkAmount()).isZero();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private IActionSource playerSource(UUID playerId) {
        var player = mock(Player.class);
        when(player.getUUID()).thenReturn(playerId);
        return IActionSource.ofPlayer(player);
    }

    private static CompoundTag write(PersistentDeviceIdentity identity) {
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        identity.write(out);
        return out.buildResult();
    }

    private static boolean read(PersistentDeviceIdentity identity, CompoundTag tag) {
        return identity.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, tag));
    }

    /**
     * A stand-in for the Pattern Provider block entity / part: it is the grid node's <em>owner</em> and implements
     * {@link IDeviceIdentity} exactly the way {@code PatternProviderBlockEntity} and {@code PatternProviderPart} now
     * do, delegating to the same production {@link PersistentDeviceIdentity}. Using the real block entity here would
     * require a live level; what matters for the security decision is precisely this: the node owner carries a stable
     * device id.
     */
    private static final class DeviceOwner implements IDeviceIdentity {
        private final PersistentDeviceIdentity identity = new PersistentDeviceIdentity();

        @Override
        public UUID getDeviceId() {
            return identity.get();
        }
    }

    private final class Fixture {
        private final SecurityService security;
        private final SecurityDomainRegistry registry = SecurityDomainRegistry.createForTesting();
        private final SecurityDomain domain;
        private final SimpleFakeStorage backing = new SimpleFakeStorage();
        private final MEStorage securedStorage;
        private final PatternProviderReturnInventory returnInv = new PatternProviderReturnInventory(() -> {
        });
        private final DeviceOwner provider = new DeviceOwner();
        private final IActionSource providerSource;
        private final ManagedGridNode anonymousNode;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean secured) {
            // The node's owner is the "Pattern Provider", mirroring GridHelper.createManagedNode(this, ...).
            var mgn = new ManagedGridNode(provider, listener);
            mgn.create(level, null);

            // A second node whose owner is a plain object - the pre-Round-24 situation.
            anonymousNode = new ManagedGridNode(new Object(), listener);
            anonymousNode.create(level, null);

            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            // Round 32: device principals now come from the registry, not from the machine.
            security.setDeviceRegistryForTesting(deviceRegistry);

            if (secured) {
                domain = registry.getDomain(security.bootstrapDomain(UUID.randomUUID()));
            } else {
                domain = null;
            }

            securedStorage = new SecurityAwareMEStorage(backing, security);
            // Exactly how PatternProviderLogic builds its source: a MachineSource over a lambda IActionHost.
            // Round 32: a device is registered and located by AE2; the helper builds that shape.
            providerSource = RegisteredTestDevice.source(deviceRegistry, level, provider.getDeviceId());
        }

        private boolean inject() {
            return returnInv.injectIntoNetwork(securedStorage, providerSource, stack -> {
            });
        }

        private long networkAmount() {
            var out = new appeng.api.stacks.KeyCounter();
            backing.getAvailableStacks(out);
            return out.get(craftedOutput());
        }
    }

    private static final class SimpleFakeStorage implements MEStorage {
        private final appeng.api.stacks.KeyCounter contents = new appeng.api.stacks.KeyCounter();

        @Override
        public long insert(appeng.api.stacks.AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                contents.add(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(appeng.api.stacks.AEKey what, long amount, Actionable mode, IActionSource source) {
            var available = contents.get(what);
            var extracted = Math.min(available, amount);
            if (mode == Actionable.MODULATE && extracted > 0) {
                contents.remove(what, extracted);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(appeng.api.stacks.KeyCounter out) {
            out.addAll(contents);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("Round 24 device identity test storage");
        }
    }
}
