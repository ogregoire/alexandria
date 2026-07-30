package be.imgn.alexandria.domain.shared;

import java.util.Locale;

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

    private static final char NARROW_NO_BREAK_SPACE = ' ';

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

    /**
     * The languages that hold a space before the colon. French is the one this catalogue meets; the others follow the
     * same rule and are listed so that adding a book in one of them is not a surprise.
     */
    private static String separator(Locale locale) {
        return switch (locale == null ? "" : locale.getLanguage()) {
            case "fr", "br" -> NARROW_NO_BREAK_SPACE + ": ";
            default -> ": ";
        };
    }
}
