package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.desc.Dependency;
import io.smallrye.modules.desc.ModuleDescriptor;
import io.smallrye.modules.desc.PackageAccess;
import io.smallrye.modules.desc.PackageInfo;

/**
 * Tests for {@link ModuleDescriptor#fromXml(java.io.BufferedReader)} and related overloads.
 */
public final class ModuleDescriptorXmlTests {

    private static final String NS = "urn:jboss:module:3.0";

    private static ModuleDescriptor parseXml(String xml) throws IOException {
        try (BufferedReader br = new BufferedReader(new StringReader(xml))) {
            return ModuleDescriptor.fromXml(br);
        }
    }

    // --- minimal module ---

    @Test
    public void minimalModule() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"my.module\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertEquals("my.module", desc.name());
        assertEquals(Optional.empty(), desc.version());
        assertTrue(desc.modifiers().isEmpty());
        assertEquals(Optional.empty(), desc.mainClass());
    }

    // --- version ---

    @Test
    public void moduleWithVersion() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" version=\"3.0\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertEquals(Optional.of("3.0"), desc.version());
    }

    // --- modifiers ---

    @Test
    public void automaticModule() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" automatic=\"true\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    @Test
    public void unnamedModule() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" unnamed=\"true\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.UNNAMED));
    }

    @Test
    public void openModule() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" open=\"true\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.OPEN));
    }

    @Test
    public void nativeAccessModule() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" native-access=\"true\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
    }

    @Test
    public void modifierFalseIsIgnored() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" open=\"false\" automatic=\"false\"/>";
        ModuleDescriptor desc = parseXml(xml);
        assertFalse(desc.modifiers().contains(ModuleDescriptor.Modifier.OPEN));
        assertFalse(desc.modifiers().contains(ModuleDescriptor.Modifier.AUTOMATIC));
    }

    // --- main-class ---

    @Test
    public void mainClass() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <main-class name="com.example.Main"/>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertEquals(Optional.of("com.example.Main"), desc.mainClass());
    }

    // --- dependencies ---

    @Test
    public void simpleDependency() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other.module"/>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other.module"))
                .findFirst()
                .orElseThrow();
        // defaults: services, linked, read
        assertTrue(dep.isServices());
        assertTrue(dep.isLinked());
        assertTrue(dep.isRead());
    }

    @Test
    public void transitiveDependency() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other" transitive="true"/>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertTrue(dep.isTransitive());
    }

    @Test
    public void optionalDependency() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other" optional="true"/>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertTrue(dep.isOptional());
    }

    @Test
    public void dependencyWithLinkedFalse() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other" linked="false"/>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertFalse(dep.isLinked());
    }

    @Test
    public void dependencyWithReadFalse() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other" read="false"/>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertFalse(dep.isRead());
    }

    @Test
    public void dependencyWithServicesFalse() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other" services="false"/>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertFalse(dep.isServices());
    }

    @Test
    public void dependencyWithAddExports() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other">
                            <add-exports name="com.pkg"/>
                        </dependency>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.EXPORTED, dep.packageAccesses().get("com.pkg"));
    }

    @Test
    public void dependencyWithAddOpens() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other">
                            <add-opens name="com.internal"/>
                        </dependency>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("com.internal"));
    }

    @Test
    public void dependencyWithAddExportsAndOpens() throws IOException {
        // add-opens should upgrade add-exports
        String xml = """
                <module xmlns="%s" name="m">
                    <dependencies>
                        <dependency name="other">
                            <add-exports name="com.pkg"/>
                            <add-opens name="com.pkg"/>
                        </dependency>
                    </dependencies>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        Dependency dep = desc.dependencies().stream()
                .filter(d -> d.moduleName().equals("other"))
                .findFirst()
                .orElseThrow();
        assertEquals(PackageAccess.OPEN, dep.packageAccesses().get("com.pkg"));
    }

    // --- packages ---

    @Test
    public void privatePackage() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <private package="com.internal"/>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertEquals(PackageInfo.PRIVATE, desc.packages().get("com.internal"));
    }

    @Test
    public void exportedPackage() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <export package="com.api"/>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertEquals(PackageInfo.EXPORTED, desc.packages().get("com.api"));
    }

    @Test
    public void openPackage() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <open package="com.reflective"/>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertEquals(PackageInfo.OPEN, desc.packages().get("com.reflective"));
    }

    @Test
    public void privatePackageWithExportTo() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <private package="com.internal">
                            <export-to module="friend.module"/>
                        </private>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        PackageInfo pi = desc.packages().get("com.internal");
        assertNotNull(pi);
        assertEquals(PackageAccess.PRIVATE, pi.packageAccess());
        assertTrue(pi.exportTargets().contains("friend.module"));
    }

    @Test
    public void privatePackageWithOpenTo() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <private package="com.internal">
                            <open-to module="friend.module"/>
                        </private>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        PackageInfo pi = desc.packages().get("com.internal");
        assertEquals(PackageAccess.PRIVATE, pi.packageAccess());
        assertTrue(pi.openTargets().contains("friend.module"));
    }

    @Test
    public void exportedPackageWithOpenTo() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <export package="com.api">
                            <open-to module="test.module"/>
                        </export>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        PackageInfo pi = desc.packages().get("com.api");
        assertEquals(PackageAccess.EXPORTED, pi.packageAccess());
        assertTrue(pi.openTargets().contains("test.module"));
    }

    @Test
    public void privatePackageWithMultipleExportTo() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <packages>
                        <private package="com.internal">
                            <export-to module="friend.a"/>
                            <export-to module="friend.b"/>
                            <export-to module="friend.c"/>
                        </private>
                    </packages>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        PackageInfo pi = desc.packages().get("com.internal");
        assertEquals(3, pi.exportTargets().size());
        assertTrue(pi.exportTargets().containsAll(Set.of("friend.a", "friend.b", "friend.c")));
    }

    // --- uses ---

    @Test
    public void usesElement() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <uses>
                        <use name="com.example.SomeService"/>
                        <use name="com.example.OtherService"/>
                    </uses>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertTrue(desc.uses().contains("com.example.SomeService"));
        assertTrue(desc.uses().contains("com.example.OtherService"));
    }

    // --- provides ---

    @Test
    public void providesElement() throws IOException {
        String xml = """
                <module xmlns="%s" name="m">
                    <provides>
                        <provide name="com.Svc">
                            <with name="com.Impl1"/>
                            <with name="com.Impl2"/>
                        </provide>
                    </provides>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertEquals(List.of("com.Impl1", "com.Impl2"), desc.provides().get("com.Svc"));
    }

    // --- full module ---

    @Test
    public void fullModule() throws IOException {
        String xml = """
                <module xmlns="%s" name="full.module" version="1.0" open="true" native-access="true">
                    <dependencies>
                        <dependency name="dep.a" transitive="true"/>
                        <dependency name="dep.b" optional="true">
                            <add-exports name="dep.b.internal"/>
                        </dependency>
                    </dependencies>
                    <packages>
                        <export package="full.module.api"/>
                        <private package="full.module.impl"/>
                        <open package="full.module.spi"/>
                    </packages>
                    <uses>
                        <use name="full.module.api.Service"/>
                    </uses>
                    <provides>
                        <provide name="full.module.api.Service">
                            <with name="full.module.impl.ServiceImpl"/>
                        </provide>
                    </provides>
                    <main-class name="full.module.Main"/>
                </module>""".formatted(NS);
        ModuleDescriptor desc = parseXml(xml);
        assertEquals("full.module", desc.name());
        assertEquals(Optional.of("1.0"), desc.version());
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.OPEN));
        assertTrue(desc.modifiers().contains(ModuleDescriptor.Modifier.NATIVE_ACCESS));
        assertEquals(Optional.of("full.module.Main"), desc.mainClass());
        assertEquals(3, desc.packages().size());
        assertTrue(desc.uses().contains("full.module.api.Service"));
        assertNotNull(desc.provides().get("full.module.api.Service"));
    }

    // --- parsing from different sources ---

    @Test
    public void parseFromInputStream() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\"/>";
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            ModuleDescriptor desc = ModuleDescriptor.fromXml(is);
            assertEquals("m", desc.name());
        }
    }

    @Test
    public void parseFromReader() throws IOException {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\"/>";
        try (StringReader sr = new StringReader(xml)) {
            ModuleDescriptor desc = ModuleDescriptor.fromXml(sr);
            assertEquals("m", desc.name());
        }
    }

    // --- error cases ---

    @Test
    public void wrongNamespaceThrows() {
        String xml = "<module xmlns=\"urn:wrong:ns\" name=\"m\"/>";
        assertThrows(IOException.class, () -> parseXml(xml));
    }

    @Test
    public void missingNameThrows() {
        String xml = "<module xmlns=\"" + NS + "\"/>";
        assertThrows(IOException.class, () -> parseXml(xml));
    }

    @Test
    public void unknownElementThrows() {
        String xml = """
                <module xmlns="%s" name="m">
                    <bogus/>
                </module>""".formatted(NS);
        assertThrows(IOException.class, () -> parseXml(xml));
    }

    @Test
    public void unknownAttributeThrows() {
        String xml = "<module xmlns=\"" + NS + "\" name=\"m\" bogus=\"true\"/>";
        assertThrows(IOException.class, () -> parseXml(xml));
    }

    @Test
    public void unknownRootElementThrows() {
        String xml = "<notmodule xmlns=\"" + NS + "\" name=\"m\"/>";
        assertThrows(IOException.class, () -> parseXml(xml));
    }
}
