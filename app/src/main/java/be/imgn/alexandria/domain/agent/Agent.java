package be.imgn.alexandria.domain.agent;

import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Slug;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An LRM Agent: a person or a collective agent, held once and referred to by everything
 * that involves it — a work it authored, an expression it translated, an edition it
 * published.
 *
 * <p>Aggregate root. Promoting it out of the works that mention it is what lets "Penguin",
 * "Penguin Books" and "Penguin Classics" be one thing with three names, and what lets
 * Willa Muir be one record whether she is translating or writing.
 *
 * @param name    the form to display
 * @param sortName the form to file under, "Le Guin, Ursula K."
 * @param aliases other names the same agent is known by, all of which resolve back here
 */
public record Agent(AgentId id, AgentKind kind, String name, String sortName, Set<String> aliases) {

    public Agent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Guard.notBlank(name, "name");
        sortName = sortName == null || sortName.isBlank() ? name : sortName;
        aliases = normalise(name, aliases);
    }

    public static Agent person(String name, String sortName) {
        return new Agent(AgentId.forName(name), AgentKind.PERSON, name, sortName, Set.of());
    }

    public static Agent person(String name) {
        return person(name, name);
    }

    public static Agent organisation(String name) {
        return new Agent(AgentId.forName(name), AgentKind.ORGANISATION, name, name, Set.of());
    }

    /** Every string this agent answers to: the preferred name first, then the aliases. */
    public Stream<String> names() {
        return Stream.concat(Stream.of(name), aliases.stream());
    }

    /** True when the text names this agent, ignoring case, accents and punctuation. */
    public boolean answersTo(String text) {
        String wanted = Slug.of(text);
        return names().anyMatch(known -> Slug.of(known).equals(wanted));
    }

    public Agent withName(String newName) {
        return new Agent(id, kind, newName, sortName, aliases);
    }

    public Agent withSortName(String newSortName) {
        return new Agent(id, kind, name, newSortName, aliases);
    }

    public Agent withAliases(Set<String> newAliases) {
        return new Agent(id, kind, name, sortName, newAliases);
    }

    public Agent withAlias(String alias) {
        Set<String> updated = new LinkedHashSet<>(aliases);
        updated.add(alias);
        return withAliases(updated);
    }

    /**
     * Aliases are stored sorted and free of anything that merely repeats the preferred
     * name, so the file has no redundant entries and no run-to-run ordering churn.
     */
    private static Set<String> normalise(String name, Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return Set.of();
        }
        String preferred = Slug.of(name);
        return Guard.sortedCopyOf(aliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(String::trim)
                .filter(alias -> !Slug.of(alias).equals(preferred))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
