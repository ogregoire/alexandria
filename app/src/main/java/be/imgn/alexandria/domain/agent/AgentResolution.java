package be.imgn.alexandria.domain.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import be.imgn.alexandria.domain.shared.Slug;

/**
 * Turns the names typed into a form into agent references, reusing whoever is already in the registry and minting a
 * record for whoever is not.
 *
 * <p>New agents are buffered rather than saved: a form that fails to parse halfway through must not leave half a dozen
 * freshly invented authors behind. The caller persists {@link #created()} only once the whole aggregate has been read
 * successfully.
 *
 * <p>Matching goes through {@link AgentDirectory}, so it is case-, accent- and punctuation-insensitive and searches
 * aliases as well as preferred names: typing "Penguin" on a second book finds the "Penguin Books" record created by the
 * first.
 */
public final class AgentResolution {

    private final AgentDirectory directory;
    private final Map<String, Agent> minted = new LinkedHashMap<>();

    public AgentResolution(AgentDirectory directory) {
        this.directory = directory;
    }

    /**
     * @param name what the user typed
     * @param kindIfNew applied only when nobody of that name is on file yet
     */
    public AgentId resolve(String name, AgentKind kindIfNew) {
        String typed = name == null ? "" : name.trim();
        if (typed.isEmpty()) {
            throw new IllegalArgumentException("an agent needs a name");
        }
        Optional<Agent> known = directory.resolve(typed);
        if (known.isPresent()) {
            return known.get().id();
        }
        String key = Slug.of(typed);
        Agent alreadyMinted = minted.get(key);
        if (alreadyMinted != null) {
            return alreadyMinted.id();
        }
        NameForm parsed = NameForm.of(typed, kindIfNew);
        Agent fresh = new Agent(freeId(typed), kindIfNew, parsed.display(), parsed.sortName(), Set.of());
        minted.put(key, fresh);
        return fresh.id();
    }

    /** Agents invented during this parse, in the order they were first named. */
    public List<Agent> created() {
        return List.copyOf(minted.values());
    }

    public boolean isEmpty() {
        return minted.isEmpty();
    }

    /** The id derives from the whole name, so two Tolkiens do not collide on the surname. */
    private AgentId freeId(String wholeName) {
        AgentId candidate = directory.freeId(wholeName);
        List<AgentId> taken =
                new ArrayList<>(minted.values().stream().map(Agent::id).toList());
        int suffix = 2;
        while (taken.contains(candidate)) {
            candidate = AgentId.of(AgentId.forName(wholeName).value() + "-" + suffix++);
        }
        return candidate;
    }
}
