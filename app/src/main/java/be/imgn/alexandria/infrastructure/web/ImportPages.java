package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.Slug;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adding a book from its ISBN.
 *
 * <p>The lookup only ever <em>prefills</em>. Nothing is written until the filled-in form is
 * reviewed and submitted, because third-party metadata is routinely wrong about exactly the
 * things this model cares about — which name is on the title page, whether an edition is a
 * translation, what the series number is.
 *
 * <p>One submission creates the whole descent at once: the Work with its first Expression,
 * the Manifestation embodying it, and optionally the Item that is your copy. If any of it is
 * rejected, the same form comes back holding everything that was typed, with each problem
 * shown against the field that caused it — nothing has to be entered twice.
 */
final class ImportPages {

    private static final int CONTRIBUTOR_ROWS = 2;

    private final CatalogService service;
    private final BookLookup lookup;

    ImportPages(CatalogService service, BookLookup lookup) {
        this.service = service;
        this.lookup = lookup;
    }

    /** The starting point: an ISBN and whether a copy is being shelved. */
    String ask(String isbn, String problem) {
        String note = problem == null || problem.isBlank() ? "" :
                "<div class=\"error\" role=\"alert\">" + Html.escape(problem) + "</div>";
        FormState state = FormState.prefilled(Map.of("isbn", isbn == null ? "" : isbn));
        return Html.page("Add from ISBN", Html.link("/", "Home") + " / Add from ISBN", """
                <h1>Add a book from its ISBN</h1>
                <p class="hint">Looks the ISBN up in %s and fills in a form for you to check.
                   Nothing is saved until you review it.</p>
                %s
                <form method="post" action="/import">
                  <fieldset><legend>ISBN</legend>
                    %s
                    <label class="check"><input type="checkbox" name="addItem" value="yes" checked>
                      <span>I own a copy — also record it as an item</span></label>
                  </fieldset>
                  <button type="submit">Look it up</button>
                </form>
                """.formatted(Html.escape(lookup.name()), note,
                Html.input(state, "isbn", "ISBN-10 or ISBN-13", "text", "required isbn")));
    }

    /** The form after a lookup: suggested values, nothing wrong yet. */
    String review(Identifier isbn, Optional<BookDraft> found, boolean addItem) {
        String provenance = found
                .map(draft -> "<p class=\"ok\">Prefilled from " + Html.escape(draft.source())
                        + ". Check every field before saving.</p>")
                .orElse("<p class=\"error\">No catalogue had this ISBN. "
                        + "The form is empty — fill it in by hand.</p>");
        return form(FormState.prefilled(prefill(isbn, found, addItem)), provenance);
    }

    /** The same form after rejection: what was typed, and what is wrong with it. */
    String reviewAgain(FormState state) {
        return form(state, "");
    }

    // ------------------------------------------------------------- rendering

