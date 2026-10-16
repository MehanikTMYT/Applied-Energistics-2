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
 * FINDING-2 - <b>closed in Round 27. This is now its permanent regression test.</b>
 * <p/>
 * This test pins the <em>current</em> behaviour so the gap is a reproducible regression test rather than a note in a
 * report. It asserts what the code does today, which is <em>not</em> what it should do. Do not read a green result here
 * as approval: the whole point of the class is to make the next round's fix show up as a diff in this file.
 *
 * <h2>The gap</h2>
 *
 * An ME Chest does two things with the same storage cell:
 * <ol>
 * <li>It <b>mounts</b> the cell into the network ({@code MEChestBlockEntity#mountInventories}, line ~507). Everything
 * that reaches the cell <em>through the network</em> passes {@link SecurityAwareMEStorage} first, so it is correctly
 * ACL-gated. That half is fine and is what {@code Round25PassThroughLaunderingTest} proves.</li>
 * <li>It also <b>exposes the same cell directly</b> as an {@code AECapabilities.ME_STORAGE} block capability
 * ({@code InitCapabilityProviders#initMEChest} -> {@code MEChestBlockEntity#getMEStorage(Direction)} ->
 * {@code getInventory()} -> {@code SupplierStorage(cellHandler)}, {@code MEChestBlockEntity.java:437} and
 * {@code :619}). That path returns the <em>raw</em> cell handler. It is not wrapped in {@link SecurityAwareMEStorage},
 * and the NeoForge block-capability signature carries only a {@code Direction} context, no {@link IActionSource} - so
 * there is structurally nowhere for the ACL check to happen.</li>
 * </ol>
 *
 * Consequence on a {@code SECURED} network: anything that can read the block capability from an adjacent position - for
 * example a Storage Bus belonging to a <em>different</em> network, or any other mod - reads and writes the SECURED
 * network's contents without holding {@code EXTRACT} or {@code INJECT} on it.
 *
 * <h2>Why Round 25 did not fix it</h2>
 *
 * <ul>
 * <li>It is not an <em>actor</em> problem, which is what Round 25 was scoped to classify. The caller's identity is
 * perfectly well defined on this path; what is missing is a check, not a principal. Giving the ME Chest an
 * {@code IDeviceIdentity} would not close it - the chest is not the actor here, the capability's caller is (which is
 * why the ME Chest is still, correctly, classification E - PASSIVE_STORAGE_ENDPOINT).</li>
 * <li>The fix changes user-visible behaviour of an existing block on SECURED networks (hoppers, adjacent automation and
 * other mods reading the capability would all start needing grants). That is a design decision to take deliberately,
 * not a side effect of a machine-actor round.</li>
 * <li>Brief §42 STOP-15: where the semantic expectation is not unambiguous, classify and stop rather than write a fix.
 * The <em>mechanism</em> is unambiguous; the <em>intended product behaviour</em> is not.</li>
 * </ul>
 *
 * <h2>Historical note</h2>
 *
 * Pre-2023 AE2 did gate this. {@code TileChest#getMonitorable(ForgeDirection, BaseActionSource)} on {@code rv3-1.7.10}
 * returned {@code null} unless {@code Platform.canAccess(proxy, src)} passed. The gate was lost upstream when
 * {@code getMonitorable(side, src)} was replaced by the source-less NeoForge block capability, not by anything this
 * fork did. See {@code round25-me-chest-architecture-note.md}.
 */
// See Round25PassThroughLaunderingTest for why the ephemeral test server is required here.
@ExtendWith(EphemeralTestServerProvider.class)
class Round25MeChestCapabilityFinding2Test extends AbstractGridNodeTest {

    private static AEItemKey item() {
        return AEItemKey.of(Items.DIAMOND);
    }

    /**
     * The control: through the network, the ME Chest's cell is correctly gated. This is the behaviour the direct
     * capability path should eventually match.
     */
    @Test
    void throughTheNetworkTheMeChestCellIsProperlyGated() {
        var f = new Fixture();
        var stranger = playerSource(UUID.randomUUID());

        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, stranger)).isZero();

        f.domain.grant(PrincipalId.ofPlayer(UUID.randomUUID()), Set.of(SecurityPermissions.EXTRACT));
        assertThat(f.securedNetwork.extract(item(), 8, Actionable.MODULATE, stranger))
                .as("someone else's grant must not help either")
                .isZero();
    }

    /**
     * FINDING-2. The <em>same</em> cell, reached the way {@code MEChestBlockEntity#getMEStorage(Direction)} hands it
     * out, answers to anybody. This assertion documents today's behaviour and must be inverted by whichever round
     * decides to close the gap.
     */
    @Test
    void finding2TheCapabilityViewOfTheSameCellIsNowGatedToo() {
        var f = new Fixture();
        var stranger = playerSource(UUID.randomUUID());

        assertThat(f.security.hasPermission(stranger, SecurityPermissions.EXTRACT))
                .as("the caller genuinely has no rights on this network")
                .isFalse();

        assertThat(f.capabilityViewAsExposedByTheChest.extract(item(), 8, Actionable.MODULATE, stranger))
                .as("FINDING-2 closed in Round 27: MEChestBlockEntity#getMEStorage(Direction) now wraps the external "
                        + "view in SecurityAwareMEStorage, so the capability path and the network path agree")
                .isZero();

        assertThat(f.capabilityViewAsExposedByTheChest.extract(item(), 8, Actionable.MODULATE, IActionSource.empty()))
                .as("and an unidentified external caller gets nothing either")
                .isZero();
    }

    /**
     * LEGACY interoperability (Decision 3A, second half): on a network that was never secured, external automation
     * reaches the chest exactly as it always did. The chest was not turned into a gateway and did not acquire an
     * identity - the wrapper simply defers to the same {@code hasPermission} that returns {@code true} for everything
     * on LEGACY.
     */
    @Test
    void onALegacyNetworkTheCapabilityViewStaysOpen() {
        var f = new Fixture(false);

        assertThat(f.security.getState())
                .as("precondition: this fixture must be LEGACY")
                .isEqualTo(appeng.api.networking.security.SecurityDomainState.LEGACY);
        assertThat(f.capabilityViewAsExposedByTheChest.extract(item(), 8, Actionable.MODULATE, IActionSource.empty()))
                .as("existing external automation on a LEGACY network keeps working unchanged")
                .isEqualTo(8L);
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
        /** Stands in for the ME Chest's {@code cellHandler}: one object, reachable two ways. */
        private final MEStorage rawCell = new FakeCell();
        /**
         * What {@code MEChestBlockEntity#getMEStorage(Direction)} hands to a neighbour after Round 27: the same cell,
         * behind the same {@link SecurityAwareMEStorage} the network path uses.
         */
        private final MEStorage capabilityViewAsExposedByTheChest;
        private final MEStorage securedNetwork;

        private Fixture() {
            this(true);
        }

        private Fixture(boolean secured) {
            var mgn = new ManagedGridNode(owner, listener);
            mgn.create(level, null);
            security = (SecurityService) mgn.getGrid().getService(ISecurityService.class);
            security.setRegistryForTesting(registry);
            domain = secured ? registry.getDomain(security.bootstrapDomain(UUID.randomUUID())) : null;

            // The network view: the very same cell, mounted inside NetworkStorage behind the security decorator -
            // exactly MEChestBlockEntity#mountInventories + StorageService.java:105.
            var network = new NetworkStorage();
            network.mount(0, rawCell);
            securedNetwork = new SecurityAwareMEStorage(network, security);

            // The capability view, mirroring the Round 27 change in MEChestBlockEntity#getMEStorage(Direction).
            capabilityViewAsExposedByTheChest = new SecurityAwareMEStorage(rawCell, security);

            rawCell.insert(item(), 64, Actionable.MODULATE, IActionSource.empty());
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
            return Component.literal("Round 25 FINDING-2 cell");
        }
    }
}
