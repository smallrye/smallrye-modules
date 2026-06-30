package io.smallrye.modules.desc;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.smallrye.common.constraint.Assert;
import io.smallrye.modules.ModuleLoader;
import io.smallrye.modules.impl.Util;

/**
 * A dependency description.
 * If no optional dependency resolver is given, the module's own dependency resolver will be used.
 */
public final class Dependency {
    private final String moduleName;
    private final Modifier.Set modifiers;
    private final Optional<ModuleLoader> moduleLoader;
    private final Map<String, PackageAccess> packageAccesses;

    private Dependency(Builder builder) {
        moduleName = Assert.checkNotNullParam("moduleName", builder.moduleName);
        modifiers = builder.modifiers;
        moduleLoader = builder.moduleLoader;
        packageAccesses = Map.copyOf(builder.packageAccesses);
    }

    /**
     * {@return the dependency module name (not {@code null})}
     */
    public String moduleName() {
        return moduleName;
    }

    /**
     * {@return the dependency modifiers (not {@code null})}
     */
    public Modifier.Set modifiers() {
        return modifiers;
    }

    /**
     * {@return the optional module loader to use for this dependency (not {@code null})}
     */
    public Optional<ModuleLoader> moduleLoader() {
        return moduleLoader;
    }

    /**
     * {@return the extra package accesses for this dependency (not {@code null})}
     */
    public Map<String, PackageAccess> packageAccesses() {
        return packageAccesses;
    }

    /**
     * {@return a dependency with the given package accesses merged with existing accesses (not {@code null})}
     *
     * @param packageAccesses additional package accesses (must not be {@code null})
     */
    public Dependency withAdditionalPackageAccesses(Map<String, PackageAccess> packageAccesses) {
        Assert.checkNotNullParam("packageAccesses", packageAccesses);
        return new Builder(this)
                .addPackageAccesses(packageAccesses)
                .build();
    }

    /**
     * {@return a dependency that is the semantic merge of this dependency with the given one (not {@code null})}
     * Both dependencies must have the same module name.
     *
     * @param dependency the dependency to merge with (must not be {@code null})
     */
    public Dependency mergedWith(Dependency dependency) {
        Assert.checkNotNullParam("dependency", dependency);
        if (!moduleName.equals(dependency.moduleName)) {
            throw new IllegalArgumentException(
                    "Dependency module name " + dependency.moduleName + " does not match our module name of " + moduleName);
        }
        Optional<ModuleLoader> mergedLoader;
        if (moduleLoader.isPresent()) {
            if (dependency.moduleLoader.isPresent()) {
                throw new IllegalArgumentException("This dependency (on " + moduleName
                        + ") and the dependency to merge specify conflicting module loaders");
            } else {
                mergedLoader = moduleLoader;
            }
        } else {
            mergedLoader = dependency.moduleLoader;
        }
        Modifier.Set mergedModifiers = modifiers.mergedWith(dependency.modifiers);
        Map<String, PackageAccess> mergedPackageAccesses = Util.merge(packageAccesses, dependency.packageAccesses);
        if (modifiers.equals(mergedModifiers) && moduleLoader.equals(mergedLoader)
                && packageAccesses.equals(mergedPackageAccesses)) {
            return this;
        } else if (dependency.modifiers.equals(mergedModifiers) && dependency.moduleLoader.equals(mergedLoader)
                && packageAccesses.equals(mergedPackageAccesses)) {
            return dependency;
        } else {
            return builder(moduleName)
                    .mergeModifiers(mergedModifiers)
                    .setModuleLoader(mergedLoader)
                    .setPackageAccesses(mergedPackageAccesses)
                    .build();
        }
    }

    /**
     * {@return {@code true} if the dependency is synthetic}
     */
    public boolean isSynthetic() {
        return modifiers.contains(Modifier.SYNTHETIC);
    }

    /**
     * {@return {@code true} if the dependency is <em>not</em> synthetic}
     */
    public boolean isNonSynthetic() {
        return !modifiers.contains(Modifier.SYNTHETIC);
    }

    /**
     * {@return {@code true} if the dependency is mandated}
     */
    public boolean isMandated() {
        return modifiers.contains(Modifier.MANDATED);
    }

    /**
     * {@return {@code true} if the dependency is <em>not</em> mandated}
     */
    public boolean isNonMandated() {
        return !modifiers.contains(Modifier.MANDATED);
    }

    /**
     * {@return {@code true} if the dependency is optional}
     */
    public boolean isOptional() {
        return modifiers.contains(Modifier.OPTIONAL);
    }

    /**
     * {@return {@code true} if the dependency is <em>not</em> optional}
     */
    public boolean isNonOptional() {
        return !modifiers.contains(Modifier.OPTIONAL);
    }

