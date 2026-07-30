package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.manifestation.ManifestationId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That a book has been read is worth recording on its own. When it was read is often not
 * remembered, and demanding it would either block the record or invite an invented date.
 */
class ReadingProgressTest {

    private static Item copy() {
        return Item.shelved(ItemId.of("a-copy"), ManifestationId.of("an-edition"),
                Acquisition.UNRECORDED, "study");
    }

    @Test
    void finishesWithoutADate() {
        ReadingProgress read = ReadingProgress.Finished.undated();

        assertThat(read.completed()).isTrue();
        assertThat(read.display()).isEqualTo("read");
    }

    @Test
    void finishesWithoutADateButWithARating() {
        ReadingProgress read = new ReadingProgress.Finished(Optional.empty(), Optional.of(Rating.of(4)));

        assertThat(read.display()).isEqualTo("read, 4/5");
    }

    @Test
    void stillReadsBackADateWhenThereIsOne() {
        ReadingProgress read = ReadingProgress.Finished.on(LocalDate.of(2020, 1, 6), Rating.of(5));

        assertThat(read.display()).isEqualTo("read 2020-01-06, 5/5");
    }

    @Test
    void finishesACopyWithNoDateAtAll() {
        Item finished = copy().finishReading(null, null);

        assertThat(finished.reading().completed()).isTrue();
        assertThat(finished.reading().display()).isEqualTo("read");
    }

    @Test
    void startsAndAbandonsWithoutDatesToo() {
        assertThat(new ReadingProgress.Reading(Optional.empty(), Optional.of(120)).display())
                .isEqualTo("reading, p. 120");
        assertThat(new ReadingProgress.Abandoned(Optional.empty(), Optional.empty(), "dull").display())
                .isEqualTo("abandoned — dull");
    }

    @Test
    void keepsRequiringTheReasonForGivingUp() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ReadingProgress.Abandoned(Optional.empty(), Optional.empty(), " ")))
                .hasMessageContaining("why");
    }
}
