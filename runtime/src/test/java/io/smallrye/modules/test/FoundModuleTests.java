package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.FoundModule;
import io.smallrye.modules.ModuleDescriptorLoader;
import io.smallrye.modules.ResourceLoaderOpener;
import io.smallrye.modules.desc.ModuleDescriptor;

/**
 * Tests for {@link FoundModule}.
 */
public final class FoundModuleTests {

    @Test
    public void constructorStoresValues() {
        List<ResourceLoaderOpener> openers = List.of();
        ModuleDescriptorLoader loader = (moduleName, loaders) -> ModuleDescriptor.builder()
                .setName(moduleName)
                .build();
        FoundModule fm = new FoundModule(openers, loader);
        assertSame(openers, fm.loaderOpeners());
        assertSame(loader, fm.descriptorLoader());
    }

    @Test
    public void constructorRejectsNullOpeners() {
        ModuleDescriptorLoader loader = (moduleName, loaders) -> ModuleDescriptor.builder()
                .setName(moduleName)
                .build();
        assertThrows(IllegalArgumentException.class, () -> new FoundModule(null, loader));
    }

    @Test
    public void constructorRejectsNullDescriptorLoader() {
        assertThrows(IllegalArgumentException.class, () -> new FoundModule(List.of(), null));
    }

    @Test
    public void recordEquality() {
        List<ResourceLoaderOpener> openers = List.of();
        ModuleDescriptorLoader loader = (moduleName, loaders) -> ModuleDescriptor.builder()
                .setName(moduleName)
                .build();
        FoundModule a = new FoundModule(openers, loader);
        FoundModule b = new FoundModule(openers, loader);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
