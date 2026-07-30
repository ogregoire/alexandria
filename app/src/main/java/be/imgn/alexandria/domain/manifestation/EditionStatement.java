package be.imgn.alexandria.domain.manifestation;

import be.imgn.alexandria.domain.shared.Guard;

/** Which edition the title page claims this is — "2e éd." — when it claims anything at all. */
public sealed interface EditionStatement {

    EditionStatement UNSTATED = new Unstated();

    static EditionStatement of(int number) {
        return new Stated(number);
    }

    /** Nothing typed in the field means the title page said nothing, not edition zero. */
    static EditionStatement parse(String number) {
        return number == null || number.isBlank() ? UNSTATED : new Stated(Integer.parseInt(number.trim()));
    }

    /** For a form field and for the codec: the number, or blank. */
    String stored();

    record Stated(int number) implements EditionStatement {

        public Stated {
            Guard.inRange(number, 1, 1_000, "editionStatement");
        }

        @Override
        public String stored() {
            return String.valueOf(number);
        }
    }

    record Unstated() implements EditionStatement {

        @Override
        public String stored() {
            return "";
        }
    }
}
