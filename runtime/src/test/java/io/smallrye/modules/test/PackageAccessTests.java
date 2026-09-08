package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.PackageAccess;

/**
 * Tests for the {@link PackageAccess} enum.
 */
public final class PackageAccessTests {

    @Test
    public void valuesListContents() {
        assertEquals(List.of(PackageAccess.PRIVATE, PackageAccess.EXPORTED, PackageAccess.OPEN), PackageAccess.values);
    }

    @Test
    public void valuesListOrdering() {
        assertTrue(PackageAccess.PRIVATE.ordinal() < PackageAccess.EXPORTED.ordinal());
        assertTrue(PackageAccess.EXPORTED.ordinal() < PackageAccess.OPEN.ordinal());
    }

    @Test
    public void isAtLeastPrivate() {
        assertTrue(PackageAccess.PRIVATE.isAtLeast(PackageAccess.PRIVATE));
        assertFalse(PackageAccess.PRIVATE.isAtLeast(PackageAccess.EXPORTED));
        assertFalse(PackageAccess.PRIVATE.isAtLeast(PackageAccess.OPEN));
    }

    @Test
    public void isAtLeastExported() {
        assertTrue(PackageAccess.EXPORTED.isAtLeast(PackageAccess.PRIVATE));
        assertTrue(PackageAccess.EXPORTED.isAtLeast(PackageAccess.EXPORTED));
        assertFalse(PackageAccess.EXPORTED.isAtLeast(PackageAccess.OPEN));
    }

    @Test
    public void isAtLeastOpen() {
        assertTrue(PackageAccess.OPEN.isAtLeast(PackageAccess.PRIVATE));
        assertTrue(PackageAccess.OPEN.isAtLeast(PackageAccess.EXPORTED));
        assertTrue(PackageAccess.OPEN.isAtLeast(PackageAccess.OPEN));
    }

    @Test
    public void minReturnsLesser() {
        assertEquals(PackageAccess.PRIVATE, PackageAccess.min(PackageAccess.PRIVATE, PackageAccess.EXPORTED));
        assertEquals(PackageAccess.PRIVATE, PackageAccess.min(PackageAccess.EXPORTED, PackageAccess.PRIVATE));
        assertEquals(PackageAccess.PRIVATE, PackageAccess.min(PackageAccess.PRIVATE, PackageAccess.OPEN));
        assertEquals(PackageAccess.EXPORTED, PackageAccess.min(PackageAccess.EXPORTED, PackageAccess.OPEN));
        assertEquals(PackageAccess.EXPORTED, PackageAccess.min(PackageAccess.OPEN, PackageAccess.EXPORTED));
    }

    @Test
    public void minSameReturnsSame() {
        for (PackageAccess access : PackageAccess.values) {
            assertEquals(access, PackageAccess.min(access, access));
        }
    }

    @Test
    public void maxReturnsGreater() {
        assertEquals(PackageAccess.EXPORTED, PackageAccess.max(PackageAccess.PRIVATE, PackageAccess.EXPORTED));
        assertEquals(PackageAccess.EXPORTED, PackageAccess.max(PackageAccess.EXPORTED, PackageAccess.PRIVATE));
        assertEquals(PackageAccess.OPEN, PackageAccess.max(PackageAccess.PRIVATE, PackageAccess.OPEN));
        assertEquals(PackageAccess.OPEN, PackageAccess.max(PackageAccess.EXPORTED, PackageAccess.OPEN));
        assertEquals(PackageAccess.OPEN, PackageAccess.max(PackageAccess.OPEN, PackageAccess.EXPORTED));
    }

    @Test
    public void maxSameReturnsSame() {
        for (PackageAccess access : PackageAccess.values) {
            assertEquals(access, PackageAccess.max(access, access));
        }
    }
}
