package be.imgn.alexandria.domain.manifestation;

import java.util.Optional;

import be.imgn.alexandria.domain.agent.AgentId;

/**
 * Who issued an edition, or the admission that the title page did not say.
 *
 * <p>Second-hand books turn up without a publisher often enough that refusing to catalogue them would be worse than
 * recording the gap — so the gap is a shape rather than an {@code Optional} in the constructor.
 */
public sealed interface Publisher {

    Publisher UNRECORDED = new Unrecorded();

    static Publisher of(AgentId id) {
        return id == null ? UNRECORDED : new Known(id);
    }

    /** Reads the stored form, treating blank and null as never recorded. */
    static Publisher parse(String id) {
        return id == null || id.isBlank() ? UNRECORDED : new Known(AgentId.of(id));
    }

    /** The agent, for the caller that has to resolve it against the registry. */
    Optional<AgentId> agent();

    /** The stored form, or blank when there is nothing to write. */
    String stored();

    record Known(AgentId id) implements Publisher {

        public Known {
            if (id == null) {
                throw new IllegalArgumentException("a known publisher needs an agent");
            }
        }

        @Override
        public Optional<AgentId> agent() {
            return Optional.of(id);
        }

        @Override
        public String stored() {
            return id.value();
        }
    }

    record Unrecorded() implements Publisher {

        @Override
        public Optional<AgentId> agent() {
            return Optional.empty();
        }

        @Override
        public String stored() {
            return "";
        }
    }
}
