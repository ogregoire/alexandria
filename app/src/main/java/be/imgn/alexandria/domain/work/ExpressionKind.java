package be.imgn.alexandria.domain.work;

import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Language;

/**
 * How an Expression realises its Work. This is the level at which a translation lives:
 * Grossman's English <em>Don Quixote</em> and Rutherford's are two Expressions of one Work,
 * and every printing of either is a Manifestation below them.
 */
public sealed interface ExpressionKind {

    /** The text as its creator issued it. */
    record Original() implements ExpressionKind {
    }

    record Translation(Language from) implements ExpressionKind {
        public Translation {
            if (from == null) {
                throw new IllegalArgumentException("from language is required for a translation");
            }
        }
    }

    /** A reworked text issued under the same Work, e.g. "revised and expanded". */
    record Revision(String label) implements ExpressionKind {
        public Revision {
            Guard.notBlank(label, "label");
        }
    }

    record Abridgement() implements ExpressionKind {
    }

    /** A change of form that stays within the Work, e.g. a graphic-novel treatment. */
    record Adaptation(String into) implements ExpressionKind {
        public Adaptation {
            Guard.notBlank(into, "into");
        }
    }

    /** A read-aloud realisation; the narrator is a contributor of this Expression. */
    record Narration() implements ExpressionKind {
    }

    ExpressionKind ORIGINAL = new Original();
    ExpressionKind ABRIDGED = new Abridgement();
    ExpressionKind NARRATED = new Narration();
}
