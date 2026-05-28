package io.smallrye.modules.desc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.module.FindException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.smallrye.classfile.Annotation;
import io.smallrye.classfile.AnnotationElement;
import io.smallrye.classfile.AnnotationValue;
import io.smallrye.classfile.AnnotationValue.OfAnnotation;
import io.smallrye.classfile.AnnotationValue.OfArray;
import io.smallrye.classfile.AnnotationValue.OfString;
import io.smallrye.classfile.Attributes;
import io.smallrye.classfile.ClassFile;
import io.smallrye.classfile.ClassModel;
import io.smallrye.classfile.attribute.ModuleAttribute;
import io.smallrye.classfile.attribute.ModuleExportInfo;
import io.smallrye.classfile.attribute.ModuleMainClassAttribute;
import io.smallrye.classfile.attribute.ModuleOpenInfo;
import io.smallrye.classfile.attribute.ModulePackagesAttribute;
import io.smallrye.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import io.smallrye.classfile.constantpool.ClassEntry;
import io.smallrye.classfile.constantpool.ModuleEntry;
import io.smallrye.classfile.constantpool.PackageEntry;
import io.smallrye.classfile.constantpool.Utf8Entry;
import io.smallrye.classfile.extras.reflect.AccessFlag;
import io.smallrye.common.constraint.Assert;
import io.smallrye.common.resource.Resource;
import io.smallrye.common.resource.ResourceLoader;
import io.smallrye.modules.impl.TextIter;
import io.smallrye.modules.impl.Util;

/**
 * A descriptor for initially defining a module.
 *
 */
public final class ModuleDescriptor {
    private final String name;
    private final Optional<String> version;
    private final Modifier.Set modifiers;
    private final Optional<String> mainClass;
    private final Optional<URI> location;
    private final List<Dependency> dependencies;
    private final Set<String> uses;
    private final Map<String, List<String>> provides;
    private final Map<String, PackageInfo> packages;

    private ModuleDescriptor(Builder builder) {
        name = builder.name;
        version = builder.version;
        modifiers = builder.modifiers;
        mainClass = builder.mainClass;
        location = builder.location;
        dependencies = List.copyOf(builder.dependencies.values());
        uses = Set.copyOf(builder.uses);
        //noinspection unchecked
        provides = Map.ofEntries(
                builder.provides.entrySet().stream()
                        .map(e -> Map.entry(e.getKey(), List.copyOf(e.getValue())))
                        .toArray(Entry[]::new));
        packages = Map.copyOf(builder.packages);
    }

    /**
     * Module-wide modifiers.
     */
    public enum Modifier implements ModifierFlag {
        /**
         * Enable native access for this module.
         */
        NATIVE_ACCESS,
        /**
         * The entire module is open for reflective access.
         * Not recommended.
         */
        OPEN,
        /**
         * Define the module as "automatic" which exports and opens all packages.
         * Automatic modules also can use any service.
         * A module cannot be both automatic and unnamed.
         */
        AUTOMATIC,
        /**
         * Define the module as an "unnamed" module, which
         * reads all modules.
         * A module cannot be both automatic and unnamed.
         */
        UNNAMED,
        ;

        /**
         * The list of all possible modifiers in {@link #ordinal()} order.
         */
        public static final List<Modifier> values = List.of(values());

        /**
         * A set of modifiers.
         */
        public static final class Set extends Modifiers<Modifier> {
            private static final VarHandle setsHandle = ConstantBootstraps.arrayVarHandle(MethodHandles.lookup(), "_",
                    VarHandle.class, Set[].class);
            private static final Set[] sets = new Set[1 << values.size()];

            Set(final int flags) {
                super(flags);
            }

            /**
             * {@return the empty set of modifiers (not {@code null})}
             */
            public static Set of() {
                return getSet(0);
            }

            /**
             * {@return the set of one modifier (not {@code null})}
             *
             * @param modifier the modifier
             */
            public static Set of(Modifier modifier) {
                return getSet(bit(modifier));
            }

            /**
             * {@return the set of two modifiers (not {@code null})}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1) {
                return getSet(bit(modifier0) | bit(modifier1));
            }

            /**
             * {@return the set of three modifiers (not {@code null})}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             * @param modifier2 the third modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1, Modifier modifier2) {
                return getSet(bit(modifier0) | bit(modifier1) | bit(modifier2));
            }

            /**
             * {@return the set of four modifiers (not {@code null})}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             * @param modifier2 the third modifier
             * @param modifier3 the fourth modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1, Modifier modifier2, Modifier modifier3) {
                return getSet(bit(modifier0) | bit(modifier1) | bit(modifier2) | bit(modifier3));
            }

            /**
             * {@return the set of all modifiers (not {@code null})}
             */
            public static Set ofAll() {
                return getSet(sets.length - 1);
            }

            /**
             * {@inheritDoc}
             */
            public Set with(final Modifier item) {
                return setOf(flags | bit(item));
            }

            /**
             * {@inheritDoc}
             */
            public Set withAll(final Modifier item0, final Modifier item1) {
                return setOf(flags | bit(item0) | bit(item1));
            }

            /**
             * {@return a modifier set that includes the given modifier set in addition to the modifiers in this set
             * (not {@code null})}
             *
             * @param other the modifier set to add (must not be {@code null})
             */
            public Set withAll(final Set other) {
                return setOf(flags | other.flags);
            }

            /**
             * {@inheritDoc}
             */
            public Set without(final Modifier item) {
                return setOf(flags & ~bit(item));
            }

            /**
             * {@inheritDoc}
             */
            public Set xor(final Modifier item) {
                return setOf(flags ^ bit(item));
            }

            /**
             * {@return a modifier set that is the logical combination of this set and the given set (not {@code null})}
             *
             * @param modifiers the modifier set to merge with (must not be {@code null})
             */
            public Set mergedWith(final Set modifiers) {
                return withAll(modifiers);
            }

            /**
             * {@inheritDoc}
             */
            public boolean contains(final Object o) {
                return o instanceof Modifier m && contains(m);
            }

            /**
             * {@inheritDoc}
             */
            public Iterator<Modifier> iterator() {
                return new Biterator<>(flags, values);
            }

            /**
             * {@inheritDoc}
             */
            public boolean equals(final Object o) {
                return o instanceof Set other && equals(other);
            }

