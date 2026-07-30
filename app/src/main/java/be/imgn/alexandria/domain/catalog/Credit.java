package be.imgn.alexandria.domain.catalog;

import java.util.Objects;
import java.util.Optional;

import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.Work;

/**
 * One appearance of an agent in the catalogue: which work, in what capacity, under which name — and, when the credit is
 * for a particular realisation rather than the work itself, which expression.
 *
 * <p>Two shapes, because a credit is on one level or the other and never both. Keeping them apart matters because the
 * levels are dated differently: Tolkien created <em>The Lord of the Rings</em> between 1937 and 1949; Daniel Lauzon
 * translated it between 2014 and 2016. A credit that forgot which level it belonged to would date the translator by the
 * author's dates, which is how this type came to exist in the first place.
 */
public sealed interface Credit {

    Work work();

    Role role();

    /** The name the book was actually issued under, which is not always the agent's preferred one. */
    String publishedAs();

    /** The date this contribution belongs to, taken from the level the credit sits on. */
    BibliographicDate when();

    /**
     * The language of the realisation contributed to, for a credit that is on one.
     *
     * <p>Deliberately just the language rather than the expression's full description. On a translator's own page,
     * "translated from English by Daniel Lauzon" tells the reader nothing they did not already know and reads as though
     * "the French" were the thing translated. What a bare role is missing is which realisation, and the language says
     * that in one word.
     */
    Optional<String> realisation();

    record OnWork(Work work, Role role, String publishedAs) implements Credit {

        public OnWork {
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(publishedAs, "publishedAs");
        }

        @Override
        public BibliographicDate when() {
            return work.created();
        }

        @Override
        public Optional<String> realisation() {
            return Optional.empty();
        }
    }

    record OnExpression(Work work, Expression expression, Role role, String publishedAs) implements Credit {

        public OnExpression {
            Objects.requireNonNull(work, "work");
            Objects.requireNonNull(expression, "expression");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(publishedAs, "publishedAs");
        }

        @Override
        public BibliographicDate when() {
            return expression.realised();
        }

        @Override
        public Optional<String> realisation() {
            return Optional.of(expression.language().displayName());
        }
    }
}
