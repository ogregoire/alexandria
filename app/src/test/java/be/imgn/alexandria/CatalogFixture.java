package be.imgn.alexandria;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentKind;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.shared.Note;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkForm;
import be.imgn.alexandria.domain.work.WorkId;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;

/**
 * Three agents, one Work, two Expressions, one Manifestation, one Item — the smallest catalogue that still exercises
 * all four WEMI levels, an alias, and a spread of sum-type variants.
 */
public final class CatalogFixture {

    public static final AgentId CERVANTES = AgentId.of("miguel-de-cervantes");
    public static final AgentId GROSSMAN_AGENT = AgentId.of("edith-grossman");
    public static final AgentId ECCO_AGENT = AgentId.of("ecco");

    public static final WorkId QUIXOTE = WorkId.of("cervantes-don-quixote");
    public static final ExpressionId SPANISH = new ExpressionId(QUIXOTE, "original-es");
    public static final ExpressionId GROSSMAN = new ExpressionId(QUIXOTE, "grossman-en");
    public static final ManifestationId ECCO = ManifestationId.of("quixote-ecco-2003-hb");
    public static final ItemId MY_COPY = ItemId.of("quixote-ecco-2003-hb-1");

    private CatalogFixture() {}

    public static JsonCatalog writeInto(Path root) {
        JsonCatalog catalog = new JsonCatalog(root);
        agents().forEach(catalog::save);
        catalog.save(work());
        catalog.save(manifestation());
        catalog.save(item());
        return catalog;
    }

    public static final Agent CERVANTES_AGENT =
            new Agent(CERVANTES, AgentKind.PERSON, "Miguel de Cervantes", "Cervantes, Miguel de", Set.of("Cervantes"));
    public static final Agent GROSSMAN_PERSON = Agent.person("Edith Grossman", "Grossman, Edith");
    public static final Agent ECCO_ORG = Agent.organisation("Ecco");

    public static List<Agent> agents() {
        return List.of(CERVANTES_AGENT, GROSSMAN_PERSON, ECCO_ORG);
    }

    public static Work work() {
        return new Work(
                QUIXOTE,
                Title.of("Don Quixote", "The Ingenious Gentleman of La Mancha"),
                List.of(Contribution.author(CERVANTES_AGENT)),
                WorkForm.NOVEL,
                new BibliographicDate.Between(1605, 1615),
                Set.of("satire", "chivalry"),
                List.of(
                        Expression.original(SPANISH, Language.SPANISH, BibliographicDate.year(1605)),
                        Expression.translation(
                                GROSSMAN,
                                Language.SPANISH,
                                Language.ENGLISH,
                                GROSSMAN_PERSON,
                                BibliographicDate.year(2003))));
    }

    public static Manifestation manifestation() {
        return new Manifestation(
                ECCO,
                List.of(GROSSMAN),
                Title.of("Don Quixote"),
                Optional.of(ECCO_AGENT),
                BibliographicDate.year(2003),
                Carrier.HARDCOVER,
                Identifier.isbn("9780060188702"),
                Extent.pages(940),
                Optional.of(Series.of("Ecco Classics")),
                Optional.of(1));
    }

    public static Item item() {
        return new Item(
                MY_COPY,
                ECCO,
                Acquisition.Purchased.on(LocalDate.of(2019, 4, 12), Money.of("28.50", "EUR"), "De Slegte"),
                new Location.Shelf("living room", Note.of("shelf 3")),
                ReadingProgress.Finished.on(LocalDate.of(2020, 1, 6), Rating.of(5)),
                Condition.VERY_GOOD,
                Optional.of("Spine sunned."));
    }
}
