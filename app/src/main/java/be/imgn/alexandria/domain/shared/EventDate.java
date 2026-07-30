package be.imgn.alexandria.domain.shared;

import java.time.LocalDate;

/**
 * The date something happened to a copy — bought, given, started, finished — or the admission that nobody wrote it
 * down.
 *
 * <p>Distinct from {@link BibliographicDate}, which describes a publication and so admits a year, a circa and a range:
 * a book was published across 1937–1949, but you did not start reading one across a range of years. Here the date is
 * either known to the day or not known at all, and those are the two shapes.
 *
 * <p>A caller that needs the {@link LocalDate} itself matches on {@link On} — which is the point of the shape.
 *
 * <p>Its absence is a shape, not a null and not an {@code Optional} component. {@code Optional} is documented for
 * return types; a record component is also a constructor parameter, and asking a caller to build an {@code Optional} in
 * order to hand it straight back is not what it is for.
 */
public sealed interface EventDate {

    EventDate UNRECORDED = new Unrecorded();

    static EventDate on(LocalDate date) {
        return date == null ? UNRECORDED : new On(date);
    }

    /** Parses an ISO date, treating blank and null as never recorded. */
    static EventDate parse(String iso) {
        return iso == null || iso.isBlank() ? UNRECORDED : new On(LocalDate.parse(iso));
    }

    /** The ISO form, or empty when there is nothing to write. Blank means "omit the key". */
    String iso();

    /** How it reads to a person: the date, or nothing at all. */
    String display();

    record On(LocalDate date) implements EventDate {

        public On {
            if (date == null) {
                throw new IllegalArgumentException("a recorded date needs a date");
            }
        }

        @Override
        public String iso() {
            return date.toString();
        }

        @Override
        public String display() {
            return date.toString();
        }
    }

    record Unrecorded() implements EventDate {

        @Override
        public String iso() {
            return "";
        }

        @Override
        public String display() {
            return "";
        }
    }
}
