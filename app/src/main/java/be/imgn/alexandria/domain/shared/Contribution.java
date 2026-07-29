package be.imgn.alexandria.domain.shared;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentId;

import java.util.Objects;

/**
 * An agent acting in a {@link Role} upon some bibliographic entity, under the name that
 * appeared on the publication.
 *
 * <p>Identity and appearance are separate on purpose. {@code agent} is who it was, and it
 * groups every credit together however they were signed; {@code publishedAs} is what the
 * title page said, and it is a fact about that publication rather than about the person.
 * Robin Hobb and Megan Lindholm are one agent and two published names, so the catalogue can
 * both list her whole output together and keep each book credited as it was issued.
 *
 * <p>The published name is stored rather than derived so that renaming an agent in the
 * registry cannot rewrite the byline of a book that was never issued under the new name.
 */
public record Contribution(AgentId agent, Role role, String publishedAs) {

    public Contribution {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(role, "role");
        Guard.notBlank(publishedAs, "publishedAs");
    }

    /** Credits the agent under its preferred name. */
    public static Contribution of(Agent agent, Role role) {
        return new Contribution(agent.id(), role, agent.name());
    }

    /** Credits the agent under one of its other names — a pseudonym, a maiden name. */
    public static Contribution as(Agent agent, Role role, String publishedAs) {
        if (!agent.answersTo(publishedAs)) {
            throw new IllegalArgumentException(
                    agent.name() + " is not on file as '" + publishedAs + "' — add it as an alias first");
        }
        return new Contribution(agent.id(), role, publishedAs);
    }

    public static Contribution author(Agent agent) {
        return of(agent, Role.AUTHOR);
    }

    public static Contribution translator(Agent agent) {
        return of(agent, Role.TRANSLATOR);
    }

    /** True when this credit uses a name other than the agent's preferred one. */
    public boolean isPseudonymous(Agent agent) {
        return !agent.name().equals(publishedAs);
    }
}
