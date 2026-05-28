package io.smallrye.modules.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.smallrye.modules.DelegatingModuleLoader;
import io.smallrye.modules.FoundModule;
import io.smallrye.modules.LoadedModule;
import io.smallrye.modules.ModuleFinder;
import io.smallrye.modules.ModuleLoadException;
import io.smallrye.modules.ModuleLoader;
import io.smallrye.modules.ModuleNotFoundException;
import io.smallrye.modules.desc.ModuleDescriptor;
import io.smallrye.modules.desc.PackageInfo;

/**
 * Tests for {@link ModuleLoader}.
 */
public final class ModuleLoaderTests {

    // --- BOOT loader ---

    @Test
    public void bootLoaderFindsJavaBase() {
        LoadedModule jb = ModuleLoader.BOOT.loadModule("java.base");
        assertNotNull(jb);
        assertEquals("java.base", jb.module().getName());
    }

    @Test
    public void bootLoaderFindsJavaLogging() {
        LoadedModule jl = ModuleLoader.BOOT.loadModule("java.logging");
        assertNotNull(jl);
        assertEquals("java.logging", jl.module().getName());
    }

    @Test
    public void bootLoaderReturnsNullForUnknown() {
        LoadedModule unknown = ModuleLoader.BOOT.loadModule("nonexistent.module.xyz");
        assertNull(unknown);
    }

    // --- EMPTY loader ---

    @Test
    public void emptyLoaderReturnsNull() {
        assertNull(ModuleLoader.EMPTY.loadModule("any.module"));
    }

    @Test
    public void emptyLoaderFindsJavaBase() {
        // java.base is special-cased in ModuleLoader.loadModule
        LoadedModule jb = ModuleLoader.EMPTY.loadModule("java.base");
        assertNotNull(jb);
    }

    // --- requireModule ---

    @Test
    public void requireModuleThrowsWhenNotFound() {
        assertThrows(ModuleNotFoundException.class, () -> ModuleLoader.EMPTY.requireModule("nonexistent.module"));
    }

    @Test
    public void requireModuleReturnsWhenFound() {
        LoadedModule jb = ModuleLoader.BOOT.requireModule("java.base");
        assertNotNull(jb);
    }

    // --- loadModule with programmatic module ---

