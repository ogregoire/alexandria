package be.imgn.alexandria.domain.shared;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Bibliographic dates are routinely imprecise: a title page may bear a year, a decade, a circa marker, or nothing at
 * all. Modelling that spread as a sum type keeps the imprecision explicit instead of smuggling it into a nullable
 * {@code LocalDate}.
 */
public sealed interface BibliographicDate {

    /** The year to sort and filter on, when one can be established at all. */
    Optional<Integer> sortYear();

    String display();

    record Exact(LocalDate date) implements BibliographicDate {
        @Override
        public Optional<Integer> sortYear() {
            return Optional.of(date.getYear());
        }

        @Override
        public String display() {
            return date.toString();
        }
    }

    record Year(int value) implements BibliographicDate {
        public Year {
            Guard.inRange(value, -3000, 2200, "value");
        }

        @Override
        public Optional<Integer> sortYear() {
            return Optional.of(value);
        }

        @Override
        public String display() {
            return Integer.toString(value);
        }
    }

    /** "circa 1750" — a year believed correct but not evidenced. */
    record Circa(int value) implements BibliographicDate {
        public Circa {
            Guard.inRange(value, -3000, 2200, "value");
        }

        @Override
        public Optional<Integer> sortYear() {
            return Optional.of(value);
        }

        @Override
        public String display() {
            return "ca. " + value;
        }
    }

    /** A closed interval, e.g. a multi-volume set issued 1901-1907. */
    record Between(int from, int to) implements BibliographicDate {
        public Between {
            if (from > to) {
                throw new IllegalArgumentException("from must not be after to");
            }
        }

        @Override
        public Optional<Integer> sortYear() {
            return Optional.of(from);
        }

        @Override
        public String display() {
            return from + "-" + to;
        }
    }

    record Unknown() implements BibliographicDate {
        @Override
        public Optional<Integer> sortYear() {
            return Optional.empty();
        }

        @Override
        public String display() {
            return "s.d.";
        }
    }

    BibliographicDate UNKNOWN = new Unknown();

    static BibliographicDate year(int value) {
        return new Year(value);
    }
}
