package be.imgn.alexandria.application;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkId;
import be.imgn.alexandria.infrastructure.h2.H2Projection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The application service the editor talks to. It owns the one rule the web layer must not
 * be trusted with: a write goes to the JSON files first, and only then is the H2 read model
 * rebuilt from them. The files are the catalogue; the database is a consequence.
 */
public final class CatalogService {

    private final Catalog catalog;
    private final H2Projection projection;

    public CatalogService(Catalog catalog, H2Projection projection) {
        this.catalog = catalog;
        this.projection = projection;
        refresh();
    }

    public Catalog catalog() {
        return catalog;
    }

    public H2Projection projection() {
        return projection;
    }

    public AgentDirectory directory() {
        return catalog.directory();
    }

    /** A fresh resolver over the current registry, for reading one form. */
    public AgentResolution newResolution() {
        return new AgentResolution(directory());
    }

    public void refresh() {
        projection.rebuildFrom(catalog);
    }

    // -------------------------------------------------------------- agents

    /** Rejects a name or alias that another agent already answers to, naming the clash. */
    public void save(Agent agent) {
        AgentDirectory others = AgentDirectory.of(
                catalog.agents().stream().filter(existing -> !existing.id().equals(agent.id())).toList());
        agent.names().forEach(name -> others.resolve(name).ifPresent(clash -> {
            throw new IllegalArgumentException(
                    "'" + name + "' already belongs to " + clash.name() + " (" + clash.id() + ")");
        }));
        catalog.save(agent);
        refresh();
    }

    public void deleteAgent(AgentId id) {
        List<String> blocked = catalog.referencesTo(id);
        if (!blocked.isEmpty()) {
            throw new IllegalStateException(
                    "cannot delete " + id + ": still referenced by " + String.join(", ", blocked));
        }
        catalog.deleteAgent(id);
        refresh();
    }

    // -------------------------------------------------- bibliographic saves

    /**
     * Saves the agents the form invented before the aggregate that names them, so the
     * catalogue on disk is never momentarily dangling.
     */
    public void save(Work work, AgentResolution resolution) {
        register(resolution);
        catalog.save(work);
        refresh();
    }

    public void save(Manifestation manifestation, AgentResolution resolution) {
        for (ExpressionId reference : manifestation.embodies()) {
            if (expression(reference).isEmpty()) {
                throw new IllegalArgumentException("no such expression: " + reference.qualified());
            }
        }
        register(resolution);
        catalog.save(manifestation);
        refresh();
    }

    public void save(Item item) {
        if (catalog.manifestation(item.embodiedIn()).isEmpty()) {
            throw new IllegalArgumentException("no such manifestation: " + item.embodiedIn());
        }
        catalog.save(item);
        refresh();
    }

    /**
     * Saves a whole book at once: the agents it names, the work, the edition, and the copy
     * if one is being recorded.
     *
     * <p>Written in dependency order — agents, then work, then manifestation, then item —
     * so a failure part-way through leaves records that reference only things already on
     * disk. There is no transaction across four files; ordering is what keeps the catalogue
     * readable if the process dies mid-save.
     */
    public void saveNewBook(Work work, Manifestation manifestation, Optional<Item> copy,
                            AgentResolution resolution) {
        if (catalog.work(work.id()).isPresent()) {
            throw new IllegalArgumentException("a work with id " + work.id() + " already exists");
        }
        if (catalog.manifestation(manifestation.id()).isPresent()) {
            throw new IllegalArgumentException(
                    "a manifestation with id " + manifestation.id() + " already exists");
        }
        copy.ifPresent(item -> {
            if (catalog.item(item.id()).isPresent()) {
                throw new IllegalArgumentException("an item with id " + item.id() + " already exists");
            }
        });

        register(resolution);
        catalog.save(work);
        catalog.save(manifestation);
        copy.ifPresent(catalog::save);
        refresh();
    }

    private void register(AgentResolution resolution) {
        resolution.created().forEach(catalog::save);
    }

    /**
     * Deleting a Work would orphan every Manifestation embodying its Expressions, so the
     * cross-aggregate check runs before the file is removed rather than after.
     */
    public void deleteWork(WorkId id) {
        List<String> blocked = catalog.work(id).stream()
                .flatMap(work -> work.expressions().stream())
                .flatMap(expression -> catalog.manifestationsOf(expression.id()).stream())
                .map(manifestation -> manifestation.id().value())
                .distinct()
                .toList();
        if (!blocked.isEmpty()) {
            throw new IllegalStateException(
                    "cannot delete " + id + ": still embodied by " + String.join(", ", blocked));
        }
        catalog.deleteWork(id);
        refresh();
    }

    public void deleteManifestation(ManifestationId id) {
        List<String> blocked = catalog.copiesOf(id).stream().map(item -> item.id().value()).toList();
        if (!blocked.isEmpty()) {
            throw new IllegalStateException(
                    "cannot delete " + id + ": still held as " + String.join(", ", blocked));
        }
        catalog.deleteManifestation(id);
        refresh();
    }

    public void deleteItem(ItemId id) {
        catalog.deleteItem(id);
        refresh();
    }

    // ------------------------------------------------------------- queries

    public Optional<Expression> expression(ExpressionId id) {
        return catalog.work(id.work()).flatMap(work -> work.expression(id));
    }

    /** Every expression in the catalogue, labelled for a picker. */
    public Map<String, String> expressionChoices() {
        AgentDirectory agents = directory();
        Map<String, String> choices = new LinkedHashMap<>();
        for (Work work : catalog.works()) {
            for (Expression expression : work.expressions()) {
                choices.put(expression.id().qualified(),
                        work.title().main() + " — " + expression.describe());
            }
        }
        return choices;
    }

    public Map<String, String> manifestationChoices() {
        AgentDirectory agents = directory();
        Map<String, String> choices = new LinkedHashMap<>();
        for (Manifestation manifestation : catalog.manifestations()) {
            choices.put(manifestation.id().value(),
                    manifestation.title().main() + " — " + manifestation.imprint(agents));
        }
        return choices;
    }

    /** How many records name each agent — a zero here is usually a typo left behind. */
    public Map<AgentId, Integer> agentUsage() {
        Map<AgentId, Integer> usage = new LinkedHashMap<>();
        for (Agent agent : catalog.agents()) {
            usage.put(agent.id(), catalog.referencesTo(agent.id()).size());
        }
        return usage;
    }

    public List<ReferentialIntegrity.Violation> problems() {
        return ReferentialIntegrity.check(catalog);
    }
}
