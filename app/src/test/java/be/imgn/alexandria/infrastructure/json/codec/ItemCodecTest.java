package be.imgn.alexandria.infrastructure.json.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.PageReached;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.EventDate;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.shared.Note;

/**
 * Every shape an {@link Item} can take, round-tripped.
 *
 * <p>The committed format is pinned by {@link CatalogFilesTest} against the real files; this covers the variants and
 * escapes that the sample library happens not to contain.
 */
class ItemCodecTest {

    private static final ManifestationId EDITION = ManifestationId.of("quixote-ecco-2003-hb");

    /** One item per variant of each of the three sealed hierarchies the record carries. */
    private static List<Item> everyShape() {
        return List.of(
                item(
                        Acquisition.Purchased.on(LocalDate.of(2019, 4, 12), Money.of("28.50", "EUR"), "De Slegte"),
                        new Location.Shelf("living room", Note.of("shelf 3")),
                        ReadingProgress.Finished.on(LocalDate.of(2020, 1, 6), Rating.of(5)),
                        Note.of("Spine sunned.")),
                item(
                        Acquisition.Gift.unattributed(),
                        Location.MISSING,
                        ReadingProgress.Finished.undated(),
                        Note.NOTHING),
                item(
                        Acquisition.Gift.from("Émeline", LocalDate.of(2025, 9, 23)),
                        new Location.Box("attic 2"),
                        ReadingProgress.UNREAD,
                        Note.NOTHING),
                item(
                        Acquisition.Inherited.from("my father", null),
                        Location.LentTo.to("Thomas", LocalDate.of(2025, 9, 3)),
                        new ReadingProgress.Reading(EventDate.on(LocalDate.of(2026, 7, 2)), PageReached.at(148)),
                        Note.of("Quote \"marks\" and a \\ backslash.")),
                item(
                        new Acquisition.Borrowed(
                                "Hugo",
                                EventDate.on(LocalDate.of(2026, 5, 12)),
                                EventDate.on(LocalDate.of(2026, 8, 12))),
                        new Location.Device("phone"),
                        new ReadingProgress.Abandoned(EventDate.UNRECORDED, PageReached.at(212), "Stalled."),
                        Note.NOTHING),
                item(
                        Acquisition.UNRECORDED,
                        Location.shelf("study"),
                        new ReadingProgress.Reading(EventDate.UNRECORDED, PageReached.UNRECORDED),
                        Note.NOTHING));
    }

    private static Item item(Acquisition acquisition, Location location, ReadingProgress reading, Note notes) {
        return new Item(ItemId.of("a-copy"), EDITION, acquisition, location, reading, Condition.VERY_GOOD, notes);
    }

    @Test
    void everyShapeSurvivesARoundTrip() {
        for (Item item : everyShape()) {
            assertThat(ItemCodec.read(ItemCodec.write(item))).isEqualTo(item);
        }
    }

    @Test
    void omitsAbsentFieldsRatherThanWritingNull() {
        String json = ItemCodec.write(item(
                Acquisition.Gift.unattributed(), Location.MISSING, ReadingProgress.Finished.undated(), Note.NOTHING));

        assertThat(json)
                .contains("\"type\" : \"gift\"")
                .contains("\"type\" : \"finished\"")
                .doesNotContain("null")
                .doesNotContain("date")
                .doesNotContain("notes")
                .endsWith("\n");
    }

    @Test
    void refusesAVariantItDoesNotKnow() {
        String json =
                ItemCodec.write(item(Acquisition.UNRECORDED, Location.MISSING, ReadingProgress.UNREAD, Note.NOTHING));

        assertThatThrownBy(() -> ItemCodec.read(json.replace("\"unrecorded\"", "\"inveigled\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown acquisition variant 'inveigled'");
    }

    @Test
    void refusesMalformedJsonInsteadOfGuessing() {
        assertThatThrownBy(() -> ItemCodec.read("{\"id\" : \"x\",")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemCodec.read("{} trailing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing content");
    }

    @Test
    void survivesTheEscapesThatMatter() {
        Item awkward = item(
                Acquisition.UNRECORDED,
                Location.MISSING,
                ReadingProgress.UNREAD,
                Note.of("tab\there, newline\nthere, quote \" and backslash \\"));

        assertThat(ItemCodec.read(ItemCodec.write(awkward))).isEqualTo(awkward);
    }
}
