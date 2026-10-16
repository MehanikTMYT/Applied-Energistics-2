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

import org.jetbrains.annotations.Nullable;
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
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.storage.NetworkStorage;

/**
 * Round 26 §12 - <b>can a DEVICE principal be forged through the public API?</b>
 * <p/>
 * Round 25 established that an external caller can claim <em>no</em> actor ({@code IActionSource.empty()}) and be
 * treated as trusted internal (FINDING-1). This class asks the strictly harder question: can an external caller claim
 * to <em>be</em> a specific, privileged device?
 *
 * <h2>Why the answer decides Round 26's architecture</h2>
 *
 * Every candidate fix for FINDING-1 that works by inspecting the caller-supplied {@link IActionSource} - "reject
 * sources with no actor", "attribute the operation to the boundary device" - is only as strong as the caller's
 * inability to supply a <em>better-looking</em> source. If a device principal is forgeable, then closing the actorless
 * path merely moves the bypass one step sideways, and no property of a caller-supplied source can be the basis of
 * trust.
 *
 * <h2>What the public API actually permits</h2>
 *
 * Three things are all public API in {@code appeng.api.networking.security}:
 * <ul>
 * <li>{@link IActionSource} is a public interface with a public static {@code ofMachine(IActionHost)} factory - and,
 * being an interface, it can simply be implemented directly, so sealing the factory alone would change nothing.</li>
 * <li>{@link IActionHost} is a public interface whose only method may return {@code null}.</li>
 * <li>{@link IDeviceIdentity} is a public interface whose only method returns a {@link UUID} the implementor
 * chooses.</li>
 * </ul>
 *
 * Round 26 answered yes: {@code SecurityService#resolveDeviceId} used to accept the first branch it matched,
 * {@code machine instanceof IDeviceIdentity identity -> identity.getDeviceId()}, authenticating nothing. And the
 * victim's id was not a secret either - {@code InterfaceBlockEntity} is a public class implementing the public
 * {@link IDeviceIdentity} with a public {@code getDeviceId()}, so {@code level.getBlockEntity(pos)} on a neighbour was
 * enough to read the very id to replay.
 *
 * <h2>What Round 27 changed</h2>
 *
 * The id is no longer read from the object the caller passed in. {@code resolveDeviceId} now requires
 * {@code getActionableNode()} to return a real grid node and takes the identity from {@code node.getOwner()}. The
 * forgery below returns {@code null} for its node and is therefore no longer a device at all - while every real machine
 * (and a legitimate third-party device such as the AE2&lt;-&gt;MineColonies bridge block entity) keeps working, because
 * for each of them the node's owner <em>is</em> the identity holder.
 *
 * <h2>What is deliberately still true</h2>
 *
 * This is a barrier, not authentication, and the tests are written to say so. {@code GridHelper.createManagedNode} is
 * public, so an addon can still register a real node whose owner returns someone else's id; what changed is that
 * forgery now costs an actual machine placed and connected to the network instead of a throwaway object. Closing that
 * remainder means AE2 minting device ids itself rather than accepting them from the host, which changes the public
 * {@link IDeviceIdentity} contract - an owner-level decision, not this round's.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class Round26SourceForgeryTest extends AbstractGridNodeTest {

    /** Round 32: in-memory device registry - a device is authenticated through it, not by self-assertion. */
    private final DevicePrincipalRegistry deviceRegistry = DevicePrincipalRegistry.createForTesting();

    private static AEItemKey item() {
        return AEItemKey.of(Items.DIAMOND);
    }

    /**
     * The legitimate counterpart: an object that is the <em>owner</em> of a real grid node. It does not need to be an
     * {@link IActionHost} at all - after Round 27 the identity is read from {@code node.getOwner()}, never from the
     * wrapper the caller passes to {@code ofMachine(...)}.
     */
    private record RealDevice(UUID id) implements IDeviceIdentity {
        @Override
        public UUID getDeviceId() {
            return id;
        }
    }

    /**
     * The thing an addon would write. Nothing here is AE2-internal: both interfaces are public API, and the grid node
     * may legally be {@code null} - which, after Round 27, is exactly what disqualifies it.
     */
    private record ForgedDevice(UUID claimed) implements IActionHost, IDeviceIdentity {
        @Nullable
        @Override
        public IGridNode getActionableNode() {
            return null;
        }

        @Override
        public UUID getDeviceId() {
            return claimed;
        }
    }

    /**
     * Control: a forged device that claims an id nobody granted anything to is correctly refused. This shows the ACL
     * itself works - the failure below is specifically about <em>who may assert an identity</em>, not about the
     * permission lookup.
     */
    @Test
    void aForgedDeviceClaimingAnUngrantedIdIsRefused() {
        var f = new Fixture();

        var nobody = IActionSource.ofMachine(new ForgedDevice(UUID.randomUUID()));
        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, nobody))
                .as("an unknown device principal has no grants and must get nothing")
                .isZero();
    }

    /**
     * FINDING-3's permanent regression test. Before Round 27 this call returned 8: naming the granted device id with
     * nothing but public API types was enough to be authorized as that device.
     */
    @Test
    void aForgedDeviceClaimingAGrantedIdIsNoLongerAuthorized() {
        var f = new Fixture();

        var victimDeviceId = UUID.randomUUID();
        f.domain.grant(PrincipalId.ofDevice(victimDeviceId), Set.of(SecurityPermissions.EXTRACT));

        var forged = IActionSource.ofMachine(new ForgedDevice(victimDeviceId));

        assertThat(f.security.hasPermission(forged, SecurityPermissions.EXTRACT))
                .as("Round 27: the id is no longer read off the object the caller passed in. A claim with no grid "
                        + "node resolves to no principal at all.")
                .isFalse();
        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, forged))
                .as("FINDING-3 closed: claiming another device's id through the public API no longer authorizes")
                .isZero();
    }

    /**
     * The other half of the fix, and the one that makes it a fix rather than a blanket denial (brief §18): a
     * <em>legitimate</em> device - one that owns a real registered grid node on this grid, exactly as every in-tree AE2
     * machine and the AE2&lt;-&gt;MineColonies bridge block entity do - is still authorized by a grant to its own
     * principal.
     */
    @Test
    void aRealNodeBackedDeviceIsStillAuthorizedByItsOwnGrant() {
        var f = new Fixture();

        f.domain.grant(PrincipalId.ofDevice(f.realDeviceId), Set.of(SecurityPermissions.EXTRACT));

        assertThat(f.security.hasPermission(f.realDeviceSource, SecurityPermissions.EXTRACT))
                .as("a device whose identity comes from the owner of a real node on this grid still resolves")
                .isTrue();
        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, f.realDeviceSource))
                .as("legitimate autonomous machine behaviour is unaffected by the Round 27 hardening")
                .isEqualTo(8L);
    }

    /** And it is still scoped to exactly what that device was granted. */
    @Test
    void aRealDeviceGetsExactlyItsOwnRightsAndNoMore() {
        var f = new Fixture();
        f.domain.grant(PrincipalId.ofDevice(f.realDeviceId), Set.of(SecurityPermissions.EXTRACT));

        assertThat(f.security.hasPermission(f.realDeviceSource, SecurityPermissions.EXTRACT)).isTrue();
        assertThat(f.security.hasPermission(f.realDeviceSource, SecurityPermissions.INJECT))
                .as("EXTRACT was granted, INJECT was not")
                .isFalse();
        assertThat(f.security.hasPermission(f.realDeviceSource, SecurityPermissions.SECURITY))
                .as("and it certainly does not confer super-admin")
                .isFalse();
    }

    /**
     * Cross-device isolation (brief §19): device B, holding a real node of its own, cannot borrow device A's grant by
     * claiming A's id. This is the case node-presence alone would <em>not</em> catch, so it is asserted explicitly.
     */
    @Test
    void aRealDeviceCannotBorrowAnotherDevicesGrantByClaimingItsId() {
        var f = new Fixture();

        var deviceAId = UUID.randomUUID();
        f.domain.grant(PrincipalId.ofDevice(deviceAId), Set.of(SecurityPermissions.EXTRACT));

        // The real, node-backed device B has no grant of its own.
        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, f.realDeviceSource))
                .as("B has no grant, so B gets nothing")
                .isZero();

        // And a bare claim of A's id gets nothing either.
        var claimingA = IActionSource.ofMachine(new ForgedDevice(deviceAId));
        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, claimingA))
                .as("nor can A's id be replayed by an object that is not A")
                .isZero();
    }

    /**
     * A forged <em>player</em> source is a different matter and is pinned separately: AE2 resolves a player source to
     * that player's UUID, so a caller able to obtain a server-side {@code Player} object can act as them. That is
     * ordinary delegation (a terminal does exactly this on the player's behalf), which is why it is documented as a
     * trust assumption rather than filed as a bypass.
     */
    @Test
    void ownerRightsStillDoNotLeakToAForgedDevice() {
        var f = new Fixture();

        // The domain owner is a player principal; a device - forged or real - is a different principal subtype.
        var forgedAsOwnerUuid = IActionSource.ofMachine(new ForgedDevice(f.ownerId));

        assertThat(f.security.hasPermission(forgedAsOwnerUuid, SecurityPermissions.EXTRACT))
                .as("replaying the OWNER's uuid as a device id must not inherit owner rights - owner is checked only "
                        + "for PrincipalId.Player, and a device is PrincipalId.Device")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private final class Fixture {
        private final SecurityService security;
        private final SecurityDomainRegistry registry = SecurityDomainRegistry.createForTesting();
        private final SecurityDomain domain;
        private final UUID ownerId = UUID.randomUUID();
        private final MEStorage securedNetwork;
        /** Identity of the legitimate, node-backed device in this fixture. */
        private final UUID realDeviceId = UUID.randomUUID();
        /** A source shaped exactly like the ones AE2's own machines build: a host that returns a real grid node. */
        private final IActionSource realDeviceSource;

        private Fixture() {
            // The node's OWNER carries the identity - this is the shape SecurityService trusts after Round 27, and the
            // shape every real AE2 machine (and the AE2<->MineColonies bridge block entity) already has.
            var mgn = new ManagedGridNode(owner, listener);
            mgn.create(level, null);
            // Round 32: the legitimate device is now one AE2 can locate, registered in the registry - the same shape
            // a placed Interface has. RealDevice is kept only as the "owner carries the identity" illustration.
            realDeviceSource = RegisteredTestDevice.source(deviceRegistry, level, realDeviceId);
            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            // Round 32: device principals now come from the registry, not from the machine.
            security.setDeviceRegistryForTesting(deviceRegistry);
            domain = registry.getDomain(security.bootstrapDomain(ownerId));

            var cell = new FakeCell();
            var network = new NetworkStorage();
            network.mount(0, cell);
            securedNetwork = new SecurityAwareMEStorage(network, security);

            cell.insert(item(), 64, Actionable.MODULATE, IActionSource.empty());
        }
    }

    private static final class FakeCell implements MEStorage {
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
            return Component.literal("Round 26 forgery fixture cell");
        }
    }
}