    private String form(FormState state, String provenance) {
        AgentDirectory agents = service.directory();

        return Html.page("Check and save", Html.link("/import", "Add from ISBN") + " / Check", """
                <h1>Check and save</h1>
                %s
                %s
                %s
                <form method="post" action="/import/save" novalidate>
                  %s
                  <fieldset><legend>Work — the text itself, in any language</legend>
                    %s%s%s%s%s%s
                  </fieldset>
                  %s
                  <fieldset><legend>Expression — this language, this translation</legend>
                    %s%s%s%s
                  </fieldset>
                  <fieldset><legend>Manifestation — this edition</legend>
                    %s%s%s%s%s%s%s%s%s%s
                  </fieldset>
                  <fieldset><legend>Item — your copy</legend>
                    <label class="check"><input type="checkbox" name="addItem" value="yes"%s>
                      <span>Record a copy of this edition</span></label>
                    %s%s%s%s%s
                  </fieldset>
                  <button type="submit">Save the book</button>
                </form>
                """.formatted(
                provenance,
                Html.problemSummary(state, fieldLabels()),
                Html.datalist(VariantForms.AGENT_LIST, agents.suggestions()),
                WorkPages.hidden("isbn", state.value("isbn")),

                Html.input(state, "id", "Work identifier (slug)", "text", "required slug"),
                Html.input(state, "title.main", "Title in the original language", "text", "required"),
                Html.input(state, "title.subtitle", "Subtitle", "text"),
                SumTypeForms.render(state, "form", "Form", SumTypeForms.WORK_FORM),
                SumTypeForms.render(state, "created", "First published", SumTypeForms.DATE),
                Html.input(state, "subjects", "Subjects (comma separated)", "text"),

                contributors(state, "creators", "Author", agents),

                Html.input(state, "expressions[0].id", "Expression identifier (slug)", "text", "required slug"),
                Html.input(state, "expressions[0].language", "Language code", "text", "required language"),
                SumTypeForms.render(state, "expressions[0].kind", "Kind", SumTypeForms.EXPRESSION_KIND),
                contributors(state, "expressions[0].contributors", "Translator and others", agents),

                Html.input(state, "manifestation.id", "Edition identifier (slug)", "text", "required slug"),
                Html.input(state, "manifestation.title.main", "Title on this edition", "text"),
                Html.suggest(state, "manifestation.publisher", "Publisher", VariantForms.AGENT_LIST, null),
                Html.choice(state, "manifestation.publisherKind", "If new",
                        VariantForms.agentKinds(), "organisation"),
                SumTypeForms.render(state, "manifestation.published", "Printed", SumTypeForms.DATE),
                SumTypeForms.render(state, "manifestation.carrier", "Carrier", SumTypeForms.CARRIER),
                SumTypeForms.render(state, "manifestation.identifier", "Identifier", SumTypeForms.IDENTIFIER),
                SumTypeForms.render(state, "manifestation.extent", "Extent", SumTypeForms.EXTENT),
                Html.input(state, "manifestation.series.name", "Series", "text"),
                Html.input(state, "manifestation.series.number", "Series number", "text"),

                state.checked("addItem") ? " checked" : "",
                Html.input(state, "item.id", "Copy identifier (slug, blank to derive)", "text", "slug"),
                SumTypeForms.render(state, "item.acquisition", "Acquired", SumTypeForms.ACQUISITION),
                SumTypeForms.render(state, "item.location", "Location", SumTypeForms.LOCATION),
                Html.choice(state, "item.condition", "Condition",
                        SumTypeForms.conditions(), Condition.UNGRADED.name()),
                Html.area(state, "item.notes", "Notes")));
    }

    /** A repeating group of name-plus-role rows, always with a spare row at the end. */
    private String contributors(FormState state, String group, String legend, AgentDirectory agents) {
        int rows = state.groupSize(group, 1) + 1;
        StringBuilder out = new StringBuilder("<fieldset><legend>" + Html.escape(legend) + "</legend>");
        out.append("<p class=\"hint\">Start typing to reuse someone already in the registry — "
                + "an alias works too, and the book keeps the name you type.</p>");
        for (int index = 0; index < rows; index++) {
            String prefix = group + "[" + index + "].";
            out.append("<div class=\"row\">")
                    .append(Html.suggest(state, prefix + "name", "Name", VariantForms.AGENT_LIST, null))
                    .append(Html.choice(state, prefix + "kind", "If new", VariantForms.agentKinds(), "person"))
                    .append(Html.choice(state, prefix + "role", "Role", roles(), "author"))
                    .append("</div>");
        }
        return out.append("</fieldset>").toString();
    }

