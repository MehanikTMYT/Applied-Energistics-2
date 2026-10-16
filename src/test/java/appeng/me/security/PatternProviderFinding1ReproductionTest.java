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

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityService;
import appeng.api.networking.security.SecurityDomainState;
import appeng.api.networking.security.SecurityPermissions;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.me.AbstractGridNodeTest;
import appeng.me.ManagedGridNode;
import appeng.me.helpers.MachineSource;

/**
 * Round 24, FINDING-1 reproduction.
 * <p/>
 * Round 23 observed, in a physical fixture, that autocrafting on a {@link SecurityDomainState#SECURED} network with an
 * empty ACL simply stalls (~1200 ticks of nothing happening), and empirically traced the stall to a dependency on
 * {@code EVERYONE}/{@code INJECT}. This test pins that behaviour down structurally, at the exact call site, so the
 * problem is a reproducible regression test rather than a field observation.
 * <p/>
 * The failing call site is {@code PatternProviderLogic#doWork()}:
 *
 * <pre>
 * returnInv.injectIntoNetwork(mainNode.getGrid().getStorageService().getInventory(), actionSource, ...)
 * </pre>
 *
 * where {@code actionSource} is {@code new MachineSource(mainNode::getNode)} (PatternProviderLogic.java:137) and
 * {@code getStorageService().getInventory()} is the {@link SecurityAwareMEStorage} decorator (StorageService.java:105 /
 * :205). Because {@code MachineSource} here wraps a <em>lambda</em> {@code IActionHost}, and because neither that
 * lambda nor the Pattern Provider block entity/part implements {@link appeng.api.networking.security.IDeviceIdentity},
 * {@link SecurityService#resolvePrincipal} returns {@code null}, and {@link SecurityService#hasPermission} falls
 * through to the "unidentified machine" branch which consults only what is granted to
 * {@link appeng.api.networking.security.PrincipalId#EVERYONE}.
 * <p/>
 * Everything below is the real thing: a real {@link SecurityService} on a real grid, a real
 * {@link SecurityDomainRegistry}, a real {@link SecurityAwareMEStorage}, a real {@link MachineSource} built exactly the
 * way {@code PatternProviderLogic} builds it, and the real production
 * {@link PatternProviderReturnInventory#injectIntoNetwork}. Nothing about the security decision is mocked.
 * <p/>
 * <b>Recorded failure coordinates (brief §11):</b>
 *
 * <pre>
 * FAILED_ACTOR       = Pattern Provider (PatternProviderLogic#doWork -> returnInv.injectIntoNetwork)
 * FAILED_PRINCIPAL   = null (unidentified machine; MachineSource over a lambda IActionHost)
 * FAILED_PERMISSION  = INJECT
 * FAILED_OPERATION   = MEStorage.insert into the network's secured storage
 * FAILED_CALL_CHAIN  = PatternProviderLogic#doWork -> PatternProviderReturnInventory#injectIntoNetwork
 *                      -> SecurityAwareMEStorage#insert -> SecurityService#hasPermission
 *                      -> SecurityService#resolvePrincipal -> null -> EVERYONE grants only -> DENY
 * </pre>
 */
class PatternProviderFinding1ReproductionTest extends AbstractGridNodeTest {

    /**
     * Resolved lazily rather than in a static initializer: {@link AEItemKey#of} needs the registries that
     * {@link appeng.util.BootstrapMinecraft} sets up per-test, which has not run yet at class-init time.
     */
    private static AEItemKey craftedOutput() {
        return AEItemKey.of(Items.DIAMOND_PICKAXE);
    }

    /**
     * The reproduction itself: on a SECURED network with a completely empty ACL, the Pattern Provider cannot return the
     * crafted output to the network, so the item stays stuck in the return inventory forever and the crafting job never
     * observes its own output. This is FINDING-1.
     */
    @Test
    void patternProviderCannotReturnCraftedOutputOnSecuredNetworkWithEmptyAcl() {
        var fixture = new Fixture();

        // Sanity: the network really is SECURED, and EVERYONE really has been granted nothing.
        assertThat(fixture.security.getState()).isEqualTo(SecurityDomainState.SECURED);
        assertThat(fixture.domain.getPermissions(appeng.api.networking.security.PrincipalId.EVERYONE)).isEmpty();

        // The Molecular Assembler has just handed the crafted output back to the Pattern Provider.
        fixture.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());
        assertThat(fixture.returnInv.isEmpty()).isFalse();

