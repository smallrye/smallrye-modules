package io.smallrye.modules.desc;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.smallrye.common.constraint.Assert;
import io.smallrye.modules.ModuleLoader;
import io.smallrye.modules.impl.Util;

/**
 * A dependency description.
 * If no optional dependency resolver is given, the module's own dependency resolver will be used.
 *
 * @param moduleName the dependency name (must not be {@code null})
 * @param modifiers the dependency modifiers (must not be {@code null})
 * @param moduleLoader the optional module loader to use for this dependency (must not be {@code null})
 * @param packageAccesses extra package access to the given dependency (must not be {@code null})
 */
public record Dependency(
        String moduleName,
        Modifier.Set modifiers,
        Optional<ModuleLoader> moduleLoader,
        Map<String, PackageAccess> packageAccesses) {
    /**
     * Construct a new instance.
     */
    public Dependency {
        Assert.checkNotNullParam("moduleName", moduleName);
        Assert.checkNotNullParam("modifiers", modifiers);
        Assert.checkNotNullParam("moduleLoader", moduleLoader);
        packageAccesses = Map.copyOf(packageAccesses);
    }

    /**
     * Construct a new instance.
     *
     * @param moduleName the dependency name (must not be {@code null})
     * @param modifiers the dependency modifiers (must not be {@code null})
     * @param moduleLoader the optional module loader to use for this dependency (must not be {@code null})
     */
    public Dependency(String moduleName, Modifier.Set modifiers, Optional<ModuleLoader> moduleLoader) {
        this(moduleName, modifiers, moduleLoader, Map.of());
    }

    /**
     * Construct a new instance.
     *
     * @param moduleName the dependency name (must not be {@code null})
     */
    public Dependency(String moduleName) {
        this(moduleName, Modifier.Set.of(), Optional.empty());
    }

    /**
     * Construct a new instance.
     *
     * @param moduleName the dependency name (must not be {@code null})
     * @param modifier the modifier to add (must not be {@code null})
     */
    public Dependency(String moduleName, Modifier modifier) {
        this(moduleName, Modifier.Set.of(modifier), Optional.empty());
    }

    /**
     * {@return a dependency with the given package accesses merged with existing accesses}
     *
     * @param packageAccesses additional package accesses (must not be {@code null})
     */
    public Dependency withAdditionalPackageAccesses(Map<String, PackageAccess> packageAccesses) {
        Assert.checkNotNullParam("packageAccesses", packageAccesses);
        return new Dependency(this.moduleName, this.modifiers, this.moduleLoader,
                Util.merge(this.packageAccesses, packageAccesses, PackageAccess::max));
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
             * {@return the empty set of modifiers}
             */
            public static Set of() {
                return getSet(0);
            }

            /**
             * {@return the set of one modifier}
             *
             * @param modifier the modifier
             */
            public static Set of(Modifier modifier) {
                return getSet(bit(modifier));
            }

            /**
             * {@return the set of two modifiers}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1) {
                return getSet(bit(modifier0) | bit(modifier1));
            }

            /**
             * {@return the set of three modifiers}
             *
             * @param modifier0 the first modifier
             * @param modifier1 the second modifier
             * @param modifier2 the third modifier
             */
            public static Set of(Modifier modifier0, Modifier modifier1, Modifier modifier2) {
                return getSet(bit(modifier0) | bit(modifier1) | bit(modifier2));
            }

            /**
             * {@return the set of four modifiers}
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
             * {@return the set of five modifiers}
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
             * {@return the set of six modifiers}
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
             * {@return the set of seven modifiers}
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
             * {@return the set of all modifiers}
             */
            public static Set ofAll() {
                return getSet(sets.length - 1);
            }

            public Set with(final Modifier item) {
                return setOf(flags | bit(item));
            }

            public Set withAll(final Modifier item0, final Modifier item1) {
                return setOf(flags | bit(item0) | bit(item1));
            }

            /**
             * {@return a modifier set that includes the given modifier set in addition to the modifiers in this set}
             *
             * @param other the modifier set to add
             */
            public Set withAll(final Set other) {
                return setOf(flags | other.flags);
            }

            public Set without(final Modifier item) {
                return setOf(flags & ~bit(item));
            }

            public Set xor(final Modifier item) {
                return setOf(flags ^ bit(item));
            }

            public Iterator<Modifier> iterator() {
                return new Biterator<>(flags, values);
            }

            public boolean contains(final Object o) {
                return o instanceof Modifier m && contains(m);
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
     * The standard {@code java.base} dependency, for convenience.
     */
    public static final Dependency JAVA_BASE = new Dependency("java.base",
            Modifier.Set.of(Modifier.SYNTHETIC, Modifier.MANDATED),
            Optional.empty(), Map.of());
}