            /**
             * {@return {@code true} if the given set contains the same modifiers as this one}
             *
             * @param other the other set to compare (may be {@code null})
             */
            public boolean equals(final Set other) {
                return other != null && flags == other.flags;
            }

            /**
             * {@inheritDoc}
             */
            public int hashCode() {
                return flags;
            }

            Modifier value(final int index) {
                return values.get(index);
            }

            Set setOf(final int flags) {
                return this.flags == flags ? this : getSet(flags);
            }

            static int bit(final Modifier item) {
                return item == null ? 0 : 1 << item.ordinal();
            }

            private static Set getSet(int setNum) {
                Set[] sets = Set.sets;
                Set set = (Set) setsHandle.getAcquire(sets, setNum);
                if (set == null) {
                    set = new Set(setNum);
                    Set witness = (Set) setsHandle.compareAndExchangeRelease(sets, setNum, (Set) null, set);
                    if (witness != null) {
                        set = witness;
                    }
                }
                return set;
            }
        }
    }

    /**
     * A builder for module descriptors.
     *
     * @see ModuleDescriptor#builder()
     * @see ModuleDescriptor#builder(ModuleDescriptor)
     */
    public static final class Builder {
        private static final Map<String, Dependency> INITIAL_DEP_MAP = Map.of("java.base", Dependency.JAVA_BASE);

        private String name;
        private Optional<String> version;
        private Modifier.Set modifiers;
        private Optional<String> mainClass;
        private Optional<URI> location;
        private Map<String, Dependency> dependencies;
        private Set<String> uses;
        private Map<String, List<String>> provides;
        private Map<String, PackageInfo> packages;

        private Builder() {
            version = Optional.empty();
            modifiers = Modifier.Set.of();
            mainClass = Optional.empty();
            location = Optional.empty();
            dependencies = INITIAL_DEP_MAP;
            uses = Set.of();
            provides = Map.of();
            packages = Map.of();
        }