        // This is exactly what PatternProviderLogic#doWork() does every tick.
        var didSomething = fixture.injectReturnInventoryIntoNetwork();

        assertThat(didSomething)
                .as("FINDING-1: the Pattern Provider's INJECT is denied, so no progress is ever made")
                .isFalse();
        assertThat(fixture.returnInv.isEmpty())
                .as("FINDING-1: the crafted output stays stuck in the Pattern Provider's return inventory")
                .isFalse();
        assertThat(fixture.networkContents().get(craftedOutput()))
                .as("FINDING-1: the crafted output never reaches network storage, so the crafting CPU "
                        + "(mounted inside NetworkStorage, i.e. behind the security decorator) never sees it "
                        + "and the job stalls forever")
                .isZero();
    }

    /**
     * Pins down that the denial really is the security decorator's INJECT check on an unidentified machine principal -
     * not, say, an unrelated inventory/capacity problem. Granting INJECT to EVERYONE makes the exact same call succeed.
     * <p/>
     * NOTE: this grant exists <em>only</em> to isolate the cause inside this diagnostic test. Brief §13/§36 forbid an
     * EVERYONE INJECT grant as a <em>solution</em>, and no production code path and no other test relies on one.
     */
    @Test
    void theDenialIsSpecificallyTheInjectPermissionCheck() {
        var fixture = new Fixture();
        fixture.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());

        // Diagnostic isolation only - see the note above.
        fixture.domain.grant(appeng.api.networking.security.PrincipalId.EVERYONE,
                java.util.Set.of(SecurityPermissions.INJECT));

        var didSomething = fixture.injectReturnInventoryIntoNetwork();

        assertThat(didSomething).isTrue();
        assertThat(fixture.returnInv.isEmpty()).isTrue();
        assertThat(fixture.networkContents().get(craftedOutput())).isEqualTo(1L);
    }

    /**
     * The same network is <em>not</em> broken for a LEGACY (no Security Station) network - vanilla AE2 automation keeps
     * working untouched. This is the control that shows FINDING-1 is specific to the SECURED state.
     */
    @Test
    void legacyNetworkIsUnaffected() {
        var fixture = new Fixture(false);
        assertThat(fixture.security.getState()).isEqualTo(SecurityDomainState.LEGACY);

        fixture.returnInv.insert(craftedOutput(), 1, Actionable.MODULATE, IActionSource.empty());
        var didSomething = fixture.injectReturnInventoryIntoNetwork();

        assertThat(didSomething).isTrue();
        assertThat(fixture.networkContents().get(craftedOutput())).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private final class Fixture {
        private final SecurityService security;
        private final SecurityDomainRegistry registry = SecurityDomainRegistry.createForTesting();
        private final SecurityDomain domain;
        private final SimpleFakeStorage backing = new SimpleFakeStorage();
        private final MEStorage securedStorage;
        private final PatternProviderReturnInventory returnInv = new PatternProviderReturnInventory(() -> {
        });
        private final IActionSource patternProviderSource;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean secured) {
            var mgn = new ManagedGridNode(owner, listener);
            mgn.create(level, null);

            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);

            if (secured) {
                var domainId = security.bootstrapDomain(UUID.randomUUID());
                domain = registry.getDomain(domainId);
            } else {
                domain = null;
            }

            // Exactly how StorageService wires the publicly exposed inventory (StorageService.java:105).
            securedStorage = new SecurityAwareMEStorage(backing, security);

            // Exactly how PatternProviderLogic builds its action source (PatternProviderLogic.java:137):
            // a MachineSource over a *lambda* IActionHost, which is therefore not an IDeviceIdentity.
            patternProviderSource = new MachineSource(mgn::getNode);
        }

        private boolean injectReturnInventoryIntoNetwork() {
            return returnInv.injectIntoNetwork(securedStorage, patternProviderSource, stack -> {
            });
        }

        private KeyCounter networkContents() {
            var out = new KeyCounter();
            backing.getAvailableStacks(out);
            return out;
        }
    }

    /** Minimal in-memory {@link MEStorage}, mirroring the fixture used by {@code SecuredLegacyMergeSplitTest}. */
    private static final class SimpleFakeStorage implements MEStorage {
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
            var available = contents.get(what);
            var extracted = Math.min(available, amount);
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
            return Component.literal("Round 24 FINDING-1 reproduction storage");
        }
    }

    @SuppressWarnings("unused")
    private static GenericStack unusedStackHelper() {
        return null;
    }
}
