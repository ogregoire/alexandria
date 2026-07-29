package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Money;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** How a copy entered the library — and, decisively, whether it is yours to keep. */
public sealed interface Acquisition {

    /** False only for copies that must go back to someone. */
    boolean owned();

    Optional<LocalDate> on();

    record Purchased(LocalDate date, Optional<Money> price, Optional<String> from) implements Acquisition {
        public Purchased {
            Objects.requireNonNull(date, "date");
            price = price == null ? Optional.empty() : price;
            from = from == null ? Optional.empty() : from.filter(s -> !s.isBlank());
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public Optional<LocalDate> on() {
            return Optional.of(date);
        }
    }

    record Gift(LocalDate date, String from) implements Acquisition {
        public Gift {
            Objects.requireNonNull(date, "date");
            Guard.notBlank(from, "from");
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public Optional<LocalDate> on() {
            return Optional.of(date);
        }
    }

    record Inherited(LocalDate date, String from) implements Acquisition {
        public Inherited {
            Objects.requireNonNull(date, "date");
            Guard.notBlank(from, "from");
        }

        @Override
        public boolean owned() {
            return true;
        }

        @Override
        public Optional<LocalDate> on() {
            return Optional.of(date);
        }
    }

    /** On loan to you. The copy belongs to someone else and is expected back. */
    record Borrowed(String from, LocalDate since, Optional<LocalDate> due) implements Acquisition {
        public Borrowed {
            Guard.notBlank(from, "from");
            Objects.requireNonNull(since, "since");
            due = due == null ? Optional.empty() : due;
            if (due.isPresent() && due.get().isBefore(since)) {
                throw new IllegalArgumentException("due date must not precede the loan date");
            }
        }

        @Override
        public boolean owned() {
            return false;
        }

        @Override
        public Optional<LocalDate> on() {
            return Optional.of(since);
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
