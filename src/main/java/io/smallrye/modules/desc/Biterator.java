package io.smallrye.modules.desc;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

final class Biterator<T> implements Iterator<T> {
    private final List<T> values;
    private int bits;

    Biterator(final int bits, final List<T> values) {
        this.bits = bits;
        this.values = values;
    }

    public boolean hasNext() {
        return bits != 0;
    }

    public T next() {
        int bits = this.bits;
        if (bits == 0) {
            throw new NoSuchElementException();
        }
        int lob = Integer.lowestOneBit(bits);
        this.bits = bits & ~lob;
        return values.get(Integer.numberOfTrailingZeros(lob));
    }
}
