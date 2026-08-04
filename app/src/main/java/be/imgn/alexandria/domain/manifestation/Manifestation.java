package be.imgn.alexandria.domain.manifestation;

import java.util.List;
import java.util.Objects;

import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.ExpressionId;

/**
 * FRBR Manifestation: the edition — one publisher, one carrier, one printing, one ISBN.
 *
 * <p>Aggregate root. It <em>references</em> the Expressions it embodies by id rather than containing them: an omnibus
 * embodies expressions belonging to several Works, so nesting it under any one Work would be a lie. More than one
 * reference is the normal case for collected editions.
 */
public record Manifestation(
        ManifestationId id,
        List<ExpressionId> embodies,
        Title title,
        Publisher publisher,
        BibliographicDate published,
        Carrier carrier,
        Identifier identifier,
        Extent extent,
        Series series,
        EditionStatement editionStatement,
        List<Contribution> contributors) {

    public Manifestation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(published, "published");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(extent, "extent");
        embodies = Guard.notEmpty(embodies, "embodies");
        publisher = publisher == null ? Publisher.UNRECORDED : publisher;
        series = series == null ? Series.STANDALONE : series;
        editionStatement = editionStatement == null ? EditionStatement.UNSTATED : editionStatement;
        // Whoever made this printing rather than the text: a cover artist, a typographer.
        contributors = contributors == null ? List.of() : List.copyOf(contributors);

        if (embodies.size() != embodies.stream().distinct().count()) {
            throw new IllegalArgumentException("duplicate expression reference in manifestation " + id);
        }
        if (carrier instanceof Carrier.Audiobook && extent instanceof Extent.Pages) {
            throw new IllegalArgumentException("an audiobook is measured in playing time, not pages");
        }
    }

    public static Manifestation of(
            ManifestationId id,
            ExpressionId expression,
            Title title,
            AgentId publisher,
            BibliographicDate published,
            Carrier carrier,
            Identifier identifier,
            Extent extent) {
        return new Manifestation(
                id,
                List.of(expression),
                title,
                Publisher.of(publisher),
                published,
                carrier,
                identifier,
                extent,
                Series.STANDALONE,
                EditionStatement.UNSTATED,
                List.of());
    }

    public boolean embodies(ExpressionId expressionId) {
        return embodies.contains(expressionId);
    }

    /** True for an omnibus, a collected edition, a bilingual facing-page printing. */
    public boolean isCompilation() {
        return embodies.size() > 1;
    }

    public Manifestation withSeries(Series newSeries) {
        return new Manifestation(
                id,
                embodies,
                title,
                publisher,
                published,
                carrier,
                identifier,
                extent,
                newSeries == null ? Series.STANDALONE : newSeries,
                editionStatement,
                contributors);
    }

    /** Imprint line: "Ecco, 2003. Hardcover, 940 pp." */
    public String imprint(AgentDirectory agents) {
        StringBuilder line = new StringBuilder();
        publisher.agent().ifPresent(p -> line.append(agents.nameOf(p)).append(", "));
        line.append(published.display());
        line.append(". ").append(carrier.label());
        String size = extent.display();
        if (!size.isEmpty()) {
            line.append(", ").append(size);
        }
        return line.toString();
    }
}
