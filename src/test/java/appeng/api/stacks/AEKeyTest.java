package appeng.api.stacks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.util.BootstrapMinecraft;
import appeng.util.CodecTestUtil;

@BootstrapMinecraft
class AEKeyTest {
    private RegistryAccess registries = RegistryAccess.EMPTY;

    @Test
    void testItemJsonRoundtrip() {
        var expected = GsonHelper.parse("{\"id\":\"minecraft:diamond\",\"#t\":\"ae2:i\"}");

        var ik = AEItemKey.of(Items.DIAMOND);
        testKeyTypeRoundtrip(ik, JsonOps.INSTANCE, expected);
    }

    @Test
    void testItemJsonRoundtripWithPatchedComponents() {
        var expected = GsonHelper.parse(
                "{\"id\":\"minecraft:diamond\",\"components\":{\"minecraft:max_stack_size\":99},\"#t\":\"ae2:i\"}");

        var stack = Items.DIAMOND.getDefaultInstance();
        stack.set(DataComponents.MAX_STACK_SIZE, 99);
        var ik = AEItemKey.of(stack);
        testKeyTypeRoundtrip(ik, JsonOps.INSTANCE, expected);
    }

    @Test
    void testFluidJsonRoundtrip() {
        var expected = GsonHelper.parse("{\"id\":\"minecraft:lava\",\"#t\":\"ae2:f\"}");

        var fk = AEFluidKey.of(Fluids.LAVA);
        testKeyTypeRoundtrip(fk, JsonOps.INSTANCE, expected);
    }

    @Test
    void testFluidJsonRoundtripWithPatchedComponents() {
        var expected = GsonHelper
                .parse("{\"id\":\"minecraft:lava\",\"components\":{\"minecraft:max_stack_size\":99},\"#t\":\"ae2:f\"}");

        var stack = new FluidStack(Fluids.LAVA, 1);
        stack.set(DataComponents.MAX_STACK_SIZE, 99);
        var ik = AEFluidKey.of(stack);
        testKeyTypeRoundtrip(ik, JsonOps.INSTANCE, expected);
    }

    @Test
    void testToGenericTagItemKey() {
        var expected = new CompoundTag();
        expected.putString("#t", "ae2:i");
        expected.putString("id", "minecraft:diamond");

        var key = AEItemKey.of(Items.DIAMOND);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        key.toTagGeneric(output);
        var tag = output.buildResult();
        assertEquals(expected, tag);

        assertEquals(key, AEKey.fromTagGeneric(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag)));
    }

    @Test
    void testToGenericTagFluidKey() {
        var expected = new CompoundTag();
        expected.putString("#t", "ae2:f");
        expected.putString("id", "minecraft:water");

        var key = AEFluidKey.of(Fluids.WATER);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        key.toTagGeneric(output);
        var tag = output.buildResult();
        assertEquals(expected, tag);

        assertEquals(key, AEKey.fromTagGeneric(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag)));
    }

    /**
     * Regression test for AE2 upstream issue #8951 (upstream fix 9b099627d). {@link AEFluidKey#hasComponents()} was
     * {@code stack.getComponents().isEmpty()} - inverted, and evaluated against the merged prototype+patch map instead
     * of the patch, so it returned a constant {@code false}. The contract is "true if this key has *any* components
     * attached".
     */
    @Test
    void testFluidHasComponents() {
        assertFalse(AEFluidKey.of(Fluids.WATER).hasComponents());
        assertFalse(AEFluidKey.of(new FluidStack(Fluids.LAVA, 1)).hasComponents());

        var patched = new FluidStack(Fluids.LAVA, 1);
        patched.set(DataComponents.MAX_STACK_SIZE, 99);
        var patchedKey = AEFluidKey.of(patched);
        assertTrue(patchedKey.hasComponents());

        // Two otherwise identical fluid keys differing only in a component stay distinct, and only one has components.
        assertNotEquals(AEFluidKey.of(Fluids.LAVA), patchedKey);
        assertFalse(AEFluidKey.of(Fluids.LAVA).hasComponents());

        // Survives a generic tag round trip.
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        patchedKey.toTagGeneric(output);
        var restored = AEKey.fromTagGeneric(
                TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
        assertEquals(patchedKey, restored);
        assertTrue(restored.hasComponents());
    }

    private static <T> void testKeyTypeRoundtrip(AEKey key, DynamicOps<T> ops, T encodedValue) {
        CodecTestUtil.testRoundtrip(AEKey.CODEC, key, ops, encodedValue);
    }
}
