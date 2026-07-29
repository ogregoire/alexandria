package be.imgn.alexandria.domain.shared;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Invariant checks shared by the value objects and entities of the model. */
public final class Guard {

    private Guard() {
    }

    public static String notBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public static <T> List<T> copyOf(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    public static <T> Set<T> copyOf(Set<T> value) {
        return value == null ? Set.of() : Set.copyOf(value);
    }

    /**
     * Hash-set iteration order is not something to commit to git: an unchanged set would
     * diff differently between runs. Sets that reach a file go through here.
     */
    public static <T extends Comparable<T>> Set<T> sortedCopyOf(Set<T> value) {
        if (value == null || value.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(value.stream().sorted().toList()));
    }

    public static <T> List<T> notEmpty(List<T> value, String field) {
        List<T> copy = copyOf(value);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return copy;
    }

    public static int inRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be within [" + min + ", " + max + "] but was " + value);
        }
        return value;
    }
}
