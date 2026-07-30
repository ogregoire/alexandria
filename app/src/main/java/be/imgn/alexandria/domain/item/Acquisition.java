package be.imgn.alexandria.domain.item;

import java.time.LocalDate;

import be.imgn.alexandria.domain.shared.EventDate;
import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.shared.Note;
import be.imgn.alexandria.domain.shared.Price;

/**
 * How a copy entered the library — and, decisively, whether it is yours to keep.
 *
 * <p>Dates and provenance may always be missing. That a book was a gift is worth recording even when the occasion and
 * the giver are long forgotten, and demanding them would either block the record or invite an invented one — so each of
 * those facts is a two-shape type of its own ({@link EventDate}, {@link Note}, {@link Price}) rather than an
 * {@code Optional} standing in a constructor.
 *
 * <p>{@link Borrowed#from} is the exception and stays required: a copy you cannot name the owner of is not one you can
 * give back, and {@link #owned()} returning false would be an assertion with nothing behind it.
 */
public sealed interface Acquisition {

    /** False only for copies that must go back to someone. */
    boolean owned();

    EventDate on();

    record Purchased(EventDate date, Price price, Note from) implements Acquisition {
        public Purchased {
            date = date == null ? EventDate.UNRECORDED : date;
            price = price == null ? Price.UNRECORDED : price;
            from = from == null ? Note.NOTHING : from;
        }

        public static Purchased on(LocalDate date, Money price, String from) {
            return new Purchased(EventDate.on(date), Price.of(price), Note.of(from));
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public EventDate on() {
            return date;
        }
    }

    record Gift(EventDate date, Note from) implements Acquisition {
        public Gift {
            date = date == null ? EventDate.UNRECORDED : date;
            from = from == null ? Note.NOTHING : from;
        }

        /** A gift, with neither the occasion nor the giver recorded. */
        public static Gift unattributed() {
            return new Gift(EventDate.UNRECORDED, Note.NOTHING);
        }

        public static Gift from(String giver, LocalDate date) {
            return new Gift(EventDate.on(date), Note.of(giver));
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public EventDate on() {
            return date;
        }
    }

    record Inherited(EventDate date, Note from) implements Acquisition {
        public Inherited {
            date = date == null ? EventDate.UNRECORDED : date;
            from = from == null ? Note.NOTHING : from;
        }

        public static Inherited from(String previousOwner, LocalDate date) {
            return new Inherited(EventDate.on(date), Note.of(previousOwner));
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public EventDate on() {
            return date;
        }
    }

    /**
     * On loan to you. The copy belongs to someone else and is expected back, so the lender is the one piece of
     * provenance that cannot be left out.
     */
    record Borrowed(String from, EventDate since, EventDate due) implements Acquisition {
        public Borrowed {
            Guard.notBlank(from, "from");
            since = since == null ? EventDate.UNRECORDED : since;
            due = due == null ? EventDate.UNRECORDED : due;
            if (since instanceof EventDate.On(LocalDate lent)
                    && due instanceof EventDate.On(LocalDate back)
                    && back.isBefore(lent)) {
                throw new IllegalArgumentException("due date must not precede the loan date");
            }
        }

        @Override
        public boolean owned() {
            return false;
        }

        @Override
        public EventDate on() {
            return since;
        }
    }

    /** It has always been on the shelf and nobody remembers how. */
    record Unrecorded() implements Acquisition {
        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public EventDate on() {
            return EventDate.UNRECORDED;
        }
    }

    Acquisition UNRECORDED = new Unrecorded();
}
