package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.ModuleLoader;
import io.smallrye.modules.desc.Dependency;
import io.smallrye.modules.desc.PackageAccess;

/**
 * Tests for {@link Dependency} and its builder.
 */
public final class DependencyTests {

    // --- JAVA_BASE constant ---

    @Test
    public void javaBaseModuleName() {
        assertEquals("java.base", Dependency.JAVA_BASE.moduleName());
    }

    @Test
    public void javaBaseModifiers() {
        assertTrue(Dependency.JAVA_BASE.isSynthetic());
        assertTrue(Dependency.JAVA_BASE.isMandated());
    }

    @Test
    public void javaBaseEmptyPackageAccesses() {
        assertTrue(Dependency.JAVA_BASE.packageAccesses().isEmpty());
    }

    @Test
    public void javaBaseNoModuleLoader() {
        assertEquals(Optional.empty(), Dependency.JAVA_BASE.moduleLoader());
    }

    // --- builder basics ---

    @Test
    public void builderMinimal() {
        Dependency dep = Dependency.builder("my.module").build();
        assertEquals("my.module", dep.moduleName());
        assertTrue(dep.modifiers().isEmpty());
        assertEquals(Optional.empty(), dep.moduleLoader());
        assertTrue(dep.packageAccesses().isEmpty());
    }

    @Test
    public void builderWithModifiers() {
        Dependency dep = Dependency.builder("my.module")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .addModifier(Dependency.Modifier.TRANSITIVE)
                .build();
        assertTrue(dep.isOptional());
        assertTrue(dep.isTransitive());
    }

    @Test
    public void builderRemoveModifier() {
        Dependency dep = Dependency.builder("my.module")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .addModifier(Dependency.Modifier.TRANSITIVE)
                .removeModifier(Dependency.Modifier.OPTIONAL)
                .build();
        assertFalse(dep.isOptional());
        assertTrue(dep.isTransitive());
    }

    @Test
    public void builderMergeModifiers() {
        Dependency.Modifier.Set mods = Dependency.Modifier.Set.of(
                Dependency.Modifier.LINKED, Dependency.Modifier.READ, Dependency.Modifier.SERVICES);
        Dependency dep = Dependency.builder("my.module")
                .mergeModifiers(mods)
                .build();
        assertTrue(dep.isLinked());
        assertTrue(dep.isRead());
        assertTrue(dep.isServices());
    }

    @Test
    public void builderWithModuleLoader() {
        Dependency dep = Dependency.builder("my.module")
                .setModuleLoader(ModuleLoader.BOOT)
                .build();
        assertTrue(dep.moduleLoader().isPresent());
        assertSame(ModuleLoader.BOOT, dep.moduleLoader().get());
    }

    @Test
    public void builderWithOptionalModuleLoader() {
        Dependency dep = Dependency.builder("my.module")
                .setModuleLoader(Optional.of(ModuleLoader.BOOT))
                .build();
        assertTrue(dep.moduleLoader().isPresent());
    }

