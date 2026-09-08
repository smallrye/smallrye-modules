package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.PackageAccess;
import io.smallrye.modules.desc.PackageInfo;

/**
 * Tests for {@link PackageInfo}.
 */
public final class PackageInfoTests {

    // --- canonical instances ---

    @Test
    public void privateInstanceProperties() {
        assertEquals(PackageAccess.PRIVATE, PackageInfo.PRIVATE.packageAccess());
        assertTrue(PackageInfo.PRIVATE.exportTargets().isEmpty());
        assertTrue(PackageInfo.PRIVATE.openTargets().isEmpty());
    }

    @Test
    public void exportedInstanceProperties() {
        assertEquals(PackageAccess.EXPORTED, PackageInfo.EXPORTED.packageAccess());
        assertTrue(PackageInfo.EXPORTED.exportTargets().isEmpty());
        assertTrue(PackageInfo.EXPORTED.openTargets().isEmpty());
    }

    @Test
    public void openInstanceProperties() {
        assertEquals(PackageAccess.OPEN, PackageInfo.OPEN.packageAccess());
        assertTrue(PackageInfo.OPEN.exportTargets().isEmpty());
        assertTrue(PackageInfo.OPEN.openTargets().isEmpty());
    }

    // --- forAccess ---

    @Test
    public void forAccessReturnsCanonicalInstances() {
        assertSame(PackageInfo.PRIVATE, PackageInfo.forAccess(PackageAccess.PRIVATE));
        assertSame(PackageInfo.EXPORTED, PackageInfo.forAccess(PackageAccess.EXPORTED));
        assertSame(PackageInfo.OPEN, PackageInfo.forAccess(PackageAccess.OPEN));
    }

    // --- of factory ---

    @Test
    public void ofPrivateNoTargetsReturnsCanonical() {
        assertSame(PackageInfo.PRIVATE, PackageInfo.of(PackageAccess.PRIVATE, Set.of(), Set.of()));
    }

    @Test
    public void ofExportedNoTargetsReturnsCanonical() {
        assertSame(PackageInfo.EXPORTED, PackageInfo.of(PackageAccess.EXPORTED, Set.of(), Set.of()));
    }

    @Test
    public void ofOpenReturnsCanonicalRegardlessOfTargets() {
        assertSame(PackageInfo.OPEN, PackageInfo.of(PackageAccess.OPEN, Set.of("mod.a"), Set.of("mod.b")));
    }

    @Test
    public void ofPrivateWithExportTargets() {
        PackageInfo pi = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.a", "mod.b"), Set.of());
        assertEquals(PackageAccess.PRIVATE, pi.packageAccess());
        assertEquals(Set.of("mod.a", "mod.b"), pi.exportTargets());
        assertTrue(pi.openTargets().isEmpty());
    }

    @Test
    public void ofPrivateWithOpenTargets() {
        PackageInfo pi = PackageInfo.of(PackageAccess.PRIVATE, Set.of(), Set.of("mod.c"));
        assertEquals(PackageAccess.PRIVATE, pi.packageAccess());
        assertTrue(pi.exportTargets().isEmpty());
        assertEquals(Set.of("mod.c"), pi.openTargets());
    }

    @Test
    public void ofExportedClearsExportTargets() {
        // when access is EXPORTED, export targets are redundant and should be cleared
        PackageInfo pi = PackageInfo.of(PackageAccess.EXPORTED, Set.of("mod.a"), Set.of());
        assertSame(PackageInfo.EXPORTED, pi);
    }

    @Test
    public void ofExportedWithOpenTargets() {
        PackageInfo pi = PackageInfo.of(PackageAccess.EXPORTED, Set.of(), Set.of("mod.c"));
        assertEquals(PackageAccess.EXPORTED, pi.packageAccess());
        assertTrue(pi.exportTargets().isEmpty());
        assertEquals(Set.of("mod.c"), pi.openTargets());
    }

    // --- mergedWith ---

    @Test
    public void mergedWithUpgradesAccess() {
        PackageInfo result = PackageInfo.PRIVATE.mergedWith(PackageInfo.EXPORTED);
        assertEquals(PackageAccess.EXPORTED, result.packageAccess());
    }

