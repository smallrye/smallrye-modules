package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.Dependency;
import io.smallrye.modules.desc.ModuleDescriptor;
import io.smallrye.modules.desc.PackageAccess;
import io.smallrye.modules.desc.PackageInfo;

/**
 * Tests for {@link ModuleDescriptor.Builder} and built descriptors.
 */
public final class ModuleDescriptorBuilderTests {

    // --- minimal build ---

    @Test
    public void minimalBuild() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("my.module")
                .build();
        assertEquals("my.module", desc.name());
        assertEquals(Optional.empty(), desc.version());
        assertTrue(desc.modifiers().isEmpty());
        assertEquals(Optional.empty(), desc.mainClass());
        assertEquals(Optional.empty(), desc.location());
        assertTrue(desc.uses().isEmpty());
        assertTrue(desc.provides().isEmpty());
        assertTrue(desc.packages().isEmpty());
    }

    @Test
    public void javaBaseAlwaysPresent() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("my.module")
                .build();
        assertTrue(desc.dependencies().stream()
                .anyMatch(d -> d.moduleName().equals("java.base")),
                "java.base should always be present in dependencies");
    }

    // --- full build ---

    @Test
    public void fullBuild() {
        URI location = URI.create("file:///my/module.jar");
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("my.module")
                .setVersion("2.0")
                .addModifier(ModuleDescriptor.Modifier.OPEN)
                .addModifier(ModuleDescriptor.Modifier.NATIVE_ACCESS)
                .setMainClass("com.example.Main")
                .setLocation(location)
                .addDependency(Dependency.builder("other.module")
                        .addModifier(Dependency.Modifier.OPTIONAL)
                        .build())
                .addUses("com.example.SomeService")
                .addProvides("com.example.SomeService", "com.example.SomeServiceImpl")
                .addPackage("com.example", PackageInfo.EXPORTED)
                .addPackage("com.example.impl", PackageInfo.PRIVATE)
                .build();

        assertEquals("my.module", desc.name());
        assertEquals(Optional.of("2.0"), desc.version());
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
        assertEquals(Optional.of("com.example.Main"), desc.mainClass());
        assertEquals(Optional.of(location), desc.location());
        assertTrue(desc.uses().contains("com.example.SomeService"));
        assertEquals(List.of("com.example.SomeServiceImpl"), desc.provides().get("com.example.SomeService"));
        assertEquals(PackageInfo.EXPORTED, desc.packages().get("com.example"));
        assertEquals(PackageInfo.PRIVATE, desc.packages().get("com.example.impl"));
    }

    // --- version ---

    @Test
    public void setVersionOptional() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .setVersion(Optional.of("1.0"))
                .build();
        assertEquals(Optional.of("1.0"), desc.version());
    }

    @Test
    public void setVersionNull() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .setVersion((String) null)
                .build();
        assertEquals(Optional.empty(), desc.version());
    }

    @Test
    public void setVersionOptionalEmpty() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .setVersion(Optional.empty())
                .build();
        assertEquals(Optional.empty(), desc.version());
    }

    // --- modifiers ---

    @Test
    public void addAndRemoveModifier() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addModifier(ModuleDescriptor.Modifier.OPEN)
                .removeModifier(ModuleDescriptor.Modifier.OPEN)
                .build();
        assertFalse(desc.modifiers().contains(ModuleDescriptor.Modifier.OPEN));
    }

    @Test
    public void mergeModifiers() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addModifier(ModuleDescriptor.Modifier.OPEN)
                .mergeModifiers(ModuleDescriptor.Modifier.Set.of(ModuleDescriptor.Modifier.AUTOMATIC))
                .build();
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    // --- mainClass ---

    @Test
    public void setMainClassOptional() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .setMainClass(Optional.of("com.Main"))
                .build();
        assertEquals(Optional.of("com.Main"), desc.mainClass());
    }

    @Test
    public void setMainClassOptionalEmpty() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .setMainClass(Optional.empty())
                .build();
        assertEquals(Optional.empty(), desc.mainClass());
    }

    // --- location ---

    @Test
    public void setLocationNull() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .setLocation(null)
                .build();
        assertEquals(Optional.empty(), desc.location());
    }

    // --- dependencies ---

    @Test
    public void addDependencyMergesExisting() {
        Dependency dep1 = Dependency.builder("other")
                .addModifier(Dependency.Modifier.OPTIONAL)
                .build();
        Dependency dep2 = Dependency.builder("other")
                .addModifier(Dependency.Modifier.TRANSITIVE)
                .build();
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addDependency(dep1)
                .addDependency(dep2)
                .build();
        // should be merged, not duplicated
        long count = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .count();
        assertEquals(1, count);
        Dependency merged = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertTrue(merged.isOptional());
        assertTrue(merged.isTransitive());
    }

    @Test
    public void addDependenciesCollection() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addDependencies(List.of(
                        Dependency.builder("a").build(),
                        Dependency.builder("b").build()))
                .build();
        assertTrue(desc.dependencies().stream().anyMatch(d -> d.moduleName().equals("a")));
        assertTrue(desc.dependencies().stream().anyMatch(d -> d.moduleName().equals("b")));
    }

    @Test
    public void addDependencyOnJavaBaseIgnored() {
        // adding java.base explicitly should not create a duplicate
        Dependency jb = Dependency.builder("java.base").build();
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addDependency(jb)
                .build();
        long count = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("java.base"))
                .count();
        assertEquals(1, count);
    }

    // --- uses ---

    @Test
    public void addUsesSingle() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addUses("com.example.Svc")
                .build();
        assertTrue(desc.uses().contains("com.example.Svc"));
    }

    @Test
    public void addUsesCollection() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addUses(Set.of("com.example.A", "com.example.B"))
                .build();
        assertTrue(desc.uses().contains("com.example.A"));
        assertTrue(desc.uses().contains("com.example.B"));
    }

    // --- provides ---

    @Test
    public void addProvidesVarargs() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addProvides("com.Svc", "com.Impl1", "com.Impl2")
                .build();
        assertEquals(List.of("com.Impl1", "com.Impl2"), desc.provides().get("com.Svc"));
    }

    @Test
    public void addProvidesCollection() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addProvides("com.Svc", List.of("com.Impl"))
                .build();
        assertEquals(List.of("com.Impl"), desc.provides().get("com.Svc"));
    }

    @Test
    public void addProvidesMap() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addProvides(Map.of("com.Svc", List.of("com.Impl")))
                .build();
        assertEquals(List.of("com.Impl"), desc.provides().get("com.Svc"));
    }

    @Test
    public void addProvidesAccumulates() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addProvides("com.Svc", "com.Impl1")
                .addProvides("com.Svc", "com.Impl2")
                .build();
        List<String> providers = desc.provides().get("com.Svc");
        assertEquals(2, providers.size());
        assertTrue(providers.contains("com.Impl1"));
        assertTrue(providers.contains("com.Impl2"));
    }

    // --- packages ---

    @Test
    public void addPackageMerges() {
        PackageInfo a = PackageInfo.of(PackageAccess.PRIVATE, Set.of("mod.x"), Set.of());
        PackageInfo b = PackageInfo.of(PackageAccess.EXPORTED, Set.of(), Set.of());
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addPackage("com.pkg", a)
                .addPackage("com.pkg", b)
                .build();
        PackageInfo merged = desc.packages().get("com.pkg");
        assertNotNull(merged);
        assertEquals(PackageAccess.EXPORTED, merged.packageAccess());
    }

    @Test
    public void addPackagesMap() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addPackages(Map.of("com.a", PackageInfo.EXPORTED, "com.b", PackageInfo.PRIVATE))
                .build();
        assertEquals(2, desc.packages().size());
    }

    @Test
    public void clearPackages() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addPackage("com.pkg", PackageInfo.EXPORTED)
                .clearPackages()
                .build();
        assertTrue(desc.packages().isEmpty());
    }

    // --- copy constructor ---

    @Test
    public void builderFromExisting() {
        ModuleDescriptor original = ModuleDescriptor.builder()
                .setName("m")
                .setVersion("1.0")
                .addModifier(ModuleDescriptor.Modifier.OPEN)
                .setMainClass("com.Main")
                .addDependency(Dependency.builder("other").build())
                .addUses("com.Svc")
                .addProvides("com.Svc", "com.Impl")
                .addPackage("com.pkg", PackageInfo.EXPORTED)
                .build();

        ModuleDescriptor copy = ModuleDescriptor.builder(original).build();
        assertEquals(original, copy);
    }

    @Test
    public void builderFromExistingAllowsModification() {
        ModuleDescriptor original = ModuleDescriptor.builder()
                .setName("m")
                .setVersion("1.0")
                .build();

        ModuleDescriptor modified = ModuleDescriptor.builder(original)
                .setVersion("2.0")
                .build();
        assertEquals(Optional.of("2.0"), modified.version());
        assertNotEquals(original, modified);
    }

    // --- equals / hashCode ---

    @Test
    public void equalsIdentical() {
        ModuleDescriptor a = ModuleDescriptor.builder()
                .setName("m")
                .setVersion("1.0")
                .addPackage("com.a", PackageInfo.EXPORTED)
                .build();
        ModuleDescriptor b = ModuleDescriptor.builder()
                .setName("m")
                .setVersion("1.0")
                .addPackage("com.a", PackageInfo.EXPORTED)
                .build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void notEqualsDifferentName() {
        ModuleDescriptor a = ModuleDescriptor.builder().setName("a").build();
        ModuleDescriptor b = ModuleDescriptor.builder().setName("b").build();
        assertNotEquals(a, b);
    }

    @Test
    public void equalsNullAndWrongType() {
        ModuleDescriptor desc = ModuleDescriptor.builder().setName("m").build();
        assertNotEquals(null, desc);
        assertFalse(desc.equals((ModuleDescriptor) null));
        assertNotEquals("not a descriptor", desc);
    }

    // --- toString ---

    @Test
    public void toStringContainsName() {
        String s = ModuleDescriptor.builder().setName("my.mod").build().toString();
        assertTrue(s.contains("my.mod"), s);
    }

    // --- immutability ---

    @Test
    public void dependenciesListIsImmutable() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addDependency(Dependency.builder("other").build())
                .build();
        assertThrows(UnsupportedOperationException.class, () -> desc.dependencies().add(Dependency.builder("x").build()));
    }

    @Test
    public void usesSetIsImmutable() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addUses("com.Svc")
                .build();
        assertThrows(UnsupportedOperationException.class, () -> desc.uses().add("com.Other"));
    }

    @Test
    public void packagesMapIsImmutable() {
        ModuleDescriptor desc = ModuleDescriptor.builder()
                .setName("m")
                .addPackage("com.a", PackageInfo.EXPORTED)
                .build();
        assertThrows(UnsupportedOperationException.class, () -> desc.packages().put("com.b", PackageInfo.PRIVATE));
    }
}
