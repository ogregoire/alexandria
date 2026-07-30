package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Money;

import java.time.LocalDate;
import java.util.Optional;

/**
 * How a copy entered the library — and, decisively, whether it is yours to keep.
 *
 * <p>Dates and provenance are optional throughout. That a book was a gift is worth recording
 * even when the occasion and the giver are long forgotten, and demanding them would either
 * block the record or invite an invented one.
 *
 * <p>{@link Borrowed#from} is the exception and stays required: a copy you cannot name the
 * owner of is not one you can give back, and {@link #owned()} returning false would be an
 * assertion with nothing behind it.
 */
public sealed interface Acquisition {

    /** False only for copies that must go back to someone. */
    boolean owned();

    Optional<LocalDate> on();

    record Purchased(Optional<LocalDate> date, Optional<Money> price, Optional<String> from)
            implements Acquisition {
        public Purchased {
            date = date == null ? Optional.empty() : date;
            price = price == null ? Optional.empty() : price;
            from = from == null ? Optional.empty() : from.filter(s -> !s.isBlank());
        }

        public static Purchased on(LocalDate date, Money price, String from) {
            return new Purchased(Optional.ofNullable(date), Optional.ofNullable(price),
                    Optional.ofNullable(from));
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public Optional<LocalDate> on() {
            return date;
        }
    }

    record Gift(Optional<LocalDate> date, Optional<String> from) implements Acquisition {
        public Gift {
            date = date == null ? Optional.empty() : date;
            from = from == null ? Optional.empty() : from.filter(s -> !s.isBlank());
        }

        /** A gift, with neither the occasion nor the giver recorded. */
        public static Gift unattributed() {
            return new Gift(Optional.empty(), Optional.empty());
        }

        public static Gift from(String giver, LocalDate date) {
            return new Gift(Optional.ofNullable(date), Optional.ofNullable(giver));
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public Optional<LocalDate> on() {
            return date;
        }
    }

    record Inherited(Optional<LocalDate> date, Optional<String> from) implements Acquisition {
        public Inherited {
            date = date == null ? Optional.empty() : date;
            from = from == null ? Optional.empty() : from.filter(s -> !s.isBlank());
        }

        public static Inherited from(String previousOwner, LocalDate date) {
            return new Inherited(Optional.ofNullable(date), Optional.ofNullable(previousOwner));
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public Optional<LocalDate> on() {
            return date;
        }
    }

    /**
     * On loan to you. The copy belongs to someone else and is expected back, so the lender is
     * the one piece of provenance that cannot be left out.
     */
    record Borrowed(String from, Optional<LocalDate> since, Optional<LocalDate> due)
            implements Acquisition {
        public Borrowed {
            Guard.notBlank(from, "from");
            since = since == null ? Optional.empty() : since;
            due = due == null ? Optional.empty() : due;
            if (since.isPresent() && due.isPresent() && due.get().isBefore(since.get())) {
                throw new IllegalArgumentException("due date must not precede the loan date");
            }
        }

        @Override
        public boolean owned() {
            return false;
        }

        @Override
        public Optional<LocalDate> on() {
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
        public Optional<LocalDate> on() {
            return Optional.empty();
        }
    }

    Acquisition UNRECORDED = new Unrecorded();
}
