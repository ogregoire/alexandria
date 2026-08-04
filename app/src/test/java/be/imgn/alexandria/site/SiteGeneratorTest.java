package be.imgn.alexandria.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.Note;
import be.imgn.alexandria.domain.shared.Title;
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
                .as("the index links every agent page the generator wrote")
                .contains("agents/edith-grossman\"");
        assertThat(search)
                .as("and searching a translator's name matches the translator, not only the book")
                .contains("\"agent:edith-grossman\"");
    }

    @Test
    void listsTheEditionsHeldRatherThanTheWorksBehindThem(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);
        String index = Files.readString(output.resolve("index.html"));
        String search = Files.readString(output.resolve("search-index.json"));

        assertThat(index)
                .as("the shelf is editions and agents")
                .contains("editions/quixote-ecco-2003-hb\"")
                .contains("agents/edith-grossman\"")
                .as("not the abstractions behind them")
                .doesNotContain("href=\"works/");

        // One list, alphabetical across both kinds: Cervantes files under C, his Don Quixote
        // edition under D.
        int cervantes = index.indexOf("agents/miguel-de-cervantes\"");
        int quixote = index.indexOf("editions/quixote-ecco-2003-hb\"");
        assertThat(cervantes).isNotNegative();
        assertThat(quixote).isNotNegative();
        assertThat(cervantes).as("interleaved, not editions-then-agents").isLessThan(quixote);

        assertThat(search)
                .as("an edition is searchable by the work it embodies, so its original title still finds it")
                .contains("\"edition:quixote-ecco-2003-hb\"")
                .contains("don quixote");
    }

    @Test
    void givesEveryEditionAPageOfItsOwn(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);
        String page = Files.readString(output.resolve("editions/quixote-ecco-2003-hb.html"));

        assertThat(page)
                .as("what was printed")
                .contains("Ecco")
                .contains("hardcover")
                .contains("940 pp.")
                .as("the expression it embodies, linked up to its work")
                .contains("works/cervantes-don-quixote\"")
                .contains("English, translated from Spanish by Edith Grossman")
                .as("and the copy on the shelf")
                .contains("living room (shelf 3)");
    }

    @Test
    void saysSoOnAnEditionNobodyHoldsACopyOf(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);
        catalog.deleteItem(CatalogFixture.MY_COPY);

        new SiteGenerator(catalog).generateInto(output);

        assertThat(Files.readString(output.resolve("editions/quixote-ecco-2003-hb.html")))
                .as("the shelf section still appears, and admits the shelf is empty")
                .contains("On the shelf")
                .contains("No copy of this edition is held")
                .doesNotContain("class=\"copy\"");
    }

    @Test
    void countsLiveOnTheirOwnPageRatherThanEveryPage(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);

        assertThat(Files.readString(output.resolve("statistics.html")))
                .as("the tallies")
                .contains("works")
                .contains("manifestations")
                .as("and the distributions read off the catalogue")
                .contains("Languages")
                .contains("English");
        assertThat(Files.readString(output.resolve("works/cervantes-don-quixote.html")))
                .as("a reader looking at a book is not told how many books there are")
                .doesNotContain("expressions ·");
    }

    @Test
    void namesTheEditionOnTheWorkPageWhenItWasSoldUnderAnotherTitle(@TempDir Path root, @TempDir Path output)
            throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);
        Manifestation original = CatalogFixture.manifestation();
        catalog.save(new Manifestation(
                original.id(),
                original.embodies(),
                Title.of("El Ingenioso Hidalgo"),
                original.publisher(),
                original.published(),
                original.carrier(),
                original.identifier(),
                original.extent(),
                original.series(),
                original.editionStatement(),
                original.contributors()));

        new SiteGenerator(catalog).generateInto(output);

        assertThat(Files.readString(output.resolve("works/cervantes-don-quixote.html")))
                .as("the name it was sold under is the one fact about an edition the work page cannot otherwise show")
                .contains("El Ingenioso Hidalgo")
                .as("and the imprint stays, as the detail beneath it")
                .contains("Ecco, 2003. hardcover, 940 pp.");
    }

    /**
     * Searching the words on a cover reached the publisher and the cover artist, whose credits sit on the printing, but
     * not the author or the translator, whose credits sit above it — so a French edition's own author was missing from
     * a search for its French title.
     */
    @Test
    void findsEveryoneBehindAnEditionByTheTitleItWasSoldUnder(@TempDir Path root, @TempDir Path output)
            throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);
        Manifestation original = CatalogFixture.manifestation();
        catalog.save(new Manifestation(
                original.id(),
                original.embodies(),
                Title.of("El Ingenioso Hidalgo"),
                original.publisher(),
                original.published(),
                original.carrier(),
                original.identifier(),
                original.extent(),
                original.series(),
                original.editionStatement(),
                original.contributors()));

        new SiteGenerator(catalog).generateInto(output);
        String index = Files.readString(output.resolve("search-index.json"));

        assertThat(entryFor(index, "agent:miguel-de-cervantes"))
                .as("the author, credited on the work")
                .contains("el ingenioso hidalgo");
        assertThat(entryFor(index, "agent:edith-grossman"))
                .as("the translator, credited on the expression")
                .contains("el ingenioso hidalgo");
        assertThat(entryFor(index, "agent:miguel-de-cervantes"))
                .as("and the catalogued title still finds him too")
                .contains("don quixote");
    }

    /** The one line of the search index belonging to that entry. Entries are written one per line. */
    private static String entryFor(String searchIndex, String id) {
        return searchIndex
                .lines()
                .filter(line -> line.contains("\"" + id + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no search entry for " + id));
    }

    /**
     * Pages are linked without their {@code .html}, which GitHub Pages resolves for us. Two things can go wrong: a link
     * that keeps the suffix, and a link that resolves to nothing. This walks every page and checks every link the way
     * the host would — {@code /foo} as {@code foo.html}, a directory as its {@code index.html}.
     */
    @Test
    void linksEveryPageWithoutItsSuffixAndBreaksNone(@TempDir Path root, @TempDir Path output) throws Exception {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        new SiteGenerator(catalog).generateInto(output);

        List<String> broken = new ArrayList<>();
        List<String> suffixed = new ArrayList<>();
        try (var pages = Files.walk(output)) {
            for (Path page : pages.filter(p -> p.toString().endsWith(".html")).toList()) {
                Matcher link = Pattern.compile("href=\"([^\"]+)\"").matcher(Files.readString(page));
                while (link.find()) {
                    String href = link.group(1);
                    if (href.startsWith("http") || href.startsWith("#")) {
                        continue;
                    }
                    if (href.endsWith(".html")) {
                        suffixed.add(page.getFileName() + " -> " + href);
                    }
                    Path target = page.getParent().resolve(href).normalize();
                    boolean resolves = Files.isRegularFile(target)
                            || Files.isRegularFile(Path.of(target + ".html"))
                            || Files.isRegularFile(target.resolve("index.html"));
                    if (!resolves) {
                        broken.add(page.getFileName() + " -> " + href);
                    }
                }
            }
        }

        assertThat(suffixed).as("a page link should carry no .html").isEmpty();
        assertThat(broken).as("and every link should still find its page").isEmpty();
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
                Note.NOTHING));

        assertThatThrownBy(() -> new SiteGenerator(catalog).generateInto(output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown manifestation no-such-edition");
    }
}
