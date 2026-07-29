package be.imgn.alexandria.domain.agent;

import be.imgn.alexandria.domain.shared.Slug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * A read-only index over the agent registry: it turns a typed-in name into the agent that
 * already exists, or reports that there is none.
 *
 * <p>Matching folds case, accents and punctuation through {@link Slug}, so "J.R.R. Tolkien"
 * finds "J. R. R. Tolkien", and it searches aliases as well as preferred names, so
 * "Penguin" finds "Penguin Books". Two agents claiming the same name would make the lookup
 * ambiguous, so that is reported as a {@link Conflict} rather than silently resolved.
 */
public final class AgentDirectory {

    /** Two or more agents answering to one name — the registry needs a human decision. */
    public record Conflict(String name, List<AgentId> claimedBy) {
        @Override
        public String toString() {
            return "'" + name + "' is claimed by "
                    + String.join(" and ", claimedBy.stream().map(AgentId::value).toList());
        }
    }

    private final Map<AgentId, Agent> byId;
    private final Map<String, Agent> byName;
    private final List<Conflict> conflicts;

    private AgentDirectory(Map<AgentId, Agent> byId, Map<String, Agent> byName, List<Conflict> conflicts) {
        this.byId = byId;
        this.byName = byName;
        this.conflicts = conflicts;
    }

    public static AgentDirectory of(Collection<Agent> agents) {
        Map<AgentId, Agent> byId = new LinkedHashMap<>();
        Map<String, Agent> byName = new LinkedHashMap<>();
        Map<String, List<AgentId>> claims = new LinkedHashMap<>();

        for (Agent agent : agents) {
            byId.put(agent.id(), agent);
            agent.names().forEach(known -> {
                String key = Slug.of(known);
                claims.computeIfAbsent(key, k -> new ArrayList<>()).add(agent.id());
                byName.putIfAbsent(key, agent);
            });
        }

        List<Conflict> conflicts = claims.entrySet().stream()
                .filter(claim -> claim.getValue().stream().distinct().count() > 1)
                .map(claim -> new Conflict(claim.getKey(), List.copyOf(claim.getValue())))
                .toList();

        return new AgentDirectory(Map.copyOf(byId), Map.copyOf(byName), conflicts);
    }

    public static AgentDirectory empty() {
        return new AgentDirectory(Map.of(), Map.of(), List.of());
    }

    /** The agent already known by this name or one of its aliases, if any. */
    public Optional<Agent> resolve(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(Slug.of(text)));
    }

    public Optional<Agent> find(AgentId id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Agent require(AgentId id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("no such agent: " + id));
    }

    /** Display name, falling back to the raw id so a dangling reference is visible, not blank. */
    public String nameOf(AgentId id) {
        return find(id).map(Agent::name).orElseGet(() -> id.value() + " (unknown)");
    }

    public String sortNameOf(AgentId id) {
        return find(id).map(Agent::sortName).orElseGet(id::value);
    }

    public List<Agent> all() {
        return List.copyOf(byId.values());
    }

    /** Every name and alias, sorted — what the editor offers as completions. */
    public List<String> suggestions() {
        return List.copyOf(new TreeSet<>(byId.values().stream().flatMap(Agent::names).toList()));
    }

    /**
     * An id derived from the name that no other agent is using yet, so two different
     * people who happen to slug identically can both be registered.
     */
    public AgentId freeId(String name) {
        AgentId candidate = AgentId.forName(name);
        int suffix = 2;
        while (byId.containsKey(candidate)) {
            candidate = AgentId.of(AgentId.forName(name).value() + "-" + suffix++);
        }
        return candidate;
    }

    public List<Conflict> conflicts() {
        return conflicts;
    }
}
