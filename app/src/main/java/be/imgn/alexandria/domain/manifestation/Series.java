package be.imgn.alexandria.domain.manifestation;

import be.imgn.alexandria.domain.shared.Guard;

/**
 * A publisher's series, e.g. "Penguin Classics" no. 42.
 *
 * <p>Numbered or not, rather than one shape holding an optional number, for the same reason as
 * {@link be.imgn.alexandria.domain.shared.Title}: {@code Optional} is a return type, and a record component is a
 * constructor parameter. Plenty of series are unnumbered, and a prequel can belong to a numbered series without taking
 * a number in it.
 */
public sealed interface Series {

    String name();

    /** The series as it is written on a spine: the name, and the number when it has one. */
    String display();

    static Series of(String name) {
        return new Unnumbered(name);
    }

    /** A blank or absent number gives an {@link Unnumbered} series. */
    static Series of(String name, String number) {
        return number == null || number.isBlank() ? new Unnumbered(name) : new Numbered(name, number);
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
