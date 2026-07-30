package be.imgn.alexandria.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.shared.Note;

class JsonCatalogTest {

    @Test
    void roundTripsEveryAggregateThroughDisk(@TempDir Path root) {
        CatalogFixture.writeInto(root);

        JsonCatalog reread = new JsonCatalog(root);

        assertThat(reread.works()).containsExactly(CatalogFixture.work());
        assertThat(reread.manifestations()).containsExactly(CatalogFixture.manifestation());
        assertThat(reread.items()).containsExactly(CatalogFixture.item());
    }

    @Test
    void namesFilesAfterTheAggregateId(@TempDir Path root) {
        CatalogFixture.writeInto(root);

        assertThat(root.resolve("works/cervantes-don-quixote.json")).exists();
        assertThat(root.resolve("manifestations/quixote-ecco-2003-hb.json")).exists();
        assertThat(root.resolve("items/quixote-ecco-2003-hb-1.json")).exists();
    }

    @Test
    void writesIdentifiersAsPlainStringsAndOmitsAbsentOptionals(@TempDir Path root) throws Exception {
        CatalogFixture.writeInto(root);

        String json = Files.readString(root.resolve("works/cervantes-don-quixote.json"));

        assertThat(json)
                .contains("\"id\" : \"cervantes-don-quixote\"")
                .contains("\"id\" : \"cervantes-don-quixote/original-es\"")
                .contains("\"type\" : \"translation\"")
                .as("absent optionals must be omitted, not written as null")
                .doesNotContain("null")
                .as("file must end with a newline")
                .endsWith("\n");
    }

    @Test
    void producesTheSameBytesForTheSameCatalogue(@TempDir Path root) throws Exception {
        Path file = root.resolve("works/cervantes-don-quixote.json");
        var work = CatalogFixture.work().withSubjects(Set.of("satire", "chivalry", "spain", "madness"));

        new JsonCatalog(root).save(work);
        String first = Files.readString(file);
        new JsonCatalog(root).save(work);

        assertThat(Files.readString(file)).isEqualTo(first);
        assertThat(first).containsSubsequence("chivalry", "madness", "satire", "spain");
    }

    @Test
    void omitsProvenanceThatWasNeverRecorded(@TempDir Path root) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);
        catalog.save(new Item(
                ItemId.of("a-gift"),
                CatalogFixture.ECCO,
                Acquisition.Gift.unattributed(),
                Location.shelf("study"),
                ReadingProgress.Finished.undated(),
                Condition.UNGRADED,
                Note.NOTHING));

        String json = Files.readString(root.resolve("items/a-gift.json"));

        assertThat(json)
                .as("the kind is still stated")
                .contains("\"type\" : \"gift\"")
                .contains("\"type\" : \"finished\"")
                .as("but nothing is invented to fill the gaps")
                .doesNotContain("date")
                .doesNotContain("from")
                .doesNotContain("null");
        assertThat(new JsonCatalog(root)
                        .item(ItemId.of("a-gift"))
                        .orElseThrow()
                        .reading()
                        .display())
                .isEqualTo("read");
    }

    @Test
    void deletesRemoveTheFile(@TempDir Path root) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        catalog.deleteItem(CatalogFixture.MY_COPY);

        assertThat(root.resolve("items/quixote-ecco-2003-hb-1.json")).doesNotExist();
        assertThat(catalog.items()).isEmpty();
    }
}
