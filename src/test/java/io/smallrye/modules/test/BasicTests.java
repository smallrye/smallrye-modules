package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import io.smallrye.common.resource.MemoryResource;
import io.smallrye.modules.DelegatingModuleLoader;
import io.smallrye.modules.FoundModule;
import io.smallrye.modules.LoadedModule;
import io.smallrye.modules.ModuleFinder;
import io.smallrye.modules.ModuleLoader;
import io.smallrye.modules.desc.Dependency;
import io.smallrye.modules.desc.ModuleDescriptor;
import io.smallrye.modules.desc.PackageInfo;

public final class BasicTests {

    @Test
    public void basics() throws ClassNotFoundException {
        ModuleLoader ml = new DelegatingModuleLoader("test", new ModuleFinder() {
            public FoundModule findModule(final String name) {
                return name.equals("hello") ? new FoundModule(List.of(), (moduleName, loaders) -> new ModuleDescriptor(
                        "hello",
                        Optional.of("1.2.3"),
                        ModuleDescriptor.Modifier.set(),
                        Optional.of("test.foobar.Main"),
                        Optional.empty(),
                        List.of(Dependency.JAVA_BASE),
                        Set.of(RandomGenerator.class.getName(), "java.lang.Unknown"),
                        Map.of("java.lang.Nothing", List.of("test.foobar.NonExistent")),
                        Map.of("test.foobar", PackageInfo.EXPORTED, "test.foobar.impl", PackageInfo.PRIVATE))) : null;
            }
        }, ModuleLoader.BOOT);
        LoadedModule resolved = ml.loadModule("hello");
        assertNotNull(resolved);
        assertNotNull(resolved.classLoader().loadClass("java.lang.Object"));
        assertEquals("1.2.3", resolved.module().getDescriptor().rawVersion().orElseThrow(NullPointerException::new));
    }

    @Test
    public void moduleDescriptorParsing() throws IOException {
        Module myMod = getClass().getModule();
        InputStream mi = myMod.getResourceAsStream("module-info.class");
        assertNotNull(mi);
        byte[] bytes;
        try (mi) {
            bytes = mi.readAllBytes();
        }
        MemoryResource resource = new MemoryResource("module-info.class", bytes);
        ModuleDescriptor desc = ModuleDescriptor.fromModuleInfo(resource, List.of());
        assertEquals(myMod.getName(), desc.name());
    }
}
