package io.smallrye.modules.desc;

import java.util.List;
import java.util.function.IntFunction;

/**
 * A set of modifiers.
 */
public final class Modifiers<M extends Enum<M> & ModifierFlag> {
    private final List<M> values;
    private final IntFunction<Modifiers<M>> setFn;
    private final int flags;

    Modifiers(List<M> values, IntFunction<Modifiers<M>> setFn, final int flags) {
        this.values = values;
        this.setFn = setFn;
        this.flags = flags;
    }

    /**
     * {@return a string representation of this modifier set}
     */
    public String toString() {
        int flags = this.flags;
        if (flags != 0) {
            StringBuilder sb = new StringBuilder();
            int hob = Integer.highestOneBit(flags);
            sb.append(values.get(Integer.numberOfTrailingZeros(hob)));
            flags &= ~hob;
            while (flags != 0) {
                sb.append(' ');
                hob = Integer.highestOneBit(flags);
                sb.append(values.get(Integer.numberOfTrailingZeros(hob)));
                flags &= ~hob;
            }
            return sb.toString();
        }
        return "(none)";
    }

    /**
     * {@return {@code true} if this set contains the given modifier}
     *
     * @param item the modifier to test (may be {@code null}, which is always absent)
     */
    public boolean contains(M item) {
        return containsAny(bit(item));
    }

    /**
     * {@return {@code true} if this set contains any of the given modifiers}
     *
     * @param item0 the first modifier to test
     * @param item1 the second modifier to test
     */
    public boolean containsAny(M item0, M item1) {
        return containsAny(bit(item0) | bit(item1));
    }

    /**
     * {@return {@code true} if this set contains any of the given modifiers}
     *
     * @param item0 the first modifier to test
     * @param item1 the second modifier to test
     * @param item2 the third modifier to test
     */
    public boolean containsAny(M item0, M item1, M item2) {
        return containsAny(bit(item0) | bit(item1) | bit(item2));
    }

    /**
     * {@return {@code true} if this set contains any of the given modifiers}
     *
     * @param item0 the first modifier to test
     * @param item1 the second modifier to test
     * @param item2 the third modifier to test
     * @param item3 the fourth modifier to test
     */
    public boolean containsAny(M item0, M item1, M item2, M item3) {
        return containsAny(bit(item0) | bit(item1) | bit(item2) | bit(item3));
    }

    private boolean containsAny(int bits) {
        return (flags & bits) != 0;
    }

    /**
     * {@return {@code true} if this set contains all of the given modifiers}
     *
     * @param items the modifiers to test (must not be {@code null})
     */
    @SafeVarargs
    public final boolean containsAll(M... items) {
        int bits = 0;
        for (M item : items) {
            bits |= bit(item);
        }
        return containsAll(bits);
    }

    /**
     * {@return {@code true} if this set contains all of the given modifiers}
     *
     * @param item0 the first modifier to test
     * @param item1 the second modifier to test
     */
    public boolean containsAll(M item0, M item1) {
        return containsAll(bit(item0) | bit(item1));
    }

    /**
     * {@return {@code true} if this set contains all of the given modifiers}
     *
     * @param item0 the first modifier to test
     * @param item1 the second modifier to test
     * @param item2 the third modifier to test
     */
    public boolean containsAll(M item0, M item1, M item2) {
        return containsAll(bit(item0) | bit(item1) | bit(item2));
    }

    /**
     * {@return {@code true} if this set contains all of the given modifiers}
     *
     * @param item0 the first modifier to test
     * @param item1 the second modifier to test
     * @param item2 the third modifier to test
     * @param item3 the fourth modifier to test
     */
    public boolean containsAll(M item0, M item1, M item2, M item3) {
        return containsAll(bit(item0) | bit(item1) | bit(item2) | bit(item3));
    }

    /**
     * {@return {@code true} if this set contains any of the given modifiers}
     *
     * @param items the modifiers to test (must not be {@code null})
     */
    @SafeVarargs
    public final boolean containsAny(M... items) {
        int bits = 0;
        for (M item : items) {
            bits |= bit(item);
        }
        return containsAny(bits);
    }

    private boolean containsAll(int bits) {
        return (flags & bits) == bits;
    }

    private static <M extends Enum<M> & ModifierFlag> int bit(final M item) {
        return item == null ? 0 : 1 << item.ordinal();
    }

    /**
     * {@return a modifier set that includes the given modifier in addition to the modifiers in this set}
     *
     * @param item the modifier to add (may be {@code null}, which is ignored)
     */
    public Modifiers<M> with(M item) {
        int newFlags = flags | bit(item);
        return newFlags == flags ? this : setFn.apply(newFlags);
    }

    /**
     * {@return a modifier set that includes both of the given modifiers in addition to the modifiers in this set}
     *
     * @param item0 the first modifier to add
     * @param item1 the second modifier to add
     */
    public Modifiers<M> withAll(M item0, M item1) {
        int newFlags = flags | bit(item0) | bit(item1);
        return newFlags == flags ? this : setFn.apply(newFlags);
    }

    /**
     * {@return a modifier set that is the union of this set and the given set}
     *
     * @param other the other modifier set (must not be {@code null})
     */
    public Modifiers<M> withAll(Modifiers<M> other) {
        int newFlags = flags | other.flags;
        return newFlags == flags ? this : setFn.apply(newFlags);
    }

    /**
     * {@return a modifier set with the modifiers in this set, excluding the given modifier}
     *
     * @param item the modifier to remove
     */
    public Modifiers<M> without(M item) {
        int newFlags = flags & ~bit(item);
        return newFlags == flags ? this : setFn.apply(newFlags);
    }
}
