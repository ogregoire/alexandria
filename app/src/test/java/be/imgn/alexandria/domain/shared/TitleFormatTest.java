package be.imgn.alexandria.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class TitleFormatTest {

    @Test
    void leavesATitleWithoutASubtitleAlone() {
        Title plain = Title.of("New Spring");

        assertThat(TitleFormat.isbd(plain)).isEqualTo("New Spring");
        assertThat(TitleFormat.display(plain, Locale.FRENCH)).isEqualTo("New Spring");
        assertThat(TitleFormat.display(plain, Locale.ENGLISH)).isEqualTo("New Spring");
    }

    @Test
    void punctuatesTheCataloguingFormTheSameInEveryLanguage() {
        // ISBD prescribes space-colon-space precisely so that a record stays legible to someone
        // who does not read the language it describes.
        assertThat(TitleFormat.isbd(Title.of("Nouveau Printemps", "La Préquelle de La Roue du temps")))
                .isEqualTo("Nouveau Printemps : La Préquelle de La Roue du temps");
    }

    @Test
    void holdsASpaceBeforeTheColonInFrenchAndClosesItUpInEnglish() {
        Title french = Title.of("Nouveau Printemps", "La Préquelle de La Roue du temps");
        Title english = Title.of("Don Quixote", "The Ingenious Gentleman of La Mancha");

        assertThat(TitleFormat.display(french, Locale.FRENCH))
                .as("a narrow no-break space, so the colon cannot wrap onto a line of its own")
                .isEqualTo("Nouveau Printemps : La Préquelle de La Roue du temps");
        assertThat(TitleFormat.display(english, Locale.ENGLISH))
                .isEqualTo("Don Quixote: The Ingenious Gentleman of La Mancha");
    }

    @Test
    void treatsABlankSubtitleAsNoSubtitleAtAll() {
        assertThat(Title.of("New Spring", "")).isInstanceOf(Title.Plain.class);
        assertThat(Title.of("New Spring", "   ")).isInstanceOf(Title.Plain.class);
        assertThat(Title.of("New Spring", null)).isInstanceOf(Title.Plain.class);
        assertThat(Title.of("New Spring", "A prequel")).isInstanceOf(Title.Subtitled.class);
    }
}
