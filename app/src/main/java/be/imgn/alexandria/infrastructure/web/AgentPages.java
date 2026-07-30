package be.imgn.alexandria.infrastructure.web;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.infrastructure.Template;
import be.imgn.alexandria.infrastructure.VariantNames;

/**
 * The agent registry: authors, translators, narrators and publishing houses, each held once with the names it answers
 * to.
 */
final class AgentPages {

    private final CatalogService service;

    AgentPages(CatalogService service) {
        this.service = service;
    }

    String list() {
        var usage = service.agentUsage();
        List<List<String>> rows = service.catalog().agents().stream()
                .map(agent -> List.of(
                        Html.link("/agents/" + agent.id().value(), agent.name()),
                        Html.escape(agent.sortName()),
                        Html.escape(agent.kind().label()),
                        Html.escape(String.join(" · ", agent.aliases())),
                        usage.getOrDefault(agent.id(), 0) == 0
                                ? "<span class=\"broken\">0</span>"
                                : String.valueOf(usage.get(agent.id()))))
                .toList();
        return Html.page("Agents", Html.link("/", "Home") + " / Agents", """
                <h1>Agents</h1>
                <p class="hint">One record per person or organisation. Aliases resolve to the same
                   record, so "Penguin" and "Penguin Classics" need not be two publishers.
                   A zero in the last column means nothing refers to it — usually a typo.</p>
                <p><a class="button" href="/agents/new">New agent</a></p>
                %s
                """.formatted(
                        Html.table(List.of("Name", "Files under", "Kind", "Aliases", "References"), rows)));
    }

    /** A blank form for a record that does not exist yet. */
    String edit() {
        return edit((Agent) null);
    }

    String edit(Agent agent) {
        String heading = agent == null ? "New agent" : agent.name();
        String id = agent == null ? "" : agent.id().value();

        String references = agent == null ? "" : referencesOf(agent.id());
        String deleteButton = agent == null ? "" : """
                <form method="post" action="/agents/%s/delete" class="danger"
                      onsubmit="return confirm('Delete this agent?')">
                  <button type="submit">Delete agent</button>
                </form>
                """.formatted(Html.escape(id));

        return Html.page(
                heading,
                Html.link("/agents", "Agents") + " / " + Html.escape(heading),
                Template.of("""
                <h1>{heading}</h1>
                <form method="post" action="/agents/{action}">
                  <fieldset><legend>Agent</legend>
                    {id}
                    {name}
                    {sortName}
                    {kind}
                    {aliases}
                  </fieldset>
                  <button type="submit">Save</button>
                </form>
                {references}
                {delete}
                """)
                        .with("heading", heading)
                        .with("action", id.isEmpty() ? "new" : id)
                        .withMarkup(
                                "id",
                                agent == null
                                        ? Html.textField("id", "Identifier (slug, blank to derive from the name)", "")
                                        : WorkPages.readOnly("Identifier", id) + WorkPages.hidden("id", id))
                        .withMarkup("name", Html.textField("name", "Name", agent == null ? "" : agent.name()))
                        .withMarkup(
                                "sortName",
                                Html.textField("sortName", "Files under", agent == null ? "" : agent.sortName()))
                        .withMarkup(
                                "kind",
                                Html.select(
                                        "kind",
                                        "Kind",
                                        VariantForms.agentKinds(),
                                        agent == null ? "person" : VariantNames.of(agent.kind())))
                        .withMarkup(
                                "aliases",
                                Html.textArea(
                                        "aliases",
                                        "Aliases, one per line",
                                        agent == null ? "" : String.join("\n", agent.aliases())))
                        .withMarkup("references", references)
                        .withMarkup("delete", deleteButton)
                        .render());
    }

    /**
     * The bibliography, split by the name each book was published under. One agent, so the whole output is here; one
     * section per pseudonym, so each book is still credited the way it was issued.
     */
    private String referencesOf(AgentId id) {
        var byName = service.catalog().creditsByName(id);
        var published = service.catalog().publishedBy(id);
        if (byName.isEmpty() && published.isEmpty()) {
            return "<p class=\"hint\">Nothing refers to this agent yet.</p>";
        }

        String preferred = service.catalog().agent(id).map(Agent::name).orElse("");
        StringBuilder out = new StringBuilder();
        byName.forEach((name, credits) -> {
            out.append("<h2>As ")
                    .append(Html.escape(name))
                    .append(name.equals(preferred) ? "" : " <span class=\"hint\">(other name)</span>")
                    .append("</h2><ul>");
            credits.stream()
                    .sorted(Comparator.comparing(c -> c.work().title().main()))
                    .forEach(credit -> out.append("<li>")
                            .append(Html.link(
                                    "/works/" + credit.work().id().value(),
                                    credit.work().title().main()))
                            .append(" <span class=\"hint\">")
                            .append(Html.escape(credit.role().label()))
                            .append(credit.realisation()
                                    .map(language -> " · " + Html.escape(language))
                                    .orElse(""))
                            .append(" · ")
                            .append(Html.escape(credit.when().display()))
                            .append("</span></li>"));
            out.append("</ul>");
        });

        if (!published.isEmpty()) {
            out.append("<h2>Published</h2><ul>");
            published.forEach(manifestation -> out.append("<li>")
                    .append(Html.link(
                            "/manifestations/" + manifestation.id().value(),
                            manifestation.title().main()))
                    .append(" <span class=\"hint\">")
                    .append(Html.escape(manifestation.published().display()))
                    .append("</span></li>"));
            out.append("</ul>");
        }
        return out.toString();
    }

    Agent read(FormData form) {
        String name = form.required("name");
        AgentId id = form.optional("id").map(AgentId::of).orElseGet(() -> AgentId.forName(name));
        Set<String> aliases = form.optional("aliases").stream()
                .flatMap(value -> Arrays.stream(value.split("\\R")))
                .map(String::trim)
                .filter(alias -> !alias.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new Agent(
                id,
                VariantForms.readAgentKind(form.orEmpty("kind")),
                name,
                form.optional("sortName").orElse(name),
                aliases);
    }
}