    @Test
    public void mergedWithCombinesTargets() {
        PackageInfo a = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.a"), Set.of());
        PackageInfo b = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.b"), Set.of("mod.c"));
        PackageInfo result = a.mergedWith(b);
        assertEquals(PackageAccess.PRIVATE, result.packageAccess());
        assertTrue(result.exportTargets().containsAll(Set.of("mod.a", "mod.b")));
        assertEquals(Set.of("mod.c"), result.openTargets());
    }

    @Test
    public void mergedWithOpenDominates() {
        PackageInfo result = PackageInfo.EXPORTED.mergedWith(PackageInfo.OPEN);
        assertSame(PackageInfo.OPEN, result);
    }

    // --- static merge ---

    @Test
    public void mergeNullNull() {
        assertSame(PackageInfo.PRIVATE, PackageInfo.merge(null, null));
    }

    @Test
    public void mergeNullB() {
        assertSame(PackageInfo.EXPORTED, PackageInfo.merge(null, PackageInfo.EXPORTED));
    }

    @Test
    public void mergeANull() {
        assertSame(PackageInfo.OPEN, PackageInfo.merge(PackageInfo.OPEN, null));
    }

    @Test
    public void mergeAB() {
        PackageInfo result = PackageInfo.merge(PackageInfo.PRIVATE, PackageInfo.EXPORTED);
        assertEquals(PackageAccess.EXPORTED, result.packageAccess());
    }

    // --- withAccessAtLeast ---

    @Test
    public void withAccessAtLeastUpgrades() {
        PackageInfo result = PackageInfo.PRIVATE.withAccessAtLeast(PackageAccess.EXPORTED);
        assertEquals(PackageAccess.EXPORTED, result.packageAccess());
    }

    @Test
    public void withAccessAtLeastNoOp() {
        PackageInfo result = PackageInfo.EXPORTED.withAccessAtLeast(PackageAccess.PRIVATE);
        assertSame(PackageInfo.EXPORTED, result);
    }

    @Test
    public void withAccessAtLeastToOpen() {
        PackageInfo result = PackageInfo.PRIVATE.withAccessAtLeast(PackageAccess.OPEN);
        assertSame(PackageInfo.OPEN, result);
    }

    // --- withExportTargets ---

    @Test
    public void withExportTargetsAddsToPrivate() {
        PackageInfo result = PackageInfo.PRIVATE.withExportTargets(Set.of("mod.x"));
        assertEquals(PackageAccess.PRIVATE, result.packageAccess());
        assertEquals(Set.of("mod.x"), result.exportTargets());
    }

    @Test
    public void withExportTargetsMerges() {
        PackageInfo initial = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.a"), Set.of());
        PackageInfo result = initial.withExportTargets(Set.of("mod.b"));
        assertTrue(result.exportTargets().containsAll(Set.of("mod.a", "mod.b")));
    }

    @Test
    public void withExportTargetsOnExportedIsNoOp() {
        // EXPORTED already exports to all, so adding targets is a no-op
        PackageInfo result = PackageInfo.EXPORTED.withExportTargets(Set.of("mod.x"));
        assertSame(PackageInfo.EXPORTED, result);
    }

    // --- equals / hashCode ---

    @Test
    public void equalsReflexive() {
        assertEquals(PackageInfo.PRIVATE, PackageInfo.PRIVATE);
        assertEquals(PackageInfo.EXPORTED, PackageInfo.EXPORTED);
        assertEquals(PackageInfo.OPEN, PackageInfo.OPEN);
    }

    @Test
    public void equalsWithSameProperties() {
        PackageInfo a = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.a"), Set.of("mod.b"));
        PackageInfo b = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.a"), Set.of("mod.b"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsDifferentAccess() {
        assertNotEquals(PackageInfo.PRIVATE, PackageInfo.EXPORTED);
    }

    @Test
    public void notEqualsDifferentTargets() {
        PackageInfo a = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.a"), Set.of());
        PackageInfo b = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.b"), Set.of());
        assertNotEquals(a, b);
    }

    @Test
    public void equalsNullSafe() {
        assertNotEquals(null, PackageInfo.PRIVATE);
        assertFalse(PackageInfo.PRIVATE.equals((PackageInfo) null));
    }

    @Test
    public void toStringContainsAccess() {
        String s = PackageInfo.EXPORTED.toString();
        assertTrue(s.contains("EXPORTED"), "toString should contain access level: " + s);
    }
}
