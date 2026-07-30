package be.imgn.alexandria.domain.catalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.Work;

/**
 * Invariants that span aggregates and therefore cannot live inside any one of them: every contribution and every
 * publisher must name an agent that exists, no two agents may answer to the same name, every manifestation must embody
 * an expression that exists, and every item must embody a manifestation that exists. Checked on save and again before
 * the site is generated.
 */
public final class ReferentialIntegrity {

    private ReferentialIntegrity() {}

    public record Violation(String subject, String problem) {
        @Override
        public String toString() {
            return subject + ": " + problem;
        }
    }

    public static List<Violation> check(Catalog catalog) {
        AgentDirectory directory = catalog.directory();
        Set<String> agents = new HashSet<>();
        catalog.agents().forEach(agent -> agents.add(agent.id().value()));
        Set<String> expressions = new HashSet<>();
        for (Work work : catalog.works()) {
            work.expressions().forEach(e -> expressions.add(e.id().qualified()));
        }
        Set<String> manifestations = new HashSet<>();
        catalog.manifestations().forEach(m -> manifestations.add(m.id().value()));

        List<Violation> violations = new ArrayList<>();

        for (AgentDirectory.Conflict conflict : directory.conflicts()) {
            violations.add(new Violation("agent registry", conflict.toString()));
        }

        for (Work work : catalog.works()) {
            checkAgents(violations, "work " + work.id(), work.creators(), agents, directory);
            for (Expression expression : work.expressions()) {
                checkAgents(violations, "expression " + expression.id(), expression.contributors(), agents, directory);
            }
        }

        for (Manifestation manifestation : catalog.manifestations()) {
            manifestation
                    .publisher()
                    .agent()
                    .filter(publisher -> !agents.contains(publisher.value()))
                    .ifPresent(publisher -> violations.add(
                            new Violation("manifestation " + manifestation.id(), "unknown publisher " + publisher)));
            for (ExpressionId reference : manifestation.embodies()) {
                if (!expressions.contains(reference.qualified())) {
                    violations.add(new Violation(
                            "manifestation " + manifestation.id(), "unknown expression " + reference.qualified()));
                }
            }
        }

        for (Item item : catalog.items()) {
            if (!manifestations.contains(item.embodiedIn().value())) {
                violations.add(new Violation("item " + item.id(), "unknown manifestation " + item.embodiedIn()));
            }
        }
        return List.copyOf(violations);
    }

    /** Agents nothing points at — harmless, but worth surfacing so typos can be cleaned up. */
    public static List<Agent> unreferenced(Catalog catalog) {
        return catalog.agents().stream()
                .filter(agent -> catalog.referencesTo(agent.id()).isEmpty())
                .toList();
    }

    private static void checkAgents(
            List<Violation> violations,
            String subject,
            List<Contribution> contributions,
            Set<String> agents,
            AgentDirectory directory) {
        for (Contribution contribution : contributions) {
            AgentId agent = contribution.agent();
            if (!agents.contains(agent.value())) {
                violations.add(new Violation(subject, "unknown agent " + agent));
                continue;
            }
            // The credited name must be one the agent is on file under, otherwise the
            // pseudonym has been dropped from the registry and the link is no longer
            // findable from that name.
            directory
                    .find(agent)
                    .filter(known -> !known.answersTo(contribution.publishedAs()))
                    .ifPresent(known -> violations.add(new Violation(
                            subject,
                            "credited as '" + contribution.publishedAs() + "' but " + known.name()
                                    + " is not on file under that name — add it as an alias")));
        }
    }

    public static void enforce(Catalog catalog) {
        List<Violation> violations = check(catalog);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("catalogue is inconsistent:\n  "
                    + String.join(
                            "\n  ", violations.stream().map(Violation::toString).toList()));
        }
    }
}
