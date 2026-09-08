package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.Dependency;

/**
 * Tests for {@link Dependency.Modifier} and {@link Dependency.Modifier.Set}.
 */
public final class DependencyModifierTests {

    // --- factory methods ---

    @Test
    public void emptySet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
    }

    @Test
    public void singletonSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.OPTIONAL);
        assertEquals(1, set.size());
        assertTrue(set.contains(Dependency.Modifier.OPTIONAL));
        assertFalse(set.contains(Dependency.Modifier.TRANSITIVE));
    }

    @Test
    public void twoElementSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.TRANSITIVE);
        assertEquals(2, set.size());
        assertTrue(set.contains(Dependency.Modifier.OPTIONAL));
        assertTrue(set.contains(Dependency.Modifier.TRANSITIVE));
    }

    @Test
    public void threeElementSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.TRANSITIVE, Dependency.Modifier.LINKED);
        assertEquals(3, set.size());
    }

    @Test
    public void fourElementSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.TRANSITIVE,
                Dependency.Modifier.LINKED, Dependency.Modifier.READ);
        assertEquals(4, set.size());
    }

    @Test
    public void fiveElementSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.TRANSITIVE,
                Dependency.Modifier.LINKED, Dependency.Modifier.READ,
                Dependency.Modifier.SERVICES);
        assertEquals(5, set.size());
    }

    @Test
    public void sixElementSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.SYNTHETIC, Dependency.Modifier.MANDATED,
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.TRANSITIVE,
                Dependency.Modifier.LINKED, Dependency.Modifier.READ);
        assertEquals(6, set.size());
    }

    @Test
    public void sevenElementSet() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.SYNTHETIC, Dependency.Modifier.MANDATED,
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.TRANSITIVE,
                Dependency.Modifier.LINKED, Dependency.Modifier.READ,
                Dependency.Modifier.SERVICES);
        assertEquals(7, set.size());
    }

    @Test
    public void ofAllContainsAll() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.ofAll();
        assertEquals(Dependency.Modifier.values.size(), set.size());
        for (Dependency.Modifier m : Dependency.Modifier.values) {
            assertTrue(set.contains(m));
        }
    }

    // --- with / without / xor ---

    @Test
    public void withAddsModifier() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of();
        Dependency.Modifier.Set updated = set.with(Dependency.Modifier.LINKED);
        assertTrue(updated.contains(Dependency.Modifier.LINKED));
    }

    @Test
    public void withoutRemovesModifier() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.LINKED, Dependency.Modifier.READ);
        Dependency.Modifier.Set updated = set.without(Dependency.Modifier.LINKED);
        assertFalse(updated.contains(Dependency.Modifier.LINKED));
        assertTrue(updated.contains(Dependency.Modifier.READ));
    }

    @Test
    public void xorToggles() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.LINKED);
        Dependency.Modifier.Set toggled = set.xor(Dependency.Modifier.LINKED);
        assertFalse(toggled.contains(Dependency.Modifier.LINKED));
    }

    // --- mergedWith (special SYNTHETIC semantics) ---

    @Test
    public void mergedWithUnionExceptSynthetic() {
        // SYNTHETIC only present in result if present in both sets
        Dependency.Modifier.Set a = Dependency.Modifier.Set.of(
                Dependency.Modifier.SYNTHETIC, Dependency.Modifier.OPTIONAL);
        Dependency.Modifier.Set b = Dependency.Modifier.Set.of(Dependency.Modifier.TRANSITIVE);
        Dependency.Modifier.Set merged = a.mergedWith(b);
        assertTrue(merged.contains(Dependency.Modifier.OPTIONAL));
        assertTrue(merged.contains(Dependency.Modifier.TRANSITIVE));
        assertFalse(merged.contains(Dependency.Modifier.SYNTHETIC),
                "SYNTHETIC should be dropped when only in one set");
    }

    @Test
    public void mergedWithSyntheticInBoth() {
        Dependency.Modifier.Set a = Dependency.Modifier.Set.of(
                Dependency.Modifier.SYNTHETIC, Dependency.Modifier.OPTIONAL);
        Dependency.Modifier.Set b = Dependency.Modifier.Set.of(
                Dependency.Modifier.SYNTHETIC, Dependency.Modifier.TRANSITIVE);
        Dependency.Modifier.Set merged = a.mergedWith(b);
        assertTrue(merged.contains(Dependency.Modifier.SYNTHETIC),
                "SYNTHETIC should be kept when in both sets");
        assertTrue(merged.contains(Dependency.Modifier.OPTIONAL));
        assertTrue(merged.contains(Dependency.Modifier.TRANSITIVE));
    }

    @Test
    public void mergedWithSyntheticInNeither() {
        Dependency.Modifier.Set a = Dependency.Modifier.Set.of(Dependency.Modifier.OPTIONAL);
        Dependency.Modifier.Set b = Dependency.Modifier.Set.of(Dependency.Modifier.TRANSITIVE);
        Dependency.Modifier.Set merged = a.mergedWith(b);
        assertFalse(merged.contains(Dependency.Modifier.SYNTHETIC));
    }

    // --- withAll ---

    @Test
    public void withAllTwoModifiers() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of();
        Dependency.Modifier.Set updated = set.withAll(Dependency.Modifier.LINKED, Dependency.Modifier.READ);
        assertTrue(updated.contains(Dependency.Modifier.LINKED));
        assertTrue(updated.contains(Dependency.Modifier.READ));
    }

    @Test
    public void withAllSet() {
        Dependency.Modifier.Set a = Dependency.Modifier.Set.of(Dependency.Modifier.LINKED);
        Dependency.Modifier.Set b = Dependency.Modifier.Set.of(Dependency.Modifier.READ);
        Dependency.Modifier.Set merged = a.withAll(b);
        assertTrue(merged.contains(Dependency.Modifier.LINKED));
        assertTrue(merged.contains(Dependency.Modifier.READ));
    }

    // --- containsAny / containsAll ---

    @Test
    public void containsAnyTrue() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.LINKED);
        assertTrue(set.containsAny(Dependency.Modifier.LINKED, Dependency.Modifier.READ));
    }

    @Test
    public void containsAnyFalse() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.OPTIONAL);
        assertFalse(set.containsAny(Dependency.Modifier.LINKED, Dependency.Modifier.READ));
    }

    @Test
    public void containsAnyThree() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.SERVICES);
        assertTrue(set.containsAny(Dependency.Modifier.LINKED, Dependency.Modifier.READ, Dependency.Modifier.SERVICES));
    }

    @Test
    public void containsAnyFour() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.MANDATED);
        assertTrue(set.containsAny(Dependency.Modifier.LINKED, Dependency.Modifier.READ,
                Dependency.Modifier.SERVICES, Dependency.Modifier.MANDATED));
    }

    @Test
    public void containsAllTrue() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.LINKED, Dependency.Modifier.READ, Dependency.Modifier.SERVICES);
        assertTrue(set.containsAll(Dependency.Modifier.LINKED, Dependency.Modifier.READ));
    }

    @Test
    public void containsAllFalse() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(Dependency.Modifier.LINKED);
        assertFalse(set.containsAll(Dependency.Modifier.LINKED, Dependency.Modifier.READ));
    }

    @Test
    public void containsAllThree() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.LINKED, Dependency.Modifier.READ, Dependency.Modifier.SERVICES);
        assertTrue(set.containsAll(Dependency.Modifier.LINKED, Dependency.Modifier.READ, Dependency.Modifier.SERVICES));
    }

    @Test
    public void containsAllFour() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.ofAll();
        assertTrue(set.containsAll(Dependency.Modifier.LINKED, Dependency.Modifier.READ,
                Dependency.Modifier.SERVICES, Dependency.Modifier.OPTIONAL));
    }

    // --- contains(Object) ---

    @Test
    public void containsObjectWrongType() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.ofAll();
        assertFalse(set.contains("not a modifier"));
    }

    // --- iterator ---

    @Test
    public void iteratorEmpty() {
        Iterator<Dependency.Modifier> iter = Dependency.Modifier.Set.of().iterator();
        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test
    public void iteratorCoversAllElements() {
        Dependency.Modifier.Set set = Dependency.Modifier.Set.of(
                Dependency.Modifier.OPTIONAL, Dependency.Modifier.SERVICES);
        Set<Dependency.Modifier> iterated = new java.util.HashSet<>();
        for (Dependency.Modifier m : set) {
            iterated.add(m);
        }
        assertEquals(Set.of(Dependency.Modifier.OPTIONAL, Dependency.Modifier.SERVICES), iterated);
    }

    // --- equals / hashCode ---

    @Test
    public void equalsSameFlags() {
        Dependency.Modifier.Set a = Dependency.Modifier.Set.of(
                Dependency.Modifier.LINKED, Dependency.Modifier.READ);
        Dependency.Modifier.Set b = Dependency.Modifier.Set.of(
                Dependency.Modifier.READ, Dependency.Modifier.LINKED);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsDifferentFlags() {
        Dependency.Modifier.Set a = Dependency.Modifier.Set.of(Dependency.Modifier.LINKED);
        Dependency.Modifier.Set b = Dependency.Modifier.Set.of(Dependency.Modifier.READ);
        assertNotEquals(a, b);
    }

    // --- toString ---

    @Test
    public void toStringEmpty() {
        assertEquals("(none)", Dependency.Modifier.Set.of().toString());
    }

    @Test
    public void toStringSingle() {
        String s = Dependency.Modifier.Set.of(Dependency.Modifier.OPTIONAL).toString();
        assertEquals("OPTIONAL", s);
    }

    // --- values list ---

    @Test
    public void modifierValuesListSize() {
        assertEquals(7, Dependency.Modifier.values.size());
    }

    @Test
    public void modifierValuesListOrder() {
        assertEquals(Dependency.Modifier.SYNTHETIC, Dependency.Modifier.values.get(0));
        assertEquals(Dependency.Modifier.MANDATED, Dependency.Modifier.values.get(1));
        assertEquals(Dependency.Modifier.OPTIONAL, Dependency.Modifier.values.get(2));
        assertEquals(Dependency.Modifier.TRANSITIVE, Dependency.Modifier.values.get(3));
        assertEquals(Dependency.Modifier.LINKED, Dependency.Modifier.values.get(4));
        assertEquals(Dependency.Modifier.READ, Dependency.Modifier.values.get(5));
        assertEquals(Dependency.Modifier.SERVICES, Dependency.Modifier.values.get(6));
    }
}
