package io.smallrye.modules.boot;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * A lock-free class loader that defines classes from pre-indexed offsets
 * within a memory-mapped composite JAR.
 * <p>
 * This class loader eliminates class-loading locks using the same pattern
 * as {@code ModuleClassLoader}: {@link #getClassLoadingLock(String)} eagerly
 * loads the class and returns it as the "lock" object, so the JDK's
 * {@code ClassLoader.loadClass(Module, String)} never blocks.
 * <p>
 * Boot layer packages are delegated to the boot loader via
 * {@link Class#forName(Module, String)}. All other classes are defined
 * from zero-copy {@link ByteBuffer} slices of the composite JAR.
 */
public final class BootClassLoader extends ClassLoader {
    static {
        if (!ClassLoader.registerAsParallelCapable()) {
            throw new InternalError("Class loader cannot be made parallel-capable");
        }
    }

    /**
     * Boot layer index: package name to {@code Module}.
     */
    private static final Map<String, Module> bootModuleIndex;

    static {
        HashMap<String, Module> map = new HashMap<>(1000);
        populateIndex(map, ModuleLayer.boot());
        bootModuleIndex = Map.copyOf(map);
    }

    private static void populateIndex(Map<String, Module> map, ModuleLayer layer) {
        for (Module module : layer.modules()) {
            for (String pn : module.getPackages()) {
                map.put(pn, module);
            }
        }
        for (ModuleLayer parent : layer.parents()) {
            populateIndex(map, parent);
        }
    }

    private final ByteBuffer compositeBuffer;
    private final JarIndex jarIndex;
    /**
     * Package name (dots) to module name.
     */
    private final Map<String, String> packageIndex;
    private final ProtectionDomain protectionDomain;

    /**
     * Construct a new boot class loader.
     * <p>
     * Reads module descriptors from the composite JAR via the index to build
     * the package-to-module routing map. No classes are loaded during construction.
     *
     * @param compositeBuffer the memory-mapped composite JAR buffer (must not be {@code null})
     * @param jarIndex the pre-built index of class entries (must not be {@code null})
     * @param protectionDomain the protection domain to assign to defined classes, or {@code null}
     */
    public BootClassLoader(ByteBuffer compositeBuffer, JarIndex jarIndex, ProtectionDomain protectionDomain) {
        // no parent class loader — boot layer delegation is handled explicitly
        super("boot", null);
        this.compositeBuffer = compositeBuffer;
        this.jarIndex = jarIndex;
        this.protectionDomain = protectionDomain;
        this.packageIndex = buildPackageIndex(compositeBuffer, jarIndex);
    }

    private static Map<String, String> buildPackageIndex(ByteBuffer buffer, JarIndex index) {
        // scan module descriptors to map package → module name
        HashMap<String, String> map = new HashMap<>();
        for (String moduleName : index.moduleNames()) {
            long offset = index.classOffset(moduleName, "module-info");
            long size = index.classSize(moduleName, "module-info");
            if (offset < 0 || size < 0) {
                throw new IllegalArgumentException("Module " + moduleName + " has no module-info.class entry");
            }
            ByteBuffer descriptorBytes = buffer.slice((int) offset, (int) size);
            ModuleDescriptor descriptor = ModuleDescriptor.read(descriptorBytes);
            for (String pkg : descriptor.packages()) {
                map.put(pkg, moduleName);
            }
        }
        return Map.copyOf(map);
    }

    // ── Lock elimination ──────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * Overridden as {@code final} to bypass parent delegation.
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden as {@code final} to bypass parent delegation and route
     * classes either to the boot layer or to direct definition from the
     * composite buffer.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("[")) {
            // array type — delegate to the boot loader
            return Class.forName(name, false, null);
        }
        String binaryName = name.replace('/', '.');
        String packageName = packageName(binaryName);
        if (bootModuleIndex.containsKey(packageName)) {
            // boot layer class — delegate via boot module
            Module module = bootModuleIndex.get(packageName);
            Class<?> result = Class.forName(module, binaryName);
            if (result != null) {
                return result;
            }
            throw new ClassNotFoundException("Cannot find " + name + " in boot layer module " + module.getName());
        }
        return loadClassDirect(binaryName);
    }

    /**
     * Eagerly loads the requested class and returns it as the "lock" object.
     * <p>
     * The JDK's {@code ClassLoader.loadClass(Module, String)} acquires this
     * lock before calling {@link #findClass(String, String)}. By loading the
     * class here, the subsequent {@code findClass} call becomes a no-op,
     * eliminating all lock contention.
     */
    @Override
    protected Object getClassLoadingLock(String className) {
        try {
            return loadClass(className);
        } catch (ClassNotFoundException e) {
            return new Object();
        }
    }

    /**
     * Returns {@code null} because the class was already loaded by
     * {@link #getClassLoadingLock(String)}.
     */
    @Override
    protected Class<?> findClass(String moduleName, String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> findClass(String name) {
        throw new UnsupportedOperationException();
    }

    // ── Class definition ──────────────────────────────────────────────

    /**
     * Load a class directly from the composite buffer.
     * <p>
     * Looks up the class in the package index, retrieves its byte offset
     * and size from the jar index, slices a zero-copy view, and defines
     * the class. Concurrent definition races are handled by catching
     * {@link LinkageError} and falling back to {@link #findLoadedClass}.
     *
     * @param binaryName the binary name with dots (e.g. {@code "io.smallrye.modules.Launcher"})
     * @return the defined or previously-loaded class
     * @throws ClassNotFoundException if the class is not found in the composite
     */
    private Class<?> loadClassDirect(String binaryName) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(binaryName);
        if (loaded != null) {
            return loaded;
        }
        String packageName = packageName(binaryName);
        String moduleName = packageIndex.get(packageName);
        if (moduleName == null) {
            throw new ClassNotFoundException("No module contains package " + packageName);
        }
        long offset = jarIndex.classOffset(moduleName, binaryName);
        long size = jarIndex.classSize(moduleName, binaryName);
        if (offset < 0 || size < 0) {
            throw new ClassNotFoundException(binaryName + " not found in module " + moduleName);
        }
        ByteBuffer classBytes = compositeBuffer.slice((int) offset, (int) size);
        if (!packageName.isEmpty() && getDefinedPackage(packageName) == null) {
            try {
                definePackage(packageName, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException ignored) {
                // concurrent define — package already exists
            }
        }
        return defineOrGetClass(binaryName, classBytes);
    }

    /**
     * Define the class from the given buffer, handling concurrent definition races.
     */
    private Class<?> defineOrGetClass(String binaryName, ByteBuffer buffer) {
        try {
            return defineClass(binaryName, buffer, protectionDomain);
        } catch (LinkageError e) {
            // concurrent definition race — check if another thread defined it
            Class<?> loaded = findLoadedClass(binaryName);
            if (loaded != null) {
                return loaded;
            }
            throw e;
        }
    }

    // ── Sealed unsupported operations ─────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    protected URL findResource(String moduleName, String name) throws IOException {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URL findResource(String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Enumeration<URL> findResources(String name) {
        return java.util.Collections.emptyEnumeration();
    }

    // ── Utility ───────────────────────────────────────────────────────

    /**
     * Extract the package name from a binary class name.
     *
     * @param binaryName the binary name (dots)
     * @return the package name, or the empty string for the default package
     */
    private static String packageName(String binaryName) {
        int lastDot = binaryName.lastIndexOf('.');
        return lastDot < 0 ? "" : binaryName.substring(0, lastDot);
    }
}
