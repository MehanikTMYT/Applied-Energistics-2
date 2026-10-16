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

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Round 29 - the device registry's state machine, tested as a state machine.
 * <p/>
 * These are deliberately plain unit tests with no world attached: the registry is the piece that has to be
 * <em>provably</em> correct across reload, clone, retirement and load order, and that argument is much easier to follow
 * - and much harder to fool - when the sequences are written out explicitly rather than acted out through block
 * placement.
 * <p/>
 * Every test below maps to one of the mandatory invariants in brief §60.
 */
class DevicePrincipalRegistryTest {

    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
    private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");

    private static MachineKey key(int x, int y, int z) {
        return new MachineKey(OVERWORLD, new BlockPos(x, y, z), MachineKey.BLOCK_SLOT);
    }

    // ------------------------------------------------------------------
    // Issuance and stability
    // ------------------------------------------------------------------

    @Test
    void aFreshMachineIsIssuedAnIdentityAndKeepsItAcrossReloads() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var machine = key(1, 2, 3);

        var issued = registry.resolveOrRegister(machine, null);
        assertThat(issued).isNotNull();

        // A reload is simply the same key asking again - possibly with the id it had persisted.
        assertThat(registry.resolveOrRegister(machine, issued)).isEqualTo(issued);
        // And even if the host had persisted nothing at all, the binding still answers.
        assertThat(registry.resolveOrRegister(machine, null)).isEqualTo(issued);
        assertThat(registry.getAuthenticated(machine)).isEqualTo(issued);
    }

    @Test
    void twoFreshMachinesGetDifferentIdentities() {
        var registry = DevicePrincipalRegistry.createForTesting();

        var a = registry.resolveOrRegister(key(0, 0, 0), null);
        var b = registry.resolveOrRegister(key(0, 0, 1), null);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void twoPartsOnOneBlockDoNotCollide() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var pos = new BlockPos(4, 5, 6);

        var north = registry.resolveOrRegister(new MachineKey(OVERWORLD, pos, "north"), null);
        var south = registry.resolveOrRegister(new MachineKey(OVERWORLD, pos, "south"), null);
        var centre = registry.resolveOrRegister(new MachineKey(OVERWORLD, pos, MachineKey.CENTER_SLOT), null);

        assertThat(north).isNotEqualTo(south).isNotEqualTo(centre);
        assertThat(south).isNotEqualTo(centre);
    }

    // ------------------------------------------------------------------
    // Legacy migration
    // ------------------------------------------------------------------

    @Test
    void anUnclaimedLegacyIdIsAdoptedSoExistingGrantsSurvive() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var legacy = UUID.randomUUID();

        assertThat(registry.resolveOrRegister(key(1, 1, 1), legacy))
                .as("upgrading a world must not orphan the ACL entry that already names this id")
                .isEqualTo(legacy);
    }

    @Test
    void aSecondMachineClaimingTheSameLegacyIdIsRemintedInstead() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var legacy = UUID.randomUUID();

        var first = registry.resolveOrRegister(key(1, 1, 1), legacy);
        var second = registry.resolveOrRegister(key(2, 2, 2), legacy);

        assertThat(first).isEqualTo(legacy);
        assertThat(second).isNotEqualTo(legacy);
        assertThat(registry.getAuthenticated(key(2, 2, 2))).isEqualTo(second);
    }

    /**
     * Brief §35/§58 - the security invariant must not depend on which copy the world happens to load first. Which
     * machine ends up with the legacy id does depend on order, and the report says so; what must never differ is that
     * only one of them holds it.
     */
    @Test
    void theSecurityInvariantHoldsInEitherLoadOrder() {
        var legacy = UUID.randomUUID();
        var original = key(10, 0, 0);
        var clone = key(20, 0, 0);

        var originalFirst = DevicePrincipalRegistry.createForTesting();
        var o1 = originalFirst.resolveOrRegister(original, legacy);
        var c1 = originalFirst.resolveOrRegister(clone, legacy);

        var cloneFirst = DevicePrincipalRegistry.createForTesting();
        var c2 = cloneFirst.resolveOrRegister(clone, legacy);
        var o2 = cloneFirst.resolveOrRegister(original, legacy);

        assertThat(o1).isNotEqualTo(c1);
        assertThat(o2).isNotEqualTo(c2);
        assertThat(java.util.List.of(o1, c1)).contains(legacy);
        assertThat(java.util.List.of(o2, c2)).contains(legacy);
        assertThat(java.util.List.of(o1, c1, o2, c2).stream().filter(legacy::equals).count())
                .as("exactly one holder per registry, in both orders")
                .isEqualTo(2L); // one per registry
    }

    // ------------------------------------------------------------------
    // The attacks Rounds 26-28 established
    // ------------------------------------------------------------------

    @Test
    void aCloneCannotInheritTheOriginalsIdentity() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var original = key(0, 64, 0);

        var originalId = registry.resolveOrRegister(original, null);
        // A blueprint copy carries the original's whole NBT, so it claims the original's id - from a different place.
        var cloneId = registry.resolveOrRegister(key(100, 64, 100), originalId);

        assertThat(cloneId).isNotEqualTo(originalId);
        assertThat(registry.getAuthenticated(original))
                .as("and the original is untouched by the attempt")
                .isEqualTo(originalId);
    }

    /**
     * Brief §19 / Danger 1 - the case the Round 28 sketch would have got wrong. Nothing here consults activity at all,
     * so an unloaded original is simply irrelevant: the binding is keyed by machine, not by liveness.
     */
    @Test
    void aCloneCannotTakeTheIdentityOfAnUnloadedOriginal() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var original = key(0, 64, 0);
        var originalId = registry.resolveOrRegister(original, null);

        // ...original's chunk unloads. No call is made, because unload is not an event this registry has.
        var cloneId = registry.resolveOrRegister(key(500, 64, 500), originalId);
        assertThat(cloneId).isNotEqualTo(originalId);

        // ...and when the original comes back it is still itself.
        assertThat(registry.resolveOrRegister(original, originalId)).isEqualTo(originalId);
    }

    @Test
    void aMaliciousMachineCannotClaimAnotherDevicesIdentity() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var victim = key(1, 70, 1);
        var victimId = registry.resolveOrRegister(victim, null);

        // The attacker owns a real node at its own real position - which is exactly why it cannot be the victim.
        var attackerId = registry.resolveOrRegister(key(9, 70, 9), victimId);

        assertThat(attackerId).isNotEqualTo(victimId);
    }

    @Test
    void aHostThatChangesItsClaimAfterBindingGainsNothing() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var machine = key(3, 3, 3);
        var bound = registry.resolveOrRegister(machine, null);

        var victimId = UUID.randomUUID();
        registry.resolveOrRegister(key(8, 8, 8), victimId); // victim exists elsewhere

        assertThat(registry.resolveOrRegister(machine, victimId))
                .as("the established binding wins over any later claim")
                .isEqualTo(bound);
    }

    @Test
    void theSameClaimInTwoDimensionsCannotBothAuthenticate() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var legacy = UUID.randomUUID();

        var overworld = registry.resolveOrRegister(new MachineKey(OVERWORLD, BlockPos.ZERO, MachineKey.BLOCK_SLOT),
                legacy);
        var nether = registry.resolveOrRegister(new MachineKey(NETHER, BlockPos.ZERO, MachineKey.BLOCK_SLOT), legacy);

        assertThat(overworld).isEqualTo(legacy);
        assertThat(nether)
                .as("a server-scoped registry is the whole reason this cannot be claimed twice")
                .isNotEqualTo(legacy);
    }

    // ------------------------------------------------------------------
    // Retirement
    // ------------------------------------------------------------------

    @Test
    void aRetiredIdentityIsNeverHandedOutAgain() {
        var registry = DevicePrincipalRegistry.createForTesting();
        var machine = key(7, 7, 7);
        var id = registry.resolveOrRegister(machine, null);

        registry.retire(machine);
        assertThat(registry.isBound(id)).isFalse();
        assertThat(registry.isTombstoned(id)).isTrue();

        // A new machine at the very same position is a new device, not the resurrected old one.
        var replacement = registry.resolveOrRegister(machine, id);
        assertThat(replacement)
                .as("break-and-replace must not inherit the old machine's grants")
                .isNotEqualTo(id);

        // And an old NBT copy surfacing much later cannot resurrect the retired identity either.
        assertThat(registry.resolveOrRegister(key(42, 42, 42), id)).isNotEqualTo(id);
    }
}
