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
                .extracting(credit -> credit.work().title().main())
                .containsExactlyInAnyOrder("Assassin's Apprentice", "Royal Assassin", "Wizard of the Pigeons");
    }

    @Test
    void splitsThatOutputByTheNameEachBookCarries() {
        var byName = catalog.creditsByName(HOBB.id());

        assertThat(byName.keySet()).containsExactly("Robin Hobb", "Megan Lindholm");
        assertThat(byName.get("Robin Hobb"))
                .extracting(credit -> credit.work().title().main())
                .containsExactlyInAnyOrder("Assassin's Apprentice", "Royal Assassin");
        assertThat(byName.get("Megan Lindholm"))
                .extracting(credit -> credit.work().title().main())
                .containsExactly("Wizard of the Pigeons");
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
