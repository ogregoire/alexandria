package be.imgn.alexandria.domain.item;

import java.time.LocalDate;

import be.imgn.alexandria.domain.shared.EventDate;
import be.imgn.alexandria.domain.shared.Guard;

/**
 * Reading is a property of the copy you hold, not of the Work: you may have read one translation and left another
 * untouched, and the catalogue should be able to say so.
 *
 * <p>Every date here is optional. That a book has been read is a fact worth recording on its own; when it was read is
 * often simply not remembered, and requiring the date would either block the record or invite a made-up one.
 */
public sealed interface ReadingProgress {

    record Unread() implements ReadingProgress {}

    record Reading(EventDate since, PageReached page) implements ReadingProgress {
        public Reading {
            since = since == null ? EventDate.UNRECORDED : since;
            page = page == null ? PageReached.UNRECORDED : page;
        }

        public static Reading startedOn(LocalDate since) {
            return new Reading(EventDate.on(since), PageReached.UNRECORDED);
        }
    }

    record Finished(EventDate on, Verdict verdict) implements ReadingProgress {
        public Finished {
            on = on == null ? EventDate.UNRECORDED : on;
            verdict = verdict == null ? Verdict.UNRATED : verdict;
        }

        /** Read, date unrecorded — the common case for anything read before you kept a list. */
        public static Finished undated() {
            return new Finished(EventDate.UNRECORDED, Verdict.UNRATED);
        }

        public static Finished on(LocalDate date, Rating rating) {
            return new Finished(EventDate.on(date), Verdict.of(rating));
        }
    }

    record Abandoned(EventDate on, PageReached atPage, String why) implements ReadingProgress {
        public Abandoned {
            on = on == null ? EventDate.UNRECORDED : on;
            Guard.notBlank(why, "why");
            atPage = atPage == null ? PageReached.UNRECORDED : atPage;
        }
    }

    ReadingProgress UNREAD = new Unread();

    default String display() {
        return switch (this) {
            case Unread() -> "unread";
            case Reading(EventDate since, PageReached page) ->
                "reading" + phrase(" since ", since.display()) + phrase(", p. ", page.stored());
            case Finished(EventDate on, Verdict verdict) ->
                "read" + phrase(" ", on.display())
                        + (verdict.stars().isPresent() ? ", " + verdict.stars().getAsInt() + "/5" : "");
            case Abandoned(EventDate on, PageReached atPage, String why) ->
                "abandoned" + phrase(" ", on.display()) + phrase(" at p. ", atPage.stored()) + " — " + why;
        };
    }

    /** A fragment and its lead-in, or nothing at all when the fragment was never recorded. */
    private static String phrase(String lead, String value) {
        return value.isEmpty() ? "" : lead + value;
    }

    default boolean completed() {
        return this instanceof Finished;
    }
}
