package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.work.WorkId;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The ISBN flow end to end, with a stubbed lookup so nothing reaches the network. */
class ImportPagesTest {

    private static final Identifier ISBN = Identifier.isbn("9780060188702");

    /** What a provider would return for Grossman's Don Quixote. */
    private static final BookDraft QUIXOTE = BookDraft.of("Don Quixote", ISBN, "Stub Library")
            .originalTitle("Don Quijote de la Mancha")
            .authors(List.of("Miguel de Cervantes"))
            .translators(List.of("Edith Grossman"))
            .publisher("Ecco")
            .publishedYear(Optional.of(2003))
            .originalYear(Optional.of(1605))
            .language(Optional.of(Language.ENGLISH))
            .pages(Optional.of(940))
            .series("Ecco Classics")
            .build();

    private JsonCatalog catalog;
    private Editor editor;
    private String base;

    @BeforeEach
    void start(@TempDir Path root) {
        catalog = CatalogFixture.writeInto(root);
        editor = new Editor(new CatalogService(catalog), stub(QUIXOTE));
        base = "http://127.0.0.1:" + editor.start(0);
    }

    @AfterEach
    void stop() {
        editor.stop();
    }

    private static BookLookup stub(BookDraft draft) {
        return new BookLookup() {
            @Override
            public Optional<BookDraft> byIsbn(Identifier isbn) {
                return isbn.isbnDigits().filter(d -> d.equals("9780060188702")).map(d -> draft);
            }

            @Override
            public String name() {
                return "Stub Library";
            }
        };
    }

    @Test
    void offersTheIsbnBoxWithTheCopyCheckboxTickedByDefault() throws Exception {
        String page = get("/import").body();

        assertThat(page)
                .contains("name=\"isbn\"")
                .contains("name=\"addItem\"")
                .contains("checked");
    }

    @Test
    void prefillsTheFormWithoutSavingAnything() throws Exception {
        int worksBefore = catalog.works().size();

        String page = post("/import", Map.of("isbn", "9780060188702", "addItem", "yes")).body();

        assertThat(page)
                .contains("Prefilled from Stub Library")
                .as("the work takes the original title")
                .contains("value=\"Don Quijote de la Mancha\"")
                .as("the edition keeps the translated one")
                .contains("value=\"Don Quixote\"")
                .contains("value=\"Miguel de Cervantes\"")
                .contains("value=\"Edith Grossman\"")
                .contains("value=\"Ecco\"")
                .contains("value=\"940\"");
        assertThat(catalog.works()).as("a lookup must not write anything").hasSize(worksBefore);
    }

    @Test
    void suggestsIdentifiersDerivedFromTheMetadata() throws Exception {
        String page = post("/import", Map.of("isbn", "9780060188702")).body();

        assertThat(page)
                .contains("value=\"cervantes-don-quijote-de-la-mancha\"")
                .as("a translation is identified by its translator")
                .contains("value=\"grossman-en\"");
    }

    @Test
    void createsWorkExpressionManifestationAndItemFromOneSubmission() throws Exception {
        HttpResponse<String> response = post("/import/save", filledForm(true));

        assertThat(response.statusCode()).isEqualTo(303);

        var work = catalog.work(WorkId.of("cervantes-don-quijote")).orElseThrow();
        assertThat(work.title().main()).isEqualTo("Don Quijote de la Mancha");
        assertThat(work.byline()).isEqualTo("Miguel de Cervantes");
        assertThat(work.expressions()).singleElement().satisfies(expression -> {
            assertThat(expression.language()).isEqualTo(Language.ENGLISH);
            assertThat(expression.describe()).contains("translated from Spanish by Edith Grossman");
        });

        var edition = catalog.manifestation(ManifestationId.of("quixote-ecco-2003")).orElseThrow();
        assertThat(edition.embodies()).containsExactly(work.expressions().getFirst().id());
        assertThat(edition.identifier().display()).contains("9780060188702".substring(0, 3));

        assertThat(catalog.copiesOf(edition.id())).singleElement()
                .satisfies(copy -> assertThat(copy.id().value()).isEqualTo("quixote-ecco-2003-1"));
    }

    @Test
    void leavesTheItemOutWhenTheCheckboxIsClear() throws Exception {
        post("/import/save", filledForm(false));

        assertThat(catalog.manifestation(ManifestationId.of("quixote-ecco-2003"))).isPresent();
        assertThat(catalog.item(ItemId.of("quixote-ecco-2003-1"))).isEmpty();
    }

    @Test
    void reusesAnAgentAlreadyInTheRegistryRatherThanDuplicatingIt() throws Exception {
        // The fixture already holds "Miguel de Cervantes" and "Edith Grossman".
        int before = catalog.agents().size();

        post("/import/save", filledForm(false));

        assertThat(catalog.agents()).hasSize(before);
        assertThat(catalog.work(WorkId.of("cervantes-don-quijote")).orElseThrow().creators())
                .extracting(c -> c.agent().value())
                .containsExactly("miguel-de-cervantes");
    }

