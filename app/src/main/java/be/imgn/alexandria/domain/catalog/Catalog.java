package be.imgn.alexandria.domain.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkId;

/**
 * The repository port for the four aggregate roots, kept in the domain so the model never depends on how the catalogue
 * is stored. Implemented by the JSON store.
 */
public interface Catalog {

    List<Agent> agents();

    List<Work> works();

    List<Manifestation> manifestations();

    List<Item> items();

    Optional<Agent> agent(AgentId id);

    Optional<Work> work(WorkId id);

    Optional<Manifestation> manifestation(ManifestationId id);

    Optional<Item> item(ItemId id);

    void save(Agent agent);

    void save(Work work);

    void save(Manifestation manifestation);

    void save(Item item);

    void deleteAgent(AgentId id);

    void deleteWork(WorkId id);

    void deleteManifestation(ManifestationId id);

    void deleteItem(ItemId id);

    /** The name index that everything displaying an agent reference needs. */
    default AgentDirectory directory() {
        return AgentDirectory.of(agents());
    }

    default List<Manifestation> manifestationsOf(ExpressionId expression) {
        return manifestations().stream().filter(m -> m.embodies(expression)).toList();
    }

    default List<Item> copiesOf(ManifestationId manifestation) {
        return items().stream()
                .filter(i -> i.embodiedIn().equals(manifestation))
                .toList();
    }

    /**
     * Every work this agent is credited on, at either the work or the expression level, carrying the name it was
     * published under.
     *
     * <p>Grouped by {@link Credit#publishedAs} this is an author's bibliography split by pseudonym; ungrouped it is
     * their whole output regardless of how each book was signed.
     */
    default List<Credit> creditsOf(AgentId agent) {
        List<Credit> credits = new ArrayList<>();
        for (Work work : works()) {
            work.creators().stream()
                    .filter(c -> c.agent().equals(agent))
                    .forEach(c -> credits.add(new Credit(work, c.role(), c.publishedAs())));
            work.expressions().stream()
                    .flatMap(expression -> expression.contributors().stream())
                    .filter(c -> c.agent().equals(agent))
                    .forEach(c -> credits.add(new Credit(work, c.role(), c.publishedAs())));
        }
        return List.copyOf(credits);
    }

    /**
     * An agent's credits keyed by the name they were published under, that name first when it is the agent's preferred
     * one so the primary identity leads.
     */
    default Map<String, List<Credit>> creditsByName(AgentId agent) {
        String preferred = agent(agent).map(Agent::name).orElse("");
        Map<String, List<Credit>> grouped =
                new TreeMap<>(Comparator.comparing((String name) -> name.equals(preferred) ? 0 : 1)
                        .thenComparing(Comparator.naturalOrder()));
        for (Credit credit : creditsOf(agent)) {
            grouped.computeIfAbsent(credit.publishedAs(), name -> new ArrayList<>())
                    .add(credit);
        }
        return grouped;
    }

    /** Editions this agent published, for a publishing house. */
    default List<Manifestation> publishedBy(AgentId agent) {
        return manifestations().stream()
                .filter(m -> m.publisher().filter(agent::equals).isPresent())
                .toList();
    }

    /** Everything that would dangle if this agent were removed. */
    default List<String> referencesTo(AgentId agent) {
        List<String> references = new ArrayList<>();
        for (Work work : works()) {
            if (work.creators().stream().anyMatch(c -> c.agent().equals(agent))) {
                references.add("work " + work.id());
            }
            for (Expression expression : work.expressions()) {
                if (expression.contributors().stream().anyMatch(c -> c.agent().equals(agent))) {
                    references.add("expression " + expression.id());
                }
            }
        }
        manifestations().stream()
                .filter(m -> m.publisher().filter(agent::equals).isPresent())
                .forEach(m -> references.add("manifestation " + m.id()));
        return List.copyOf(references);
    }
}
