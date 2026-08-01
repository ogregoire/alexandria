package be.imgn.alexandria.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlugTest {

    @Test
    void foldsATitleIntoAFileName() {
        assertThat(Slug.of("Le Seigneur des Anneaux")).isEqualTo("le-seigneur-des-anneaux");
        assertThat(Slug.of("Éditions Gallimard")).isEqualTo("editions-gallimard");
        assertThat(Slug.of("Assassin's Apprentice")).isEqualTo("assassin-s-apprentice");
    }

    /**
     * Decomposition strips an accent off a letter; it leaves a letter that <em>is</em> a letter untouched, and the
     * ASCII filter behind it used to delete those outright — "Der Prozeß" filed as "der-proze".
     */
    @Test
    void spellsOutTheLettersThatCarryNoAccentToStrip() {
        assertThat(Slug.of("Der Prozeß")).isEqualTo("der-prozess");
        assertThat(Slug.of("Straße")).isEqualTo("strasse");
        assertThat(Slug.of("Øst")).isEqualTo("ost");
        assertThat(Slug.of("Łódź")).isEqualTo("lodz");
        assertThat(Slug.of("Þingvellir")).isEqualTo("thingvellir");
        assertThat(Slug.of("Œuvres")).isEqualTo("oeuvres");
        assertThat(Slug.of("Le Cœur de l'hiver")).isEqualTo("le-coeur-de-l-hiver");
    }

    /** A letter that decomposes is still handled by decomposition; the table is only for the ones that do not. */
    @Test
    void leavesAccentedLettersToDecomposition() {
        assertThat(Slug.of("Fräulein")).isEqualTo("fraulein");
        assertThat(Slug.of("Håndbog")).isEqualTo("handbog");
        assertThat(Slug.of("Der Prozess")).isEqualTo("der-prozess");
    }

    /**
     * Deleting a letter is worse than replacing it: two titles that differ only by one would otherwise land on the same
     * identifier, and identifiers here are file names.
     */
    @Test
    void keepsTitlesApartThatDifferOnlyBySuchALetter() {
        assertThat(Slug.of("Øst")).isNotEqualTo(Slug.of("St"));
        assertThat(Slug.of("Łódź")).isNotEqualTo(Slug.of("Odz"));
    }

    /**
     * The distinction the import form depends on: a value that cannot name a file is an empty answer to whoever is only
     * suggesting an identifier, and an exception only to whoever must have one.
     */
    @Test
    void hasNoCandidateForAValueMadeOnlyOfPunctuation() {
        assertThat(Slug.candidate("*")).isEmpty();
        assertThat(Slug.candidate("—")).isEmpty();
        assertThat(Slug.candidate("   ")).isEmpty();
        assertThat(Slug.candidate(null)).isEmpty();

        assertThatThrownBy(() -> Slug.of("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not yield a usable slug");
    }

    @Test
    void keepsAnIdentifierShortEnoughToLiveInAFileName() {
        String slug = Slug.of("A ".repeat(80));

        assertThat(slug).hasSizeLessThanOrEqualTo(60).doesNotEndWith("-");
    }
}
