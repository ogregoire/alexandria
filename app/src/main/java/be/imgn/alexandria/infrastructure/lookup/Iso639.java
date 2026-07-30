package be.imgn.alexandria.infrastructure.lookup;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the three-letter language codes catalogues use into the two-letter ones the model stores.
 *
 * <p>Library data is written in ISO 639-2/B, the <em>bibliographic</em> set, where French is {@code fre} and German is
 * {@code ger}. The JDK only knows 639-2/T, the terminological set, where the same languages are {@code fra} and
 * {@code deu}. Twenty languages differ between the two, and those twenty are exactly the ones a European library
 * catalogue is full of, so they are listed here rather than guessed at.
 */
final class Iso639 {

    private static final Map<String, String> BIBLIOGRAPHIC_TO_TERMINOLOGIC = Map.ofEntries(
            Map.entry("alb", "sqi"),
            Map.entry("arm", "hye"),
            Map.entry("baq", "eus"),
            Map.entry("bur", "mya"),
            Map.entry("chi", "zho"),
            Map.entry("cze", "ces"),
            Map.entry("dut", "nld"),
            Map.entry("fre", "fra"),
            Map.entry("geo", "kat"),
            Map.entry("ger", "deu"),
            Map.entry("gre", "ell"),
            Map.entry("ice", "isl"),
            Map.entry("mac", "mkd"),
            Map.entry("mao", "mri"),
            Map.entry("may", "msa"),
            Map.entry("per", "fas"),
            Map.entry("rum", "ron"),
            Map.entry("slo", "slk"),
            Map.entry("tib", "bod"),
            Map.entry("wel", "cym"));

    private static final Map<String, String> THREE_TO_TWO = buildIndex();

    private Iso639() {}

    private static Map<String, String> buildIndex() {
        Map<String, String> index = new HashMap<>();
        for (String two : Locale.getISOLanguages()) {
            String three = Locale.of(two).getISO3Language();
            if (!three.isEmpty()) {
                index.put(three, two);
            }
        }
        BIBLIOGRAPHIC_TO_TERMINOLOGIC.forEach((bibliographic, terminologic) -> {
            String two = index.get(terminologic);
            if (two != null) {
                index.put(bibliographic, two);
            }
        });
        return Map.copyOf(index);
    }

    /**
     * @return the two-letter code, or the input unchanged when it is already two letters
     * @throws IllegalArgumentException for a code that names no known language
     */
    static String toTwoLetter(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("blank language code");
        }
        String normalised = code.trim().toLowerCase(Locale.ROOT);
        if (normalised.length() == 2) {
            return normalised;
        }
        String two = THREE_TO_TWO.get(normalised);
        if (two == null) {
            throw new IllegalArgumentException("unknown language code " + code);
        }
        return two;
    }
}
