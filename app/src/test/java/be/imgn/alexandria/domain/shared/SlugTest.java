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
