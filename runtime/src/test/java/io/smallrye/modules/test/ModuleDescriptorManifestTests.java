package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.module.FindException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.Dependency;
import io.smallrye.modules.desc.ModuleDescriptor;
import io.smallrye.modules.desc.PackageAccess;

/**
 * Tests for {@link ModuleDescriptor#fromManifest}.
 */
public final class ModuleDescriptorManifestTests {

    private static Manifest manifest(String... keyValues) {
        Manifest mf = new Manifest();
        Attributes attrs = mf.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        for (int i = 0; i < keyValues.length; i += 2) {
            attrs.putValue(keyValues[i], keyValues[i + 1]);
        }
        return mf;
    }

    // --- module name ---

    @Test
    public void automaticModuleNameFromManifest() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "my.module");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertEquals("my.module", desc.name());
    }

    @Test
    public void fallbackToDefaultName() throws IOException {
        Manifest mf = manifest();
        ModuleDescriptor desc = ModuleDescriptor.fromManifest("default.module", null, mf, List.of());
        assertEquals("default.module", desc.name());
    }

    @Test
    public void manifestNameOverridesDefault() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "manifest.name");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest("default.name", null, mf, List.of());
        assertEquals("manifest.name", desc.name());
    }

    @Test
    public void noNameThrowsFindException() {
        Manifest mf = manifest();
        assertThrows(FindException.class, () -> ModuleDescriptor.fromManifest(null, null, mf, List.of()));
    }

    @Test
    public void emptyNameThrowsFindException() {
        Manifest mf = manifest("Automatic-Module-Name", "");
        // empty Automatic-Module-Name is treated the same as null
        assertThrows(FindException.class, () -> ModuleDescriptor.fromManifest(null, null, mf, List.of()));
    }

    // --- version ---

    @Test
    public void moduleVersionAttribute() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Module-Version", "2.0");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertEquals(Optional.of("2.0"), desc.version());
    }

    @Test
    public void implementationVersionFallback() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Implementation-Version", "1.5");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertEquals(Optional.of("1.5"), desc.version());
    }

    @Test
    public void defaultVersionFallback() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, "3.0", mf, List.of());
        assertEquals(Optional.of("3.0"), desc.version());
    }

    @Test
    public void moduleVersionTakesPrecedence() throws IOException {
        Manifest mf = manifest(
                "Automatic-Module-Name", "m",
                "Module-Version", "2.0",
                "Implementation-Version", "1.0");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, "3.0", mf, List.of());
        assertEquals(Optional.of("2.0"), desc.version());
    }

    @Test
    public void noVersion() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertEquals(Optional.empty(), desc.version());
    }

    // --- main class ---

    @Test
    public void mainClass() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Main-Class", "com.example.Main");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertEquals(Optional.of("com.example.Main"), desc.mainClass());
    }

    @Test
    public void noMainClass() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertEquals(Optional.empty(), desc.mainClass());
    }

    // --- modifiers ---

    @Test
    public void alwaysAutomatic() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void enableNativeAccess() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Enable-Native-Access", "ALL-UNNAMED");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
    }

    @Test
    public void enableNativeAccessEmpty() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Enable-Native-Access", "");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        assertFalse(desc.modifiers().contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
    }

    // --- dependencies ---

    @Test
    public void simpleDependency() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Dependencies", "dep.a");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertTrue(dep.isServices());
        assertFalse(dep.isOptional());
        assertFalse(dep.isTransitive());
    }

    @Test
    public void optionalDependency() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Dependencies", "dep.a optional");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertTrue(dep.isOptional());
    }

    @Test
    public void exportDependency() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Dependencies", "dep.a export");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertTrue(dep.isTransitive());
    }

    @Test
    public void optionalExportDependency() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m", "Dependencies", "dep.a optional export");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertTrue(dep.isOptional());
        assertTrue(dep.isTransitive());
    }

    @Test
    public void noDependencies() throws IOException {
        Manifest mf = manifest("Automatic-Module-Name", "m");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        // should only have java.base
        assertEquals(1, desc.dependencies().size());
        assertEquals("java.base", desc.dependencies().get(0).moduleName());
    }

    // --- Add-Opens / Add-Exports ---

    @Test
    public void addOpensHeader() throws IOException {
        Manifest mf = manifest(
                "Automatic-Module-Name", "m",
                "Dependencies", "dep.a",
                "Add-Opens", "dep.a/dep.a.internal");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("dep.a.internal"));
    }

    @Test
    public void addExportsHeader() throws IOException {
        Manifest mf = manifest(
                "Automatic-Module-Name", "m",
                "Dependencies", "dep.a",
                "Add-Exports", "dep.a/dep.a.internal");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.EXPORTED, dep.packageAccesses().get("dep.a.internal"));
    }

    @Test
    public void addOpensOverridesAddExports() throws IOException {
        Manifest mf = manifest(
                "Automatic-Module-Name", "m",
                "Dependencies", "dep.a",
                "Add-Exports", "dep.a/dep.a.pkg",
                "Add-Opens", "dep.a/dep.a.pkg");
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of());
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("dep.a.pkg"));
    }

    // --- extraAccesses ---

    @Test
    public void extraAccessesMerged() throws IOException {
        Manifest mf = manifest(
                "Automatic-Module-Name", "m",
                "Dependencies", "dep.a");
        Map<String, Map<String, PackageAccess>> extras = Map.of(
                "dep.a", Map.of("dep.a.extra", PackageAccess.EXPORTED));
        ModuleDescriptor desc = ModuleDescriptor.fromManifest(null, null, mf, List.of(), extras);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("dep.a"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.EXPORTED, dep.packageAccesses().get("dep.a.extra"));
    }
}
