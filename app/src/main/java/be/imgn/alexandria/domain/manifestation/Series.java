package be.imgn.alexandria.domain.manifestation;

import be.imgn.alexandria.domain.shared.Guard;

/**
 * A publisher's series, e.g. "Penguin Classics" no. 42.
 *
 * <p>Three shapes, not an {@code Optional} anywhere: most books belong to no series at all, plenty of series go
 * unnumbered, and a prequel can belong to a numbered series without taking a number in it. Folding "no series" in here
 * rather than wrapping the whole type in an {@code Optional} is what lets {@link Manifestation} hold a Series flat.
 */
public sealed interface Series {

    Series STANDALONE = new Standalone();

    /** The series as it is written on a spine: the name, and the number when it has one. Blank for a standalone. */
    String display();

    static Series of(String name) {
        return name == null || name.isBlank() ? STANDALONE : new Unnumbered(name);
    }

    /** A blank or absent name gives a {@link Standalone}; a blank number, an {@link Unnumbered} series. */
    static Series of(String name, String number) {
        if (name == null || name.isBlank()) {
            return STANDALONE;
        }
        return number == null || number.isBlank() ? new Unnumbered(name) : new Numbered(name, number);
    }

    record Standalone() implements Series {

        @Override
        public String display() {
            return "";
        }
    }

    record Unnumbered(String name) implements Series {

        public Unnumbered {
            Guard.notBlank(name, "name");
        }

        @Override
        public String display() {
            return name;
        }
    }

    record Numbered(String name, String number) implements Series {

        public Numbered {
            Guard.notBlank(name, "name");
            Guard.notBlank(number, "number");
        }

        @Override
        public String display() {
            return name + " " + number;
        }
    }
}
