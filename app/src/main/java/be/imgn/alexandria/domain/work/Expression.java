package be.imgn.alexandria.domain.work;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Guard;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Role;

/**
 * FRBR Expression: the intellectual realisation of a Work in one specific form — a language, a translation, an
 * abridgement, a narration.
 *
 * <p>An entity of the {@link Work} aggregate; it is never loaded or saved on its own.
 */
public record Expression(
        ExpressionId id,
        ExpressionKind kind,
        Language language,
        List<Contribution> contributors,
        BibliographicDate realised) {

    public Expression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(realised, "realised");
        contributors = Guard.copyOf(contributors);

        if (kind instanceof ExpressionKind.Translation(Language from) && from.equals(language)) {
            throw new IllegalArgumentException("a translation must differ from its source language: " + language);
        }
    }

    public static Expression original(ExpressionId id, Language language, BibliographicDate realised) {
        return new Expression(id, ExpressionKind.ORIGINAL, language, List.of(), realised);
    }

    public static Expression translation(
            ExpressionId id, Language from, Language into, Agent translator, BibliographicDate realised) {
        return new Expression(
                id, new ExpressionKind.Translation(from), into, List.of(Contribution.translator(translator)), realised);
    }

    /** A one-line rendering used by the editor and the generated site. */
    /**
     * How this realisation reads on a page: the language, how it came to be, and who made it.
     *
     * <p>The illustrator is named alongside the translator because illustrations are part of a realisation rather than
     * of a printing — a text illustrated is a different expression from the text alone. Two editions of one
     * translation, one illustrated and one not, are two expressions, and without the illustrator in this line they
     * would describe themselves identically.
     */
    public String describe() {
        return realisation() + agentSuffix(Role.ILLUSTRATOR, ", illustrated by ");
    }

    private String realisation() {
        return switch (kind) {
            case ExpressionKind.Original() -> language.displayName() + " (original)";
            case ExpressionKind.Translation(Language from) ->
                language.displayName() + ", translated from " + from.displayName() + agentSuffix(Role.TRANSLATOR);
            case ExpressionKind.Revision(String label) -> language.displayName() + ", " + label;
            case ExpressionKind.Abridgement() -> language.displayName() + ", abridged";
            case ExpressionKind.Adaptation(String into) -> language.displayName() + ", adapted as " + into;
            case ExpressionKind.Narration() -> language.displayName() + ", narrated" + agentSuffix(Role.NARRATOR);
        };
    }

    public Optional<Contribution> contributorIn(Role role) {
        return contributors.stream().filter(c -> c.role().equals(role)).findFirst();
    }

    private String agentSuffix(Role role) {
        return agentSuffix(role, " by ");
    }

    private String agentSuffix(Role role, String lead) {
        return contributorIn(role).map(c -> lead + c.publishedAs()).orElse("");
    }
}
