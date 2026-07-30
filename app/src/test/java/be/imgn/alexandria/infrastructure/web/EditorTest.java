package be.imgn.alexandria.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.shared.TitleFormat;
import be.imgn.alexandria.domain.work.WorkId;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;

class EditorTest {

    private final HttpClient client = HttpClient.newHttpClient();

    private JsonCatalog catalog;
    private Editor editor;
    private String base;

    @BeforeEach
    void start(@TempDir Path root) {
        catalog = CatalogFixture.writeInto(root);
        editor = new Editor(new CatalogService(catalog));
        base = "http://127.0.0.1:" + editor.start(0);
    }

    @AfterEach
    void stop() {
        editor.stop();
    }

    @Test
    void servesTheCatalogueItAlreadyHas() throws Exception {
        assertThat(get("/works").body()).contains("Don Quixote").contains("Miguel de Cervantes");
        assertThat(get("/manifestations").body()).contains("Ecco");
        assertThat(get("/items").body()).contains("quixote-ecco-2003-hb-1");
    }

    @Test
    void createsAWorkFromAPostedForm() throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", "tolkien-the-hobbit");
        form.put("title.main", "The Hobbit");
        form.put("title.subtitle", "There and Back Again");
        form.put("form.type", "novel");
        form.put("created.type", "year");
        form.put("created.year.value", "1937");
        form.put("subjects", "fantasy, quests");
        form.put("creators[0].name", "J. R. R. Tolkien");
        form.put("creators[0].kind", "person");
        form.put("creators[0].role", "author");
        form.put("expressions[0].id", "original-en");
        form.put("expressions[0].language", "en");
        form.put("expressions[0].kind.type", "original");
        form.put("expressions[0].realised.type", "year");
        form.put("expressions[0].realised.year.value", "1937");

        HttpResponse<String> response = post("/works/new", form);

