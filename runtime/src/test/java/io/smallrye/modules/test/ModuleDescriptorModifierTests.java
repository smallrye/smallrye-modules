package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.ModuleDescriptor;

/**
 * Tests for {@link ModuleDescriptor.Modifier} and {@link ModuleDescriptor.Modifier.Set}.
 */
public final class ModuleDescriptorModifierTests {

    // --- factory methods ---

    @Test
    public void emptySet() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void singletonSet() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        assertEquals(1, set.size());
        assertTrue(set.contains(ModuleDescriptor.Modifier.OPEN));
        assertFalse(set.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void twoElementSet() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.OPEN,
                ModuleDescriptor.Modifier.NATIVE_ACCESS);
        assertEquals(2, set.size());
        assertTrue(set.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(set.contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
        assertFalse(set.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void threeElementSet() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.NATIVE_ACCESS,
                ModuleDescriptor.Modifier.OPEN,
                ModuleDescriptor.Modifier.AUTOMATIC);
        assertEquals(3, set.size());
        assertTrue(set.contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
        assertTrue(set.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(set.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void fourElementSet() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.NATIVE_ACCESS,
                ModuleDescriptor.Modifier.OPEN,
                ModuleDescriptor.Modifier.AUTOMATIC,
                ModuleDescriptor.Modifier.UNNAMED);
        assertEquals(4, set.size());
        for (ModuleDescriptor.Modifier m : ModuleDescriptor.Modifier.values) {
            assertTrue(set.contains(m));
        }
    }

    @Test
    public void ofAllContainsAll() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.ofAll();
        assertEquals(ModuleDescriptor.Modifier.values.size(), set.size());
        for (ModuleDescriptor.Modifier m : ModuleDescriptor.Modifier.values) {
            assertTrue(set.contains(m));
        }
    }

    // --- with / without / xor ---

    @Test
    public void withAddsModifier() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of();
        ModuleDescriptor.Modifier.Set updated = set.with(ModuleDescriptor.Modifier.OPEN);
        assertFalse(set.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(updated.contains(ModuleDescriptor.Modifier.OPEN));
    }

    @Test
    public void withIdempotent() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        ModuleDescriptor.Modifier.Set updated = set.with(ModuleDescriptor.Modifier.OPEN);
        assertEquals(set, updated);
    }

    @Test
    public void withoutRemovesModifier() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC);
        ModuleDescriptor.Modifier.Set updated = set.without(ModuleDescriptor.Modifier.OPEN);
        assertFalse(updated.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(updated.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void withoutAbsentIsNoOp() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        ModuleDescriptor.Modifier.Set updated = set.without(ModuleDescriptor.Modifier.AUTOMATIC);
        assertEquals(set, updated);
    }

    @Test
    public void xorToggles() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        // toggle off
        ModuleDescriptor.Modifier.Set toggled = set.xor(ModuleDescriptor.Modifier.OPEN);
        assertFalse(toggled.contains(ModuleDescriptor.Modifier.OPEN));
        // toggle on
        ModuleDescriptor.Modifier.Set toggledBack = toggled.xor(ModuleDescriptor.Modifier.OPEN);
        assertTrue(toggledBack.contains(ModuleDescriptor.Modifier.OPEN));
    }

    // --- withAll ---

    @Test
    public void withAllTwoModifiers() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of();
        ModuleDescriptor.Modifier.Set updated = set.withAll(
                ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC);
        assertTrue(updated.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(updated.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void withAllSet() {
        ModuleDescriptor.Modifier.Set a = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        ModuleDescriptor.Modifier.Set b = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.AUTOMATIC);
        ModuleDescriptor.Modifier.Set merged = a.withAll(b);
        assertTrue(merged.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(merged.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    // --- mergedWith ---

    @Test
    public void mergedWithIsUnion() {
        ModuleDescriptor.Modifier.Set a = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        ModuleDescriptor.Modifier.Set b = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.AUTOMATIC);
        ModuleDescriptor.Modifier.Set merged = a.mergedWith(b);
        assertTrue(merged.contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(merged.contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    // --- contains (Object) ---

    @Test
    public void containsObjectWrongType() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.ofAll();
        assertFalse(set.contains("not a modifier"));
        assertFalse(set.contains(42));
    }

    // --- iterator ---

    @Test
    public void iteratorEmpty() {
        Iterator<ModuleDescriptor.Modifier> iter = ModuleDescriptor.Modifier.Set.of().iterator();
        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test
    public void iteratorCoversAllElements() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.NATIVE_ACCESS,
                ModuleDescriptor.Modifier.AUTOMATIC);
        Set<ModuleDescriptor.Modifier> iterated = new java.util.HashSet<>();
        for (ModuleDescriptor.Modifier m : set) {
            iterated.add(m);
        }
        assertEquals(Set.of(ModuleDescriptor.Modifier.NATIVE_ACCESS, ModuleDescriptor.Modifier.AUTOMATIC), iterated);
    }

    @Test
    public void iteratorExhaustedThrows() {
        Iterator<ModuleDescriptor.Modifier> iter = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.OPEN).iterator();
        assertTrue(iter.hasNext());
        iter.next();
        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iter::next);
    }

    // --- equals / hashCode ---

    @Test
    public void equalsSameFlags() {
        ModuleDescriptor.Modifier.Set a = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC);
        ModuleDescriptor.Modifier.Set b = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.AUTOMATIC, ModuleDescriptor.Modifier.OPEN);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsDifferentFlags() {
        ModuleDescriptor.Modifier.Set a = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        ModuleDescriptor.Modifier.Set b = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.AUTOMATIC);
        assertNotEquals(a, b);
    }

    @Test
    public void equalsNotOtherType() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of();
        assertNotEquals("not a set", set);
    }

    @Test
    public void equalsNullSafe() {
        assertFalse(ModuleDescriptor.Modifier.Set.of().equals((ModuleDescriptor.Modifier.Set) null));
    }

    // --- toString ---

    @Test
    public void toStringEmpty() {
        assertEquals("(none)", ModuleDescriptor.Modifier.Set.of().toString());
    }

    @Test
    public void toStringSingle() {
        String s = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN).toString();
        assertEquals("OPEN", s);
    }

    @Test
    public void toStringMultiple() {
        String s = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.NATIVE_ACCESS, ModuleDescriptor.Modifier.OPEN).toString();
        // should contain both names
        assertTrue(s.contains("NATIVE_ACCESS"), s);
        assertTrue(s.contains("OPEN"), s);
    }

    // --- containsAny / containsAll ---

    @Test
    public void containsAnyTrue() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        assertTrue(set.containsAny(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void containsAnyFalse() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.UNNAMED);
        assertFalse(set.containsAny(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void containsAllTrue() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(
                ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC, ModuleDescriptor.Modifier.UNNAMED);
        assertTrue(set.containsAll(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void containsAllFalse() {
        ModuleDescriptor.Modifier.Set set = ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.OPEN);
        assertFalse(set.containsAll(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.AUTOMATIC));
    }

    // --- values list ---

    @Test
    public void modifierValuesListContents() {
        assertEquals(4, ModuleDescriptor.Modifier.values.size());
        assertEquals(ModuleDescriptor.Modifier.NATIVE_ACCESS, ModuleDescriptor.Modifier.values.get(0));
        assertEquals(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.values.get(1));
        assertEquals(ModuleDescriptor.Modifier.AUTOMATIC, ModuleDescriptor.Modifier.values.get(2));
        assertEquals(ModuleDescriptor.Modifier.UNNAMED, ModuleDescriptor.Modifier.values.get(3));
    }
}
