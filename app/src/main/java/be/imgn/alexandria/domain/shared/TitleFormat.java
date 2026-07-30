package be.imgn.alexandria.domain.shared;

import java.util.Locale;
import java.util.Set;

/**
 * Joins a title to its subtitle, which is a question of punctuation and therefore of language.
 *
 * <p>Two renderings, because there are two jobs. {@link #isbd} is the cataloguing form: space, colon, space, the same
 * in every language, which is exactly why the standard prescribes it — a record has to be legible to someone who does
 * not read the language it describes. {@link #display} is for a reader, and follows the typography of the language the
 * title is actually in: English closes the colon up against the word before it, French holds a space there.
 *
 * <p>The space French holds is a narrow no-break space (U+202F). A plain space would let the colon wrap onto the next
 * line by itself, which is the thing the rule exists to prevent.
 */
public final class TitleFormat {

    private static final char NARROW_NO_BREAK_SPACE = '\u202f';

    /**
     * ISO 639-1 codes for the languages that hold a space before a colon. Codes rather than {@link Locale} constants
     * because {@link Locale#getLanguage()} is what the comparison is made against, and because Breton has no constant.
     */
    private static final Set<String> SPACE_BEFORE_COLON = Set.of("fr", "br");

    private TitleFormat() {}

    /** The ISBD rendering: {@code Main : subtitle}, language-neutral by design. */
    public static String isbd(Title title) {
        return switch (title) {
            case Title.Plain(String main) -> main;
            case Title.Subtitled(String main, String subtitle) -> main + " : " + subtitle;
        };
    }

    /** The rendering a reader of that language expects. */
    public static String display(Title title, Locale locale) {
        return switch (title) {
            case Title.Plain(String main) -> main;
            case Title.Subtitled(String main, String subtitle) -> main + separator(locale) + subtitle;
        };
    }

    /** How a language punctuates the break between a title and its subtitle. */
    private enum Colon {
        /** English, German, Dutch, Spanish, Italian: the colon sits against the word before it. */
        CLOSED(": "),
        /** French and Breton hold a space there, and it must not be one the line can break at. */
        SPACED(NARROW_NO_BREAK_SPACE + ": ");

        private final String separator;

        Colon(String separator) {
            this.separator = separator;
        }
    }

    /**
     * Which rule a language follows. A closed set of two, so an enum is right where a sum type would be ceremony — the
     * variants carry no payload beyond the separator itself.
     */
    private static Colon rule(Locale locale) {
        if (locale == null) {
            return Colon.CLOSED;
        }
        return SPACE_BEFORE_COLON.contains(locale.getLanguage()) ? Colon.SPACED : Colon.CLOSED;
    }

    private static String separator(Locale locale) {
        return rule(locale).separator;
    }
}