    @Test
    void registersThePublisherAsAnOrganisation() throws Exception {
        post("/import/save", filledForm(false));

        assertThat(catalog.agent(AgentId.of("ecco")).orElseThrow().kind().label())
                .isEqualTo("organisation");
    }

    @Test
    void offersAnEmptyFormWhenNoServiceHasTheIsbn() throws Exception {
        String page = post("/import", Map.of("isbn", "9780306406157")).body();

        assertThat(page).contains("No catalogue had this ISBN").contains("name=\"title.main\"");
    }

    @Test
    void rejectsSomethingThatIsNotAnIsbnWithoutLookingItUp() throws Exception {
        String page = post("/import", Map.of("isbn", "not-a-book")).body();

        assertThat(page).contains("is not a valid ISBN").contains("name=\"isbn\"");
    }

    @Test
    void refusesToOverwriteAWorkThatAlreadyExistsWithoutLosingTheForm() throws Exception {
        post("/import/save", filledForm(false));
        HttpResponse<String> second = post("/import/save", filledForm(false));

        assertThat(second.statusCode()).as("the form comes back, it is not an error page").isEqualTo(200);
        assertThat(second.body())
                .contains("already exists")
                .as("and still holds everything that was typed")
                .contains("value=\"Don Quijote de la Mancha\"")
                .contains("value=\"cervantes-don-quijote\"");
    }

    // ------------------------------------------------- problems shown in the form

    @Test
    void keepsEveryOtherFieldWhenOneIsMissing() throws Exception {
        Map<String, String> form = filledForm(false);
        form.remove("title.main");

        HttpResponse<String> response = post("/import/save", form);

        assertThat(response.statusCode()).as("not a 400 error page").isEqualTo(200);
        assertThat(response.body())
                .as("the problem is named against its field")
                .contains("thing to fix")
                .as("and nothing else has to be retyped")
                .contains("value=\"cervantes-don-quijote\"")
                .contains("value=\"grossman-en\"")
                .contains("value=\"quixote-ecco-2003\"")
                .contains("value=\"Miguel de Cervantes\"")
                .contains("value=\"Edith Grossman\"")
                .contains("value=\"Ecco\"")
                .contains("value=\"940\"");
        assertThat(catalog.work(WorkId.of("cervantes-don-quijote"))).isEmpty();
    }

    @Test
    void reportsEveryProblemAtOnceRatherThanTheFirst() throws Exception {
        Map<String, String> form = filledForm(false);
        form.remove("title.main");
        form.put("id", "Not A Slug");
        form.put("expressions[0].language", "englishe");

        String page = post("/import/save", form).body();

        assertThat(page).contains("3 things to fix");
        assertThat(page)
                .as("each problem sits with its own field")
                .contains("Work identifier")
                .contains("Title")
                .contains("Language code");
    }

    @Test
    void marksTheOffendingFieldForAssistiveTechnology() throws Exception {
        Map<String, String> form = filledForm(false);
        form.put("id", "Not A Slug");

        String page = post("/import/save", form).body();

        assertThat(page)
                .contains("aria-invalid=\"true\"")
                .contains("aria-errormessage=\"id-error\"")
                .contains("id=\"id-error\"");
    }

    @Test
    void keepsTheChosenVariantOfASumTypeWhenSomethingElseFails() throws Exception {
        Map<String, String> form = filledForm(true);
        form.remove("title.main");
        form.put("item.acquisition.type", "purchased");
        form.put("item.acquisition.purchased.date", "2020-05-01");
        form.put("item.acquisition.purchased.price", "12.00 EUR");

        String page = post("/import/save", form).body();

        assertThat(page)
                .contains("value=\"purchased\" selected")
                .contains("value=\"2020-05-01\"")
                .contains("value=\"12.00 EUR\"");
    }

    @Test
    void keepsTheCopyCheckboxTickedAcrossARejection() throws Exception {
        Map<String, String> form = filledForm(true);
        form.remove("title.main");

        String page = post("/import/save", form).body();

        assertThat(page).contains("name=\"addItem\" value=\"yes\" checked");
    }

    @Test
    void datesTheTranslationSeparatelyFromTheWork() throws Exception {
        Map<String, String> form = filledForm(false);
        form.put("created.type", "between");
        form.put("created.between.from", "1605");
        form.put("created.between.to", "1615");
        form.remove("created.year.value");
        form.put("expressions[0].realised.type", "year");
        form.put("expressions[0].realised.year.value", "2003");

        assertThat(post("/import/save", form).statusCode()).isEqualTo(303);

        var work = catalog.work(WorkId.of("cervantes-don-quijote")).orElseThrow();
        assertThat(work.created().display()).isEqualTo("1605-1615");
        assertThat(work.expressions()).singleElement().satisfies(expression ->
                assertThat(expression.realised().display())
                        .as("the translation is dated on its own, not borrowed from the work")
                        .isEqualTo("2003"));
    }

    @Test
    void offersARealisedDateForTheExpression() throws Exception {
        String page = post("/import", Map.of("isbn", "9780060188702")).body();

        assertThat(page)
                .contains("name=\"expressions[0].realised.type\"")
                .as("a translation's date cannot be inferred, so it is left blank")
                .contains("value=\"unknown\" selected");
    }

