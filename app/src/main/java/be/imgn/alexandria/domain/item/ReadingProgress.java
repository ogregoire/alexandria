package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Reading is a property of the copy you hold, not of the Work: you may have read one
 * translation and left another untouched, and the catalogue should be able to say so.
 *
 * <p>Every date here is optional. That a book has been read is a fact worth recording on its
 * own; when it was read is often simply not remembered, and requiring the date would either
 * block the record or invite a made-up one.
 */
public sealed interface ReadingProgress {

    record Unread() implements ReadingProgress {
    }

    record Reading(Optional<LocalDate> since, Optional<Integer> page) implements ReadingProgress {
        public Reading {
            since = since == null ? Optional.empty() : since;
            page = page == null ? Optional.empty() : page;
            page.ifPresent(p -> Guard.inRange(p, 1, 100_000, "page"));
        }

        public static Reading startedOn(LocalDate since) {
            return new Reading(Optional.ofNullable(since), Optional.empty());
        }
    }

    record Finished(Optional<LocalDate> on, Optional<Rating> rating) implements ReadingProgress {
        public Finished {
            on = on == null ? Optional.empty() : on;
            rating = rating == null ? Optional.empty() : rating;
        }

        /** Read, date unrecorded — the common case for anything read before you kept a list. */
        public static Finished undated() {
            return new Finished(Optional.empty(), Optional.empty());
        }

        public static Finished on(LocalDate date, Rating rating) {
            return new Finished(Optional.ofNullable(date), Optional.ofNullable(rating));
        }
    }

    record Abandoned(Optional<LocalDate> on, Optional<Integer> atPage, String why)
            implements ReadingProgress {
        public Abandoned {
            on = on == null ? Optional.empty() : on;
            Guard.notBlank(why, "why");
            atPage = atPage == null ? Optional.empty() : atPage;
        }
    }

    ReadingProgress UNREAD = new Unread();

    default String display() {
        return switch (this) {
            case Unread() -> "unread";
            case Reading(Optional<LocalDate> since, Optional<Integer> page) ->
                    "reading" + since.map(date -> " since " + date).orElse("")
                            + page.map(p -> ", p. " + p).orElse("");
            case Finished(Optional<LocalDate> on, Optional<Rating> rating) ->
                    "read" + on.map(date -> " " + date).orElse("")
                            + rating.map(r -> ", " + r.stars() + "/5").orElse("");
            case Abandoned(Optional<LocalDate> on, Optional<Integer> atPage, String why) ->
                    "abandoned" + on.map(date -> " " + date).orElse("")
                            + atPage.map(p -> " at p. " + p).orElse("") + " — " + why;
        };
    }

    default boolean completed() {
        return this instanceof Finished;
    }
}
