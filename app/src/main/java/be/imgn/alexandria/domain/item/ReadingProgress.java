package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Reading is a property of the copy you hold, not of the Work: you may have read one
 * translation and left another untouched, and the catalogue should be able to say so.
 */
public sealed interface ReadingProgress {

    record Unread() implements ReadingProgress {
    }

    record Reading(LocalDate since, Optional<Integer> page) implements ReadingProgress {
        public Reading {
            Objects.requireNonNull(since, "since");
            page = page == null ? Optional.empty() : page;
            page.ifPresent(p -> Guard.inRange(p, 1, 100_000, "page"));
        }
    }

    record Finished(LocalDate on, Optional<Rating> rating) implements ReadingProgress {
        public Finished {
            Objects.requireNonNull(on, "on");
            rating = rating == null ? Optional.empty() : rating;
        }
    }

    record Abandoned(LocalDate on, Optional<Integer> atPage, String why) implements ReadingProgress {
        public Abandoned {
            Objects.requireNonNull(on, "on");
            Guard.notBlank(why, "why");
            atPage = atPage == null ? Optional.empty() : atPage;
        }
    }

    ReadingProgress UNREAD = new Unread();

    default String display() {
        return switch (this) {
            case Unread() -> "unread";
            case Reading(LocalDate since, Optional<Integer> page) ->
                    "reading since " + since + page.map(p -> ", p. " + p).orElse("");
            case Finished(LocalDate on, Optional<Rating> rating) ->
                    "read " + on + rating.map(r -> ", " + r.stars() + "/5").orElse("");
            case Abandoned(LocalDate on, Optional<Integer> atPage, String why) ->
                    "abandoned " + on + atPage.map(p -> " at p. " + p).orElse("") + " — " + why;
        };
    }

    default boolean completed() {
        return this instanceof Finished;
    }
}
