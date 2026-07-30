package be.imgn.alexandria.infrastructure.json.codec;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.Title;

/**
 * The pieces more than one aggregate is made of: titles, dates, roles and credits.
 *
 * <p>Every {@code switch} here is exhaustive over a sealed type, so a new variant of any of them stops the build in
 * each codec that has to learn about it. That is the whole reason this package exists instead of a set of annotations.
 */
final class SharedCodec {

    private SharedCodec() {}

    // -------------------------------------------------------------------- title

    static void title(JsonOut out, Title title) {
        switch (title) {
            case Title.Plain(String main) -> out.text("main", main);
            case Title.Subtitled(String main, String subtitle) ->
                out.text("main", main).text("subtitle", subtitle);
        }
    }

    static Title title(JsonIn in) {
        String main = in.text("main");
        return in.optionalText("subtitle")
                .<Title>map(subtitle -> new Title.Subtitled(main, subtitle))
                .orElseGet(() -> new Title.Plain(main));
    }

    // --------------------------------------------------------------------- date

    static void date(JsonOut out, BibliographicDate date) {
        switch (date) {
            case BibliographicDate.Exact(LocalDate on) ->
                out.text("type", "exact").text("date", on.toString());
            case BibliographicDate.Year(int value) -> out.text("type", "year").number("value", value);
            case BibliographicDate.Circa(int value) -> out.text("type", "circa").number("value", value);
            case BibliographicDate.Between(int from, int to) ->
                out.text("type", "between").number("from", from).number("to", to);
            case BibliographicDate.Unknown() -> out.text("type", "unknown");
        }
    }

    static BibliographicDate date(JsonIn in) {
        return switch (in.type()) {
            case "exact" ->
                new BibliographicDate.Exact(in.optionalDate("date").orElseThrow(() -> missing("date", "exact date")));
            case "year" -> new BibliographicDate.Year(required(in, "value", "year"));
            case "circa" -> new BibliographicDate.Circa(required(in, "value", "circa"));
            case "between" ->
                new BibliographicDate.Between(required(in, "from", "between"), required(in, "to", "between"));
            case "unknown" -> BibliographicDate.UNKNOWN;
            default -> unknown("date", in.type());
        };
    }

    // --------------------------------------------------------------------- role

    static void role(JsonOut out, Role role) {
        switch (role) {
            case Role.Author() -> out.text("type", "author");
            case Role.Translator() -> out.text("type", "translator");
            case Role.Editor() -> out.text("type", "editor");
            case Role.Illustrator() -> out.text("type", "illustrator");
            case Role.Narrator() -> out.text("type", "narrator");
            case Role.Publisher() -> out.text("type", "publisher");
            case Role.Other(String label) -> out.text("type", "other").text("label", label);
        }
    }

    static Role role(JsonIn in) {
        return switch (in.type()) {
            case "author" -> Role.AUTHOR;
            case "translator" -> Role.TRANSLATOR;
            case "editor" -> Role.EDITOR;
            case "illustrator" -> Role.ILLUSTRATOR;
            case "narrator" -> Role.NARRATOR;
            case "publisher" -> Role.PUBLISHER;
            case "other" -> new Role.Other(in.text("label"));
            default -> unknown("role", in.type());
        };
    }

    // -------------------------------------------------------------- contribution

    static void contribution(JsonOut out, Contribution contribution) {
        out.text("agent", contribution.agent().value())
                .object("role", nested -> role(nested, contribution.role()))
                .text("publishedAs", contribution.publishedAs());
    }

    static Contribution contribution(JsonIn in) {
        return new Contribution(AgentId.of(in.text("agent")), role(in.object("role")), in.text("publishedAs"));
    }

    static List<Contribution> contributions(JsonIn in, String key) {
        List<Contribution> contributions = new ArrayList<>();
        for (JsonIn entry : in.objects(key)) {
            contributions.add(contribution(entry));
        }
        return List.copyOf(contributions);
    }

    // ----------------------------------------------------------------- language

    static Language language(JsonIn in, String key) {
        return new Language(in.text(key));
    }

    static Optional<Language> optionalLanguage(JsonIn in, String key) {
        return in.optionalText(key).map(Language::new);
    }

    // ------------------------------------------------------------------ helpers

    private static int required(JsonIn in, String key, String what) {
        return in.optionalInt(key).orElseThrow(() -> missing(key, what));
    }

    private static IllegalArgumentException missing(String key, String what) {
        return new IllegalArgumentException("a " + what + " needs '" + key + "'");
    }

    static <T> T unknown(String what, String variant) {
        throw new IllegalArgumentException("unknown " + what + " variant '" + variant + "'");
    }
}
