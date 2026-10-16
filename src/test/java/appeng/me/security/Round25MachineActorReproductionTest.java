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

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
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
import appeng.me.helpers.MachineSource;

/**
 * Round 25 - failing-first reproduction for the remaining AE2 machine actors (brief §25).
 * <p/>
 * Round 24 closed FINDING-1 for the Pattern Provider only. Its own archaeology noted that the rest of AE2's autonomous
 * machines still resolve to a {@code null} principal. Round 25 rebuilt that set from the code rather than from the
 * manifest's prose: the complete list of <em>production</em> {@code new MachineSource(...)} construction sites in
 * {@code src/main} (excluding {@code appeng.server.testplots} / {@code appeng.server.testworld} fixtures and the
 * {@code IActionSource#ofMachine} factory itself) is exactly seven:
 *
 * <pre>
 * IOBusPart.java:76                 -> Import Bus AND Export Bus (one shared base class)
 * IOPortBlockEntity.java:107        -> IO Port
 * AnnihilationPlanePart.java:64     -> Annihilation Plane
 * InterfaceLogic.java:111 / :113    -> Interface (block + part)
 * StorageBusPart.java:127           -> Storage Bus         (field is never read - see Round25PassThroughLaunderingTest)
 * MEChestBlockEntity.java:107       -> ME Chest            (only ever aimed at its own local cell)
 * CraftingCPUCluster.java:144       -> Crafting CPU        (propagated actor; see the same test)
 * PatternProviderLogic.java:137     -> Pattern Provider    (already closed by Round 24)
 * </pre>
 *
 * <b>Recorded failure coordinates before the Round 25 fix (brief §25 evidence format):</b>
 *
 * <pre>
 * COMPONENT                   = Import Bus / Export Bus / IO Port / Annihilation Plane / Interface
 * CURRENT_SOURCE              = new MachineSource(this)            (IOBusPart, IOPort, AnnihilationPlane)
 *                               new MachineSource(mainNode::getNode) (InterfaceLogic)
 * CURRENT_MACHINE_RUNTIME_TYPE= ImportBusPart / ExportBusPart / IOPortBlockEntity / AnnihilationPlanePart
 *                               / InterfaceBlockEntity / InterfacePart - none of which implemented IDeviceIdentity,
 *                               and whose grid node owner did not either
 * RESOLVED_PRINCIPAL          = null   (SecurityService#resolveDeviceId returns null on both of its branches)
 * REQUIRED_PERMISSION         = INJECT (Import Bus, Annihilation Plane), EXTRACT (Export Bus),
 *                               INJECT+EXTRACT (IO Port), EXTRACT+CRAFT (Interface)
 * EXPECTED                    = a device grant to that specific machine authorizes it, and nothing else does
 * ACTUAL                      = hasPermission() fell through to the "unidentified machine" branch and consulted only
 *                               PrincipalId.EVERYONE, so on a SECURED network with an empty ACL every one of these
 *                               machines was denied, and the only way to make them work was a blanket EVERYONE grant -
 *                               which brief §42 STOP-2 forbids.
 * </pre>
 *
 * Nothing about the security decision is mocked here: a real {@link SecurityService} on a real grid, a real
 * {@link SecurityDomainRegistry}, a real {@link SecurityAwareMEStorage} and a real {@link MachineSource}.
 */
