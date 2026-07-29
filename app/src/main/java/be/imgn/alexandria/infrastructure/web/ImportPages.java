package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentKind;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.Slug;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkForm;
import be.imgn.alexandria.domain.work.WorkId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
 * the Manifestation embodying it, and optionally the Item that is your copy.
 */
final class ImportPages {

    private final CatalogService service;
    private final BookLookup lookup;

    ImportPages(CatalogService service, BookLookup lookup) {
        this.service = service;
        this.lookup = lookup;
    }

    /** The starting point: an ISBN and whether a copy is being shelved. */
    String ask(String isbn, String problem) {
        String note = problem == null || problem.isBlank() ? "" :
                "<p class=\"error\">" + Html.escape(problem) + "</p>";
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
                Html.textField("isbn", "ISBN-10 or ISBN-13", isbn == null ? "" : isbn)));
    }

    /**
     * The prefilled form. Rendered whether or not the lookup found anything: a miss simply
     * means every field starts empty and the ISBN is still carried through.
     */
    String review(Identifier isbn, Optional<BookDraft> found, boolean addItem) {
        AgentDirectory agents = service.directory();
        BookDraft draft = found.orElse(null);

        String provenance = found
                .map(d -> "<p class=\"ok\">Prefilled from " + Html.escape(d.source())
                        + ". Check every field before saving.</p>")
                .orElse("<p class=\"error\">No catalogue had this ISBN. "
                        + "The form is empty — fill it in by hand.</p>");

        String author = draft == null ? "" : draft.authors().stream().findFirst().orElse("");
        String translator = draft == null ? "" : draft.translators().stream().findFirst().orElse("");
        String workTitle = draft == null ? "" : draft.workTitle();
        String editionTitle = draft == null ? "" : draft.title();
        Language language = draft == null ? null : draft.language().orElse(null);
        boolean translated = draft != null && draft.looksTranslated();

        return Html.page("Check and save", Html.link("/import", "Add from ISBN") + " / Check", """
                <h1>Check and save</h1>
                %s
                %s
                <form method="post" action="/import/save">
                  %s
                  <fieldset><legend>Work — the text itself, in any language</legend>
                    %s
                    %s
                    %s
                    %s
                    %s
                  </fieldset>
                  %s
                  <fieldset><legend>Expression — this language, this translation</legend>
                    %s
                    %s
                    %s
                    %s
                  </fieldset>
                  <fieldset><legend>Manifestation — this edition</legend>
                    %s
                    %s
                    %s
                    %s
                    %s
                    %s
                    %s
                    %s
                    %s
                  </fieldset>
                  <fieldset><legend>Item — your copy</legend>
                    <label class="check"><input type="checkbox" name="addItem" value="yes"%s>
                      <span>Record a copy of this edition</span></label>
                    %s
                    %s
                    %s
                    %s
                  </fieldset>
                  <button type="submit">Save the book</button>
                </form>
                """.formatted(
                provenance,
                Html.datalist(VariantForms.AGENT_LIST, agents.suggestions()),
                WorkPages.hidden("isbn", isbn.isbnDigits().orElse("")),

                Html.textField("id", "Work identifier (slug)",
                        draft == null ? "" : suggestWorkId(author, workTitle)),
                Html.textField("title.main", "Title in the original language", workTitle),
                Html.textField("title.subtitle", "Subtitle",
                        draft == null ? "" : draft.subtitle().orElse("")),
                VariantForms.workForm("form", "Form", WorkForm.NOVEL),
                VariantForms.date("created", "First published",
                        draft == null ? BibliographicDate.UNKNOWN : yearOrUnknown(draft.originalYear())),

                VariantForms.contributions("creators", "Author",
                        author.isBlank() ? List.of() : List.of(pretend(author, agents, Role.AUTHOR)), agents),

                Html.textField("expressions[0].id", "Expression identifier (slug)",
                        draft == null ? "" : suggestExpressionId(translator, language, translated)),
                Html.textField("expressions[0].language", "Language code",
                        language == null ? "" : language.code()),
                VariantForms.expressionKind("expressions[0].kind", "Kind",
                        translated ? new ExpressionKind.Translation(Language.ENGLISH)
                                : new ExpressionKind.Original()),
                VariantForms.contributions("expressions[0].contributors", "Translator and others",
                        translator.isBlank() ? List.of()
                                : List.of(pretend(translator, agents, Role.TRANSLATOR)), agents),

                Html.textField("manifestation.id", "Edition identifier (slug)",
                        draft == null ? "" : suggestManifestationId(draft, editionTitle)),
                Html.textField("manifestation.title.main", "Title on this edition", editionTitle),
                Html.suggestField("manifestation.publisher", "Publisher",
                        draft == null ? "" : draft.publisher().orElse(""), VariantForms.AGENT_LIST),
                Html.select("manifestation.publisherKind", "If new", VariantForms.agentKinds(),
                        "organisation"),
                VariantForms.date("manifestation.published", "Printed",
                        draft == null ? BibliographicDate.UNKNOWN : yearOrUnknown(draft.publishedYear())),
                VariantForms.carrier("manifestation.carrier", "Carrier", Carrier.PAPERBACK),
                VariantForms.identifier("manifestation.identifier", "Identifier", isbn),
                VariantForms.extent("manifestation.extent", "Extent",
                        draft == null ? Extent.UNSPECIFIED
                                : draft.pages().<Extent>map(Extent.Pages::new).orElse(Extent.UNSPECIFIED)),
                Html.textField("manifestation.series.name", "Series",
                        draft == null ? "" : draft.series().orElse(""))
                        + Html.textField("manifestation.series.number", "Series number",
                        draft == null ? "" : draft.seriesNumber().orElse("")),

                addItem ? " checked" : "",
                Html.textField("item.id", "Copy identifier (slug, blank to derive)", ""),
                VariantForms.acquisition("item.acquisition", "Acquired", null),
                VariantForms.location("item.location", "Location", null),
                Html.select("item.condition", "Condition", VariantForms.conditions(),
                        Condition.UNGRADED.name())));
    }

    /**
     * A contribution for a name that may not be registered yet, purely so the form can
     * render it. It never reaches the catalogue — {@link #read} resolves the typed name for
     * real.
     */
    private static Contribution pretend(String name, AgentDirectory agents, Role role) {
        AgentId id = agents.resolve(name).map(agent -> agent.id()).orElseGet(() -> AgentId.forName(name));
        return new Contribution(id, role, name);
    }

    private static BibliographicDate yearOrUnknown(Optional<Integer> year) {
        return year.<BibliographicDate>map(BibliographicDate.Year::new).orElse(BibliographicDate.UNKNOWN);
    }

    // ------------------------------------------------------------- reading

    /** Everything the review form submitted, as the four aggregates it describes. */
    record NewBook(Work work, Manifestation manifestation, Optional<Item> copy) {
    }

    NewBook read(FormData form, AgentResolution agents) {
        WorkId workId = WorkId.of(form.required("id"));
        String localExpressionId = form.at("expressions", 0).required("id");
        ExpressionId expressionId = new ExpressionId(workId, localExpressionId);

        FormData expressionFields = form.at("expressions", 0);
        Expression expression = new Expression(
                expressionId,
                VariantForms.readExpressionKind(expressionFields, "kind"),
                new Language(expressionFields.required("language")),
                VariantForms.readContributions(expressionFields, "contributors", agents),
                VariantForms.readDate(form, "created"));

        Set<String> subjects = form.optional("subjects").stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Work work = new Work(
                workId,
                new Title(form.required("title.main"), form.optional("title.subtitle")),
                VariantForms.readContributions(form, "creators", agents),
                VariantForms.readWorkForm(form, "form"),
                VariantForms.readDate(form, "created"),
                subjects,
                List.of(expression));

        FormData edition = form.scope("manifestation.");
        Optional<Series> series = edition.optional("series.name")
                .map(name -> new Series(name, edition.optional("series.number")));

        Manifestation manifestation = new Manifestation(
                ManifestationId.of(edition.required("id")),
                List.of(expressionId),
                new Title(edition.optional("title.main").orElse(work.title().main()),
                        edition.optional("title.subtitle")),
                edition.optional("publisher").map(name -> agents.resolve(
                        name, VariantForms.readAgentKind(edition.optional("publisherKind")
                                .orElse("organisation")))),
                VariantForms.readDate(edition, "published"),
                VariantForms.readCarrier(edition, "carrier"),
                VariantForms.readIdentifier(edition, "identifier"),
                VariantForms.readExtent(edition, "extent"),
                series,
                edition.optionalInt("edition"));

        Optional<Item> copy = Optional.empty();
        if (form.checked("addItem")) {
            FormData item = form.scope("item.");
            copy = Optional.of(new Item(
                    ItemId.of(item.optional("id").orElseGet(() -> manifestation.id().value() + "-1")),
                    manifestation.id(),
                    VariantForms.readAcquisition(item, "acquisition"),
                    VariantForms.readLocation(item, "location"),
                    ReadingProgress.UNREAD,
                    Condition.valueOf(item.optional("condition").orElse(Condition.UNGRADED.name())),
                    item.optional("notes")));
        }
        return new NewBook(work, manifestation, copy);
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
