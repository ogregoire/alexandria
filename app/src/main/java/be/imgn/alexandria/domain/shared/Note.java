package be.imgn.alexandria.domain.shared;

/**
 * A short piece of free text that may simply never have been written down: who a book came from, where on a shelf it
 * sits, what edition statement the title page carries.
 *
 * <p>One type for the whole family because the alternative is a dozen near-identical two-shape sums —
 * {@code Provenance}, {@code Position}, {@code EditionStatement} — that differ only in the noun. The shape is the same
 * in every case: somebody wrote something, or nobody did.
 *
 * <p>Blank is not a third state. A field left empty on a form is nothing recorded, so {@link #of} folds it to
 * {@link #NOTHING} rather than storing an empty string that would later serialise as a key with no content.
 */
public sealed interface Note {

    Note NOTHING = new Nothing();

    static Note of(String text) {
        return text == null || text.isBlank() ? NOTHING : new Written(text.trim());
    }

    /** The text, or empty when there is none — what a form field wants, and what a codec omits. */
    String text();

    record Written(String text) implements Note {

        public Written {
            Guard.notBlank(text, "text");
        }
    }

    record Nothing() implements Note {

        @Override
        public String text() {
            return "";
        }
    }
}
