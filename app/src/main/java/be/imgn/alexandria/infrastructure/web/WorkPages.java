package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkForm;
import be.imgn.alexandria.domain.work.WorkId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Browsing and editing the Work aggregate, Expressions included. */
final class WorkPages {

    private final CatalogService service;

    WorkPages(CatalogService service) {
        this.service = service;
    }

    String list() {
        AgentDirectory agents = service.directory();
        List<List<String>> rows = service.catalog().works().stream()
                .map(work -> List.of(
                        Html.link("/works/" + work.id().value(), work.title().main()),
                        Html.escape(work.byline()),
                        Html.escape(work.form().label()),
                        Html.escape(work.created().display()),
                        String.valueOf(work.expressions().size()),
                        String.valueOf(work.expressions().stream()
                                .mapToLong(e -> service.catalog().manifestationsOf(e.id()).size()).sum())))
                .toList();
        return Html.page("Works", Html.link("/", "Home") + " / Works", """
                <h1>Works</h1>
                <p><a class="button" href="/works/new">New work</a></p>
                %s
                """.formatted(Html.table(
                List.of("Title", "Author", "Form", "Created", "Expressions", "Editions"), rows)));
    }

    String edit(Optional<Work> existing) {
        Work work = existing.orElse(null);
        AgentDirectory agents = service.directory();
        String heading = work == null ? "New work" : work.title().main();
        String id = work == null ? "" : work.id().value();

        StringBuilder expressions = new StringBuilder();
        int index = 0;
        if (work != null) {
            for (Expression expression : work.expressions()) {
                expressions.append(expressionFields(index++, expression, agents));
            }
        }
        expressions.append(expressionFields(index, null, agents));

        String subjects = work == null ? "" : String.join(", ", work.subjects());
        String deleteButton = work == null ? "" : """
                <form method="post" action="/works/%s/delete" class="danger"
                      onsubmit="return confirm('Delete this work and its expressions?')">
                  <button type="submit">Delete work</button>
                </form>
                """.formatted(Html.escape(id));

        return Html.page(heading, Html.link("/works", "Works") + " / " + Html.escape(heading), """
                <h1>%s</h1>
                %s
                <form method="post" action="/works/%s">
                  <fieldset><legend>Work</legend>
                    %s
                    %s
                    %s
                    %s
                    %s
                    %s
                  </fieldset>
                  %s
                  <fieldset><legend>Expressions</legend>
                    <p class="hint">One per language, translation, abridgement or narration.
                       Leave the identifier blank to skip a row.</p>
                    %s
                  </fieldset>
                  <button type="submit">Save</button>
                </form>
                %s
                """.formatted(
                Html.escape(heading),
                Html.datalist(VariantForms.AGENT_LIST, agents.suggestions()),
                Html.escape(id.isEmpty() ? "new" : id),
                work == null
                        ? Html.textField("id", "Identifier (slug)", "")
                        : readOnly("Identifier", id) + hidden("id", id),
                Html.textField("title.main", "Title", work == null ? "" : work.title().main()),
                Html.textField("title.subtitle", "Subtitle",
                        work == null ? "" : work.title().subtitle().orElse("")),
                VariantForms.workForm("form", "Form", work == null ? WorkForm.NOVEL : work.form()),
                VariantForms.date("created", "Created",
                        work == null ? BibliographicDate.UNKNOWN : work.created()),
                Html.textField("subjects", "Subjects (comma separated)", subjects),
                VariantForms.contributions("creators", "Creators",
                        work == null ? List.of() : work.creators(), agents),
                expressions,
                deleteButton));
    }

    private String expressionFields(int index, Expression expression, AgentDirectory agents) {
        String prefix = "expressions[" + index + "].";
        return """
                <fieldset class="nested"><legend>Expression %d</legend>
                  %s
                  %s
                  %s
                  %s
                  %s
                </fieldset>
                """.formatted(
                index + 1,
                Html.textField(prefix + "id", "Identifier (slug)",
                        expression == null ? "" : expression.id().value()),
                Html.textField(prefix + "language", "Language code",
                        expression == null ? "" : expression.language().code()),
                VariantForms.expressionKind(prefix + "kind", "Kind",
                        expression == null ? new be.imgn.alexandria.domain.work.ExpressionKind.Original()
                                : expression.kind()),
                VariantForms.date(prefix + "realised", "Realised",
                        expression == null ? BibliographicDate.UNKNOWN : expression.realised()),
                VariantForms.contributions(prefix + "contributors", "Contributors",
                        expression == null ? List.of() : expression.contributors(), agents));
    }

    Work read(FormData form, AgentResolution agents) {
        WorkId id = WorkId.of(form.required("id"));
        List<Expression> expressions = new ArrayList<>();
        for (int index = 0; index < form.size("expressions"); index++) {
            FormData row = form.at("expressions", index);
            Optional<String> localId = row.optional("id");
            if (localId.isEmpty()) {
                continue;
            }
            expressions.add(new Expression(
                    new ExpressionId(id, localId.get()),
                    VariantForms.readExpressionKind(row, "kind"),
                    new Language(row.required("language")),
                    VariantForms.readContributions(row, "contributors", agents),
                    VariantForms.readDate(row, "realised")));
        }
        if (expressions.isEmpty()) {
            throw new IllegalArgumentException(
                    "a work must have at least one expression — give the first one an identifier and a language");
        }
        Set<String> subjects = form.optional("subjects").stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new Work(
                id,
                new Title(form.required("title.main"), form.optional("title.subtitle")),
                VariantForms.readContributions(form, "creators", agents),
                VariantForms.readWorkForm(form, "form"),
                VariantForms.readDate(form, "created"),
                subjects,
                expressions);
    }

    static String readOnly(String label, String value) {
        return "<label><span>" + Html.escape(label) + "</span><input type=\"text\" value=\""
                + Html.escape(value) + "\" readonly></label>";
    }

    static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + Html.escape(name) + "\" value=\"" + Html.escape(value) + "\">";
    }
}
