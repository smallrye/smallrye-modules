package io.smallrye.modules.desc;

import java.util.List;

/**
 * The access level of a package.
 */
public enum PackageAccess {
    /**
     * The package is private and not accessible outside the module.
     */
    PRIVATE,
    /**
     * The package is exported for compile-time and run-time access.
     */
    EXPORTED,
    /**
     * The package is open for deep reflective access.
     */
    OPEN,
    ;

    /**
     * An immutable list of all values of this enum type.
     */
    public static final List<PackageAccess> values = List.of(values());

    /**
     * {@return {@code true} if this access level is at least as permissive as the given level}
     *
     * @param other the access level to compare against (must not be {@code null})
     */
    public boolean isAtLeast(PackageAccess other) {
        return this.compareTo(other) >= 0;
    }

    /**
     * {@return the lesser of the two given access levels}
     *
     * @param access1 the first access level (must not be {@code null})
     * @param access2 the second access level (must not be {@code null})
     */
    public static PackageAccess min(final PackageAccess access1, PackageAccess access2) {
        return values.get(Math.min(access1.ordinal(), access2.ordinal()));
    }

    /**
     * {@return the greater of the two given access levels}
     *
     * @param access1 the first access level (must not be {@code null})
     * @param access2 the second access level (must not be {@code null})
     */
    public static PackageAccess max(final PackageAccess access1, PackageAccess access2) {
        return values.get(Math.max(access1.ordinal(), access2.ordinal()));
    }
}
