package be.imgn.alexandria.domain.work;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.Title;

/**
 * FRBR Work: the distinct intellectual creation, independent of any language or printing.
 *
 * <p>Aggregate root. Its {@link Expression}s are entities within the boundary — they change together with the Work and
 * are persisted as one file. Manifestations sit <em>outside</em> the boundary because a single volume can embody
 * expressions of several Works (an omnibus), which no single Work may own.
 */
public record Work(
        WorkId id,
        Title title,
        List<Contribution> creators,
        WorkForm form,
        BibliographicDate created,
        Set<String> subjects,
        List<Expression> expressions) {

    public Work {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(created, "created");
        creators = Guard.copyOf(creators);
        subjects = Guard.sortedCopyOf(subjects);
        expressions = Guard.notEmpty(expressions, "expressions");

        Set<String> seen = new HashSet<>();
        for (Expression expression : expressions) {
            if (!expression.id().work().equals(id)) {
                throw new IllegalArgumentException("expression " + expression.id() + " does not belong to work " + id);
            }
            if (!seen.add(expression.id().value())) {
                throw new IllegalArgumentException("duplicate expression id " + expression.id());
            }
        }
    }

    public static Work create(
            WorkId id,
            Title title,
            List<Contribution> creators,
            WorkForm form,
            BibliographicDate created,
            Expression firstExpression) {
        return new Work(id, title, creators, form, created, Set.of(), List.of(firstExpression));
    }

    public Optional<Expression> expression(ExpressionId expressionId) {
        return expressions.stream().filter(e -> e.id().equals(expressionId)).findFirst();
    }

    public Work withExpression(Expression expression) {
        List<Expression> updated = new ArrayList<>(expressions);
        updated.removeIf(e -> e.id().equals(expression.id()));
        updated.add(expression);
        return new Work(id, title, creators, form, created, subjects, updated);
    }

    /** Removing the last Expression would leave a Work that is realised by nothing. */
    public Work withoutExpression(ExpressionId expressionId) {
        List<Expression> updated = new ArrayList<>(expressions);
        updated.removeIf(e -> e.id().equals(expressionId));
        if (updated.isEmpty()) {
            throw new IllegalStateException("cannot remove the only expression of work " + id);
        }
        return new Work(id, title, creators, form, created, subjects, updated);
    }

    public Work withTitle(Title newTitle) {
        return new Work(id, newTitle, creators, form, created, subjects, expressions);
    }

    public Work withSubjects(Set<String> newSubjects) {
        return new Work(id, title, creators, form, created, newSubjects, expressions);
    }

    /**
     * The author line as the book was issued — "Robin Hobb", not the agent's preferred name if the two differ. Filing
     * still happens under the agent, so the whole output stays together however it was signed.
     */
    public String byline() {
        List<String> authors = creators.stream()
                .filter(c -> c.role().equals(Role.AUTHOR))
                .map(Contribution::publishedAs)
                .toList();
        return authors.isEmpty() ? "Anonymous" : String.join(", ", authors);
    }

    /** Files anonymous works last, hence the high code point rather than an empty string. */
    public String sortKey(AgentDirectory agents) {
        return creators.stream()
                        .filter(c -> c.role().equals(Role.AUTHOR))
                        .map(c -> agents.sortNameOf(c.agent()))
                        .findFirst()
                        .orElse("￿")
                + " | " + title.main();
    }
}
