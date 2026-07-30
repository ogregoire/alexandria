package be.imgn.alexandria.domain.manifestation;

import java.util.Optional;

import be.imgn.alexandria.domain.shared.Guard;

/** The publisher-assigned identifier printed on a Manifestation, if any. */
public sealed interface Identifier {

    String display();

    record Isbn13(String digits) implements Identifier {
        public Isbn13 {
            digits = Isbn.normalise(digits);
            if (digits.length() != 13 || !Isbn.isValid13(digits)) {
                throw new IllegalArgumentException("not a valid ISBN-13: " + digits);
            }
        }

        @Override
        public String display() {
            return "ISBN " + Isbn.hyphenate13(digits);
        }
    }

    record Isbn10(String digits) implements Identifier {
        public Isbn10 {
            digits = Isbn.normalise(digits);
            if (digits.length() != 10 || !Isbn.isValid10(digits)) {
                throw new IllegalArgumentException("not a valid ISBN-10: " + digits);
            }
        }

        @Override
        public String display() {
            return "ISBN " + digits;
        }
    }

    record Asin(String value) implements Identifier {
        public Asin {
            Guard.notBlank(value, "value");
        }

        @Override
        public String display() {
            return "ASIN " + value;
        }
    }

    /** Anything else printed on the copyright page: a publisher's own catalogue number, a DOI. */
    record Custom(String scheme, String value) implements Identifier {
        public Custom {
            Guard.notBlank(scheme, "scheme");
            Guard.notBlank(value, "value");
        }

        @Override
        public String display() {
            return scheme + " " + value;
        }
    }

    /** Pre-1970 printings and private editions genuinely have none. */
    record None() implements Identifier {
        @Override
        public String display() {
            return "";
        }
    }

    Identifier NONE = new None();

    /** Accepts either length and picks the right variant. */
    static Identifier isbn(String raw) {
        String digits = Isbn.normalise(raw);
        return digits.length() == 10 ? new Isbn10(digits) : new Isbn13(digits);
    }

    /** The digits, whichever ISBN length this is; empty for everything else. */
    default Optional<String> isbnDigits() {
        return switch (this) {
            case Isbn13(String digits) -> Optional.of(digits);
            case Isbn10(String digits) -> Optional.of(digits);
            default -> Optional.empty();
        };
    }
}
