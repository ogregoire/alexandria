package be.imgn.alexandria.domain.catalog;

import java.util.Objects;
import java.util.Optional;

import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.Work;

/**
 * One appearance of an agent in the catalogue: which work, in what capacity, under which name, and — when the credit is
 * for a particular realisation rather than the work itself — which expression.
 *
 * <p>Keeping the expression matters because the two levels are dated differently. Tolkien created <em>The Lord of the
 * Rings</em> between 1937 and 1949; Daniel Lauzon translated it between 2014 and 2016. A credit that forgot which level
 * it belonged to would date the translator by the author's dates, which is how this record came to exist.
 *
 * @param expression the realisation contributed to, empty when the credit is on the work
 */
public record Credit(Work work, Optional<Expression> expression, Role role, String publishedAs) {

    public Credit {
        Objects.requireNonNull(work, "work");
        expression = expression == null ? Optional.empty() : expression;
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(publishedAs, "publishedAs");
    }

    public static Credit onWork(Work work, Role role, String publishedAs) {
        return new Credit(work, Optional.empty(), role, publishedAs);
    }

    public static Credit onExpression(Work work, Expression expression, Role role, String publishedAs) {
        return new Credit(work, Optional.of(expression), role, publishedAs);
    }

    /**
     * The date this contribution belongs to: when the expression was realised, or when the work was created for a
     * credit on the work itself.
     */
    public BibliographicDate when() {
        return expression.map(Expression::realised).orElseGet(work::created);
    }

    /** The role, qualified by the realisation when the credit is for one. */
    public String describe() {
        return expression
                .map(realisation -> role.label() + " of the " + realisation.describe())
                .orElse(role.label());
    }
}
