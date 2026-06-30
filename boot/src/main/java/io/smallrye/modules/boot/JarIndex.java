package io.smallrye.modules.boot;

import java.util.List;

/**
 * Index of class entries within a composite JAR.
 * <p>
 * An implementation of this interface is synthesized by the composite JAR builder
 * and placed at the top level of the composite JAR so that it is visible to the
 * system class loader. The boot shim loads the implementation and uses it to
 * locate class bytes within the memory-mapped composite without any ZIP parsing.
 * <p>
 * The {@code module-info.class} entries in the composite JAR must contain
 * complete package lists. The boot class loader reads
 * {@link java.lang.module.ModuleDescriptor#packages()} to build the
 * package-to-module routing map; a module descriptor with a missing or
 * incomplete package index will cause classes in unlisted packages to be
 * unroutable.
 * <p>
 * All offsets and sizes are {@code long} to future-proof for {@code MemorySegment}
 * support.
 */
public interface JarIndex {

    /**
     * {@return the names of all modules stored in the composite JAR}
     * The returned list is unmodifiable and never empty.
     */
    List<String> moduleNames();

    /**
     * Return the byte offset within the composite JAR of the given class entry.
     * <p>
     * The {@code className} is a binary name (dots, not slashes), e.g.
     * {@code "io.smallrye.modules.Launcher"}.
     * Use {@code "module-info"} to retrieve the {@code module-info.class} entry
     * for the given module.
     *
     * @param moduleName the module name (must not be {@code null})
     * @param className the binary class name (must not be {@code null})
     * @return the byte offset, or {@code -1} if the entry is not found
     */
    long classOffset(String moduleName, String className);

    /**
     * Return the byte size of the given class entry within the composite JAR.
     *
     * @param moduleName the module name (must not be {@code null})
     * @param className the binary class name (must not be {@code null})
     * @return the byte size, or {@code -1} if the entry is not found
     */
    long classSize(String moduleName, String className);
}
