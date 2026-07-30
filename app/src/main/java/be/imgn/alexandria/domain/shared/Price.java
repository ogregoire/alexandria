package be.imgn.alexandria.domain.shared;

import java.util.Optional;

/**
 * What a copy cost, or the fact that nobody kept the receipt.
 *
 * <p>Worth recording that a book was bought even when the price is long forgotten, so absence is one of the two shapes
 * rather than an {@code Optional} in the constructor.
 */
public sealed interface Price {

    Price UNRECORDED = new Unrecorded();

    static Price of(Money paid) {
        return paid == null ? UNRECORDED : new Paid(paid);
    }

    /** Parses the stored form, treating blank and null as never recorded. */
    static Price parse(String amount) {
        return amount == null || amount.isBlank() ? UNRECORDED : new Paid(Money.parse(amount));
    }

    Optional<Money> money();

    /** The stored form, or blank when there is nothing to write. */
    String stored();

    record Paid(Money amount) implements Price {

        public Paid {
            if (amount == null) {
                throw new IllegalArgumentException("a recorded price needs an amount");
            }
        }

        @Override
        public Optional<Money> money() {
            return Optional.of(amount);
        }

        @Override
        public String stored() {
            return amount.text();
        }
    }

    record Unrecorded() implements Price {

        @Override
        public Optional<Money> money() {
            return Optional.empty();
        }

        @Override
        public String stored() {
            return "";
        }
    }
}
