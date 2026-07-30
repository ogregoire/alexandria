package be.imgn.alexandria.domain.manifestation;

import java.util.Locale;
import java.util.Optional;

import be.imgn.alexandria.domain.shared.Guard;

/** ISBN normalisation, check-digit arithmetic and conversion between the two lengths. */
public final class Isbn {

    private Isbn() {}

    public static String normalise(String raw) {
        Guard.notBlank(raw, "isbn");
        return raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    static boolean isValid13(String digits) {
        if (!digits.matches("\\d{13}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += (digits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }

    static boolean isValid10(String digits) {
        if (!digits.matches("\\d{9}[\\dX]")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * (10 - i);
        }
        char last = digits.charAt(9);
        sum += last == 'X' ? 10 : last - '0';
        return sum % 11 == 0;
    }

    /** Groups as 978-x-xxxxx-xxx-x; the middle split is cosmetic, not registrant-accurate. */
    static String hyphenate13(String digits) {
        return digits.substring(0, 3) + "-" + digits.substring(3, 4) + "-" + digits.substring(4, 9) + "-"
                + digits.substring(9, 12) + "-" + digits.substring(12);
    }

    /**
     * The ISBN-10 equivalent of a 978-prefixed ISBN-13.
     *
     * <p>Needed because catalogues that predate 2007 — the BnF among them — index the older form, so a search for
     * 9782070360024 finds nothing while 2070360024 finds the record. Returns empty for 979-prefixed ISBNs, which have
     * no ISBN-10 equivalent at all.
     */
    public static Optional<String> toIsbn10(String isbn13) {
        String digits = normalise(isbn13);
        if (!isValid13(digits) || !digits.startsWith("978")) {
            return Optional.empty();
        }
        String body = digits.substring(3, 12);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (body.charAt(i) - '0') * (10 - i);
        }
        int check = (11 - (sum % 11)) % 11;
        return Optional.of(body + (check == 10 ? "X" : Integer.toString(check)));
    }

    /** The 978-prefixed ISBN-13 equivalent of an ISBN-10. */
    public static Optional<String> toIsbn13(String isbn10) {
        String digits = normalise(isbn10);
        if (!isValid10(digits)) {
            return Optional.empty();
        }
        String body = "978" + digits.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return Optional.of(body + ((10 - (sum % 10)) % 10));
    }
}
