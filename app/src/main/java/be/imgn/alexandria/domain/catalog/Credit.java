package be.imgn.alexandria.domain.catalog;

import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.work.Work;

import java.util.Objects;

/**
 * One appearance of an agent in the catalogue: which work, in what capacity, and under
 * which name it was published.
 *
 * <p>Grouping these by {@link #publishedAs} is what produces the "as Robin Hobb" /
 * "as Megan Lindholm" split on an agent's page, while the agent reference keeps the whole
 * output in one place.
 */
public record Credit(Work work, Role role, String publishedAs) {

    public Credit {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(publishedAs, "publishedAs");
    }
}
