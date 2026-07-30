package be.imgn.alexandria.domain.item;

import java.util.OptionalInt;

/**
 * What a reader thought of a book, or the fact that they never said.
 *
 * <p>Finishing a book and rating it are different acts, and most books are finished without one. {@link Rating} stays a
 * plain one-to-five score; the not-having-rated-it is this type's business, so that {@code Finished} need not carry an
 * {@code Optional}.
 */
public sealed interface Verdict {

    Verdict UNRATED = new Unrated();

    static Verdict of(Rating rating) {
        return rating == null ? UNRATED : new Rated(rating);
    }

    static Verdict ofStars(int stars) {
        return new Rated(Rating.of(stars));
    }

    /** Nothing chosen in the field means no verdict, not nought stars. */
    static Verdict parse(String stars) {
        return stars == null || stars.isBlank() ? UNRATED : ofStars(Integer.parseInt(stars.trim()));
    }

    OptionalInt stars();

    /** For a form field and for the codec: the number of stars, or blank. */
    String stored();

    String display();

    record Rated(Rating rating) implements Verdict {

        public Rated {
            if (rating == null) {
                throw new IllegalArgumentException("a verdict needs a rating");
            }
        }

        @Override
        public OptionalInt stars() {
            return OptionalInt.of(rating.stars());
        }

        @Override
        public String stored() {
            return String.valueOf(rating.stars());
        }

        @Override
        public String display() {
            return rating.display();
        }
    }

    record Unrated() implements Verdict {

        @Override
        public OptionalInt stars() {
            return OptionalInt.empty();
        }

        @Override
        public String stored() {
            return "";
        }

        @Override
        public String display() {
            return "";
        }
    }
}
