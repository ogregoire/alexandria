package be.imgn.alexandria.domain.manifestation;

import java.util.Locale;

import be.imgn.alexandria.domain.shared.Guard;

/**
 * A publisher's series, e.g. "Penguin Classics" no. 42.
 *
 * <p>Three shapes, not an {@code Optional} anywhere: most books belong to no series at all, plenty of series go
 * unnumbered, and a prequel can belong to a numbered series without taking a number in it. Folding "no series" in here
 * rather than wrapping the whole type in an {@code Optional} is what lets {@link Manifestation} hold a Series flat.
 */
public sealed interface Series {

    Series STANDALONE = new Standalone();

    /** The series as it is written on a spine: the name, and the number when it has one. Blank for a standalone. */
    String display();

    /**
     * Where this falls in its series, for putting volumes in reading order. {@link #UNPLACED} when there is no number
     * to read, so anything unnumbered sorts after everything numbered.
     *
     * <p>The number is stored as it is printed and interpreted only here. That matters twice over: sorted as text,
     * volume 10 lands between 1 and 2, and a spine that says "Tome IV" means the fourth. A number that cannot be read
     * either way is left {@link #UNPLACED} rather than guessed at — the printed form is still what the page shows.
     */
    int position();

    /** The position of anything with no readable number: last. */
    int UNPLACED = Integer.MAX_VALUE;

    static Series of(String name) {
        return name == null || name.isBlank() ? STANDALONE : new Unnumbered(name);
    }

    /** A blank or absent name gives a {@link Standalone}; a blank number, an {@link Unnumbered} series. */
    static Series of(String name, String number) {
        if (name == null || name.isBlank()) {
            return STANDALONE;
        }
        return number == null || number.isBlank() ? new Unnumbered(name) : new Numbered(name, number);
    }

    record Standalone() implements Series {

        @Override
        public String display() {
            return "";
        }

        @Override
        public int position() {
            return UNPLACED;
        }
    }

    record Unnumbered(String name) implements Series {

        public Unnumbered {
            Guard.notBlank(name, "name");
        }

        @Override
        public String display() {
            return name;
        }

        @Override
        public int position() {
            return UNPLACED;
        }
    }

    record Numbered(String name, String number) implements Series {

        public Numbered {
            Guard.notBlank(name, "name");
            Guard.notBlank(number, "number");
        }

        @Override
        public String display() {
            return name + " " + number;
        }

        @Override
        public int position() {
            return read(number.trim());
        }
    }

    /** Arabic first, then Roman, then not at all. */
    private static int read(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException notArabic) {
            return roman(number);
        }
    }

    /**
     * A Roman numeral, or {@link #UNPLACED}.
     *
     * <p>Read by summing, subtracting any symbol standing before a larger one, and then checked by writing the total
     * back out and comparing. The round trip is what rejects the near-misses — "IIII", "IC", "VV" all sum to something
     * plausible and none of them is how the number is written — without a table of rules about which symbol may precede
     * which.
     */
    private static int roman(String number) {
        String upper = number.toUpperCase(Locale.ROOT);
        if (upper.isEmpty() || !upper.chars().allMatch(symbol -> "IVXLCDM".indexOf(symbol) >= 0)) {
            return UNPLACED;
        }
        int total = 0;
        for (int at = 0; at < upper.length(); at++) {
            int here = value(upper.charAt(at));
            boolean subtractive = at + 1 < upper.length() && here < value(upper.charAt(at + 1));
            total += subtractive ? -here : here;
        }
        return total > 0 && write(total).equals(upper) ? total : UNPLACED;
    }

    private static int value(char symbol) {
        return switch (symbol) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            default -> 1000;
        };
    }

    private static String write(int total) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int left = total;
        for (int at = 0; at < values.length; at++) {
            while (left >= values[at]) {
                out.append(numerals[at]);
                left -= values[at];
            }
        }
        return out.toString();
    }
}