    @Test
    void derivesIdentifiersFromTheSurnameEvenWhenTheProviderInvertsTheName() throws Exception {
        // A library catalogue hands out "Tolkien, John Ronald Reuel"; the surname is Tolkien.
        BookDraft inverted = BookDraft.of("Le seigneur des anneaux", ISBN, "Stub Library")
                .authors(List.of("Tolkien, John Ronald Reuel"))
                .translators(List.of("Lauzon, Daniel"))
                .publisher("Christian Bourgois éditeur")
                .publishedYear(Optional.of(2023))
                .language(Optional.of(new Language("fr")))
                .build();
        Editor other = new Editor(new CatalogService(catalog), stub(inverted));
        String otherBase = "http://127.0.0.1:" + other.start(0);
        try {
            String page = postTo(otherBase, "/import", Map.of("isbn", "9780060188702")).body();

            assertThat(page)
                    .as("the work files under Tolkien, not Reuel")
                    .contains("value=\"tolkien-le-seigneur-des-anneaux\"")
                    .as("the expression under Lauzon, not Daniel")
                    .contains("value=\"lauzon-fr\"")
                    .as("the edition under Bourgois, not éditeur")
                    .contains("value=\"le-seigneur-des-anneaux-bourgois-2023\"")
                    .as("and the name field shows the form a title page would use")
                    .contains("value=\"John Ronald Reuel Tolkien\"")
                    .contains("value=\"Daniel Lauzon\"");
        } finally {
            other.stop();
        }
    }

    @Test
    void registersANewAgentUnderItsSurname() throws Exception {
        Map<String, String> form = filledForm(false);
        form.put("creators[0].name", "Le Guin, Ursula K.");
        form.put("id", "le-guin-a-book");

        assertThat(post("/import/save", form).statusCode()).isEqualTo(303);

        var agent = catalog.agents().stream()
                .filter(a -> a.name().equals("Ursula K. Le Guin"))
                .findFirst()
                .orElseThrow();
        assertThat(agent.sortName()).isEqualTo("Le Guin, Ursula K.");
    }

    @Test
    void savesAFinishedBookWithNoFinishedDate() throws Exception {
        Map<String, String> form = filledForm(true);
        form.put("item.reading.type", "finished");
        // no item.reading.finished.on at all

        assertThat(post("/import/save", form).statusCode()).isEqualTo(303);

        assertThat(catalog.items()).anySatisfy(item ->
                assertThat(item.reading().display()).isEqualTo("read"));
    }

    @Test
    void declaresTheClientSideChecksTheBrowserShouldApply() throws Exception {
        String page = post("/import", Map.of("isbn", "9780060188702")).body();

        assertThat(page)
                .contains("data-check=\"required slug\"")
                .contains("data-check=\"required language\"")
                .contains("data-check=\"isbn\"");
    }

    /** The review form as a user would submit it after checking the prefilled values. */
    /** Mutable on purpose: the tests break one field at a time. */
    private static Map<String, String> filledForm(boolean withItem) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", "cervantes-don-quijote");
        form.put("title.main", "Don Quijote de la Mancha");
        form.put("form.type", "novel");
        form.put("created.type", "year");
        form.put("created.year.value", "1605");
        form.put("creators[0].name", "Miguel de Cervantes");
        form.put("creators[0].kind", "person");
        form.put("creators[0].role", "author");
        form.put("expressions[0].id", "grossman-en");
        form.put("expressions[0].language", "en");
        form.put("expressions[0].kind.type", "translation");
        form.put("expressions[0].kind.translation.from", "es");
        form.put("expressions[0].realised.type", "year");
        form.put("expressions[0].realised.year.value", "2003");
        form.put("expressions[0].contributors[0].name", "Edith Grossman");
        form.put("expressions[0].contributors[0].kind", "person");
        form.put("expressions[0].contributors[0].role", "translator");
        form.put("manifestation.id", "quixote-ecco-2003");
        form.put("manifestation.title.main", "Don Quixote");
        form.put("manifestation.publisher", "Ecco");
        form.put("manifestation.publisherKind", "organisation");
        form.put("manifestation.published.type", "year");
        form.put("manifestation.published.year.value", "2003");
        form.put("manifestation.carrier.type", "hardcover");
        form.put("manifestation.identifier.type", "isbn13");
        form.put("manifestation.identifier.isbn13.digits", "9780060188702");
        form.put("manifestation.extent.type", "pages");
        form.put("manifestation.extent.pages.count", "940");
        if (withItem) {
            form.put("addItem", "yes");
            form.put("item.acquisition.type", "unrecorded");
            form.put("item.location.type", "shelf");
            form.put("item.location.shelf.name", "study");
            form.put("item.condition", "GOOD");
        }
        return form;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
        return postTo(base, path, form);
    }

    private HttpResponse<String> postTo(String at, String path, Map<String, String> form)
            throws Exception {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build().send(
                HttpRequest.newBuilder(URI.create(at + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
