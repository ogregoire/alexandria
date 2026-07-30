package be.imgn.alexandria.domain.agent;

import be.imgn.alexandria.domain.shared.Slug;

/** Identity of the {@link Agent} aggregate. Doubles as the on-disk file name. */
public record AgentId(String value) {

    public AgentId {
        Slug.validate(value, "AgentId");
    }

    public static AgentId of(String value) {
        return new AgentId(value);
    }

    /**
     * Derives an id from a name. Because {@link Slug} folds case, accents and punctuation, "J. R. R. Tolkien" and
     * "J.R.R. Tolkien" derive the same id — which is exactly the collision the registry wants to notice.
     */
    public static AgentId forName(String name) {
        return new AgentId(Slug.of(name));
    }

    @Override
    public String toString() {
        return value;
    }
}
