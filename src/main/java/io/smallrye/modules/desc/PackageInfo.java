package io.smallrye.modules.desc;

import java.util.Set;

import io.smallrye.common.constraint.Assert;
import io.smallrye.modules.impl.Util;

/**
 * Information about a package in the module.
 * Note that export and open targets are only recognized when the target module directly requires the module containing
 * the package described by this information.
 *
 * @param packageAccess the outbound access level of the package (must not be {@code null})
 * @param exportTargets specific export targets in addition to those implied by {@link #packageAccess()} or
 *        {@link #openTargets()} (must not be {@code null})
 * @param openTargets specific open targets in addition to those implied by {@link #packageAccess()} (must not be {@code null})
 */
public record PackageInfo(
        PackageAccess packageAccess,
        Set<String> exportTargets,
        Set<String> openTargets) {

    /**
     * Default record constructor (but use {@link #of} instead, whenever possible).
     *
     * @param packageAccess the outbound access level of the package (must not be {@code null})
     * @param exportTargets specific export targets in addition to those implied by {@link #packageAccess()} or
     *        {@link #openTargets()} (must not be {@code null})
     * @param openTargets specific open targets in addition to those implied by {@link #packageAccess()} (must not be
     *        {@code null})
     */
    public PackageInfo {
        Assert.checkNotNullParam("packageAccess", packageAccess);
        exportTargets = packageAccess.isAtLeast(PackageAccess.EXPORTED) ? Set.of() : Set.copyOf(exportTargets);
        openTargets = packageAccess.isAtLeast(PackageAccess.OPEN) ? Set.of() : Set.copyOf(openTargets);
    }

    /**
     * A private package with no export or open targets.
     */
    public static final PackageInfo PRIVATE = new PackageInfo(PackageAccess.PRIVATE, Set.of(), Set.of());
    /**
     * An exported package with no additional open targets.
     */
    public static final PackageInfo EXPORTED = new PackageInfo(PackageAccess.EXPORTED, Set.of(), Set.of());
    /**
     * A fully open package.
     */
    public static final PackageInfo OPEN = new PackageInfo(PackageAccess.OPEN, Set.of(), Set.of());

    /**
     * {@return the canonical package info instance for the given access level}
     *
     * @param access the access level (must not be {@code null})
     */
    public static PackageInfo forAccess(final PackageAccess access) {
        return switch (access) {
            case PRIVATE -> PRIVATE;
            case EXPORTED -> EXPORTED;
            case OPEN -> OPEN;
        };
    }

    /**
     * {@return a package info with the given access level and targets, returning a canonical instance when possible}
     *
     * @param packageAccess the access level (must not be {@code null})
     * @param exportTargets the specific export targets (must not be {@code null})
     * @param openTargets the specific open targets (must not be {@code null})
     */
    public static PackageInfo of(PackageAccess packageAccess, Set<String> exportTargets, Set<String> openTargets) {
        exportTargets = Set.copyOf(exportTargets);
        openTargets = Set.copyOf(openTargets);
        if (packageAccess.isAtLeast(PackageAccess.OPEN)) {
            return OPEN;
        } else if (openTargets.isEmpty()) {
            if (packageAccess.isAtLeast(PackageAccess.EXPORTED)) {
                return EXPORTED;
            } else if (exportTargets.isEmpty()) {
                return PRIVATE;
            }
        }
        return new PackageInfo(packageAccess, exportTargets, openTargets);
    }

    /**
     * {@return a package info that is the result of merging this info with the given info}
     *
     * @param other the other package info (must not be {@code null})
     */
    public PackageInfo mergedWith(PackageInfo other) {
        return of(
                PackageAccess.max(packageAccess(), other.packageAccess()),
                Util.merge(exportTargets(), other.exportTargets()),
                Util.merge(openTargets(), other.openTargets()));
    }

    /**
     * {@return the result of merging two possibly-{@code null} package info instances}
     *
     * @param a the first package info (may be {@code null})
     * @param b the second package info (may be {@code null})
     */
    public static PackageInfo merge(PackageInfo a, PackageInfo b) {
        return a == null ? b == null ? PRIVATE : b : b == null ? a : a.mergedWith(b);
    }

    /**
     * {@return a package info with an access level that is at least as permissive as the given level}
     *
     * @param newAccess the minimum access level (must not be {@code null})
     */
    public PackageInfo withAccessAtLeast(PackageAccess newAccess) {
        return of(
                PackageAccess.max(packageAccess(), newAccess),
                exportTargets,
                openTargets);
    }

    /**
     * {@return a package info with the given export targets merged with existing targets}
     *
     * @param exportTargets additional export targets (must not be {@code null})
     */
    public PackageInfo withExportTargets(final Set<String> exportTargets) {
        return of(
                packageAccess(),
                Util.merge(exportTargets(), exportTargets),
                openTargets());
    }
}