    @Test
    public void builderWithPackageAccesses() {
        Dependency dep = Dependency.builder("my.module")
                .addPackageAccess("com.foo", PackageAccess.EXPORTED)
                .addPackageAccess("com.bar", PackageAccess.OPEN)
                .build();
        assertEquals(PackageAccess.EXPORTED, dep.packageAccesses().get("com.foo"));
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("com.bar"));
    }

    @Test
    public void builderAddPackageAccessesMerges() {
        Dependency dep = Dependency.builder("my.module")
                .addPackageAccess("com.foo", PackageAccess.EXPORTED)
                .addPackageAccess("com.foo", PackageAccess.OPEN)
                .build();
        // should merge to max
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("com.foo"));
    }

    @Test
    public void builderAddPackageAccessesMap() {
        Dependency dep = Dependency.builder("my.module")
                .addPackageAccesses(Map.of("com.foo", PackageAccess.EXPORTED, "com.bar", PackageAccess.PRIVATE))
                .build();
        assertEquals(2, dep.packageAccesses().size());
    }

    @Test
    public void builderSetPackageAccessesReplaces() {
        Dependency dep = Dependency.builder("my.module")
                .addPackageAccess("com.old", PackageAccess.EXPORTED)
                .setPackageAccesses(Map.of("com.new_", PackageAccess.OPEN))
                .build();
        assertNull(dep.packageAccesses().get("com.old"));
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("com.new_"));
    }

    // --- convenience query methods ---

    @Test
    public void convenienceMethods() {
        Dependency dep = Dependency.builder("m")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .addModifier(Dependency.Modifier.LINKED)
                .addModifier(Dependency.Modifier.READ)
                .addModifier(Dependency.Modifier.SERVICES)
                .build();
        assertTrue(dep.isOptional());
        assertFalse(dep.isNonOptional());
        assertTrue(dep.isLinked());
        assertFalse(dep.isNonLinked());
        assertTrue(dep.isRead());
        assertFalse(dep.isNonRead());
        assertTrue(dep.isServices());
        assertFalse(dep.isNonServices());
        assertFalse(dep.isTransitive());
        assertTrue(dep.isNonTransitive());
        assertFalse(dep.isSynthetic());
        assertTrue(dep.isNonSynthetic());
        assertFalse(dep.isMandated());
        assertTrue(dep.isNonMandated());
    }

    // --- mergedWith ---

    @Test
    public void mergedWithCombinesModifiers() {
        Dependency a = Dependency.builder("m")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .build();
        Dependency b = Dependency.builder("m")
                .addModifier(Dependency.Modifier.TRANSITIVE)
                .build();
        Dependency merged = a.mergedWith(b);
        assertEquals("m", merged.moduleName());
        assertTrue(merged.isOptional());
        assertTrue(merged.isTransitive());
    }

    @Test
    public void mergedWithCombinesPackageAccesses() {
        Dependency a = Dependency.builder("m")
                .addPackageAccess("com.foo", PackageAccess.EXPORTED)
                .build();
        Dependency b = Dependency.builder("m")
                .addPackageAccess("com.bar", PackageAccess.OPEN)
                .build();
        Dependency merged = a.mergedWith(b);
        assertEquals(PackageAccess.EXPORTED, merged.packageAccesses().get("com.foo"));
        assertEquals(PackageAccess.OPEN, merged.packageAccesses().get("com.bar"));
    }

    @Test
    public void mergedWithMismatchedNamesThrows() {
        Dependency a = Dependency.builder("mod.a").build();
        Dependency b = Dependency.builder("mod.b").build();
        assertThrows(IllegalArgumentException.class, () -> a.mergedWith(b));
    }

    @Test
    public void mergedWithConflictingModuleLoadersThrows() {
        Dependency a = Dependency.builder("m")
                .setModuleLoader(ModuleLoader.BOOT)
                .build();
        Dependency b = Dependency.builder("m")
                .setModuleLoader(ModuleLoader.EMPTY)
                .build();
        assertThrows(IllegalArgumentException.class, () -> a.mergedWith(b));
    }

    @Test
    public void mergedWithModuleLoaderOnlyInOne() {
        Dependency a = Dependency.builder("m")
                .setModuleLoader(ModuleLoader.BOOT)
                .build();
        Dependency b = Dependency.builder("m").build();
        Dependency merged = a.mergedWith(b);
        assertTrue(merged.moduleLoader().isPresent());
        assertSame(ModuleLoader.BOOT, merged.moduleLoader().get());
    }

    @Test
    public void mergedWithSelfReturnsSelf() {
        Dependency dep = Dependency.builder("m")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .build();
        Dependency merged = dep.mergedWith(dep);
        assertSame(dep, merged);
    }

    // --- withAdditionalPackageAccesses ---

    @Test
    public void withAdditionalPackageAccesses() {
        Dependency dep = Dependency.builder("m")
                .addPackageAccess("com.foo", PackageAccess.EXPORTED)
                .build();
        Dependency updated = dep.withAdditionalPackageAccesses(Map.of("com.bar", PackageAccess.OPEN));
        assertEquals(PackageAccess.EXPORTED, updated.packageAccesses().get("com.foo"));
        assertEquals(PackageAccess.OPEN, updated.packageAccesses().get("com.bar"));
    }

    // --- equals / hashCode ---

    @Test
    public void equalsIdentical() {
        Dependency a = Dependency.builder("m")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .build();
        Dependency b = Dependency.builder("m")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsDifferentName() {
        Dependency a = Dependency.builder("m.a").build();
        Dependency b = Dependency.builder("m.b").build();
        assertNotEquals(a, b);
    }

    @Test
    public void notEqualsDifferentModifiers() {
        Dependency a = Dependency.builder("m")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .build();
        Dependency b = Dependency.builder("m")
                .addModifier(Dependency.Modifier.TRANSITIVE)
                .build();
        assertNotEquals(a, b);
    }

    @Test
    public void equalsNullSafe() {
        Dependency dep = Dependency.builder("m").build();
        assertNotEquals(null, dep);
        assertFalse(dep.equals((Dependency) null));
    }

    @Test
    public void equalsWrongType() {
        Dependency dep = Dependency.builder("m").build();
        assertNotEquals("not a dependency", dep);
    }

    // --- toString ---

    @Test
    public void toStringContainsModuleName() {
        String s = Dependency.builder("my.module").build().toString();
        assertTrue(s.contains("my.module"), s);
    }
}
