package be.imgn.alexandria.infrastructure.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.EditionStatement;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.manifestation.Publisher;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.infrastructure.VariantNames;

/** Browsing and editing the Manifestation aggregate — the edition level. */
final class ManifestationPages {

    private final CatalogService service;

    ManifestationPages(CatalogService service) {
        this.service = service;
    }

    String list() {
        AgentDirectory agents = service.directory();
        List<List<String>> rows = service.catalog().manifestations().stream()
                .map(manifestation -> List.of(
                        Html.link(
                                "/manifestations/" + manifestation.id().value(),
                                manifestation.title().main()),
                        manifestation
                                .publisher()
                                .agent()
                                .map(p -> Html.link("/agents/" + p.value(), agents.nameOf(p)))
                                .orElse(""),
                        Html.escape(manifestation.published().display()),
                        Html.escape(manifestation.carrier().label()),
                        Html.escape(manifestation.identifier().display()),
                        String.valueOf(manifestation.embodies().size()),
                        String.valueOf(
                                service.catalog().copiesOf(manifestation.id()).size())))
                .toList();
        return Html.page("Manifestations", Html.link("/", "Home") + " / Manifestations", """
                <h1>Manifestations</h1>
                <p><a class="button" href="/manifestations/new">New manifestation</a></p>
                %s
                """.formatted(Html.table(
                List.of("Title", "Publisher", "Published", "Carrier", "Identifier", "Expressions", "Copies"), rows)));
    }

    String edit(Optional<Manifestation> existing) {
        Manifestation manifestation = existing.orElse(null);
        AgentDirectory agents = service.directory();
        String heading = manifestation == null
                ? "New manifestation"
                : manifestation.title().main();
        String id = manifestation == null ? "" : manifestation.id().value();
        List<String> chosen = manifestation == null
                ? List.of()
                : manifestation.embodies().stream().map(ExpressionId::qualified).toList();

        String deleteButton = manifestation == null ? "" : """
                <form method="post" action="/manifestations/%s/delete" class="danger"
                      onsubmit="return confirm('Delete this manifestation?')">
                  <button type="submit">Delete manifestation</button>
                </form>
                """.formatted(Html.escape(id));

        return Html.page(
                heading, Html.link("/manifestations", "Manifestations") + " / " + Html.escape(heading), """
                <h1>%s</h1>
                %s
                <form method="post" action="/manifestations/%s">
                  <fieldset><legend>Edition</legend>
                    %s
                    %s
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
                  <fieldset><legend>Expressions embodied</legend>
                    <p class="hint">More than one for an omnibus or a bilingual edition.</p>
                    %s
                  </fieldset>
                  <button type="submit">Save</button>
                </form>
                %s
                """.formatted(
                        Html.escape(heading),
                        Html.datalist(VariantForms.AGENT_LIST, agents.suggestions()),
                        Html.escape(id.isEmpty() ? "new" : id),
                        manifestation == null
                                ? Html.textField("id", "Identifier (slug)", "")
                                : WorkPages.readOnly("Identifier", id) + WorkPages.hidden("id", id),
                        Html.textField(
                                "title.main",
                                "Title",
                                manifestation == null
                                        ? ""
                                        : manifestation.title().main()),
                        Html.textField(
                                "title.subtitle",
                                "Subtitle",
                                manifestation != null
                                                && manifestation.title()
                                                        instanceof Title.Subtitled(var ignored, String subtitle)
                                        ? subtitle
                                        : ""),
                        Html.suggestField(
                                "publisher",
                                "Publisher",
                                manifestation == null
                                        ? ""
                                        : manifestation
                                                .publisher()
                                                .agent()
                                                .map(agents::nameOf)
                                                .orElse(""),
                                VariantForms.AGENT_LIST),
                        Html.select(
                                "publisherKind",
                                "If new",
                                VariantForms.agentKinds(),
                                manifestation == null
                                        ? "organisation"
                                        : manifestation
                                                .publisher()
                                                .agent()
                                                .flatMap(agents::find)
                                                .map(agent -> VariantNames.of(agent.kind()))
                                                .orElse("organisation")),
                        VariantForms.date(
                                "published",
                                "Published",
                                manifestation == null ? BibliographicDate.UNKNOWN : manifestation.published()),
                        VariantForms.carrier(
                                "carrier",
                                "Carrier",
                                manifestation == null ? Carrier.PAPERBACK : manifestation.carrier()),
                        VariantForms.identifier(
                                "identifier",
                                "Identifier",
                                manifestation == null ? Identifier.NONE : manifestation.identifier()),
                        VariantForms.extent(
                                "extent",
                                "Extent",
                                manifestation == null ? Extent.UNSPECIFIED : manifestation.extent()),
                        Html.textField(
                                "series.name",
                                "Series",
                                manifestation == null
                                        ? ""
                                        : switch (manifestation.series()) {
                                            case Series.Standalone() -> "";
                                            case Series.Unnumbered(String name) -> name;
                                            case Series.Numbered(String name, var ignored) -> name;
                                        }),
                        Html.textField(
                                "series.number",
                                "Series number",
                                manifestation == null
                                        ? ""
                                        : manifestation.series()
                                                        instanceof Series.Numbered(var ignoredName, String number)
                                                ? number
                                                : ""),
                        Html.numberField(
                                "edition",
                                "Edition number",
                                manifestation == null
                                        ? ""
                                        : manifestation.editionStatement().stored()),
                        expressionCheckboxes(chosen),
                        deleteButton));
    }

    private String expressionCheckboxes(List<String> chosen) {
        Map<String, String> choices = service.expressionChoices();
        if (choices.isEmpty()) {
            return "<p class=\"hint\">No expressions yet — create a work first.</p>";
        }
        return choices.entrySet().stream()
                .map(choice -> """
                        <label class="check"><input type="checkbox" name="embodies" value="%s"%s>
                          <span>%s</span></label>
                        """.formatted(
                                Html.escape(choice.getKey()),
                                chosen.contains(choice.getKey()) ? " checked" : "",
                                Html.escape(choice.getValue())))
                .collect(Collectors.joining());
    }

    Manifestation read(FormData form, AgentResolution agents) {
        List<ExpressionId> embodies =
                form.all("embodies").stream().map(ExpressionId::parse).toList();
        if (embodies.isEmpty()) {
            throw new IllegalArgumentException("a manifestation must embody at least one expression");
        }
        Optional<AgentId> publisher = form.optional("publisher")
                .map(name -> agents.resolve(
                        name,
                        VariantForms.readAgentKind(
                                form.optional("publisherKind").orElse("organisation"))));
        Series series = Series.of(form.orEmpty("series.name"), form.orEmpty("series.number"));

        return new Manifestation(
                ManifestationId.of(form.required("id")),
                embodies,
                Title.of(form.required("title.main"), form.orEmpty("title.subtitle")),
                publisher.map(Publisher::of).orElse(Publisher.UNRECORDED),
                VariantForms.readDate(form, "published"),
                VariantForms.readCarrier(form, "carrier"),
                VariantForms.readIdentifier(form, "identifier"),
                VariantForms.readExtent(form, "extent"),
                series,
                EditionStatement.parse(form.orEmpty("edition")));
    }
}
