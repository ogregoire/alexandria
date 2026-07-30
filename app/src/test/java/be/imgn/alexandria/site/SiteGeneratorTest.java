package be.imgn.alexandria.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;

class SiteGeneratorTest {

    @Test
    void writesAnIndexAPagePerWorkAndTheAssets(@TempDir Path root, @TempDir Path output) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);

        assertThat(output.resolve("index.html")).exists();
        assertThat(output.resolve("works/cervantes-don-quixote.html")).exists();
        assertThat(output.resolve("search-index.json")).exists();
        assertThat(output.resolve("catalog.css")).exists();
        assertThat(output.resolve("catalog.js")).exists();
        assertThat(output.resolve("tokens.css")).exists();
    }

    @Test
    void reachesAgentsFromTheIndexAndTheSearch(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);
        String index = Files.readString(output.resolve("index.html"));
        String search = Files.readString(output.resolve("search-index.json"));

        assertThat(index)
                .as("the people track links every agent page the generator wrote")
                .contains("agents/edith-grossman.html");
        assertThat(search)
                .as("and searching a translator's name matches the translator, not only the book")
                .contains("\"agent:edith-grossman\"");
    }

    @Test
    void showsTheWholeWemiDescentOnAWorkPage(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);
        String page = Files.readString(output.resolve("works/cervantes-don-quixote.html"));

        assertThat(page)
                .as("work")
                .contains("Don Quixote")
                .as("expression")
                .contains("English, translated from Spanish by Edith Grossman")
                .as("manifestation")
                .contains("Ecco, 2003. hardcover, 940 pp.")
                .as("item")
                .contains("living room (shelf 3)");
    }

    @Test
    void indexesTermsThatAppearNowhereInTheWorkTitle(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);
        String index = Files.readString(output.resolve("search-index.json"));

        assertThat(index)
                .as("the translator, so that searching Grossman finds the work")
                .contains("grossman")
                .as("the publisher")
                .contains("ecco")
                .as("the shelf")
                .contains("living room")
                .as("the subjects")
                .contains("chivalry")
                .as("the author's alias, so the short form finds the work too")
                .contains("cervantes");
    }

    @Test
    void escapesMarkupComingFromTheCatalogue(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);
        catalog.save(CatalogFixture.item().withNotes("<script>alert(1)</script>"));

        new SiteGenerator(catalog).generateInto(output);
        String page = Files.readString(output.resolve("works/cervantes-don-quixote.html"));

        assertThat(page).doesNotContain("<script>alert").contains("&lt;script&gt;");
    }

    @Test
    void refusesToPublishACatalogueWithDanglingReferences(@TempDir Path root, @TempDir Path output) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);
        catalog.save(new Item(
                ItemId.of("orphan"),
                ManifestationId.of("no-such-edition"),
                Acquisition.UNRECORDED,
                Location.MISSING,
                ReadingProgress.UNREAD,
                Condition.UNGRADED,
                Optional.empty()));

        assertThatThrownBy(() -> new SiteGenerator(catalog).generateInto(output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown manifestation no-such-edition");
    }
}
