package be.imgn.alexandria.domain.work;

import be.imgn.alexandria.domain.shared.Slug;

/** Identity of the {@link Work} aggregate. Doubles as the on-disk file name. */
public record WorkId(String value) {

    public WorkId {
        Slug.validate(value, "WorkId");
    }

    public static WorkId of(String value) {
        return new WorkId(value);
    }

    /** Derives a stable, human-readable id such as {@code cervantes-don-quixote}. */
    public static WorkId from(String author, String title) {
        return new WorkId(Slug.of(author) + "-" + Slug.of(title));
    }

    @Override
    public String toString() {
        return value;
    }
}