        assertThat(response.statusCode()).isEqualTo(303);
        var agents = catalog.directory();
        var saved = catalog.work(WorkId.of("tolkien-the-hobbit")).orElseThrow();
        assertThat(TitleFormat.isbd(saved.title())).isEqualTo("The Hobbit : There and Back Again");
        assertThat(saved.byline()).isEqualTo("J. R. R. Tolkien");
        assertThat(saved.subjects()).containsExactly("fantasy", "quests");
        assertThat(saved.expressions())
                .singleElement()
                .satisfies(e -> assertThat(e.describe()).isEqualTo("English (original)"));
        assertThat(root().resolve("works/tolkien-the-hobbit.json")).exists();
    }

    @Test
    void registersAnAuthorNobodyHasEnteredBefore() throws Exception {
        postHobbitBy("J. R. R. Tolkien");

        var author = catalog.agent(AgentId.of("j-r-r-tolkien")).orElseThrow();
        assertThat(author.name()).isEqualTo("J. R. R. Tolkien");
        assertThat(author.sortName()).isEqualTo("Tolkien, J. R. R.");
        assertThat(root().resolve("agents/j-r-r-tolkien.json")).exists();
    }

    @Test
    void reusesAnAuthorAlreadyOnFileEvenWhenPunctuationDiffers() throws Exception {
        postHobbitBy("J. R. R. Tolkien");
        int before = catalog.agents().size();

        postHobbitBy("J.R.R. Tolkien", "tolkien-the-silmarillion");

        assertThat(catalog.agents()).hasSize(before);
        assertThat(catalog.work(WorkId.of("tolkien-the-silmarillion"))
                        .orElseThrow()
                        .creators())
                .extracting(c -> c.agent().value())
                .containsExactly("j-r-r-tolkien");
    }

    @Test
    void matchesAnExistingAgentThroughOneOfItsAliases() throws Exception {
        // The fixture registers "Cervantes" as an alias of "Miguel de Cervantes".
        postHobbitBy("Cervantes", "cervantes-novelas-ejemplares");

        assertThat(catalog.work(WorkId.of("cervantes-novelas-ejemplares"))
                        .orElseThrow()
                        .creators())
                .extracting(c -> c.agent().value())
                .containsExactly("miguel-de-cervantes");
    }

    @Test
    void offersKnownNamesAndAliasesAsCompletions() throws Exception {
        String page = get("/works/new").body();

        assertThat(page)
                .contains("<datalist id=\"known-agents\">")
                .contains("<option value=\"Miguel de Cervantes\">")
                .as("aliases are offered too")
                .contains("<option value=\"Cervantes\">")
                .contains("list=\"known-agents\"");
    }

    @Test
    void refusesAnAliasAnotherAgentAlreadyAnswersTo() throws Exception {
        HttpResponse<String> response = post(
                "/agents/edith-grossman",
                Map.of(
                        "id", "edith-grossman",
                        "name", "Edith Grossman",
                        "sortName", "Grossman, Edith",
                        "kind", "person",
                        "aliases", "Cervantes"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("already belongs to Miguel de Cervantes");
    }

    @Test
    void refusesToDeleteAnAgentThatIsStillCredited() throws Exception {
        HttpResponse<String> response = post("/agents/miguel-de-cervantes/delete", Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("still referenced by work cervantes-don-quixote");
        assertThat(catalog.agent(CatalogFixture.CERVANTES)).isPresent();
    }

    @Test
    void inventsNoAgentsWhenTheRestOfTheFormIsRejected() throws Exception {
        int before = catalog.agents().size();

        // No expression identifier, so reading the work fails after the creators are named.
        HttpResponse<String> response = post(
                "/works/new",
                Map.of(
                        "id", "nobody-nothing",
                        "title.main", "Nothing",
                        "form.type", "novel",
                        "created.type", "unknown",
                        "creators[0].name", "Someone Entirely New",
                        "creators[0].kind", "person",
                        "creators[0].role", "author"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(catalog.agents()).hasSize(before);
        assertThat(root().resolve("agents/someone-entirely-new.json")).doesNotExist();
    }

    private void postHobbitBy(String author) throws Exception {
        postHobbitBy(author, "tolkien-the-hobbit");
    }

    private void postHobbitBy(String author, String workId) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", workId);
        form.put("title.main", "A Book");
        form.put("form.type", "novel");
        form.put("created.type", "year");
        form.put("created.year.value", "1937");
        form.put("creators[0].name", author);
        form.put("creators[0].kind", "person");
        form.put("creators[0].role", "author");
        form.put("expressions[0].id", "original-en");
        form.put("expressions[0].language", "en");
        form.put("expressions[0].kind.type", "original");
        form.put("expressions[0].realised.type", "year");
        form.put("expressions[0].realised.year.value", "1937");
        assertThat(post("/works/" + workId, form).statusCode()).isEqualTo(303);
    }

    @Test
    void roundTripsASumTypeThroughTheFormItRendered() throws Exception {
        Item borrowed = new Item(
                ItemId.of("borrowed-copy"),
                CatalogFixture.ECCO,
                new Acquisition.Borrowed(
                        "Marie", Optional.of(LocalDate.of(2024, 3, 1)), Optional.of(LocalDate.of(2024, 4, 1))),
                Location.shelf("desk"),
                ReadingProgress.UNREAD,
                Condition.GOOD,
                Optional.empty());
        catalog.save(borrowed);

        String page = get("/items/borrowed-copy").body();

        assertThat(page)
                .contains("value=\"borrowed\" selected")
                .contains("value=\"Marie\"")
                .contains("value=\"2024-03-01\"")
                .contains("value=\"2024-04-01\"");
    }

    @Test
    void refusesToDeleteAWorkThatEditionsStillPointAt() throws Exception {
        HttpResponse<String> response = post("/works/cervantes-don-quixote/delete", Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("still embodied by quixote-ecco-2003-hb");
        assertThat(catalog.work(CatalogFixture.QUIXOTE)).isPresent();
    }

    @Test
    void runsReportsAgainstTheProjection() throws Exception {
        assertThat(get("/reports").body()).contains("Currently reading").contains("By language");
        assertThat(get("/reports/ratings").body()).contains("Don Quixote").contains("5");
    }

    @Test
    void servesItsOwnAssets() throws Exception {
        assertThat(get("/assets/editor.css").body()).contains("editor");
        assertThat(get("/assets/editor.js").body()).contains("data-variant-of");
    }

    @Test
    void servesTheTokensSharedWithThePublishedSite() throws Exception {
        // editor.css imports it by relative URL, so the same /assets route has to reach outside
        // /web to the one copy both surfaces read.
        assertThat(get("/assets/tokens.css").body()).contains("--color-paper");
    }

    @Test
    void answersUnknownRoutesWithNotFound() throws Exception {
        assertThat(get("/nope").statusCode()).isEqualTo(404);
    }

    private Path root() {
        return catalog.root();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(
                        HttpRequest.newBuilder(URI.create(base + path))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