    private static Map<String, String> roles() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("author", "Author");
        options.put("translator", "Translator");
        options.put("editor", "Editor");
        options.put("illustrator", "Illustrator");
        options.put("narrator", "Narrator");
        options.put("publisher", "Publisher");
        return options;
    }

    /** Every field the summary can link to, in the order they appear. */
    private static Map<String, String> fieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("id", "Work identifier");
        labels.put("title.main", "Title");
        labels.put("subjects", "Subjects");
        labels.putAll(SumTypeForms.fieldLabels("form", "Form", SumTypeForms.WORK_FORM));
        labels.putAll(SumTypeForms.fieldLabels("created", "First published", SumTypeForms.DATE));
        labels.put("creators", "Author");
        labels.put("expressions[0].id", "Expression identifier");
        labels.put("expressions[0].language", "Language code");
        labels.putAll(SumTypeForms.fieldLabels("expressions[0].kind", "Kind",
                SumTypeForms.EXPRESSION_KIND));
        labels.put("expressions[0].contributors", "Contributors");
        labels.put("manifestation.id", "Edition identifier");
        labels.put("manifestation.publisher", "Publisher");
        labels.putAll(SumTypeForms.fieldLabels("manifestation.published", "Printed", SumTypeForms.DATE));
        labels.putAll(SumTypeForms.fieldLabels("manifestation.carrier", "Carrier", SumTypeForms.CARRIER));
        labels.putAll(SumTypeForms.fieldLabels("manifestation.identifier", "Identifier",
                SumTypeForms.IDENTIFIER));
        labels.putAll(SumTypeForms.fieldLabels("manifestation.extent", "Extent", SumTypeForms.EXTENT));
        labels.put("manifestation.series.name", "Series");
        labels.put("item.id", "Copy identifier");
        labels.putAll(SumTypeForms.fieldLabels("item.acquisition", "Acquired", SumTypeForms.ACQUISITION));
        labels.putAll(SumTypeForms.fieldLabels("item.location", "Location", SumTypeForms.LOCATION));
        labels.put("item.condition", "Condition");
        return labels;
    }

    // ------------------------------------------------------------- prefilling

    /** A lookup result flattened into the exact field names the form renders. */
    private static Map<String, String> prefill(Identifier isbn, Optional<BookDraft> found, boolean addItem) {
        Map<String, String> values = new LinkedHashMap<>();
        String digits = isbn.isbnDigits().orElse("");
        values.put("isbn", digits);
        values.put("manifestation.identifier.type", digits.length() == 10 ? "isbn10" : "isbn13");
        values.put("manifestation.identifier." + (digits.length() == 10 ? "isbn10" : "isbn13") + ".digits",
                digits);
        values.put("manifestation.carrier.type", "paperback");
        values.put("form.type", "novel");
        if (addItem) {
            values.put("addItem", "yes");
        }
        values.put("item.condition", Condition.UNGRADED.name());

        if (found.isEmpty()) {
            return values;
        }
        BookDraft draft = found.get();
        String author = draft.authors().stream().findFirst().orElse("");
        String translator = draft.translators().stream().findFirst().orElse("");
        boolean translated = draft.looksTranslated();
        Language language = draft.language().orElse(null);

        values.put("id", suggestWorkId(author, draft.workTitle()));
        values.put("title.main", draft.workTitle());
        draft.subtitle().ifPresent(subtitle -> values.put("title.subtitle", subtitle));
        draft.originalYear().ifPresent(year -> {
            values.put("created.type", "year");
            values.put("created.year.value", String.valueOf(year));
        });
        if (!author.isBlank()) {
            values.put("creators[0].name", author);
            values.put("creators[0].kind", "person");
            values.put("creators[0].role", "author");
        }

        values.put("expressions[0].id", suggestExpressionId(translator, language, translated));
        if (language != null) {
            values.put("expressions[0].language", language.code());
        }
        values.put("expressions[0].kind.type", translated ? "translation" : "original");
        if (!translator.isBlank()) {
            values.put("expressions[0].contributors[0].name", translator);
            values.put("expressions[0].contributors[0].kind", "person");
            values.put("expressions[0].contributors[0].role", "translator");
        }

        values.put("manifestation.id", suggestManifestationId(draft, draft.title()));
        values.put("manifestation.title.main", draft.title());
        draft.publisher().ifPresent(publisher -> values.put("manifestation.publisher", publisher));
        values.put("manifestation.publisherKind", "organisation");
        draft.publishedYear().ifPresent(year -> {
            values.put("manifestation.published.type", "year");
            values.put("manifestation.published.year.value", String.valueOf(year));
        });
        draft.pages().ifPresent(pages -> {
            values.put("manifestation.extent.type", "pages");
            values.put("manifestation.extent.pages.count", String.valueOf(pages));
        });
        draft.series().ifPresent(series -> values.put("manifestation.series.name", series));
        draft.seriesNumber().ifPresent(number -> values.put("manifestation.series.number", number));
        return values;
    }

    // ------------------------------------------------------------- reading

    /** Either the four aggregates, or every reason the form cannot become them. */
    sealed interface Outcome {

        record Book(Work work, Manifestation manifestation, Optional<Item> copy) implements Outcome {
        }

        record Rejected(FormState state) implements Outcome {
        }
    }

    Outcome read(FormData form, AgentResolution agents) {
        FormProblems problems = new FormProblems();

        Optional<WorkId> workId = problems.read("id", () -> WorkId.of(form.required("id")));
        Optional<Title> workTitle = problems.read("title.main",
                () -> new Title(form.required("title.main"), form.optional("title.subtitle")));
        var created = problems.read("created.type", () -> VariantForms.readDate(form, "created"));
        var workForm = problems.read("form.type", () -> VariantForms.readWorkForm(form, "form"));
        var creators = problems.read("creators",
                () -> VariantForms.readContributions(form, "creators", agents));

        FormData expressionFields = form.at("expressions", 0);
        Optional<String> localId = problems.read("expressions[0].id", () -> {
            String value = expressionFields.required("id");
            Slug.validate(value, "expression identifier");
            return value;
        });
        var language = problems.read("expressions[0].language",
                () -> new Language(expressionFields.required("language")));
        var kind = problems.read("expressions[0].kind.type",
                () -> VariantForms.readExpressionKind(expressionFields, "kind"));
        var contributors = problems.read("expressions[0].contributors",
                () -> VariantForms.readContributions(expressionFields, "contributors", agents));

        FormData edition = form.scope("manifestation.");
        Optional<ManifestationId> editionId = problems.read("manifestation.id",
                () -> ManifestationId.of(edition.required("id")));
        var published = problems.read("manifestation.published.type",
                () -> VariantForms.readDate(edition, "published"));
        var carrier = problems.read("manifestation.carrier.type",
                () -> VariantForms.readCarrier(edition, "carrier"));
        var identifier = problems.read("manifestation.identifier.type",
                () -> VariantForms.readIdentifier(edition, "identifier"));
        var extent = problems.read("manifestation.extent.type",
                () -> VariantForms.readExtent(edition, "extent"));
        Optional<AgentId> publisher = edition.optional("publisher").flatMap(name ->
                problems.read("manifestation.publisher", () -> agents.resolve(name,
                        VariantForms.readAgentKind(edition.optional("publisherKind")
                                .orElse("organisation")))));
        Optional<Series> series = edition.optional("series.name").flatMap(name ->
                problems.read("manifestation.series.name",
                        () -> new Series(name, edition.optional("series.number"))));

        if (problems.any() || workId.isEmpty() || localId.isEmpty() || language.isEmpty()
                || kind.isEmpty() || editionId.isEmpty()) {
            return new Outcome.Rejected(FormState.submitted(form, problems));
        }

        ExpressionId expressionId = new ExpressionId(workId.get(), localId.get());
        Optional<Expression> expression = problems.read("expressions[0].id", () -> new Expression(
                expressionId, kind.get(), language.get(), contributors.orElse(List.of()), created.get()));

        Set<String> subjects = form.optional("subjects").stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Optional<Work> work = expression.flatMap(realisation -> problems.read("title.main",
                () -> new Work(workId.get(), workTitle.orElseThrow(), creators.orElse(List.of()),
                        workForm.get(), created.get(), subjects, List.of(realisation))));

        Optional<Manifestation> manifestation = problems.read("manifestation.id",
                () -> new Manifestation(
                        editionId.get(), List.of(expressionId),
                        new Title(edition.optional("title.main")
                                .orElse(workTitle.map(Title::main).orElse("Untitled")),
                                edition.optional("title.subtitle")),
                        publisher, published.get(), carrier.get(), identifier.get(), extent.get(),
                        series, edition.optionalInt("edition")));

        Optional<Item> copy = Optional.empty();
        if (form.checked("addItem") && manifestation.isPresent()) {
            FormData item = form.scope("item.");
            ManifestationId owner = manifestation.get().id();
            copy = problems.read("item.id", () -> new Item(
                    ItemId.of(item.optional("id").orElseGet(() -> owner.value() + "-1")),
                    owner,
                    VariantForms.readAcquisition(item, "acquisition"),
                    VariantForms.readLocation(item, "location"),
                    ReadingProgress.UNREAD,
                    Condition.valueOf(item.optional("condition").orElse(Condition.UNGRADED.name())),
                    item.optional("notes")));
            if (copy.isEmpty()) {
                return new Outcome.Rejected(FormState.submitted(form, problems));
            }
        }

        if (problems.any() || work.isEmpty() || manifestation.isEmpty()) {
            return new Outcome.Rejected(FormState.submitted(form, problems));
        }
        return new Outcome.Book(work.get(), manifestation.get(), copy);
    }

    // --------------------------------------------------- id suggestions

    private static String suggestWorkId(String author, String title) {
        if (title.isBlank()) {
            return "";
        }
        String surname = author.isBlank() ? "" : Slug.of(lastWord(author)) + "-";
        return surname + Slug.of(title);
    }

    /** "grossman-en" for a translation, "original-fr" otherwise. */
    private static String suggestExpressionId(String translator, Language language, boolean translated) {
        String code = language == null ? "xx" : language.code();
        if (translated && !translator.isBlank()) {
            return Slug.of(lastWord(translator)) + "-" + code;
        }
        return (translated ? "translation-" : "original-") + code;
    }

    private static String suggestManifestationId(BookDraft draft, String title) {
        List<String> parts = new ArrayList<>();
        if (!title.isBlank()) {
            parts.add(Slug.of(title));
        }
        draft.publisher().ifPresent(publisher -> parts.add(Slug.of(lastWord(publisher))));
        draft.publishedYear().ifPresent(year -> parts.add(String.valueOf(year)));
        return parts.isEmpty() ? "" : String.join("-", parts);
    }

    private static String lastWord(String value) {
        String[] words = value.trim().split("\\s+");
        return words[words.length - 1];
    }
}
