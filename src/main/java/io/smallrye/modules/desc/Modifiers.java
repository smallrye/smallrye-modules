package io.smallrye.modules.desc;

import java.util.AbstractSet;

/**
 * A set of modifiers.
 */
abstract class Modifiers<M extends Enum<M> & ModifierFlag> extends AbstractSet<M> {
    final int flags;

    Modifiers(final int flags) {
        this.flags = flags;
    }

    /**
     * {@return a string representation of this modifier set (not {@code null})}
     */
    public String toString() {
        int flags = this.flags;
        if (flags != 0) {
            StringBuilder sb = new StringBuilder();
            int hob = Integer.highestOneBit(flags);
            sb.append(value(Integer.numberOfTrailingZeros(hob)));
            flags &= ~hob;
            while (flags != 0) {
                sb.append(' ');
                hob = Integer.highestOneBit(flags);
                sb.append(value(Integer.numberOfTrailingZeros(hob)));
                flags &= ~hob;
            }
            return sb.toString();
        }
        return "(none)";
    }

    abstract M value(int index);

    abstract Modifiers<M> setOf(int flags);

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

    private boolean containsAll(int bits) {
        return (flags & bits) == bits;
    }

    private static <M extends Enum<M> & ModifierFlag> int bit(final M item) {
        return item == null ? 0 : 1 << item.ordinal();
    }

    public int size() {
        return Integer.bitCount(flags);
    }

    /**
     * {@return a modifier set that includes the given modifier in addition to the modifiers in this set (not {@code null})}
     *
     * @param item the modifier to add (may be {@code null}, which is ignored)
     */
    public abstract Modifiers<M> with(M item);

    /**
     * {@return a modifier set that includes both of the given modifiers in addition to the modifiers in this set
     * (not {@code null})}
     *
     * @param item0 the first modifier to add
     * @param item1 the second modifier to add
     */
    public abstract Modifiers<M> withAll(M item0, M item1);

    /**
     * {@return a modifier set with the modifiers in this set, excluding the given modifier (not {@code null})}
     *
     * @param item the modifier to remove
     */
    public abstract Modifiers<M> without(M item);

    /**
     * {@return a modifier set with the modifiers in this set, except with presence (or absence) of the given modifier inverted
     * (not {@code null})}
     *
     * @param item the modifier to swap
     */
    public abstract Modifiers<M> xor(M item);
}
