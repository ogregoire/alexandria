package be.imgn.alexandria.infrastructure.json.codec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkForm;
import be.imgn.alexandria.domain.work.WorkId;

/**
 * Reads and writes a {@link Work}, including the {@link Expression}s inside it.
 *
 * <p>The expressions are nested rather than referenced because they are entities of this aggregate: one file, written
 * and read as a whole, which is what makes the boundary real on disk rather than only in the model.
 */
public final class WorkCodec {

    private WorkCodec() {}

    public static String write(Work work) {
        return JsonOut.document(out -> out.text("id", work.id().value())
                .object("title", nested -> SharedCodec.title(nested, work.title()))
                .objects("creators", work.creators(), SharedCodec::contribution)
                .object("form", nested -> form(nested, work.form()))
                .object("created", nested -> SharedCodec.date(nested, work.created()))
                .texts("subjects", work.subjects())
                .objects("expressions", work.expressions(), WorkCodec::expression));
    }

    public static Work read(String json) {
        JsonIn in = JsonIn.parse(json);
        List<Expression> expressions = new ArrayList<>();
        for (JsonIn entry : in.objects("expressions")) {
            expressions.add(expression(entry));
        }
        return new Work(
                WorkId.of(in.text("id")),
                SharedCodec.title(in.object("title")),
                SharedCodec.contributions(in, "creators"),
                form(in.object("form")),
                SharedCodec.date(in.object("created")),
                new LinkedHashSet<>(in.texts("subjects")),
                expressions);
    }

    // ---------------------------------------------------------------- expression

    private static void expression(JsonOut out, Expression expression) {
        out.text("id", expression.id().qualified())
                .object("kind", nested -> kind(nested, expression.kind()))
                .text("language", expression.language().code())
                .objects("contributors", expression.contributors(), SharedCodec::contribution)
                .object("realised", nested -> SharedCodec.date(nested, expression.realised()));
    }

    private static Expression expression(JsonIn in) {
        return new Expression(
                ExpressionId.parse(in.text("id")),
                kind(in.object("kind")),
                SharedCodec.language(in, "language"),
                SharedCodec.contributions(in, "contributors"),
                SharedCodec.date(in.object("realised")));
    }

    private static void kind(JsonOut out, ExpressionKind kind) {
        switch (kind) {
            case ExpressionKind.Original() -> out.text("type", "original");
            case ExpressionKind.Translation(Language from) ->
                out.text("type", "translation").text("from", from.code());
            case ExpressionKind.Revision(String label) ->
                out.text("type", "revision").text("label", label);
            case ExpressionKind.Abridgement() -> out.text("type", "abridgement");
            case ExpressionKind.Adaptation(String into) ->
                out.text("type", "adaptation").text("into", into);
            case ExpressionKind.Narration() -> out.text("type", "narration");
        }
    }

    private static ExpressionKind kind(JsonIn in) {
        return switch (in.type()) {
            case "original" -> new ExpressionKind.Original();
            case "translation" -> new ExpressionKind.Translation(SharedCodec.language(in, "from"));
            case "revision" -> new ExpressionKind.Revision(in.text("label"));
            case "abridgement" -> new ExpressionKind.Abridgement();
            case "adaptation" -> new ExpressionKind.Adaptation(in.text("into"));
            case "narration" -> new ExpressionKind.Narration();
            default -> SharedCodec.unknown("expression kind", in.type());
        };
    }

    // --------------------------------------------------------------------- form

    private static void form(JsonOut out, WorkForm form) {
        switch (form) {
            case WorkForm.Novel() -> out.text("type", "novel");
            case WorkForm.Novella() -> out.text("type", "novella");
            case WorkForm.ShortStories() -> out.text("type", "short-stories");
            case WorkForm.Poetry() -> out.text("type", "poetry");
            case WorkForm.Drama() -> out.text("type", "drama");
            case WorkForm.Essay() -> out.text("type", "essay");
            case WorkForm.Nonfiction() -> out.text("type", "nonfiction");
            case WorkForm.Reference() -> out.text("type", "reference");
            case WorkForm.Comics() -> out.text("type", "comics");
            case WorkForm.Other(String label) -> out.text("type", "other").text("label", label);
        }
    }

    private static WorkForm form(JsonIn in) {
        return switch (in.type()) {
            case "novel" -> new WorkForm.Novel();
            case "novella" -> new WorkForm.Novella();
            case "short-stories" -> new WorkForm.ShortStories();
            case "poetry" -> new WorkForm.Poetry();
            case "drama" -> new WorkForm.Drama();
            case "essay" -> new WorkForm.Essay();
            case "nonfiction" -> new WorkForm.Nonfiction();
            case "reference" -> new WorkForm.Reference();
            case "comics" -> new WorkForm.Comics();
            case "other" -> new WorkForm.Other(in.text("label"));
            default -> SharedCodec.unknown("work form", in.type());
        };
    }
}
