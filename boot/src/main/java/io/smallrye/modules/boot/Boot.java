package io.smallrye.modules.boot;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Bootstrap entry point for loading the SmallRye Modules runtime from a
 * composite JAR without placing any dependencies on the application module path.
 * <p>
 * The bootstrap sequence:
 * <ol>
 * <li>Locate the composite JAR from the boot module's code source</li>
 * <li>Memory-map the entire JAR</li>
 * <li>Load the synthesized {@link JarIndex} implementation from the system class loader</li>
 * <li>Create a {@link BootClassLoader} backed by the mapped buffer</li>
 * <li>Build a JPMS {@link ModuleLayer} over the boot class loader</li>
 * <li>Transfer the {@code jdk.internal.module} export to the infrastructure module</li>
 * <li>Invoke the runtime's {@code Launcher.main}</li>
 * </ol>
 */
public final class Boot {

    private Boot() {
    }

    /**
     * Main entry point.
     *
     * @param args the command-line arguments, forwarded to the runtime launcher
     * @throws Throwable if bootstrap fails
     */
    public static void main(String[] args) throws Throwable {
        // 1. Locate the composite JAR from our own code source
        CodeSource codeSource = Boot.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw new IllegalStateException("Cannot determine Boot code source location");
        }
        Path jarPath = Path.of(codeSource.getLocation().toURI());

        // 2. Memory-map the entire file
        ByteBuffer compositeBuffer;
        try (FileChannel channel = FileChannel.open(jarPath, StandardOpenOption.READ)) {
            compositeBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }

        // 3. Load the synthesized JarIndex from the system class loader
        Class<?> indexClass = Class.forName("io.smallrye.modules.boot.JarIndexImpl", true,
                ClassLoader.getSystemClassLoader());
        JarIndex jarIndex = (JarIndex) indexClass.getDeclaredConstructor().newInstance();

        // 4. Create boot class loader (reads module descriptors, builds package index — no classes loaded)
        BootClassLoader bootCL = new BootClassLoader(compositeBuffer, jarIndex, null);

        // 5. Build the infrastructure ModuleLayer
        //    5a. Build a ModuleFinder from the index
        Map<String, ModuleReference> moduleRefs = buildModuleReferences(compositeBuffer, jarIndex);
        java.lang.module.ModuleFinder finder = new java.lang.module.ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(moduleRefs.get(name));
            }

            @Override
            public Set<ModuleReference> findAll() {
                return Set.copyOf(moduleRefs.values());
            }
        };

        //    5b. Resolve the configuration against the boot layer
        Set<String> roots = Set.copyOf(jarIndex.moduleNames());
        Configuration cf = ModuleLayer.boot().configuration().resolve(
                finder, java.lang.module.ModuleFinder.of(), roots);

        //    5c. Define the module layer
        ModuleLayer.Controller controller = ModuleLayer.defineModules(
                cf, List.of(ModuleLayer.boot()), name -> bootCL);
        ModuleLayer infraLayer = controller.layer();

        // 6. Transfer the jdk.internal.module export to the infrastructure module
        Module infraModule = infraLayer.findModule("io.smallrye.modules")
                .orElseThrow(() -> new IllegalStateException("io.smallrye.modules not found in infrastructure layer"));
        transferInternalExport(infraModule);

        // 7. Set TCCL and invoke Launcher.main
        Thread.currentThread().setContextClassLoader(bootCL);
        Class<?> launcherClass = Class.forName(infraModule, "io.smallrye.modules.Launcher");
        if (launcherClass == null) {
            throw new ClassNotFoundException("io.smallrye.modules.Launcher not found in infrastructure layer");
        }
        MethodHandle mainHandle = publicLookup().findStatic(launcherClass, "main",
                MethodType.methodType(void.class, String[].class));
        mainHandle.invokeExact(args);
    }

    /**
     * Build a map of module name to {@link ModuleReference} from the composite index.
     * Each reference wraps the {@link ModuleDescriptor} read from the composite buffer.
     */
    private static Map<String, ModuleReference> buildModuleReferences(ByteBuffer buffer, JarIndex index) {
        HashMap<String, ModuleReference> refs = new HashMap<>();
        for (String moduleName : index.moduleNames()) {
            long offset = index.classOffset(moduleName, "module-info");
            long size = index.classSize(moduleName, "module-info");
            ByteBuffer descriptorBytes = buffer.slice((int) offset, (int) size);
            ModuleDescriptor descriptor = ModuleDescriptor.read(descriptorBytes);
            ModuleReference ref = new ModuleReference(descriptor, null) {
                @Override
                public ModuleReader open() {
                    // module reader is not needed for defineModules — the boot class loader handles all reads
                    return new ModuleReader() {
                        @Override
                        public Optional<URI> find(String name) {
                            return Optional.empty();
                        }

                        @Override
                        public Stream<String> list() {
                            return Stream.empty();
                        }

                        @Override
                        public void close() {
                        }
                    };
                }
            };
            refs.put(moduleName, ref);
        }
        return refs;
    }

    /**
     * Use {@code jdk.internal.module.Modules.addExports} to export
     * {@code jdk.internal.module} from {@code java.base} to the given module.
     * <p>
     * This requires {@code --add-exports java.base/jdk.internal.module=ALL-UNNAMED}
     * on the command line (since the boot module is on the unnamed module of the
     * system class loader at startup).
     */
    private static void transferInternalExport(Module infraModule) {
        try {
            @SuppressWarnings("Java9ReflectionClassVisibility")
            Class<?> modulesClass = Class.forName("jdk.internal.module.Modules", true, null);
            MethodHandle addExports = lookup().findStatic(modulesClass, "addExports",
                    MethodType.methodType(void.class, Module.class, String.class, Module.class));
            Module javaBase = Object.class.getModule();
            addExports.invokeExact(javaBase, "jdk.internal.module", infraModule);
        } catch (Throwable e) {
            Module self = Boot.class.getModule();
            String target = self.isNamed() ? self.getName() : "ALL-UNNAMED";
            throw new IllegalStateException(
                    "Failed to transfer jdk.internal.module export — use: "
                            + "--add-exports java.base/jdk.internal.module=" + target,
                    e);
        }
    }
}
