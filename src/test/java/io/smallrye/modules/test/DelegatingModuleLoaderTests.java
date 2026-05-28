package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.DelegatingModuleLoader;
import io.smallrye.modules.FoundModule;
import io.smallrye.modules.LoadedModule;
import io.smallrye.modules.ModuleFinder;
import io.smallrye.modules.ModuleLoader;
import io.smallrye.modules.desc.ModuleDescriptor;
import io.smallrye.modules.desc.PackageInfo;

/**
 * Tests for {@link DelegatingModuleLoader}.
 */
public final class DelegatingModuleLoaderTests {

    private static ModuleFinder finderFor(String moduleName) {
        return name -> {
            if (name.equals(moduleName)) {
                return new FoundModule(List.of(), (mn, loaders) -> ModuleDescriptor.builder()
                        .setName(mn)
                        .addPackage(mn + ".api", PackageInfo.EXPORTED)
                        .build());
            }
            return null;
        };
    }

    // --- local module found, no delegation ---

    @Test
    public void localModuleFoundDoesNotDelegate() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                finderFor("local.mod"),
                ModuleLoader.EMPTY);
        LoadedModule lm = loader.loadModule("local.mod");
        assertNotNull(lm);
        assertEquals("local.mod", lm.module().getName());
    }

    // --- local not found, delegates ---

    @Test
    public void delegatesToFallbackLoader() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                ModuleFinder.EMPTY,
                ModuleLoader.BOOT);
        LoadedModule lm = loader.loadModule("java.logging");
        assertNotNull(lm);
        assertEquals("java.logging", lm.module().getName());
    }

    // --- both return null ---

    @Test
    public void bothReturnNull() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                ModuleFinder.EMPTY,
                ModuleLoader.EMPTY);
        assertNull(loader.loadModule("nonexistent.module"));
    }

    // --- function-based delegate ---

    @Test
    public void functionBasedDelegate() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                ModuleFinder.EMPTY,
                name -> {
                    if (name.startsWith("java.")) {
                        return ModuleLoader.BOOT;
                    }
                    return null;
                });
        // java.logging should be found via function -> BOOT
        LoadedModule lm = loader.loadModule("java.logging");
        assertNotNull(lm);
    }

    @Test
    public void functionReturnsNullDelegate() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                ModuleFinder.EMPTY,
                name -> null);
        assertNull(loader.loadModule("any.module"));
    }

    // --- java.base always found ---

    @Test
    public void javaBaseAlwaysFound() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                ModuleFinder.EMPTY,
                ModuleLoader.EMPTY);
        LoadedModule jb = loader.loadModule("java.base");
        assertNotNull(jb);
    }

    // --- local overrides delegate ---

    @Test
    public void localOverridesDelegate() {
        DelegatingModuleLoader loader = new DelegatingModuleLoader("test",
                finderFor("override.mod"),
                ModuleLoader.BOOT);
        LoadedModule lm = loader.loadModule("override.mod");
        assertNotNull(lm);
        // it was loaded locally, not from boot
        assertNotNull(lm.classLoader());
    }
}
