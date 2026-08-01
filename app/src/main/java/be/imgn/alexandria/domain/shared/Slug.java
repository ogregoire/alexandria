package be.imgn.alexandria.domain.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identifiers in Alexandria are readable slugs rather than opaque UUIDs: they are the file names committed to git, so a
 * diff should say what changed without a lookup.
 */
public final class Slug {

    private static final Pattern VALID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SEGMENT = 60;

    /**
     * Letters that carry no accent to strip, and so survive nothing.
     *
     * <p>Unicode decomposition turns é into e plus a mark, and dropping the mark leaves the letter. It does nothing for
     * a letter that is simply its own: ß, ø, ł, þ, æ and their kin decompose to themselves, are not in {@code a-z}, and
     * were being deleted — <em>Der Prozeß</em> filed as {@code der-proze} and <em>Øst</em> as {@code st}. Deleting a
     * letter is worse than replacing it, because two different titles can then land on one identifier.
     *
     * <p>Spelled out rather than derived from the language of the book. Reading the language would mean casing text by
     * it, and that is a trap: Turkish maps I to a dotless ı, which has no decomposition either, so a Turkish title run
     * through its own locale comes out with its vowels deleted. One neutral table has no such edge, and needs no
     * language — which this model does not record for a Work in any case.
     *
     * <p>What it does give up is the local convention: German files ä under ae and Swedish under a. Both come out as a
     * here. That is a filing preference rather than a loss of information, and it can grow a language-aware branch the
     * day the model knows what language a work is in.
     */
    private static final Map<Character, String> STANDS_ALONE = Map.ofEntries(
            Map.entry('ß', "ss"),
            Map.entry('æ', "ae"),
            Map.entry('œ', "oe"),
            Map.entry('ø', "o"),
            Map.entry('å', "a"),
            Map.entry('ł', "l"),
            Map.entry('þ', "th"),
            Map.entry('ð', "d"),
            Map.entry('đ', "d"),
            Map.entry('ħ', "h"),
            Map.entry('ŧ', "t"),
            Map.entry('ı', "i"),
            Map.entry('ĳ', "ij"));

    private Slug() {}

    public static void validate(String value, String field) {
        Guard.notBlank(value, field);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase dash-separated slug but was '" + value + "'");
        }
    }

    public static String of(String text) {
        return candidate(text)
                .orElseThrow(() -> new IllegalArgumentException("'" + text + "' does not yield a usable slug"));
    }

    /**
     * The slug for this text, when it has one.
     *
     * <p>Not every string names something: a value a catalogue filled with punctuation — Open Library files Rivages as
     * "Rivages *" — reduces to nothing at all. Callers merely <em>suggesting</em> an identifier want an empty answer
     * there; only callers that must have one should reach for {@link #of} and its exception.
     */
    public static Optional<String> candidate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        // Lowercase first, so the table needs only lowercase entries; then spell out the letters
        // that stand alone, before decomposition gets a chance to leave them behind.
        String spelled = spellOut(text.toLowerCase(Locale.ROOT));
        String ascii = Normalizer.normalize(spelled, Normalizer.Form.NFD);
        ascii = DIACRITICS.matcher(ascii).replaceAll("");
        String slug = NON_SLUG.matcher(ascii).replaceAll("-");
        slug = trimDashes(slug);
        if (slug.length() > MAX_SEGMENT) {
            slug = trimDashes(slug.substring(0, MAX_SEGMENT));
        }
        return slug.isEmpty() ? Optional.empty() : Optional.of(slug);
    }

    /** Replaces the letters that stand alone with what they are usually spelled as in ASCII. */
    private static String spellOut(String lowercased) {
        StringBuilder out = new StringBuilder(lowercased.length());
        for (int i = 0; i < lowercased.length(); i++) {
            char letter = lowercased.charAt(i);
            String spelling = STANDS_ALONE.get(letter);
            out.append(spelling == null ? String.valueOf(letter) : spelling);
        }
        return out.toString();
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
