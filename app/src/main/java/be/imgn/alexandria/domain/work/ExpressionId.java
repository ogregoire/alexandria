package be.imgn.alexandria.domain.work;

import java.util.Objects;

import be.imgn.alexandria.domain.shared.Slug;

/**
 * Identity of an {@link Expression}. An Expression is an entity <em>inside</em> the Work aggregate, so its identity is
 * qualified by the owning {@link WorkId}: that keeps the reference held by a Manifestation globally resolvable without
 * a lookup table.
 */
public record ExpressionId(WorkId work, String value) {

    public ExpressionId {
        Objects.requireNonNull(work, "work");
        Slug.validate(value, "ExpressionId");
    }

    /** Wire and file format, e.g. {@code cervantes-don-quixote/grossman-en}. */
    public String qualified() {
        return work.value() + "/" + value;
    }

    public static ExpressionId parse(String qualified) {
        int slash = qualified.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("expected <work>/<expression> but got '" + qualified + "'");
        }
        return new ExpressionId(WorkId.of(qualified.substring(0, slash)), qualified.substring(slash + 1));
    }

    @Override
    public String toString() {
        return qualified();
    }
}