// The ephemeral test server is required, not decorative: AEItemKey.of(Item) reaches Item#getDefaultInstance, which
// needs the item registry's data components to be bound. @BootstrapMinecraft alone does not bind them, so without this
// the class would only pass when some other test class happened to bind them first in the same JVM - an
// ordering-dependent flake this round observed for real.
@ExtendWith(EphemeralTestServerProvider.class)
class Round25MachineActorReproductionTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    private static AEItemKey item() {
        return AEItemKey.of(Items.DIAMOND);
    }

    // ------------------------------------------------------------------
    // 1. The structural half: these are the production classes that had to change.
    // Before the Round 25 fix every one of these assertions failed.
    // ------------------------------------------------------------------

    @Test
    void everyAutonomousTransferMachineDeclaresADeviceIdentity() {
        assertDeclaresIdentity(appeng.parts.automation.ImportBusPart.class,
                "Import Bus is an autonomous INJECT actor");
        assertDeclaresIdentity(appeng.parts.automation.ExportBusPart.class,
                "Export Bus is an autonomous EXTRACT actor");
        assertDeclaresIdentity(appeng.blockentity.storage.IOPortBlockEntity.class,
                "IO Port is an autonomous INJECT+EXTRACT actor");
        assertDeclaresIdentity(appeng.parts.automation.AnnihilationPlanePart.class,
                "Annihilation Plane is an autonomous INJECT actor");
        assertDeclaresIdentity(appeng.blockentity.misc.InterfaceBlockEntity.class,
                "Interface block form (brief \u00a717 block/part symmetry)");
        assertDeclaresIdentity(appeng.parts.misc.InterfacePart.class,
                "Interface part form (brief \u00a717 block/part symmetry)");
    }

    /**
     * The other half of the classification, and just as load-bearing: these must <em>not</em> become their own
     * principals. See {@code Round25PassThroughLaunderingTest} for why, in behavioural terms.
     */
    @Test
    void passThroughAndPropagatingComponentsDeliberatelyHaveNoDeviceIdentity() {
        assertDeclaresNoIdentity(appeng.parts.storagebus.StorageBusPart.class,
                "E - PASSIVE_STORAGE_ENDPOINT: a Storage Bus only mounts foreign storage into the network; every "
                        + "insert/extract that reaches it already carries the original caller's source and was "
                        + "already checked at the network boundary");
        assertDeclaresNoIdentity(appeng.blockentity.storage.MEChestBlockEntity.class,
                "E - PASSIVE_STORAGE_ENDPOINT: an ME Chest mounts its own cell; its MachineSource never touches the "
                        + "network's secured storage");
        assertDeclaresNoIdentity(appeng.me.cluster.implementations.CraftingCPUCluster.class,
                "C/D - the Crafting CPU propagates the submitter's source and is otherwise an internal executor");
        assertDeclaresNoIdentity(appeng.blockentity.networking.CableBusBlockEntity.class,
                "the CableBus host must never stand in as a shortcut identity for the parts it carries (brief "
                        + "\u00a716)");
    }

    private static void assertDeclaresIdentity(Class<?> type, String why) {
        assertThat(IDeviceIdentity.class.isAssignableFrom(type)).as(why).isTrue();
    }

    private static void assertDeclaresNoIdentity(Class<?> type, String why) {
        assertThat(IDeviceIdentity.class.isAssignableFrom(type)).as(why).isFalse();
    }

    // ------------------------------------------------------------------
    // 2. The behavioural half: the exact resolution path, end to end, with the real services.
    // ------------------------------------------------------------------

    /**
     * This is the reproduction proper. {@code UnidentifiedMachine} is shaped exactly like the pre-fix bus/port/plane -
     * an {@link IActionHost} that owns a real grid node and is <em>not</em> an {@link IDeviceIdentity} - and it is
     * denied on a SECURED network with an empty ACL no matter what is granted to anyone else.
     */
    @Test
    void anUnidentifiedMachineIsDeniedAndCannotBeGrantedAnything() {
        var f = new Fixture();
        var anonymous = new MachineSource(new UnidentifiedMachine(f.node));

        assertThat(f.security.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(f.security.hasPermission(anonymous, SecurityPermissions.INJECT)).isFalse();
        assertThat(f.security.hasPermission(anonymous, SecurityPermissions.EXTRACT)).isFalse();
        assertThat(f.insert(anonymous)).isZero();

        // ... and there is no principal an administrator could grant to in order to fix it, short of EVERYONE,
        // which brief §42 STOP-2 forbids. That is precisely why these machines needed an identity of their own.
        f.domain.grant(PrincipalId.ofDevice(UUID.randomUUID()), java.util.Set.of(SecurityPermissions.INJECT));
        f.domain.grant(PrincipalId.ofPlayer(UUID.randomUUID()), java.util.Set.of(SecurityPermissions.INJECT));
        assertThat(f.insert(anonymous)).isZero();
    }

    /**
     * ... and the same machine, once it carries a stable device identity (which is exactly what the Round 25 production
     * change gives the bus / port / plane / interface), is authorizable by a grant to itself alone.
     */
    @Test
    void anIdentifiedMachineIsAuthorizedByAGrantToItselfAlone() {
        var f = new Fixture();
        var deviceId = java.util.UUID.randomUUID();
        var source = RegisteredTestDevice.source(deviceRegistry, level, deviceId);

        assertThat(f.insert(source)).as("no grant yet -> DENY").isZero();

        f.domain.grant(PrincipalId.ofDevice(deviceId), java.util.Set.of(SecurityPermissions.INJECT));

        assertThat(f.insert(source)).isEqualTo(4L);
        assertThat(f.domain.getPermissions(PrincipalId.EVERYONE))
                .as("brief §42 STOP-2: this must never require a blanket EVERYONE grant")
                .isEmpty();
    }

    /**
     * Proves the second branch of {@link SecurityService#resolveDeviceId} too - the one the Interface depends on,
     * because {@code InterfaceLogic} builds {@code new MachineSource(mainNode::getNode)} and therefore hands
     * {@code SecurityService} a lambda {@link IActionHost} rather than the Interface object itself. Resolution has to
     * fall through to {@code IGridNode#getOwner()}.
     */
    @Test
    void identityResolvesThroughTheGridNodeOwnerForLambdaBackedMachineSources() {
        var owningDevice = new OwnerWithIdentity();
        var mgn = new ManagedGridNode(owningDevice, listener);
        mgn.create(level, null);
        var f = new Fixture(mgn);

        // Round 32: still the lambda shape InterfaceLogic and PatternProviderLogic use - the source hands back a node
        // and the identity is read from that node's owner, never from the lambda. What changed is only that the owner
        // must be a device AE2 can locate and the registry knows, which is what a placed Interface is.
        var deviceId = java.util.UUID.randomUUID();
        var source = RegisteredTestDevice.source(deviceRegistry, level, deviceId);

        assertThat(f.insert(source)).isZero();
        f.domain.grant(PrincipalId.ofDevice(deviceId), java.util.Set.of(SecurityPermissions.INJECT));
        assertThat(f.insert(source)).isEqualTo(4L);
    }

    // ------------------------------------------------------------------
    // Fixture + doubles
    // ------------------------------------------------------------------

    /**
     * A machine shaped like the pre-Round-25 buses: a real {@link IActionHost} over a real node, with no identity.
     */
    private record UnidentifiedMachine(IGridNode node) implements IActionHost {
        @Override
        public IGridNode getActionableNode() {
            return node;
        }
    }

    /** The same thing, after the Round 25 change: it carries a {@link PersistentDeviceIdentity}. */
    /**
     * A machine that owns its grid node. Round 27 resolves the device id from {@code node.getOwner()}, so a machine
     * that merely holds a reference to some other object's node is an unauthenticated claim, not a device - that shape
     * is FINDING-3 and is covered by {@code Round26SourceForgeryTest}.
     */
    private final class IdentifiedMachine implements IActionHost, IDeviceIdentity {
        private final PersistentDeviceIdentity identity = new PersistentDeviceIdentity();
        private final appeng.me.ManagedGridNode ownNode;

        private IdentifiedMachine() {
            this.ownNode = new appeng.me.ManagedGridNode(this, listener);
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

    /** A node <em>owner</em> that carries the identity - the Interface / Pattern Provider shape. */
    private static final class OwnerWithIdentity implements IDeviceIdentity {
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
        private final MEStorage securedStorage;
        private final IGridNode node;

        private Fixture() {
            this(freshNode());
        }

        private Fixture(ManagedGridNode mgn) {
            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            // Round 32: device principals now come from the registry, not from the machine.
            security.setDeviceRegistryForTesting(deviceRegistry);
            domain = registry.getDomain(security.bootstrapDomain(UUID.randomUUID()));
            // Exactly how StorageService wires the publicly exposed inventory (StorageService.java:105).
            securedStorage = new SecurityAwareMEStorage(new FakeStorage(), security);
            node = mgn.getNode();
        }

        private long insert(IActionSource source) {
            return securedStorage.insert(item(), 4, Actionable.MODULATE, source);
        }
    }

    private ManagedGridNode freshNode() {
        var mgn = new ManagedGridNode(owner, listener);
        mgn.create(level, null);
        return mgn;
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
            return Component.literal("Round 25 fixture storage");
        }
    }
}
