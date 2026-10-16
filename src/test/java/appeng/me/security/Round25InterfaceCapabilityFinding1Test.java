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
 * FINDING-1 - <b>closed in Round 27. This is now its permanent regression test.</b>
 * <p/>
 * Round 25 filed the gap and pinned the vulnerable behaviour here; Round 26 measured that no production AE2 path
 * actually depended on it; Round 27 removed the trusted-internal bypass in {@code SecurityService#hasPermission}
 * entirely. The assertions below were inverted at that point, which is exactly the diff Round 25 said this file would
 * one day have to show.
 * <p/>
 * Three things are asserted together, because the owner's Decision 1A has three halves and dropping any one of them
 * would be a different (and wrong) fix: an unidentified caller gets nothing on a SECURED network, an explicit
 * {@code EVERYONE} grant still opens it deliberately, and LEGACY networks are untouched.
 *
 * <h2>The gap</h2>
 *
 * FINDING-1 is the wider sibling of FINDING-2. FINDING-2 leaks one ME Chest's cell because the capability hands out a
 * raw, unwrapped {@code MEStorage}. FINDING-1 leaks the <em>entire network</em> even though the security decorator
 * <em>is</em> in the path:
 *
 * <ol>
 * <li>{@code InitCapabilityProviders#initInterface} (:128-133, and :233-234 for the part form) registers
 * {@code AECapabilities.ME_STORAGE} -&gt; {@code getInterfaceLogic().getInventory()}.</li>
 * <li>{@code InterfaceLogic#getInventory()} (:254-260) returns {@code networkStorage} whenever {@code hasConfig} is
 * false - and {@code hasConfig} is false by default (:96), i.e. for every Interface that has not been given a stocking
 * filter. So a plain, freshly placed Interface publishes the whole network to its neighbours.</li>
 * <li>{@code networkStorage} is {@code StorageService#getInventory()} (:205-207), which <em>is</em> the
 * {@link SecurityAwareMEStorage} decorator. So far so good.</li>
 * <li>But {@link SecurityAwareMEStorage#insert}/{@link SecurityAwareMEStorage#extract} (:59-72) gate on
 * {@code security.hasPermission(source, ...)} where {@code source} is the argument <em>the caller supplies</em>, and
 * {@link SecurityService#hasPermission} (:182-184) returns {@code true} unconditionally when the source names neither a
 * player nor a machine.</li>
 * </ol>
 *
 * Consequence on a {@code SECURED} network: an external caller that holds the block capability and passes
 * {@link IActionSource#empty()} - the documented way to say "no specific actor" - reads and writes the whole network
 * with no grant of any kind. It does not even have to be malicious; {@code empty()} is the natural thing for a generic
 * pipe-like mod to pass.
 *
 * <h2>Reachability (measured, not assumed)</h2>
 *
 * No caller in this modpack actually does it today. AE2's own {@code PatternProviderTarget#wrapMeStorage} (:80-84)
 * threads the caller's source through, {@code StorageBusPart} (:126) passes its own {@code MachineSource}, and
 * ExpandedAE's {@code PatternProviderTargetCache} (:33-37) stores and forwards a real {@code IActionSource}. FINDING-1
 * is therefore <b>latent and addon-reachable</b>, not currently exploited - which lowers the urgency but does not
 * change the fact that the boundary trusts a value the outside world controls.
 *
 * <h2>Why Round 25 did not fix it</h2>
 *
 * The mechanism is unambiguous; the intended product behaviour is not - the same reason FINDING-2 was left alone. A fix
 * has to choose what an external "no actor" claim means on a SECURED network (deny outright, or attribute it to the
 * Interface's own device principal), and either choice changes visible behaviour for every third-party mod that reads
 * the capability. That is the pack owner's design decision, not a side effect of a machine-actor round (brief §30 gate
 * item 2, §31 STOP-11).
 * <p/>
 * The minimal fix, for whoever takes that decision: wrap only at the capability boundary
 * ({@code InitCapabilityProviders#initInterface} and the {@code InterfacePart} registration) so that a source naming
 * neither player nor machine is downgraded to an unidentified principal (EVERYONE grants only) instead of being taken
 * as trusted-internal. Internal AE2 paths never travel through the capability and so stay untouched, and LEGACY
 * networks are unaffected because {@code hasPermission} already returns {@code true} there.
 */
// See Round25PassThroughLaunderingTest for why the ephemeral test server is required here.
@ExtendWith(EphemeralTestServerProvider.class)
class Round25InterfaceCapabilityFinding1Test extends AbstractGridNodeTest {

    private static AEItemKey item() {
        return AEItemKey.of(Items.DIAMOND);
    }

    /**
     * The control: an identified caller with no grant is correctly refused by the very same object the Interface
     * capability hands out. This proves the decorator is genuinely in the path, so the leak below cannot be explained
     * away as "the wrapper is missing here".
     */
    @Test
    void anIdentifiedStrangerIsCorrectlyRefusedByTheSameObject() {
        var f = new Fixture();
        var stranger = playerSource(UUID.randomUUID());

        assertThat(f.security.hasPermission(stranger, SecurityPermissions.EXTRACT))
                .as("the caller genuinely has no rights on this network")
                .isFalse();
        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, stranger))
                .as("an identified stranger gets nothing - the ACL decorator is doing its job on this path")
                .isZero();

        f.domain.grant(PrincipalId.ofPlayer(UUID.randomUUID()), Set.of(SecurityPermissions.EXTRACT));
        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, stranger))
                .as("someone else's grant must not help either")
                .isZero();
    }

    /**
     * FINDING-1. The same object, asked by a caller that claims to have no actor at all, hands over the whole network.
     * This assertion documents today's behaviour and must be inverted by whichever round decides to close the gap.
     */
    @Test
    void finding1AnExternalCallerClaimingNoActorGetsNothingOnASecuredNetwork() {
        var f = new Fixture();

        assertThat(f.security.getState())
                .as("precondition: this is a SECURED network, not a LEGACY one")
                .isEqualTo(appeng.api.networking.security.SecurityDomainState.SECURED);

        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, IActionSource.empty()))
                .as("FINDING-1 closed in Round 27: an actorless source is no longer trusted-internal. On a SECURED "
                        + "network it resolves to the unidentified principal and gets only what EVERYONE was granted, "
                        + "which is nothing here.")
                .isZero();

        assertThat(f.exposedByInterfaceCapability.insert(item(), 4, Actionable.MODULATE, IActionSource.empty()))
                .as("and the same for writes - an unidentified outsider cannot inject either")
                .isZero();
    }

    /**
     * The other half of Decision 1A, and the reason the fix is not simply {@code return false}: an <em>explicit</em>
     * public grant must keep working. Actorless means "unidentified", not "denied outright".
     */
    @Test
    void anExplicitEveryoneGrantStillAppliesToAnActorlessCaller() {
        var f = new Fixture();
        f.domain.grant(PrincipalId.EVERYONE, Set.of(SecurityPermissions.EXTRACT));

        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, IActionSource.empty()))
                .as("an explicit EVERYONE grant is a deliberate decision to open the network to the public and must "
                        + "still be honoured")
                .isEqualTo(8L);
        assertThat(f.exposedByInterfaceCapability.insert(item(), 4, Actionable.MODULATE, IActionSource.empty()))
                .as("EXTRACT was granted to EVERYONE, INJECT was not")
                .isZero();
    }

    /**
     * <b>Privilege laundering (brief §23) - mandatory.</b> The Interface itself is an autonomous device and may well
     * hold {@code EXTRACT} so that its own stocking works. An external caller reaching the network <em>through</em> the
     * Interface's capability must not inherit that grant: the caller is the actor, not the Interface.
     * <p/>
     * This is the test that decides between the owner's Decision 2A and 2B, and it pins 2B. Had Round 27 implemented
     * the rejected "boundary-owned DEVICE attribution" model, this assertion would fail - which is precisely why that
     * model was rejected.
     */
    @Test
    void anExternalCallerDoesNotInheritTheInterfacesOwnDeviceGrant() {
        var f = new Fixture();

        // The Interface's own device principal is fully entitled to extract - that is its autonomous behaviour.
        var interfaceDeviceId = UUID.randomUUID();
        f.domain.grant(PrincipalId.ofDevice(interfaceDeviceId), Set.of(SecurityPermissions.EXTRACT));

        // An external caller holding the capability has no rights of its own, and gets nothing - whether it declines
        // to name an actor at all...
        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, IActionSource.empty()))
                .as("an unidentified external caller must not ride on the Interface's own grant")
                .isZero();

        // ...or names itself as some machine of its own.
        var externalMachine = new appeng.me.helpers.MachineSource(() -> null);
        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, externalMachine))
                .as("nor may an external machine borrow it")
                .isZero();

        // And the Interface's grant really was live - otherwise the two zeroes above would prove nothing.
        assertThat(f.domain.getEffectivePermissions(PrincipalId.ofDevice(interfaceDeviceId)))
                .as("fixture check: the Interface device genuinely holds EXTRACT")
                .contains(SecurityPermissions.EXTRACT);
    }

    /**
     * LEGACY compatibility (Decision 1A, second half): a network whose security domain was never bootstrapped keeps
     * behaving exactly as plain AE2 always did, so existing external automation does not break.
     */
    @Test
    void legacyNetworksRemainOpenToActorlessCallers() {
        var f = new Fixture(false);

        assertThat(f.security.getState())
                .as("precondition: this fixture must be LEGACY")
                .isEqualTo(appeng.api.networking.security.SecurityDomainState.LEGACY);
        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, IActionSource.empty()))
                .as("LEGACY interoperability is preserved unchanged")
                .isEqualTo(8L);
    }

    /**
     * The boundary of the finding: a caller that identifies itself as a machine is <em>not</em> laundered into
     * trusted-internal. It resolves to an unidentified device principal and therefore sees only what EVERYONE was
     * granted - which is nothing here. This is what makes FINDING-1 specifically about the {@code empty()} claim rather
     * than about the capability being exposed at all.
     */
    @Test
    void aCallerThatNamesAMachineIsStillGated() {
        var f = new Fixture();
        var unknownMachine = new appeng.me.helpers.MachineSource(() -> null);

        assertThat(f.exposedByInterfaceCapability.extract(item(), 8, Actionable.MODULATE, unknownMachine))
                .as("a machine without a device identity falls back to EVERYONE grants, which are empty here")
                .isZero();
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private IActionSource playerSource(UUID playerId) {
        var player = org.mockito.Mockito.mock(net.minecraft.world.entity.player.Player.class);
        org.mockito.Mockito.when(player.getUUID()).thenReturn(playerId);
        return IActionSource.ofPlayer(player);
    }

    private final class Fixture {
        private final SecurityService security;
        private final SecurityDomainRegistry registry = SecurityDomainRegistry.createForTesting();
        private final SecurityDomain domain;
        /**
         * Exactly what {@code InterfaceLogic#getInventory()} returns for an unconfigured Interface, and therefore
         * exactly what the {@code ME_STORAGE} capability publishes to the neighbouring block: the network storage
         * behind the security decorator. Not a stand-in - the real {@link SecurityAwareMEStorage} over a real
         * {@link NetworkStorage}.
         */
        private final MEStorage exposedByInterfaceCapability;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean secured) {
            var mgn = new ManagedGridNode(owner, listener);
            mgn.create(level, null);
            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            domain = secured ? registry.getDomain(security.bootstrapDomain(UUID.randomUUID())) : null;

            var cell = new FakeCell();
            var network = new NetworkStorage();
            network.mount(0, cell);
            exposedByInterfaceCapability = new SecurityAwareMEStorage(network, security);

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
            return Component.literal("Round 25 FINDING-1 network cell");
        }
    }
}