        private Builder(ModuleDescriptor old) {
            name = old.name;
            version = old.version;
            modifiers = old.modifiers;
            mainClass = old.mainClass;
            location = old.location;
            if (old.dependencies.size() == 1) {
                dependencies = INITIAL_DEP_MAP;
            } else {
                dependencies = new LinkedHashMap<>(old.dependencies.size() * 2);
                for (Dependency dependency : old.dependencies) {
                    dependencies.put(dependency.moduleName(), dependency);
                }
            }
            uses = old.uses.isEmpty() ? Set.of() : new HashSet<>(old.uses);
            if (old.provides.isEmpty()) {
                provides = Map.of();
            } else {
                provides = new LinkedHashMap<>(old.provides.size() * 2);
                for (Entry<String, List<String>> entry : old.provides.entrySet()) {
                    provides.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
            packages = old.packages.isEmpty() ? Map.of() : new HashMap<>(old.packages);
        }

        /**
         * Set the module name.
         *
         * @param name the module name (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setName(final String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the module version.
         *
         * @param version the optional module version (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setVersion(final Optional<String> version) {
            this.version = version;
            return this;
        }

        /**
         * Set the module version.
         *
         * @param version the module version, or {@code null} for none
         * @return this builder (not {@code null})
         */
        public Builder setVersion(final String version) {
            this.version = Optional.ofNullable(version);
            return this;
        }

        /**
         * Add a modifier to the module descriptor.
         *
         * @param modifier the modifier to add (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addModifier(final Modifier modifier) {
            this.modifiers = this.modifiers.with(Assert.checkNotNullParam("modifier", modifier));
            return this;
        }

        /**
         * Remove a modifier from the module descriptor.
         *
         * @param modifier the modifier to remove (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder removeModifier(final Modifier modifier) {
            this.modifiers = this.modifiers.without(Assert.checkNotNullParam("modifier", modifier));
            return this;
        }

        /**
         * Merge the given modifier set into the module descriptor's modifiers.
         *
         * @param modifiers the modifiers to merge (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder mergeModifiers(final Modifier.Set modifiers) {
            this.modifiers = this.modifiers.mergedWith(Assert.checkNotNullParam("modifiers", modifiers));
            return this;
        }

        /**
         * Set the main class name.
         *
         * @param mainClass the main class name (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setMainClass(final String mainClass) {
            this.mainClass = Optional.of(mainClass);
            return this;
        }

        /**
         * Set the optional main class name.
         *
         * @param mainClass the optional main class name (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setMainClass(final Optional<String> mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        /**
         * Set the module location URI.
         *
         * @param location the location URI, or {@code null} for none
         * @return this builder (not {@code null})
         */
        public Builder setLocation(final URI location) {
            this.location = Optional.ofNullable(location);
            return this;
        }

        /**
         * Add a dependency to the module descriptor.
         * If a dependency on the same module already exists, the two are merged.
         *
         * @param dependency the dependency to add (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addDependency(Dependency dependency) {
            Assert.checkNotNullParam("dependency", dependency);
            if (!dependency.equals(Dependency.JAVA_BASE)) {
                if (dependencies.size() == 1) {
                    dependencies = new LinkedHashMap<>();
                    dependencies.putAll(INITIAL_DEP_MAP);
                }
                dependencies.compute(dependency.moduleName(),
                        (name, oldVal) -> oldVal == null ? dependency : oldVal.mergedWith(dependency));
            }
            return this;
        }

        /**
         * Add multiple dependencies to the module descriptor.
         *
         * @param dependencies the dependencies to add (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addDependencies(Collection<Dependency> dependencies) {
            Assert.checkNotNullParam("dependencies", dependencies);
            dependencies.forEach(this::addDependency);
            return this;
        }

        /**
         * Add a service usage declaration.
         *
         * @param name the fully-qualified service class name (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addUses(final String name) {
            Assert.checkNotNullParam("name", name);
            if (uses.isEmpty()) {
                uses = new LinkedHashSet<>();
            }
            uses.add(name);
            return this;
        }

        /**
         * Add multiple service usage declarations.
         *
         * @param uses the service class names to add (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addUses(final Collection<String> uses) {
            Assert.checkNotNullParam("uses", uses);
            uses.forEach(this::addUses);
            return this;
        }

        /**
         * Add a service provider declaration.
         *
         * @param serviceName the fully-qualified service class name (must not be {@code null})
         * @param withNames the fully-qualified provider implementation class names (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addProvides(final String serviceName, final String... withNames) {
            return addProvides(serviceName, List.of(withNames));
        }

        /**
         * Add a service provider declaration.
         *
         * @param serviceName the fully-qualified service class name (must not be {@code null})
         * @param withNames the fully-qualified provider implementation class names (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addProvides(final String serviceName, final Collection<String> withNames) {
            Assert.checkNotNullParam("serviceName", serviceName);
            Assert.checkNotNullParam("withNames", withNames);
            if (provides.isEmpty()) {
                provides = new LinkedHashMap<>();
            }
            provides.computeIfAbsent(serviceName, Util::newArrayList).addAll(List.copyOf(withNames));
            return this;
        }

        /**
         * Add multiple service provider declarations.
         *
         * @param provides a map from service class names to lists of provider implementation class names
         *        (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addProvides(final Map<String, List<String>> provides) {
            Assert.checkNotNullParam("provides", provides);
            provides.forEach(this::addProvides);
            return this;
        }

        /**
         * Add a package to the module descriptor, merging with any existing entry for the same package name.
         *
         * @param packageName the package name (must not be {@code null})
         * @param info the package info (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addPackage(final String packageName, final PackageInfo info) {
            Assert.checkNotNullParam("packageName", packageName);
            Assert.checkNotNullParam("info", info);
            if (packages.isEmpty()) {
                packages = new TreeMap<>();
            }
            packages.compute(packageName, (name, oldVal) -> oldVal == null ? info : oldVal.mergedWith(info));
            return this;
        }

        /**
         * Add multiple packages to the module descriptor.
         *
         * @param packages the map of package names to package info (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addPackages(final Map<String, PackageInfo> packages) {
            Assert.checkNotNullParam("packages", packages);
            packages.forEach(this::addPackage);
            return this;
        }

        /**
         * Clear all packages from the module descriptor.
         *
         * @return this builder (not {@code null})
         */
        public Builder clearPackages() {
            packages = Map.of();
            return this;
        }

        /**
         * Discover and add packages from the given resource loaders using the default package function.
         *
         * @param loaders the resource loaders to scan for packages (must not be {@code null})
         * @return this builder (not {@code null})
         * @throws IOException if an I/O error occurs during package discovery
         */
        public Builder addDiscoveredPackages(List<ResourceLoader> loaders) throws IOException {
            Assert.checkNotNullParam("loaders", loaders);
            for (ResourceLoader loader : loaders) {
                addDiscoveredPackages(loader);
            }
            return this;
        }

        /**
         * Discover and add packages from the given resource loaders using a custom package function.
         *
         * @param loaders the resource loaders to scan for packages (must not be {@code null})
         * @param packageFunction the function to determine the package info for each discovered package
         *        (must not be {@code null})
         * @return this builder (not {@code null})
         * @throws IOException if an I/O error occurs during package discovery
         */
        public Builder addDiscoveredPackages(List<ResourceLoader> loaders,
                BiFunction<String, PackageInfo, PackageInfo> packageFunction) throws IOException {
            Assert.checkNotNullParam("loaders", loaders);
            for (ResourceLoader loader : loaders) {
                addDiscoveredPackages(loader, packageFunction);
            }
            return this;
        }

        /**
         * Discover and add packages from the given resource loader using the default package function.
         *
         * @param loader the resource loader to scan for packages (must not be {@code null})
         * @return this builder (not {@code null})
         * @throws IOException if an I/O error occurs during package discovery
         */
        public Builder addDiscoveredPackages(ResourceLoader loader) throws IOException {
            return addDiscoveredPackages(loader, Builder::defaultFunction);
        }

        /**
         * Discover and add packages from the given resource loader using a custom package function.
         *
         * @param loader the resource loader to scan for packages (must not be {@code null})
         * @param packageFunction the function to determine the package info for each discovered package;
         *        receives the package name and the existing package info (or {@code null}) and returns
         *        the updated info (or {@code null} to skip the package) (must not be {@code null})
         * @return this builder (not {@code null})
         * @throws IOException if an I/O error occurs during package discovery
         */
        public Builder addDiscoveredPackages(ResourceLoader loader,
                BiFunction<String, PackageInfo, PackageInfo> packageFunction) throws IOException {
            Assert.checkNotNullParam("loader", loader);
            searchPackages(loader.findResource("/"), packageFunction, new HashSet<>());
            return this;
        }

        private static PackageInfo defaultFunction(String pn, PackageInfo info) {
            if (pn.contains(".impl.") || pn.endsWith(".impl")
                    || pn.contains(".private_.") || pn.endsWith(".private_")
                    || pn.contains("._private.") || pn.endsWith("._private")) {
                return info == null ? PackageInfo.PRIVATE : info;
            } else {
                return info == null ? PackageInfo.EXPORTED : info.withAccessAtLeast(PackageAccess.EXPORTED);
            }
        }

        private void searchPackages(final Resource dir, final BiFunction<String, PackageInfo, PackageInfo> packageFunction,
                Set<String> found)
                throws IOException {
            try (DirectoryStream<Resource> ds = dir.openDirectoryStream()) {
                for (Resource child : ds) {
                    if (child.isDirectory()) {
                        searchPackages(child, packageFunction, found);
                    } else {
                        String pathName = child.pathName();
                        if (pathName.endsWith(".class")) {
                            String pn = Util.resourcePackageName(pathName);
                            if (!pn.isEmpty() && found.add(pn)) {
                                PackageInfo existing = packages.get(pn);
                                PackageInfo update = packageFunction.apply(pn, existing);
                                if (update == null || update.equals(existing)) {
                                    // skip it
                                    continue;
                                }
                                addPackage(pn.intern(), update);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Build the module descriptor.
         *
         * @return the constructed module descriptor (not {@code null})
         */
        public ModuleDescriptor build() {
            return new ModuleDescriptor(this);
        }
    }

    /**
     * Obtain a module descriptor from a {@code module-info.class} file's contents.
     *
     * @param moduleInfo the bytes of the {@code module-info.class} (must not be {@code null})
     * @param resourceLoaders the loaders from which packages may be discovered if not given in the descriptor (must not be
     *        {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if the descriptor cannot be read
     */
    public static ModuleDescriptor fromModuleInfo(
            Resource moduleInfo,
            List<ResourceLoader> resourceLoaders) throws IOException {
        return fromModuleInfo(moduleInfo, resourceLoaders, Map.of());
    }

    /**
     * Obtain a module descriptor from a {@code module-info.class} file's contents.
     *
     * @param moduleInfo the bytes of the {@code module-info.class} (must not be {@code null})
     * @param resourceLoaders the loaders from which packages may be discovered if not given in the descriptor (must not be
     *        {@code null})
     * @param extraAccesses extra package accesses to merge into dependencies (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if the descriptor cannot be read
     */
    public static ModuleDescriptor fromModuleInfo(
            Resource moduleInfo,
            List<ResourceLoader> resourceLoaders,
            Map<String, Map<String, PackageAccess>> extraAccesses) throws IOException {
        final Map<String, Map<String, PackageAccess>> modifiedExtraAccesses = new HashMap<>(extraAccesses);
        ClassModel classModel;
        try (InputStream is = moduleInfo.openStream()) {
            classModel = ClassFile.of().parse(is.readAllBytes());
        }
        if (!classModel.isModuleInfo()) {
            throw new IllegalArgumentException("Not a valid module descriptor");
        }
        ModuleAttribute ma = classModel.findAttribute(Attributes.module()).orElseThrow(ModuleDescriptor::noModuleAttribute);
        Optional<ModulePackagesAttribute> mpa = classModel.findAttribute(Attributes.modulePackages());
        Optional<ModuleMainClassAttribute> mca = classModel.findAttribute(Attributes.moduleMainClass());
        Optional<RuntimeInvisibleAnnotationsAttribute> ria = classModel.findAttribute(Attributes.runtimeInvisibleAnnotations());
        Modifier.Set mods = Modifier.Set.of();
        boolean open = classModel.flags().has(AccessFlag.OPEN);
        if (open) {
            mods = mods.with(Modifier.OPEN);
        }
        if (ria.isPresent()) {
            RuntimeInvisibleAnnotationsAttribute a = ria.get();
            for (Annotation ann : a.annotations()) {
                switch (ann.className().stringValue()) {
                    case "io.smallrye.common.annotation.AddExports", "io.smallrye.common.annotation.AddOpens" ->
                        processAccessAnnotation(ann, modifiedExtraAccesses);
                    case "io.smallrye.common.annotation.AddExports$List",
                            "io.smallrye.common.annotation.AddOpens$List" -> {
                        for (AnnotationElement element : ann.elements()) {
                            if (element.name().stringValue().equals("value")) {
                                OfArray val = (OfArray) element.value();
                                for (AnnotationValue value : val.values()) {
                                    processAccessAnnotation(((OfAnnotation) value).annotation(),
                                            modifiedExtraAccesses);
                                }
                            }
                        }
                    }
                    case "io.smallrye.common.annotation.NativeAccess" -> mods = mods.with(Modifier.NATIVE_ACCESS);
                }
            }
        }
        Map<String, PackageInfo> packagesMap = new HashMap<>();
        for (ModuleOpenInfo moduleOpenInfo : ma.opens()) {
            String packageName = moduleOpenInfo.openedPackage().name().stringValue().replace('/', '.').intern();
            packagesMap.put(packageName, open ? PackageInfo.OPEN
                    : PackageInfo.of(
                            PackageAccess.PRIVATE,
                            Set.of(),
                            moduleOpenInfo.opensTo().stream()
                                    .map(ModuleEntry::name)
                                    .map(Utf8Entry::stringValue)
                                    .map(String::intern)
                                    .collect(Collectors.toUnmodifiableSet())));
        }
        for (ModuleExportInfo e : ma.exports()) {
            String packageName = e.exportedPackage().name().stringValue().replace('/', '.').intern();
            if (open) {
                packagesMap.put(packageName, PackageInfo.OPEN);
            } else if (e.exportsTo().isEmpty()) {
                // exports to all
                packagesMap.compute(packageName, (name, oldVal) -> {
                    if (oldVal == null) {
                        return PackageInfo.EXPORTED;
                    } else {
                        return oldVal.withAccessAtLeast(PackageAccess.EXPORTED);
                    }
                });
            } else {
                // exports to some, otherwise whatever the existing level was
                Set<String> exportTargets = e.exportsTo().stream()
                        .map(ModuleEntry::name)
                        .map(Utf8Entry::stringValue)
                        .map(String::intern)
                        .collect(Collectors.toUnmodifiableSet());
                packagesMap.put(packageName,
                        packagesMap.getOrDefault(packageName, PackageInfo.PRIVATE).withExportTargets(exportTargets));
            }
        }
        mpa.ifPresent(modulePackagesAttribute -> modulePackagesAttribute.packages().stream()
                .map(PackageEntry::name)
                .map(Utf8Entry::stringValue)
                .map(s -> s.replace('/', '.'))
                .map(String::intern)
                .forEach(name -> packagesMap.putIfAbsent(name, PackageInfo.PRIVATE)));
        String moduleName = ma.moduleName().name().stringValue();
        ModuleDescriptor desc = builder()
                .setName(moduleName)
                .setVersion(ma.moduleVersion().map(Utf8Entry::stringValue))
                .mergeModifiers(mods)
                .setMainClass(mca.map(ModuleMainClassAttribute::mainClass)
                        .map(ClassEntry::name)
                        .map(Utf8Entry::stringValue)
                        .map(s -> s.replace('/', '.'))
                        .map(String::intern))
                .addDependencies(
                        ma.requires().stream().map(
                                r -> Dependency.builder(r.requires().name().stringValue())
                                        .mergeModifiers(toModifiers(r.requiresFlags()))
                                        .addPackageAccesses(modifiedExtraAccesses.getOrDefault(
                                                r.requires().name().stringValue(), Map.of()))
                                        .build())
                                .collect(Util.toList()))
                .addUses(
                        ma.uses().stream()
                                .map(ClassEntry::name)
                                .map(Utf8Entry::stringValue)
                                .map(s -> s.replace('/', '.'))
                                .map(String::intern)
                                .collect(Collectors.toUnmodifiableSet()))
                .addProvides(
                        ma.provides().stream().map(
                                mpi -> Map.entry(mpi.provides().name().stringValue().replace('/', '.').intern(),
                                        mpi.providesWith().stream()
                                                .map(ClassEntry::name)
                                                .map(Utf8Entry::stringValue)
                                                .map(s -> s.replace('/', '.'))
                                                .map(String::intern)
                                                .collect(Util.toList())))
                                .collect(Util.toMap()))
                .addPackages(packagesMap)
                .addDiscoveredPackages(mpa.isEmpty() ? resourceLoaders : List.of())
                .build();
        return desc;
    }

    private static void processAccessAnnotation(Annotation ann, Map<String, Map<String, PackageAccess>> modifiedExtraAccesses) {
        String moduleName = null;
        List<String> packages = null;
        for (AnnotationElement element : ann.elements()) {
            switch (element.name().stringValue()) {
                case "module" -> moduleName = ((OfString) element.value()).stringValue();
                case "packages" -> packages = ((OfArray) element.value()).values().stream()
                        .map(OfString.class::cast).map(OfString::stringValue).toList();
            }
        }
        if (moduleName == null || moduleName.equals("ALL-UNNAMED") || packages == null) {
            // ignore invalid annotation
            return;
        }
        switch (ann.className().stringValue()) {
            // override all
            case "io.smallrye.common.annotation.AddOpens" -> {
                for (String pn : packages) {
                    modifiedExtraAccesses.computeIfAbsent(moduleName, Util::newHashMap).put(pn, PackageAccess.OPEN);
                }
            }
            // do not override OPEN
            case "io.smallrye.common.annotation.AddExports" -> {
                for (String pn : packages) {
                    modifiedExtraAccesses.computeIfAbsent(moduleName, Util::newHashMap).putIfAbsent(pn, PackageAccess.EXPORTED);
                }
            }
            default -> throw Assert.impossibleSwitchCase(ann.className().stringValue());
        }
    }

    /**
     * Obtain a module descriptor from a JAR manifest, constructing an automatic module.
     *
     * @param defaultName the default module name if not specified in the manifest (may be {@code null})
     * @param defaultVersion the default module version if not specified in the manifest (may be {@code null})
     * @param manifest the JAR manifest (must not be {@code null})
     * @param resourceLoaders the loaders from which packages may be discovered (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if an I/O error occurs during package discovery
     */
    public static ModuleDescriptor fromManifest(String defaultName, String defaultVersion, Manifest manifest,
            List<ResourceLoader> resourceLoaders) throws IOException {
        return fromManifest(defaultName, defaultVersion, manifest, resourceLoaders, Map.of());
    }

    /**
     * Obtain a module descriptor from a JAR manifest, constructing an automatic module.
     *
     * @param defaultName the default module name if not specified in the manifest (may be {@code null})
     * @param defaultVersion the default module version if not specified in the manifest (may be {@code null})
     * @param manifest the JAR manifest (must not be {@code null})
     * @param resourceLoaders the loaders from which packages may be discovered (must not be {@code null})
     * @param extraAccesses extra package accesses to merge into dependencies (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if an I/O error occurs during package discovery
     */
    public static ModuleDescriptor fromManifest(String defaultName, String defaultVersion, Manifest manifest,
            List<ResourceLoader> resourceLoaders, Map<String, Map<String, PackageAccess>> extraAccesses) throws IOException {
        var mainAttributes = manifest.getMainAttributes();
        String moduleName = mainAttributes.getValue("Automatic-Module-Name");
        String version = mainAttributes.getValue("Module-Version");
        if (version == null) {
            version = mainAttributes.getValue(Name.IMPLEMENTATION_VERSION);
        }
        if (version == null) {
            version = defaultVersion;
        }
        boolean enableNativeAccess = !Objects.requireNonNullElse(mainAttributes.getValue("Enable-Native-Access"), "").trim()
                .isEmpty();
        String mainClass = mainAttributes.getValue(Name.MAIN_CLASS);
        String depString = mainAttributes.getValue("Dependencies");
        Map<String, Set<String>> addOpens = parseManifestAdd(mainAttributes.getValue("Add-Opens"));
        Map<String, Set<String>> addExports = parseManifestAdd(mainAttributes.getValue("Add-Exports"));

        List<Dependency> dependencies = List.of();
        if (depString != null) {
            TextIter iter = TextIter.of(depString);
            iter.skipWhiteSpace();
            while (iter.hasNext()) {
                String depName = dotName(iter);
                Dependency.Modifier.Set mods = Dependency.Modifier.Set.of(Dependency.Modifier.SERVICES);
                iter.skipWhiteSpace();
                while (iter.hasNext()) {
                    if (iter.peekNext() == ',') {
                        // done with this dependency
                        break;
                    }
                    if (iter.match("optional")) {
                        mods = mods.with(Dependency.Modifier.OPTIONAL);
                    } else if (iter.match("export")) {
                        mods = mods.with(Dependency.Modifier.TRANSITIVE);
                    } else {
                        iter.skipUntil(cp -> Character.isWhitespace(cp) || cp == ',');
                        iter.skipWhiteSpace();
                    }
                    // else ignored
                }
                Map<String, PackageAccess> accesses;
                if (addOpens.containsKey(depName) || addExports.containsKey(depName)) {
                    accesses = Stream.concat(
                            Stream.concat(
                                    addExports.get(depName).stream().map(pkg -> Map.entry(pkg, PackageAccess.EXPORTED)),
                                    addOpens.get(depName).stream().map(pkg -> Map.entry(pkg, PackageAccess.OPEN))),
                            extraAccesses.getOrDefault(depName, Map.of()).entrySet().stream())
                            .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue, PackageAccess::max));
                } else {
                    accesses = Map.of();
                }
                if (dependencies.isEmpty()) {
                    dependencies = new ArrayList<>();
                }
                dependencies.add(Dependency.builder(depName)
                        .mergeModifiers(mods)
                        .addPackageAccesses(accesses)
                        .build());
            }
        }
        if (moduleName == null) {
            moduleName = defaultName;
        }
        if (moduleName == null || moduleName.isEmpty()) {
            throw new FindException("A valid module name is required");
        }
        Modifier.Set mods = Modifier.Set.of(Modifier.AUTOMATIC);
        if (enableNativeAccess) {
            mods = mods.with(Modifier.NATIVE_ACCESS);
        }
        return builder()
                .setName(moduleName)
                .setVersion(Optional.ofNullable(version))
                .mergeModifiers(mods)
                .setMainClass(Optional.ofNullable(mainClass))
                .addDependencies(dependencies)
                .addDiscoveredPackages(resourceLoaders)
                .build();
    }

    interface XMLCloser extends AutoCloseable {
        void close() throws XMLStreamException;
    }

    /**
     * Obtain a module descriptor from an XML {@code module.xml} resource.
     *
     * @param resource the resource containing the XML descriptor (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if an I/O error occurs
     */
    public static ModuleDescriptor fromXml(Resource resource) throws IOException {
        try (InputStream is = resource.openStream()) {
            return fromXml(is);
        }
    }

    /**
     * Obtain a module descriptor from an XML input stream.
     *
     * @param is the input stream containing the XML descriptor (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if an I/O error occurs
     */
    public static ModuleDescriptor fromXml(InputStream is) throws IOException {
        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return fromXml(r);
        }
    }

    /**
     * Obtain a module descriptor from an XML reader.
     *
     * @param r the reader containing the XML descriptor (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if an I/O error occurs
     */
    public static ModuleDescriptor fromXml(Reader r) throws IOException {
        if (r instanceof BufferedReader br) {
            return fromXml(br);
        } else {
            try (BufferedReader br = new BufferedReader(r)) {
                return fromXml(br);
            }
        }
    }

    /**
     * Obtain a module descriptor from an XML buffered reader.
     *
     * @param br the buffered reader containing the XML descriptor (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws IOException if an I/O error occurs
     */
    public static ModuleDescriptor fromXml(BufferedReader br) throws IOException {
        XMLInputFactory xmlInputFactory = XMLInputFactory.newDefaultFactory();
        try {
            XMLStreamReader xml = xmlInputFactory.createXMLStreamReader(br);
            try (XMLCloser ignored = xml::close) {
                return fromXml(xml);
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    /**
     * Obtain a module descriptor from an XML stream reader.
     *
     * @param xml the XML stream reader positioned before the root element (must not be {@code null})
     * @return the module descriptor (not {@code null})
     * @throws XMLStreamException if an XML parsing error occurs
     */
    public static ModuleDescriptor fromXml(XMLStreamReader xml) throws XMLStreamException {
        switch (xml.nextTag()) {
            case XMLStreamConstants.START_ELEMENT -> {
                checkNamespace(xml);
                switch (xml.getLocalName()) {
                    case "module" -> {
                        return parseModuleElement(xml);
                    }
                    default -> throw unknownElement(xml);
                }
            }
            default -> throw unexpectedContent(xml);
        }
    }

    private static <E> HashSet<E> newHashSet(Object ignored) {
        return new HashSet<>();
    }

    private static Map<String, Set<String>> parseManifestAdd(String value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Set<String>> map = Map.of();
        TextIter iter = TextIter.of(value);
        iter.skipWhiteSpace();
        while (iter.hasNext()) {
            String moduleName = dotName(iter);
            if (iter.peekNext() != '/') {
                throw invalidChar(value, iter.peekNext(), iter.position());
            }
            iter.next(); // consume /
            String packageName = dotName(iter);
            if (map.isEmpty()) {
                map = new LinkedHashMap<>();
            }
            map.computeIfAbsent(moduleName, ModuleDescriptor::newHashSet).add(packageName);
            iter.skipWhiteSpace();
        }
        return map;
    }

    private static IllegalArgumentException invalidChar(final String str, final int cp, final int idx) {
        return new IllegalArgumentException(
                "Invalid character '%s' at index %d of \"%s\"".formatted(Character.toString(cp), Integer.valueOf(idx), str));
    }

    private static String dotName(TextIter iter) {
        int cp = iter.peekNext();
        if (Character.isJavaIdentifierStart(cp)) {
            int start = iter.position();
            iter.next(); // consume
            while (iter.hasNext()) {
                cp = iter.peekNext();
                if (!Character.isJavaIdentifierPart(cp)) {
                    // done
                    return iter.substring(start);
                }
            }
            // end of string
            return iter.substring(start);
        }
        throw invalidChar(iter.text(), cp, iter.position());
    }

    private static ModuleDescriptor parseModuleElement(final XMLStreamReader xml) throws XMLStreamException {
        String name = null;
        Optional<String> version = Optional.empty();
        Modifier.Set mods = Modifier.Set.of();
        Optional<String> mainClass = Optional.empty();
        List<Dependency> dependencies = List.of();
        Set<String> uses = Set.of();
        Map<String, List<String>> provides = Map.of();
        Map<String, PackageInfo> packages = Map.of();
        // attributes
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = attrVal;
                case "automatic" -> {
                    if (attrVal.equals("true")) {
                        mods = mods.with(Modifier.AUTOMATIC);
                    }
                }
                case "unnamed" -> {
                    if (attrVal.equals("true")) {
                        mods = mods.with(Modifier.UNNAMED);
                    }
                }
                case "open" -> {
                    if (attrVal.equals("true")) {
                        mods = mods.with(Modifier.OPEN);
                    }
                }
                case "native-access" -> {
                    if (attrVal.equals("true")) {
                        mods = mods.with(Modifier.NATIVE_ACCESS);
                    }
                }
                case "version" -> version = Optional.of(attrVal);
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "dependencies" -> dependencies = parseDependenciesElement(xml);
                        case "packages" -> packages = parsePackagesElement(xml);
                        case "uses" -> uses = parseUsesElement(xml);
                        case "provides" -> provides = parseProvidesElement(xml);
                        case "main-class" -> mainClass = Optional.of(parseMainClassElement(xml));
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return builder()
                            .setName(name)
                            .setVersion(version)
                            .mergeModifiers(mods)
                            .setMainClass(mainClass)
                            .addDependencies(dependencies)
                            .addUses(uses)
                            .addProvides(provides)
                            .addPackages(packages)
                            .build();
                }
            }
        }
    }

    private static List<Dependency> parseDependenciesElement(final XMLStreamReader xml) throws XMLStreamException {
        List<Dependency> dependencies = new ArrayList<>();
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        // TODO: separate dep elements for linked, unlinked services, etc. dependency types
                        case "dependency" -> dependencies.add(parseDependencyElement(xml));
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return dependencies;
                }
            }
        }
    }

    private static Dependency parseDependencyElement(final XMLStreamReader xml) throws XMLStreamException {
        String name = null;
        Dependency.Modifier.Set modifiers = Dependency.Modifier.Set.of(Dependency.Modifier.SERVICES,
                Dependency.Modifier.LINKED, Dependency.Modifier.READ);
        Map<String, PackageAccess> packageAccesses = Map.of();
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = xml.getAttributeValue(i);
                case "transitive" -> {
                    if (Boolean.parseBoolean(xml.getAttributeValue(i))) {
                        modifiers = modifiers.with(Dependency.Modifier.TRANSITIVE);
                    }
                }
                case "optional" -> {
                    if (Boolean.parseBoolean(xml.getAttributeValue(i))) {
                        modifiers = modifiers.with(Dependency.Modifier.OPTIONAL);
                    }
                }
                case "linked" -> {
                    if (!Boolean.parseBoolean(xml.getAttributeValue(i))) {
                        modifiers = modifiers.without(Dependency.Modifier.LINKED);
                    }
                }
                case "read" -> {
                    if (!Boolean.parseBoolean(xml.getAttributeValue(i))) {
                        modifiers = modifiers.without(Dependency.Modifier.READ);
                    }
                }
                case "services" -> {
                    if (!Boolean.parseBoolean(xml.getAttributeValue(i))) {
                        modifiers = modifiers.without(Dependency.Modifier.SERVICES);
                    }
                }
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "add-exports" -> {
                            if (packageAccesses.isEmpty()) {
                                packageAccesses = new HashMap<>();
                            }
                            parseAccessElement(xml, packageAccesses, PackageAccess.EXPORTED);
                        }
                        case "add-opens" -> {
                            if (packageAccesses.isEmpty()) {
                                packageAccesses = new HashMap<>();
                            }
                            parseAccessElement(xml, packageAccesses, PackageAccess.OPEN);
                        }
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return Dependency.builder(name)
                            .mergeModifiers(modifiers)
                            .addPackageAccesses(packageAccesses)
                            .build();
                }
            }
        }
    }

    private static void parseAccessElement(final XMLStreamReader xml, final Map<String, PackageAccess> packageAccesses,
            PackageAccess access) throws XMLStreamException {
        String name = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = xml.getAttributeValue(i);
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        if (packageAccesses.containsKey(name)) {
            packageAccesses.put(name, PackageAccess.max(packageAccesses.get(name), access));
        } else {
            packageAccesses.put(name, access);
        }
        if (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            throw unknownElement(xml);
        }
    }

    private static Map<String, PackageInfo> parsePackagesElement(final XMLStreamReader xml) throws XMLStreamException {
        Map<String, PackageInfo> packages = new HashMap<>();
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "private" -> parsePrivatePackageElement(xml, packages);
                        case "export" -> parseExportPackageElement(xml, packages);
                        case "open" -> parseOpenPackageElement(xml, packages);
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return packages;
                }
            }
        }
    }

    private static void parsePrivatePackageElement(final XMLStreamReader xml, final Map<String, PackageInfo> packages)
            throws XMLStreamException {
        String pkg = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "package" -> pkg = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (pkg == null) {
            throw missingAttribute(xml, "package");
        }
        Set<String> exportTargets = Set.of();
        Set<String> openTargets = Set.of();
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "export-to" -> {
                            switch (exportTargets.size()) {
                                case 0 -> exportTargets = Set.of(parsePackageToElement(xml));
                                case 1 -> {
                                    exportTargets = new HashSet<>(exportTargets);
                                    exportTargets.add(parsePackageToElement(xml));
                                }
                                default -> exportTargets.add(parsePackageToElement(xml));
                            }
                        }
                        case "open-to" -> {
                            switch (openTargets.size()) {
                                case 0 -> openTargets = Set.of(parsePackageToElement(xml));
                                case 1 -> {
                                    openTargets = new HashSet<>(openTargets);
                                    openTargets.add(parsePackageToElement(xml));
                                }
                                default -> openTargets.add(parsePackageToElement(xml));
                            }
                        }
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (exportTargets.isEmpty() && openTargets.isEmpty()) {
                        packages.put(pkg, PackageInfo.PRIVATE);
                    } else {
                        packages.put(pkg, PackageInfo.of(PackageAccess.PRIVATE, exportTargets, openTargets));
                    }
                    return;
                }
            }
        }
    }

    private static void parseExportPackageElement(final XMLStreamReader xml, final Map<String, PackageInfo> packages)
            throws XMLStreamException {
        String pkg = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "package" -> pkg = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (pkg == null) {
            throw missingAttribute(xml, "package");
        }
        Set<String> openTargets = Set.of();
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "open-to" -> {
                            switch (openTargets.size()) {
                                case 0 -> openTargets = Set.of(parsePackageToElement(xml));
                                case 1 -> {
                                    openTargets = new HashSet<>(openTargets);
                                    openTargets.add(parsePackageToElement(xml));
                                }
                                default -> openTargets.add(parsePackageToElement(xml));
                            }
                        }
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (openTargets.isEmpty()) {
                        packages.put(pkg, PackageInfo.EXPORTED);
                    } else {
                        packages.put(pkg, PackageInfo.of(PackageAccess.EXPORTED, Set.of(), openTargets));
                    }
                    return;
                }
            }
        }
    }

    private static void parseOpenPackageElement(final XMLStreamReader xml, final Map<String, PackageInfo> packages)
            throws XMLStreamException {
        String pkg = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "package" -> pkg = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (pkg == null) {
            throw missingAttribute(xml, "package");
        }
        if (xml.nextTag() != XMLStreamConstants.END_ELEMENT) {
            throw unknownElement(xml);
        }
        packages.put(pkg, PackageInfo.OPEN);
    }

    private static String parsePackageToElement(final XMLStreamReader xml) throws XMLStreamException {
        String mod = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "module" -> mod = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (mod == null) {
            throw missingAttribute(xml, "module");
        }
        if (xml.nextTag() != XMLStreamConstants.END_ELEMENT) {
            throw unknownElement(xml);
        }
        return mod;
    }

    private static Set<String> parseUsesElement(final XMLStreamReader xml) throws XMLStreamException {
        Set<String> uses = new HashSet<>();
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "use" -> uses.add(parseUseElement(xml));
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return uses;
                }
            }
        }
    }

    private static String parseUseElement(final XMLStreamReader xml) throws XMLStreamException {
        String name = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        if (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            throw unknownElement(xml);
        }
        return name;
    }

    private static Map<String, List<String>> parseProvidesElement(final XMLStreamReader xml) throws XMLStreamException {
        Map<String, List<String>> provides = new HashMap<>();
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "provide" -> parseProvideElement(xml, provides);
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return provides;
                }
            }
        }
    }

    private static void parseProvideElement(final XMLStreamReader xml, final Map<String, List<String>> provides)
            throws XMLStreamException {
        String name = null;
        List<String> impls = new ArrayList<>();
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        for (;;) {
            switch (xml.nextTag()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    checkNamespace(xml);
                    switch (xml.getLocalName()) {
                        case "with" -> impls.add(parseWithElement(xml));
                        default -> throw unknownElement(xml);
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    provides.put(name, List.copyOf(impls));
                    return;
                }
            }
        }
    }

    private static String parseWithElement(final XMLStreamReader xml) throws XMLStreamException {
        String name = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        if (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            throw unknownElement(xml);
        }
        return name;
    }

    private static String parseMainClassElement(final XMLStreamReader xml) throws XMLStreamException {
        String name = null;
        int cnt = xml.getAttributeCount();
        for (int i = 0; i < cnt; i++) {
            final String attrVal = xml.getAttributeValue(i);
            switch (xml.getAttributeLocalName(i)) {
                case "name" -> name = attrVal;
                default -> throw unknownAttribute(xml, i);
            }
        }
        if (name == null) {
            throw missingAttribute(xml, "name");
        }
        if (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            throw unknownElement(xml);
        }
        return name;
    }

    private static Dependency.Modifier.Set toModifiers(final Set<AccessFlag> accessFlags) {
        Dependency.Modifier.Set mods = Dependency.Modifier.Set.of(Dependency.Modifier.SERVICES);
        for (AccessFlag accessFlag : accessFlags) {
            switch (accessFlag) {
                case STATIC_PHASE -> mods = mods.with(Dependency.Modifier.OPTIONAL);
                case SYNTHETIC -> mods = mods.with(Dependency.Modifier.SYNTHETIC);
                case MANDATED -> mods = mods.with(Dependency.Modifier.MANDATED);
                case TRANSITIVE -> mods = mods.with(Dependency.Modifier.TRANSITIVE);
            }
        }
        return mods;
    }

    private static void checkNamespace(final XMLStreamReader xml) throws XMLStreamException {
        if (!"urn:jboss:module:3.0".equals(xml.getNamespaceURI())) {
            throw unknownElement(xml);
        }
    }

    private static XMLStreamException missingAttribute(final XMLStreamReader xml, final String name) {
        return new XMLStreamException("Missing required attribute \"" + name + "\"", xml.getLocation());
    }

    private static XMLStreamException unexpectedContent(final XMLStreamReader xml) {
        return new XMLStreamException("Unexpected content encountered", xml.getLocation());
    }

    private static XMLStreamException unknownElement(final XMLStreamReader xml) {
        return new XMLStreamException("Unknown element \"" + xml.getName() + "\"", xml.getLocation());
    }

    private static XMLStreamException unknownAttribute(final XMLStreamReader xml, final int idx) {
        return new XMLStreamException("Unknown attribute \"" + xml.getAttributeName(idx) + "\"", xml.getLocation());
    }

    private static XMLStreamException unknownAttributeValue(final XMLStreamReader xml, final int idx) {
        return new XMLStreamException("Unknown attribute value \"" + xml.getAttributeValue(idx) + "\" for attribute \""
                + xml.getAttributeName(idx) + "\"", xml.getLocation());
    }

    private static IllegalArgumentException noModuleAttribute() {
        return new IllegalArgumentException("No module attribute found in module descriptor");
    }

    /**
     * {@return the module name (not {@code null})}
     */
    public String name() {
        return name;
    }

    /**
     * {@return the optional module version (not {@code null})}
     */
    public Optional<String> version() {
        return version;
    }

    /**
     * {@return the module modifiers (not {@code null})}
     */
    public Modifier.Set modifiers() {
        return modifiers;
    }

    /**
     * {@return the optional main class name (not {@code null})}
     */
    public Optional<String> mainClass() {
        return mainClass;
    }

    /**
     * {@return the optional module location URI (not {@code null})}
     */
    public Optional<URI> location() {
        return location;
    }

    /**
     * {@return the list of module dependencies (not {@code null})}
     */
    public List<Dependency> dependencies() {
        return dependencies;
    }

    /**
     * {@return the set of service class names that this module uses (not {@code null})}
     */
    public Set<String> uses() {
        return uses;
    }

    /**
     * {@return the map of service class names to their provider implementation class names (not {@code null})}
     */
    public Map<String, List<String>> provides() {
        return provides;
    }

    /**
     * {@return the map of package names to their package information (not {@code null})}
     */
    public Map<String, PackageInfo> packages() {
        return packages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof ModuleDescriptor other && equals(other);
    }

    /**
     * {@return {@code true} if the given module descriptor is equal to this one}
     *
     * @param other the other descriptor to compare (may be {@code null})
     */
    public boolean equals(ModuleDescriptor other) {
        return this == other || other != null &&
                name.equals(other.name) &&
                version.equals(other.version) &&
                modifiers.equals(other.modifiers) &&
                mainClass.equals(other.mainClass) &&
                location.equals(other.location) &&
                dependencies.equals(other.dependencies) &&
                uses.equals(other.uses) &&
                provides.equals(other.provides) &&
                packages.equals(other.packages);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, version, modifiers, mainClass, location, dependencies, uses, provides, packages);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ModuleDescriptor[" +
                "name=" + name + ", " +
                "version=" + version + ", " +
                "modifiers=" + modifiers + ", " +
                "mainClass=" + mainClass + ", " +
                "location=" + location + ", " +
                "dependencies=" + dependencies + ", " +
                "uses=" + uses + ", " +
                "provides=" + provides + ", " +
                "packages=" + packages + ']';
    }

    /**
     * Create a new descriptor builder.
     *
     * @return the new descriptor builder (not {@code null})
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new descriptor builder using the given descriptor as a template.
     *
     * @param existing the template descriptor (must not be {@code null})
     * @return the new descriptor builder (not {@code null})
     */
    public static Builder builder(ModuleDescriptor existing) {
        return new Builder(existing);
    }
}