    /**
     * {@return {@code true} if the dependency is transitive}
     */
    public boolean isTransitive() {
        return modifiers.contains(Modifier.TRANSITIVE);
    }

    /**
     * {@return {@code true} if the dependency is <em>not</em> transitive}
     */
    public boolean isNonTransitive() {
        return !modifiers.contains(Modifier.TRANSITIVE);
    }

    /**
     * {@return {@code true} if the dependency is linked for class loading}
     */
    public boolean isLinked() {
        return modifiers.contains(Modifier.LINKED);
    }

    /**
     * {@return {@code true} if the dependency is <em>not</em> linked}
     */
    public boolean isNonLinked() {
        return !modifiers.contains(Modifier.LINKED);
    }

    /**
     * {@return {@code true} if the dependency is readable from the source module}
     */
    public boolean isRead() {
        return modifiers.contains(Modifier.READ);
    }

    /**
     * {@return {@code true} if the dependency is <em>not</em> readable}
     */
    public boolean isNonRead() {
        return !modifiers.contains(Modifier.READ);
    }

    /**
     * {@return {@code true} if service implementations in the dependency are available}
     */
    public boolean isServices() {
        return modifiers.contains(Modifier.SERVICES);
    }

    /**
     * {@return {@code true} if service implementations in the dependency are <em>not</em> available}
     */
    public boolean isNonServices() {
        return !modifiers.contains(Modifier.SERVICES);
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(final Object obj) {
        return obj instanceof Dependency d && equals(d);
    }

    /**
     * {@return {@code true} if the given dependency is equal to this one}
     *
     * @param other the other dependency to compare (may be {@code null})
     */
    public boolean equals(final Dependency other) {
        return this == other || other != null
                && moduleName.equals(other.moduleName)
                && modifiers.equals(other.modifiers)
                && moduleLoader.equals(other.moduleLoader)
                && packageAccesses.equals(other.packageAccesses);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return Objects.hash(moduleName, modifiers, moduleLoader, packageAccesses);
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        return "Dependency[" +
                "moduleName=" + moduleName + ", " +
                "modifiers=" + modifiers + ", " +
                "moduleLoader=" + moduleLoader + ", " +
                "packageAccesses=" + packageAccesses + ']';
    }

    /**
     * {@return a new builder for constructing a dependency (not {@code null})}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@return a new builder for constructing a dependency with the given module name (not {@code null})}
     *
     * @param moduleName the dependency module name (must not be {@code null})
     */
    public static Builder builder(String moduleName) {
        return new Builder().setModuleName(moduleName);
    }

    /**
     * Modifiers for dependencies.
     */
    public enum Modifier implements ModifierFlag {
        /**
         * The dependency is synthetic.
         * Synthetic dependencies are added by frameworks.
         */
        SYNTHETIC,
        /**
         * The dependency is mandated by specification.
         */
        MANDATED,
        /**
         * The dependency is optional.
         * If it is not found when the module is linked, do not fail.
         */
        OPTIONAL,
        /**
         * The dependency is transitive, which is to say that a dependency
         * on the module containing this dependency implies a dependency on the target as well.
         * Only globally exported packages from a transitive dependency will be made available.
         */
        TRANSITIVE,
        /**
         * The dependency should be linked for class loading.
         * Implies {@link #READ}.
         */
        LINKED,
        /**
         * The dependency should be readable from the source module.
         * Implied for unnamed and automatic modules.
         */
        READ,
        /**
         * Service implementations in the dependency are available to the source module.
         */
        SERVICES,
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
             * {@return the set of five modifiers (not {@code null})}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             * @param modifier2 the third modifier
             * @param modifier3 the fourth modifier
             * @param modifier4 the fifth modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1, Modifier modifier2, Modifier modifier3,
                    Modifier modifier4) {
                return getSet(bit(modifier0) | bit(modifier1) | bit(modifier2) | bit(modifier3) | bit(modifier4));
            }

            /**
             * {@return the set of six modifiers (not {@code null})}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             * @param modifier2 the third modifier
             * @param modifier3 the fourth modifier
             * @param modifier4 the fifth modifier
             * @param modifier5 the sixth modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1, Modifier modifier2, Modifier modifier3,
                    Modifier modifier4, Modifier modifier5) {
                return getSet(bit(modifier0) | bit(modifier1) | bit(modifier2) | bit(modifier3) | bit(modifier4)
                        | bit(modifier5));
            }

            /**
             * {@return the set of seven modifiers (not {@code null})}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             * @param modifier2 the third modifier
             * @param modifier3 the fourth modifier
             * @param modifier4 the fifth modifier
             * @param modifier5 the sixth modifier
             * @param modifier6 the seventh modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1, Modifier modifier2, Modifier modifier3,
                    Modifier modifier4, Modifier modifier5, Modifier modifier6) {
                return getSet(bit(modifier0) | bit(modifier1) | bit(modifier2) | bit(modifier3) | bit(modifier4)
                        | bit(modifier5) | bit(modifier6));
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
             * Semantically merge this set with the given set.
             * The returned set has all of the modifiers of the given set,
             * with the exception that the {@link Dependency.Modifier#SYNTHETIC SYNTHETIC} modifier
             * is only in the returned set if it was present in both this set and the given one.
             *
             * @param other the other set (must not be {@code null})
             * @return the merged set (not {@code null})
             */
            public Set mergedWith(final Set other) {
                return xor(SYNTHETIC).withAll(other.xor(SYNTHETIC)).xor(SYNTHETIC);
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
            public boolean contains(final Object o) {
                return o instanceof Modifier m && contains(m);
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
     * A builder for {@link Dependency} instances.
     */
    public static final class Builder {
        private String moduleName;
        private Modifier.Set modifiers;
        private Optional<ModuleLoader> moduleLoader;
        private Map<String, PackageAccess> packageAccesses;

        private Builder() {
            modifiers = Modifier.Set.of();
            moduleLoader = Optional.empty();
            packageAccesses = Map.of();
        }

        private Builder(Dependency old) {
            moduleName = old.moduleName;
            modifiers = old.modifiers;
            moduleLoader = old.moduleLoader;
            packageAccesses = old.packageAccesses.isEmpty() ? Map.of() : new HashMap<>(old.packageAccesses);
        }

        /**
         * Set the module name of the dependency.
         *
         * @param moduleName the module name (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setModuleName(String moduleName) {
            this.moduleName = Assert.checkNotNullParam("moduleName", moduleName);
            return this;
        }

        /**
         * Add a modifier to the dependency.
         *
         * @param modifier the modifier to add (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addModifier(Modifier modifier) {
            this.modifiers = this.modifiers.with(Assert.checkNotNullParam("modifier", modifier));
            return this;
        }

        /**
         * Remove a modifier from the dependency.
         *
         * @param modifier the modifier to remove (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder removeModifier(Modifier modifier) {
            this.modifiers = this.modifiers.without(Assert.checkNotNullParam("modifier", modifier));
            return this;
        }

        /**
         * Merge the given modifier set into this dependency's modifiers.
         *
         * @param modifiers the modifiers to merge (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder mergeModifiers(Modifier.Set modifiers) {
            this.modifiers = this.modifiers.mergedWith(Assert.checkNotNullParam("modifiers", modifiers));
            return this;
        }

        /**
         * Set the module loader for this dependency.
         *
         * @param moduleLoader the module loader (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setModuleLoader(ModuleLoader moduleLoader) {
            this.moduleLoader = Optional.of(moduleLoader);
            return this;
        }

        /**
         * Set the optional module loader for this dependency.
         *
         * @param moduleLoader the optional module loader (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setModuleLoader(Optional<ModuleLoader> moduleLoader) {
            this.moduleLoader = Assert.checkNotNullParam("moduleLoader", moduleLoader);
            return this;
        }

        /**
         * Add a package access entry, merging with any existing entry via {@link PackageAccess#max}.
         *
         * @param packageName the package name (must not be {@code null})
         * @param access the access level (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addPackageAccess(String packageName, PackageAccess access) {
            Assert.checkNotNullParam("packageName", packageName);
            Assert.checkNotNullParam("access", access);
            if (packageAccesses.isEmpty()) {
                packageAccesses = new HashMap<>();
            }
            packageAccesses.merge(packageName, access, PackageAccess::max);
            return this;
        }

        /**
         * Add multiple package access entries, merging each with any existing entry via {@link PackageAccess#max}.
         *
         * @param packageAccesses the package accesses to add (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder addPackageAccesses(Map<String, PackageAccess> packageAccesses) {
            Assert.checkNotNullParam("packageAccesses", packageAccesses);
            packageAccesses.forEach(this::addPackageAccess);
            return this;
        }

        /**
         * Replace the entire package accesses map.
         *
         * @param packageAccesses the replacement package accesses (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public Builder setPackageAccesses(Map<String, PackageAccess> packageAccesses) {
            Assert.checkNotNullParam("packageAccesses", packageAccesses);
            this.packageAccesses = packageAccesses.isEmpty() ? Map.of() : new HashMap<>(packageAccesses);
            return this;
        }

        /**
         * Build the dependency.
         *
         * @return the constructed dependency (not {@code null})
         */
        public Dependency build() {
            return new Dependency(this);
        }
    }

    /**
     * The standard {@code java.base} dependency, for convenience.
     */
    public static final Dependency JAVA_BASE = builder("java.base")
            .addModifier(Modifier.SYNTHETIC)
            .addModifier(Modifier.MANDATED)
            .build();
}
