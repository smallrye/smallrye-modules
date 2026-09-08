package io.smallrye.modules;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.smallrye.common.constraint.Assert;

/**
 * A loader for modules.
 */
public class ModuleLoader implements Closeable {
    private final String name;
    private final ModuleFinder finder;
    private final ReentrantLock defineLock = new ReentrantLock();
    private final ConcurrentHashMap<String, DefinedModule> definedModules = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * Construct a new instance.
     *
     * @param name the module loader's name (must not be {@code null})
     * @param finder the module finder (must not be {@code null})
     */
    public ModuleLoader(final String name, final ModuleFinder finder) {
        this.name = Assert.checkNotNullParam("name", name);
        this.finder = Assert.checkNotNullParam("finder", finder);
    }

    /**
     * Construct a new instance with no module finder.
     *
     * @param name the module loader's name (must not be {@code null})
     */
    public ModuleLoader(final String name) {
        this(name, ModuleFinder.EMPTY);
    }

    /**
     * {@return the name of this module loader (not {@code null})}
     */
    public String name() {
        return name;
    }

    /**
     * Load a module with the given name.
     * If the module loader has been closed, an exception is thrown.
     *
     * @param moduleName the module name (must not be {@code null})
     * @return the loaded module, or {@code null} if the module is not found by this loader
     */
    public LoadedModule loadModule(final String moduleName) {
        checkClosed();
        if (moduleName.equals("java.base")) {
            return loadedJavaBase;
        }
        ModuleClassLoader loaded = findDefinedModule(moduleName);
        if (loaded == null) {
            FoundModule foundModule = finder.findModule(moduleName);
            if (foundModule != null) {
                loaded = tryDefineModule(moduleName, foundModule.loaderOpeners(), foundModule.descriptorLoader());
                if (loaded == null) {
                    loaded = findDefinedModule(moduleName);
                }
            }
        }
        return loaded == null ? null : LoadedModule.forModuleClassLoader(loaded);
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException(this + " is closed");
        }
    }

    /**
     * Load a module, throwing an exception if it is not present.
     *
     * @param moduleName the name of the module to load (must not be {@code null})
     * @return the loaded module (not {@code null})
     * @throws ModuleNotFoundException if the module cannot be found
     */
    public final LoadedModule requireModule(final String moduleName) {
        LoadedModule loadedModule = loadModule(moduleName);
        if (loadedModule == null) {
            throw new ModuleNotFoundException(moduleName);
        }
        return loadedModule;
    }

    /**
     * Create the class loader for the given configuration.
     * Used to allow module loaders to produce subclasses of {@link ModuleClassLoader}
     * which implement additional interfaces or provide a class loader name.
     *
     * @param configuration the class loader configuration (not {@code null})
     * @return the module class loader (must not be {@code null})
     */
    protected ModuleClassLoader createClassLoader(ModuleClassLoader.ClassLoaderConfiguration configuration) {
        return new ModuleClassLoader(configuration, null);
    }

    /**
     * Get a defined module with the given name.
     * If the module was not yet defined to this module loader, {@code null} is returned.
     * If the module was defined and loaded, the module's class loader is returned.
     * If the module was defined but cannot be loaded, then an exception is thrown.
     *
     * @param name the module name (must not be {@code null})
     * @return the module class loader, or {@code null} if none is found
     * @throws ModuleLoadException if the module was defined but cannot be loaded.
     */
    protected final ModuleClassLoader findDefinedModule(String name) throws ModuleLoadException {
        DefinedModule defined = definedModules.get(name);
        return defined == null ? null : defined.moduleClassLoader(this);
    }

    /**
     * Atomically try to define a module with the given name, if it is not already defined.
     * If the module is already defined, then {@code null} is returned.
     * If the module cannot be defined due to an error, then an exception is thrown.
     * Otherwise, the return value is non-{@code null} and the module has been defined to this module loader.
     *
     * @param moduleName the module name (must not be {@code null})
     * @param loaderOpeners the list of resource loader openers for the new module (must not be {@code null})
     * @param descriptorLoader the module descriptor opener (must not be {@code null})
     * @return the defined module, or {@code null} if the module was already defined
     * @throws ModuleLoadException if the module cannot be defined due to an error, or a previous module definition
     *         for this name has failed
     */
    protected final ModuleClassLoader tryDefineModule(
            String moduleName,
            List<ResourceLoaderOpener> loaderOpeners,
            ModuleDescriptorLoader descriptorLoader) throws ModuleLoadException {

        checkClosed();

        ConcurrentHashMap<String, DefinedModule> loadedModules = this.definedModules;
        if (loadedModules.containsKey(moduleName)) {
            return null;
        }
        ReentrantLock lock = defineLock;
        lock.lock();
        DefinedModule dm;
        try {
            if (loadedModules.containsKey(moduleName)) {
                return null;
            }
            dm = new DefinedModule.New(moduleName, loaderOpeners, descriptorLoader);
            loadedModules.put(moduleName, dm);
        } finally {
            lock.unlock();
        }
        return dm.moduleClassLoader(this);
    }

    /**
     * Close the module loader and release the resources associated with its modules.
     *
     * @throws IOException if closing any part of the loader fails
     */
    public void close() throws IOException {
        if (closed) {
            return;
        }
        List<DefinedModule> definedModules;
        defineLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            definedModules = List.copyOf(this.definedModules.values());
            this.definedModules.clear();
        } finally {
            defineLock.unlock();
        }
        IOException ioe = null;
        for (DefinedModule definedModule : definedModules) {
            if (definedModule instanceof DefinedModule.Loaded loaded) {
                ModuleClassLoader loader = loaded.moduleClassLoader(this);
                try {
                    loader.close();
                } catch (Throwable t) {
                    if (ioe == null) {
                        ioe = new IOException("Error while closing module loader " + this, t);
                    } else {
                        ioe.addSuppressed(t);
                    }
                }
            }
        }
        if (finder != null) {
            try {
                finder.close();
            } catch (Throwable t) {
                if (ioe == null) {
                    ioe = new IOException("Error while closing module loader " + this, t);
                } else {
                    ioe.addSuppressed(t);
                }
            }
        }
        if (ioe != null) {
            throw ioe;
        }
    }

    /**
     * Create a new module loader that aggregates the given module loaders, searching them in order.
     *
     * @param name the name of the module loader to create (must not be {@code null})
     * @param loaders the module loaders to aggregate (must not be {@code null})
     * @return the module loader (not {@code null})
     */
    public static ModuleLoader aggregate(String name, List<ModuleLoader> loaders) {
        List<ModuleLoader> copy = List.copyOf(loaders);
        return new ModuleLoader(name, ModuleFinder.EMPTY) {
            public LoadedModule loadModule(final String moduleName) {
                LoadedModule found = super.loadModule(moduleName);
                if (found != null) {
                    return found;
                }
                for (ModuleLoader moduleLoader : copy) {
                    found = moduleLoader.loadModule(moduleName);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }
        };
    }

    /**
     * Create a new module loader that aggregates the given module loaders, searching them in order.
     *
     * @param name the name of the module loader to create (must not be {@code null})
     * @param loaders the module loaders to aggregate (must not be {@code null})
     * @return the module loader (not {@code null})
     */
    public static ModuleLoader aggregate(String name, ModuleLoader... loaders) {
        return aggregate(name, List.of(loaders));
    }

    /**
     * An empty module loader.
     */
    public static final ModuleLoader EMPTY = new ModuleLoader("empty", ModuleFinder.EMPTY);

    private static final Module javaBase = Object.class.getModule();
    private static final LoadedModule loadedJavaBase = LoadedModule.forModule(javaBase);

    /**
     * A module loader which loads from the JDK boot module layer.
     */
    public static final ModuleLoader BOOT = forLayer("boot", ModuleLayer.boot());

    /**
     * Create a new module loader instance that loads modules from the given JDK module layer.
     *
     * @param name the name of the module loader to create (must not be {@code null})
     * @param layer the module layer (must not be {@code null})
     * @return the module loader (not {@code null})
     */
    public static ModuleLoader forLayer(String name, ModuleLayer layer) {
        return new ModuleLoader(name, ModuleFinder.EMPTY) {
            public LoadedModule loadModule(final String moduleName) {
                return layer.findModule(moduleName).map(LoadedModule::forModule).orElse(null);
            }
        };
    }

    /**
     * {@return the module loader for the given class loader, or {@code null} if there is none}
     *
     * @param cl the class loader
     */
    public static ModuleLoader ofClassLoader(ClassLoader cl) {
        return cl instanceof ModuleClassLoader mcl ? mcl.moduleLoader() : null;
    }

    /**
     * {@return the module loader of the given JDK module, or {@code null} if there is none}
     *
     * @param module the module (must not be {@code null})
     */
    public static ModuleLoader ofModule(Module module) {
        return ofClassLoader(module.getClassLoader());
    }

    /**
     * {@return the module loader of the given class, or {@code null} if there is none}
     *
     * @param clazz the class (must not be {@code null})
     */
    public static ModuleLoader ofClass(Class<?> clazz) {
        return ofClassLoader(clazz.getClassLoader());
    }

    /**
     * {@return the module loader of the thread's context class loader, or {@code null} if there is none}
     *
     * @param thread the thread (must not be {@code null})
     */
    public static ModuleLoader ofThread(Thread thread) {
        return ofClassLoader(thread.getContextClassLoader());
    }

    void replace(final DefinedModule.New newModule, final DefinedModule result) {
        definedModules.replace(newModule.moduleName(), newModule, result);
    }
}