    @Test
    public void loadProgrammaticModule() throws ClassNotFoundException {
        ModuleLoader ml = new DelegatingModuleLoader("test-loader", name -> {
            if (name.equals("test.mod")) {
                return new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                        .setName("test.mod")
                        .setVersion("1.0")
                        .addPackage("test.mod.api", PackageInfo.EXPORTED)
                        .build());
            }
            return null;
        }, ModuleLoader.BOOT);
        LoadedModule lm = ml.loadModule("test.mod");
        assertNotNull(lm);
        assertEquals("test.mod", lm.module().getName());
        // can load classes from boot modules through this class loader
        assertNotNull(lm.classLoader().loadClass("java.lang.Object"));
    }

    @Test
    public void loadModuleReturnsNullForUnknown() {
        ModuleLoader ml = new DelegatingModuleLoader("test-loader", ModuleFinder.EMPTY, ModuleLoader.BOOT);
        assertNull(ml.loadModule("does.not.exist"));
    }

    @Test
    public void loadModuleReturnsSameForRepeatedCalls() {
        ModuleLoader ml = new DelegatingModuleLoader("test-loader", name -> {
            if (name.equals("test.mod")) {
                return new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                        .setName("test.mod")
                        .addPackage("test.mod.api", PackageInfo.EXPORTED)
                        .build());
            }
            return null;
        }, ModuleLoader.BOOT);
        LoadedModule first = ml.loadModule("test.mod");
        LoadedModule second = ml.loadModule("test.mod");
        assertNotNull(first);
        assertSame(first.classLoader(), second.classLoader());
    }

    // --- module name mismatch ---

    @Test
    public void moduleNameMismatchThrows() {
        ModuleLoader ml = new ModuleLoader("mismatch-loader", name -> {
            if (name.equals("expected.name")) {
                return new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                        .setName("different.name")
                        .build());
            }
            return null;
        });
        assertThrows(ModuleLoadException.class, () -> ml.loadModule("expected.name"));
    }

    // --- closed loader ---

    @Test
    public void closedLoaderThrows() throws IOException {
        ModuleLoader ml = new ModuleLoader("closeable", ModuleFinder.EMPTY);
        ml.close();
        assertThrows(IllegalStateException.class, () -> ml.loadModule("any.module"));
    }

    @Test
    public void doubleCloseIsIdempotent() throws IOException {
        ModuleLoader ml = new ModuleLoader("closeable", ModuleFinder.EMPTY);
        ml.close();
        ml.close(); // should not throw
    }

    // --- aggregate ---

    @Test
    public void aggregateSearchesInOrder() {
        ModuleLoader first = ModuleLoader.forLayer("first", ModuleLayer.boot());
        ModuleLoader second = ModuleLoader.EMPTY;
        ModuleLoader agg = ModuleLoader.aggregate("agg", first, second);
        LoadedModule jl = agg.loadModule("java.logging");
        assertNotNull(jl);
    }

    @Test
    public void aggregateFallsThrough() {
        ModuleLoader first = ModuleLoader.EMPTY;
        ModuleLoader second = ModuleLoader.BOOT;
        ModuleLoader agg = ModuleLoader.aggregate("agg", List.of(first, second));
        LoadedModule jl = agg.loadModule("java.logging");
        assertNotNull(jl);
    }

    @Test
    public void aggregateReturnsNullWhenNoneFound() {
        ModuleLoader agg = ModuleLoader.aggregate("agg", ModuleLoader.EMPTY, ModuleLoader.EMPTY);
        assertNull(agg.loadModule("nonexistent.module"));
    }

    // --- forLayer ---

    @Test
    public void forLayerFindsBootModules() {
        ModuleLoader ml = ModuleLoader.forLayer("boot-layer", ModuleLayer.boot());
        LoadedModule jb = ml.loadModule("java.base");
        assertNotNull(jb);
    }

    @Test
    public void forLayerReturnsNullForUnknown() {
        ModuleLoader ml = ModuleLoader.forLayer("boot-layer", ModuleLayer.boot());
        assertNull(ml.loadModule("nonexistent.module"));
    }

    // --- ofClassLoader / ofModule / ofClass ---

    @Test
    public void ofClassLoaderNullForNonMCL() {
        assertNull(ModuleLoader.ofClassLoader(ClassLoader.getSystemClassLoader()));
    }

    @Test
    public void ofClassLoaderNullForNull() {
        assertNull(ModuleLoader.ofClassLoader(null));
    }

    @Test
    public void ofModuleNullForBootModule() {
        assertNull(ModuleLoader.ofModule(Object.class.getModule()));
    }

    @Test
    public void ofClassNullForBootClass() {
        assertNull(ModuleLoader.ofClass(Object.class));
    }

    @Test
    public void ofThreadNullForSystemThread() {
        assertNull(ModuleLoader.ofThread(Thread.currentThread()));
    }

    // --- name ---

    @Test
    public void loaderName() {
        ModuleLoader ml = new ModuleLoader("my-loader");
        assertEquals("my-loader", ml.name());
    }

    // --- ModuleFinder.andThen ---

    @Test
    public void finderAndThenFallsBack() {
        ModuleFinder first = ModuleFinder.EMPTY;
        ModuleFinder second = name -> {
            if (name.equals("found")) {
                return new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                        .setName("found")
                        .build());
            }
            return null;
        };
        ModuleFinder combined = first.andThen(second);
        assertNotNull(combined.findModule("found"));
        assertNull(combined.findModule("not.found"));
    }

    @Test
    public void finderAndThenFirstWins() {
        FoundModule fm1 = new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                .setName("m")
                .setVersion("1.0")
                .build());
        FoundModule fm2 = new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                .setName("m")
                .setVersion("2.0")
                .build());
        ModuleFinder first = name -> name.equals("m") ? fm1 : null;
        ModuleFinder second = name -> name.equals("m") ? fm2 : null;
        ModuleFinder combined = first.andThen(second);
        assertSame(fm1, combined.findModule("m"));
    }

    @Test
    public void emptyFinderAndThenReturnsOther() {
        ModuleFinder other = name -> null;
        assertSame(other, ModuleFinder.EMPTY.andThen(other));
    }

    // --- ModuleFinder.EMPTY ---

    @Test
    public void emptyFinderReturnsNull() {
        assertNull(ModuleFinder.EMPTY.findModule("anything"));
    }

    // --- module with dependency on boot module ---

    @Test
    public void moduleWithBootDependency() throws ClassNotFoundException {
        ModuleLoader ml = new DelegatingModuleLoader("dep-test", name -> {
            if (name.equals("dep.mod")) {
                return new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                        .setName("dep.mod")
                        .addPackage("dep.mod.api", PackageInfo.EXPORTED)
                        .build());
            }
            return null;
        }, ModuleLoader.BOOT);
        LoadedModule lm = ml.loadModule("dep.mod");
        assertNotNull(lm);
        // java.base is always available
        Class<?> objClass = lm.classLoader().loadClass("java.lang.Object");
        assertNotNull(objClass);
    }

    // --- module uses and provides ---

    @Test
    public void moduleWithUsesAndProvides() {
        ModuleLoader ml = new DelegatingModuleLoader("svc-test", name -> {
            if (name.equals("svc.mod")) {
                return new FoundModule(List.of(), (moduleName, loaders) -> ModuleDescriptor.builder()
                        .setName("svc.mod")
                        .addUses("java.util.spi.ToolProvider")
                        .addProvides("java.util.spi.ToolProvider", "svc.mod.MyToolProvider")
                        .addPackage("svc.mod", PackageInfo.EXPORTED)
                        .build());
            }
            return null;
        }, ModuleLoader.BOOT);
        LoadedModule lm = ml.loadModule("svc.mod");
        assertNotNull(lm);
    }
}
