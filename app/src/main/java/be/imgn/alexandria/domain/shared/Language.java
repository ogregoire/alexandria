package be.imgn.alexandria.domain.shared;

import java.util.Locale;

/** An ISO 639 language code. */
public record Language(String code) {

    public Language {
        Guard.notBlank(code, "code");
        code = code.toLowerCase(Locale.ROOT);
        if (code.length() < 2 || code.length() > 3) {
            throw new IllegalArgumentException("language code must be ISO 639-1 or 639-3 but was " + code);
        }
    }

    public static final Language ENGLISH = new Language("en");
    public static final Language FRENCH = new Language("fr");
    public static final Language DUTCH = new Language("nl");
    public static final Language SPANISH = new Language("es");
    public static final Language GERMAN = new Language("de");

    public String displayName() {
        return Locale.of(code).getDisplayLanguage(Locale.ENGLISH);
    }
}
