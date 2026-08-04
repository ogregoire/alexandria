package be.imgn.alexandria.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentKind;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkForm;
import be.imgn.alexandria.domain.work.WorkId;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;

/**
 * One person, two published names. The catalogue has to hold both truths at once: each book keeps the byline it was
 * issued under, and the whole output still gathers under one agent.
 */
class PseudonymTest {

    private static final Agent HOBB = new Agent(
            AgentId.of("robin-hobb"), AgentKind.PERSON, "Robin Hobb", "Hobb, Robin", Set.of("Megan Lindholm"));

    private Catalog catalog;

    @BeforeEach
    void setUp(@TempDir Path root) {
        JsonCatalog store = new JsonCatalog(root);
        store.save(HOBB);
        store.save(book("hobb-assassins-apprentice", "Assassin's Apprentice", "Robin Hobb", 1995));
        store.save(book("hobb-royal-assassin", "Royal Assassin", "Robin Hobb", 1996));
        store.save(book("lindholm-wizard-of-the-pigeons", "Wizard of the Pigeons", "Megan Lindholm", 1986));
        catalog = store;
    }

    @Test
    void keepsTheNameEachBookWasPublishedUnder() {
        assertThat(catalog.work(WorkId.of("hobb-assassins-apprentice"))
                        .orElseThrow()
                        .byline())
                .isEqualTo("Robin Hobb");
        assertThat(catalog.work(WorkId.of("lindholm-wizard-of-the-pigeons"))
                        .orElseThrow()
                        .byline())
                .isEqualTo("Megan Lindholm");
    }

    @Test
    void gathersTheWholeOutputUnderOneAgent() {
        assertThat(catalog.creditsOf(HOBB.id()))
                .extracting(Credit::subject)
                .containsExactlyInAnyOrder("Assassin's Apprentice", "Royal Assassin", "Wizard of the Pigeons");
    }

    @Test
    void splitsThatOutputByTheNameEachBookCarries() {
        var byName = catalog.creditsByName(HOBB.id());

        assertThat(byName.keySet()).containsExactly("Robin Hobb", "Megan Lindholm");
        assertThat(byName.get("Robin Hobb"))
                .extracting(Credit::subject)
                .containsExactlyInAnyOrder("Assassin's Apprentice", "Royal Assassin");
        assertThat(byName.get("Megan Lindholm")).extracting(Credit::subject).containsExactly("Wizard of the Pigeons");
    }

    @Test
    void leadsWithThePreferredNameThenTheOthers() {
        assertThat(catalog.creditsByName(HOBB.id()).keySet())
                .as("the agent's own name first, other names after it")
                .containsExactly("Robin Hobb", "Megan Lindholm");
    }

    @Test
    void filesEveryBookUnderTheOneAgentHoweverItWasSigned() {
        assertThat(catalog.works())
                .extracting(work -> work.sortKey(catalog.directory()).split(" \\| ")[0])
                .containsOnly("Hobb, Robin");
    }

    /**
     * A translator was being dated by the author's dates: the agent page showed Daniel Lauzon as "translator ·
     * 1937-1949", the years Tolkien wrote the work, rather than 2014-2016 when the translation was made.
     */
    @Test
    void datesACreditByTheLevelItBelongsTo() {
        JsonCatalog store = (JsonCatalog) catalog;
        WorkId id = WorkId.of("hobb-translated");
        Agent translator = Agent.person("Une Traductrice", "Traductrice, Une");
        store.save(translator);
        store.save(new Work(
                id,
                Title.of("Assassin's Apprentice"),
                List.of(Contribution.as(HOBB, Role.AUTHOR, "Robin Hobb")),
                WorkForm.NOVEL,
                BibliographicDate.year(1995),
                Set.of(),
                List.of(new Expression(
                        new ExpressionId(id, "fr"),
                        new ExpressionKind.Translation(Language.ENGLISH),
                        new Language("fr"),
                        List.of(Contribution.of(translator, Role.TRANSLATOR)),
                        BibliographicDate.year(2007)))));

        var authorCredit = store.creditsOf(HOBB.id()).stream()
                .filter(credit -> credit instanceof Credit.OnWork(Work work, var role, var as)
                        && work.id().equals(id))
                .findFirst()
                .orElseThrow();
        var translatorCredit =
                store.creditsOf(translator.id()).stream().findFirst().orElseThrow();

        assertThat(authorCredit.when().display())
                .as("the author is dated by the work")
                .isEqualTo("1995");
        assertThat(translatorCredit.when().display())
                .as("the translator by the translation, not by when the work was written")
                .isEqualTo("2007");
        assertThat(translatorCredit.realisation())
                .as("named by its language, not by a sentence restating the translator")
                .contains("French");
        assertThat(authorCredit.realisation())
                .as("a credit on the work itself has no realisation to name")
                .isEmpty();
    }

    @Test
    void refusesACreditUnderANameTheAgentIsNotOnFileUnder() {
        assertThatThrownBy(() -> Contribution.as(HOBB, Role.AUTHOR, "Ursula K. Le Guin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not on file as 'Ursula K. Le Guin'");
    }

    @Test
    void reportsACreditWhoseNameHasBeenDroppedFromTheRegistry() {
        JsonCatalog store = (JsonCatalog) catalog;
        store.save(HOBB.withAliases(Set.of()));

        assertThat(ReferentialIntegrity.check(store))
                .extracting(ReferentialIntegrity.Violation::problem)
                .anySatisfy(problem -> assertThat(problem)
                        .contains("credited as 'Megan Lindholm'")
                        .contains("not on file under that name"));
    }

    private static Work book(String id, String title, String publishedAs, int year) {
        WorkId workId = WorkId.of(id);
        return new Work(
                workId,
                Title.of(title),
                List.of(Contribution.as(HOBB, Role.AUTHOR, publishedAs)),
                WorkForm.NOVEL,
                BibliographicDate.year(year),
                Set.of(),
                List.of(Expression.original(
                        new ExpressionId(workId, "original-en"), Language.ENGLISH, BibliographicDate.year(year))));
    }
}
