package be.imgn.alexandria.domain.agent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import be.imgn.alexandria.domain.shared.Guard;

/**
 * The three shapes one name takes: how it is displayed, how it is filed, and the single word an identifier is built
 * from.
 *
 * <p>Library sources hand out names already inverted — the BnF says "Tolkien, John Ronald Reuel" — while a title page
 * says "J. R. R. Tolkien". Guessing the surname by taking the last word gets *Reuel* from the first and *Tolkien* from
 * the second, which is how one book ended up filed under a middle name. Reading the comma settles it.
 *
 * @param display the form to show, uninverted
 * @param sortName the form to file under, "Tolkien, J. R. R."
 * @param filingWord the surname alone, for building an identifier
 */
public record NameForm(String display, String sortName, String filingWord) {

    /**
     * Particles that belong to the surname when capitalised and precede it when not.
     *
     * <p>The case carries the convention, and it happens to be the rule that gets both of these right: "Le Guin" files
     * under L, "de Cervantes" files under C. Flemish "Van Damme" files under V and Dutch "van Gogh" under G, which is
     * the same distinction.
     */
    private static final Set<String> PARTICLES = Set.of(
            "de", "del", "della", "di", "da", "do", "dos", "du", "des", "van", "von", "der", "den", "ter", "ten", "la",
            "le", "les", "lo", "el", "al", "af", "av", "bin", "ibn", "abu", "ben", "mac", "mc", "st", "saint", "ap");

    /** Words that name what a publisher is rather than which publisher it is. */
    private static final Set<String> ORGANISATION_TYPES = Set.of(
            "editeur",
            "editeurs",
            "edition",
            "editions",
            "edizioni",
            "ediciones",
            "editorial",
            "verlag",
            "uitgeverij",
            "uitgevers",
            "forlag",
            "publisher",
            "publishers",
            "publishing",
            "press",
            "presses",
            "book",
            "books",
            "library",
            "classics",
            "editore",
            "ltd",
            "limited",
            "inc",
            "co",
            "company",
            "sa",
            "nv",
            "bv",
            "gmbh",
            "plc",
            "sons");

    public NameForm {
        Guard.notBlank(display, "display");
        Guard.notBlank(sortName, "sortName");
        Guard.notBlank(filingWord, "filingWord");
    }

    public static NameForm of(String raw, AgentKind kind) {
        return kind instanceof AgentKind.Organisation ? ofOrganisation(raw) : ofPerson(raw);
    }

    /**
     * Reads a personal name in either order.
     *
     * <p>A comma means it arrived inverted and the surname is what precedes it. Without one, the surname is the last
     * word plus any capitalised particles in front of it.
     */
    public static NameForm ofPerson(String raw) {
        String name = collapse(raw);
        int comma = name.indexOf(',');
        if (comma > 0) {
            String surname = name.substring(0, comma).trim();
            String rest = name.substring(comma + 1).trim();
            if (!surname.isEmpty()) {
                String display = rest.isEmpty() ? surname : rest + " " + surname;
                return new NameForm(display, rest.isEmpty() ? surname : surname + ", " + rest, filingWordOf(surname));
            }
        }

        List<String> words = List.of(name.split(" "));
        if (words.size() == 1) {
            return new NameForm(name, name, filingWordOf(name));
        }

        int start = words.size() - 1;
        while (start > 0 && isCapitalisedParticle(words.get(start - 1))) {
            start--;
        }
        String surname = String.join(" ", words.subList(start, words.size()));
        String given = String.join(" ", words.subList(0, start));
        return new NameForm(name, given.isEmpty() ? surname : surname + ", " + given, filingWordOf(surname));
    }

    /**
     * An organisation displays and files under its own name; only the identifier drops the words saying what kind of
     * thing it is, so "Christian Bourgois éditeur" yields <em>Bourgois</em> rather than <em>éditeur</em>.
     */
    public static NameForm ofOrganisation(String raw) {
        String name = collapse(raw);
        List<String> words = new ArrayList<>(List.of(name.split(" ")));
        // Catalogues carry marks that are not part of the name: Open Library files Rivages as
        // "Rivages *". A trailing token with no letter or digit in it cannot be what the
        // publisher files under, so it is dropped rather than becoming the identifier.
        while (words.size() > 1 && (isOrganisationType(words.getLast()) || isPunctuation(words.getLast()))) {
            words.removeLast();
        }
        return new NameForm(name, name, filingWordOf(words.getLast()));
    }

    /** Apostrophes would slug into a stray segment, so "Everyman's" files as "Everymans". */
    private static String filingWordOf(String surname) {
        String cleaned = surname.replaceAll("[’']", "").trim();
        return cleaned.isEmpty() ? surname : cleaned;
    }

    private static boolean isCapitalisedParticle(String word) {
        return !word.isEmpty() && Character.isUpperCase(word.charAt(0)) && PARTICLES.contains(strip(word));
    }

    private static boolean isOrganisationType(String word) {
        return ORGANISATION_TYPES.contains(strip(word)) || "&".equals(word);
    }

    private static boolean isPunctuation(String word) {
        return word.codePoints().noneMatch(Character::isLetterOrDigit);
    }

    /** Compares on letters alone, so "éditeur", "Ltd." and "Co." all match their entry. */
    private static String strip(String word) {
        String folded = Normalizer.normalize(word, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return folded.replaceAll("[^a-z]", "");
    }

    private static String collapse(String raw) {
        return Guard.notBlank(raw, "name").trim().replaceAll("\\s+", " ");
    }
}
