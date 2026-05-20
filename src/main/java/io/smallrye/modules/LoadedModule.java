package io.smallrye.modules;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.smallrye.common.constraint.Assert;

/**
 * A module that has been loaded, but not necessarily defined yet.
 * Objects of this type represent handles to a module, are not unique,
 * and have no defined identity semantics.
 */
public final class LoadedModule {
    private final Module module;
    private final ModuleClassLoader moduleClassLoader;

    private LoadedModule(Module module) {
        if (module.getClassLoader() instanceof ModuleClassLoader mcl) {
            // this will be false if mcl defines a named module but module is unnamed
            if (module.isNamed() || mcl.module() == module) {
                this.module = null;
                this.moduleClassLoader = mcl;
                return;
            }
        }
        this.module = module;
        this.moduleClassLoader = null;
    }

    private LoadedModule(final ModuleClassLoader moduleClassLoader) {
        this.module = null;
        this.moduleClassLoader = moduleClassLoader;
    }

    /**
     * Load the module instance associated with this loaded module.
     *
     * @return the module instance (not {@code null})
     */
    public Module module() {
        if (module != null) {
            return module;
        } else {
            return moduleClassLoader.module();
        }
    }

    /**
     * Find the class loader associated with the module.
     * If the module is managed by this framework, the returned class loader
     * will extend {@link ModuleClassLoader} and may represent a module
     * which is still in early stages of loading (i.e. no module yet exists).
     *
     * @return the class loader, or {@code null} if the module is on the boostrap class loader
     */
    public ClassLoader classLoader() {
        if (module != null) {
            return module.getClassLoader();
        } else {
            return moduleClassLoader;
        }
    }

    /**
     * Perform the given action for each package that is exported to the given module.
     *
     * @param toModule the target module (must not be {@code null})
     * @param action the action to perform for each exported package name (must not be {@code null})
     */
    public void forEachExportedPackage(Module toModule, Consumer<String> action) {
        if (module != null) {
            module.getPackages().stream().filter(pn -> module.isExported(pn, toModule)).forEach(action);
        } else {
            moduleClassLoader.forEachExportedPackage(toModule, action);
        }
    }

    /**
     * {@return the optional name of the module}
     */
    public Optional<String> name() {
        if (module != null) {
            return module.isNamed() ? Optional.of(module.getName()) : Optional.empty();
        } else {
            return Optional.of(moduleClassLoader.moduleName());
        }
    }

    /**
     * {@return {@code true} if the given object is a {@code LoadedModule} representing the same module}
     *
     * @param obj the object to compare (may be {@code null})
     */
    public boolean equals(final Object obj) {
        return obj instanceof LoadedModule lm && equals(lm);
    }

    /**
     * {@return {@code true} if this loaded module represents the same module as the given one}
     *
     * @param other the other loaded module (may be {@code null})
     */
    public boolean equals(LoadedModule other) {
        return other != null && module == other.module && moduleClassLoader == other.moduleClassLoader;
    }

    /**
     * {@return the hash code of this loaded module}
     */
    public int hashCode() {
        return Objects.hash(module, moduleClassLoader);
    }

    /**
     * {@return a string representation of this loaded module}
     */
    public String toString() {
        return module != null ? module.toString() : "module⁺ " + moduleClassLoader.moduleName();
    }

    /**
     * {@return a loaded module for the given module}
     *
     * @param module the module to encapsulate (must not be {@code null})
     */
    public static LoadedModule forModule(Module module) {
        return new LoadedModule(Assert.checkNotNullParam("module", module));
    }

    /**
     * {@return a loaded module for the given module class loader}
     *
     * @param cl the class loader of the module to encapsulate (must not be {@code null})
     */
    public static LoadedModule forModuleClassLoader(ModuleClassLoader cl) {
        return new LoadedModule(Assert.checkNotNullParam("cl", cl));
    }
}
