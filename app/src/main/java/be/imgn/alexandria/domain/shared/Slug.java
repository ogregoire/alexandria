package be.imgn.alexandria.domain.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identifiers in Alexandria are readable slugs rather than opaque UUIDs: they are the
 * file names committed to git, so a diff should say what changed without a lookup.
 */
public final class Slug {

    private static final Pattern VALID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SEGMENT = 60;

    private Slug() {
    }

    public static void validate(String value, String field) {
        Guard.notBlank(value, field);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase dash-separated slug but was '" + value + "'");
        }
    }

    public static String of(String text) {
        Guard.notBlank(text, "text");
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD);
        ascii = DIACRITICS.matcher(ascii).replaceAll("");
        String slug = NON_SLUG.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = trimDashes(slug);
        if (slug.length() > MAX_SEGMENT) {
            slug = trimDashes(slug.substring(0, MAX_SEGMENT));
        }
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("'" + text + "' does not yield a usable slug");
        }
        return slug;
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
