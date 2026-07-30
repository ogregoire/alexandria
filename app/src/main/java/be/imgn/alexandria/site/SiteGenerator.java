package be.imgn.alexandria.site;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.infrastructure.Escape;

/**
 * Renders the catalogue as a static site: one page listing every Work, one page per Work showing the full WEMI descent,
 * and a JSON index the page filters in the browser.
 *
 * <p>No server, no build step in the page — the output is meant to be committed by CI and served by GitHub Pages.
 */
public final class SiteGenerator {

    private final Catalog catalog;
    private final AgentDirectory agents;

    public SiteGenerator(Catalog catalog) {
        this.catalog = catalog;
        this.agents = catalog.directory();
    }

    /** Writes the site and returns the files it produced. */
    public List<Path> generateInto(Path output) {
        ReferentialIntegrity.enforce(catalog);
        List<Path> written = new ArrayList<>();
        try {
            Files.createDirectories(output.resolve("works"));
            Files.createDirectories(output.resolve("agents"));
            written.add(write(output.resolve("index.html"), indexPage()));
            written.add(write(output.resolve("search-index.json"), searchIndex()));
            written.add(write(output.resolve("tokens.css"), resource("/tokens.css")));
            written.add(write(output.resolve("catalog.css"), resource("/site/catalog.css")));
            written.add(write(output.resolve("catalog.js"), resource("/site/catalog.js")));
            for (Work work : catalog.works()) {
                written.add(write(output.resolve("works/" + work.id().value() + ".html"), workPage(work)));
            }
            for (Agent agent : catalog.agents()) {
                written.add(write(output.resolve("agents/" + agent.id().value() + ".html"), agentPage(agent)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot generate the site into " + output, e);
        }
        return List.copyOf(written);
    }

    // ------------------------------------------------------------- pages

    /**
     * The index is the whole site: one search field over two tracks. Agents are a track of their own rather than a
     * detail of the works, because "who translated this" is as good a way into a catalogue as "what do I own" — and
     * because an agent page that nothing links to may as well not have been generated.
     */
    private String indexPage() {
        String works = catalog.works().stream()
                .map(work -> """
                <li class="entry" data-id="work:%s">
                  <a class="title" href="works/%s.html">%s</a>
                  <p class="meta">%s · %s · %s</p>
                  <p class="holdings">%s</p>
                </li>
                """.formatted(
                                Escape.html(work.id().value()),
                                Escape.html(work.id().value()),
                                Escape.html(work.title().full()),
                                Escape.html(work.byline()),
                                Escape.html(work.created().display()),
                                Escape.html(work.form().label()),
                                Escape.html(holdingsSummary(work))))
                .collect(Collectors.joining());

        String people = catalog.agents().stream()
                .sorted(Comparator.comparing(Agent::sortName))
                .map(agent -> """
                <li class="person" data-id="agent:%s">
                  <a href="agents/%s.html">%s</a>
                  <span class="role">%s</span>
                </li>
                """.formatted(
                                Escape.html(agent.id().value()),
                                Escape.html(agent.id().value()),
                                Escape.html(agent.name()),
                                Escape.html(standingOf(agent))))
                .collect(Collectors.joining());

        return shell("The library", ".", """
                <form class="find" role="search" onsubmit="return false">
                  <label for="q">Search</label>
                  <input type="search" id="q" autocomplete="off" autofocus
                         placeholder="A title, a name, a publisher, a subject, a shelf…">
                  <p class="count" id="count"></p>
                </form>
                <section class="track" data-track="works" data-one="work">
                  <h2>Works</h2>
                  <ul class="entries">%s</ul>
                </section>
                <section class="track" data-track="agents" data-one="agent">
                  <h2>Agents</h2>
                  <ul class="entries">%s</ul>
                </section>
                <p class="empty" id="empty" hidden>Nothing matches.</p>
                """.formatted(works, people), true);
    }

    /** "1 work", "12 works" — a catalogue holding one of something should not read as a bug. */
    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /** What an agent is in this catalogue: the roles they are credited in, or their kind when nothing credits them. */
    private String standingOf(Agent agent) {
        List<String> roles = new ArrayList<>(catalog.creditsOf(agent.id()).stream()
                .map(credit -> credit.role().label())
                .distinct()
                .sorted()
                .toList());
        if (!catalog.publishedBy(agent.id()).isEmpty()) {
            roles.add("publisher");
        }
        return roles.isEmpty() ? agent.kind().label() : String.join(", ", roles);
    }

    private String workPage(Work work) {
        String expressions = work.expressions().stream()
                .map(expression -> """
                <section class="expression">
                  <h3>%s</h3>
                  %s
                  %s
                </section>
                """.formatted(
                                Escape.html(expression.describe()),
                                contributors(expression.contributors()),
                                editionsOf(expression)))
                .collect(Collectors.joining());

        String subjects = work.subjects().isEmpty()
                ? ""
                : """
                <p class="subjects">%s</p>
                """.formatted(work.subjects().stream()
                        .map(s -> "<span class=\"tag\">" + Escape.html(s) + "</span>")
                        .collect(Collectors.joining(" ")));

        return shell(work.title().main(), "..", """
                <p class="crumb"><a href="../index.html">The library</a></p>
                <h1>%s</h1>
                <p class="meta">%s · %s · %s</p>
                %s
                %s
                """.formatted(
                        Escape.html(work.title().full()),
                        bylineLinks(work),
                        Escape.html(work.created().display()),
                        Escape.html(work.form().label()),
                        subjects,
                        expressions));
    }

    /**
     * One page per agent, listing everything they are credited on, sectioned by the name each book was published under
     * — so arriving here from "Megan Lindholm" shows both the Lindholm books and the Robin Hobb ones, and arriving from
     * "Robin Hobb" shows the same page.
     */
    private String agentPage(Agent agent) {
        StringBuilder sections = new StringBuilder();
        var byName = catalog.creditsByName(agent.id());

        byName.forEach((name, credits) -> {
            String works = credits.stream()
                    .sorted(Comparator.comparing(c -> c.work().title().main()))
                    .map(credit -> """
                            <li><a href="../works/%s.html">%s</a>
                              <span class="detail">%s · %s</span></li>
                            """.formatted(
                                    Escape.html(credit.work().id().value()),
                                    Escape.html(credit.work().title().full()),
                                    Escape.html(credit.role().label()
                                            + credit.realisation()
                                                    .map(language -> " · " + language)
                                                    .orElse("")),
                                    Escape.html(credit.when().display())))
                    .collect(Collectors.joining());
            // "as Robin Hobb" earns its heading only when there is more than one name to tell
            // apart. On someone who published under one name it just says their name twice.
            String heading = byName.size() == 1
                    ? ""
                    : "<h2>as %s%s</h2>"
                            .formatted(
                                    Escape.html(name),
                                    name.equals(agent.name()) ? "" : " <span class=\"detail\">— other name</span>");
            sections.append("""
                    <section class="as">
                      %s
                      <ul class="works">%s</ul>
                    </section>
                    """.formatted(heading, works));
        });

        List<Manifestation> published = catalog.publishedBy(agent.id());
        if (!published.isEmpty()) {
            String editions = published.stream()
                    .map(edition ->
                            """
                    <li>%s <span class="detail">%s</span></li>
                    """.formatted(Escape.html(edition.title().full()), Escape.html(edition.imprint(agents))))
                    .collect(Collectors.joining());
            sections.append("""
                    <section class="as"><h2>published</h2><ul class="works">%s</ul></section>
                    """.formatted(editions));
        }

        if (sections.isEmpty()) {
            sections.append("<p class=\"none\">Nothing in the catalogue credits this agent.</p>");
        }

        String otherNames =
                agent.aliases().isEmpty() ? "" : """
                <p class="meta">also known as %s</p>
                """.formatted(Escape.html(String.join(", ", agent.aliases())));

        return shell(agent.name(), "..", """
                <p class="crumb"><a href="../index.html">The library</a></p>
                <h1>%s</h1>
                <p class="meta">%s</p>
                %s
                %s
                """.formatted(
                        Escape.html(agent.name()), Escape.html(agent.kind().label()), otherNames, sections));
    }

    private String editionsOf(Expression expression) {
        List<Manifestation> editions = catalog.manifestationsOf(expression.id());
        if (editions.isEmpty()) {
            return "<p class=\"none\">No edition held.</p>";
        }
        return editions.stream()
                .map(edition -> """
                <div class="edition">
                  <p class="imprint">%s%s</p>
                  %s
                  %s
                </div>
                """.formatted(
                        Escape.html(edition.imprint(agents)),
                        edition.identifier().display().isEmpty()
                                ? ""
                                : " · " + Escape.html(edition.identifier().display()),
                        edition.series()
                                .map(s -> "<p class=\"series\">" + Escape.html(s.display()) + "</p>")
                                .orElse(""),
                        copiesOf(edition)))
                .collect(Collectors.joining());
    }

    private String copiesOf(Manifestation edition) {
        List<Item> copies = catalog.copiesOf(edition.id());
        if (copies.isEmpty()) {
            return "<p class=\"none\">Not held.</p>";
        }
        return copies.stream()
                .map(copy -> """
                <p class="copy">%s · %s%s</p>
                """.formatted(
                                Escape.html(copy.reading().display()),
                                Escape.html(copy.location().display()),
                                copy.notes().map(n -> " · " + Escape.html(n)).orElse("")))
                .collect(Collectors.joining());
    }

    private String contributors(List<Contribution> contributions) {
        if (contributions.isEmpty()) {
            return "";
        }
        return "<p class=\"contributors\">"
                + contributions.stream()
                        .map(c -> agentLink(c.agent(), c.publishedAs()) + " ("
                                + Escape.html(c.role().label()) + ")")
                        .collect(Collectors.joining(", "))
                + "</p>";
    }

    // ------------------------------------------------------------ search

    /**
     * One entry per Work, carrying every string a reader might search by — including the translator, publisher and
     * shelf of copies below it, so that "Grossman" or "Penguin" finds the Work even though neither word appears in its
     * title.
     *
     * <p>Every alias an agent is registered under goes in too: someone who knows the author as "U. K. Le Guin" should
     * not have to guess the form the catalogue prefers.
     */
    private String searchIndex() {
        String workEntries = catalog.works().stream()
                .map(work -> {
                    Set<String> terms = new LinkedHashSet<>();
                    terms.add(work.title().full());
                    terms.add(work.byline());
                    terms.add(work.form().label());
                    terms.add(work.created().display());
                    terms.addAll(work.subjects());
                    work.creators().forEach(c -> addAgent(terms, c.agent()));
                    for (Expression expression : work.expressions()) {
                        terms.add(expression.language().displayName());
                        terms.add(expression.describe());
                        expression.contributors().forEach(c -> addAgent(terms, c.agent()));
                        for (Manifestation edition : catalog.manifestationsOf(expression.id())) {
                            terms.add(edition.title().full());
                            edition.publisher().ifPresent(publisher -> addAgent(terms, publisher));
                            edition.series().ifPresent(s -> terms.add(s.display()));
                            terms.add(edition.identifier().display());
                            terms.add(edition.carrier().label());
                            catalog.copiesOf(edition.id()).forEach(copy -> {
                                terms.add(copy.location().display());
                                terms.add(copy.reading().display());
                            });
                        }
                    }
                    return """
                    {"id":%s,"text":%s}""".formatted(
                                    json("work:" + work.id().value()),
                                    json(String.join(" ", terms).toLowerCase(Locale.ROOT)));
                })
                .collect(Collectors.joining(",\n  "));

        String agentEntries = catalog.agents().stream()
                .sorted(Comparator.comparing(Agent::sortName))
                .map(agent -> {
                    Set<String> terms = new LinkedHashSet<>();
                    agent.names().forEach(terms::add);
                    terms.add(agent.sortName());
                    terms.add(agent.kind().label());
                    terms.add(standingOf(agent));
                    // The titles they are credited on, so "who translated the Ring" reaches the person too.
                    catalog.creditsOf(agent.id()).forEach(credit -> {
                        terms.add(credit.work().title().full());
                        terms.add(credit.publishedAs());
                        credit.realisation().ifPresent(terms::add);
                    });
                    catalog.publishedBy(agent.id())
                            .forEach(edition -> terms.add(edition.title().full()));
                    return """
                    {"id":%s,"text":%s}""".formatted(
                                    json("agent:" + agent.id().value()),
                                    json(String.join(" ", terms).toLowerCase(Locale.ROOT)));
                })
                .collect(Collectors.joining(",\n  "));

        String all = agentEntries.isEmpty() ? workEntries : workEntries + ",\n  " + agentEntries;
        return "[\n  " + all + "\n]\n";
    }

    /** The author line, each name linking to the agent behind it. */
    private String bylineLinks(Work work) {
        List<Contribution> authors = work.creators().stream()
                .filter(c -> c.role().equals(Role.AUTHOR))
                .toList();
        if (authors.isEmpty()) {
            return "Anonymous";
        }
        return authors.stream().map(c -> agentLink(c.agent(), c.publishedAs())).collect(Collectors.joining(", "));
    }

    /** Links a credit to its agent page while showing the name the book was published under. */
    private String agentLink(AgentId agent, String publishedAs) {
        if (agents.find(agent).isEmpty()) {
            return Escape.html(publishedAs);
        }
        return "<a href=\"../agents/" + Escape.html(agent.value()) + ".html\">" + Escape.html(publishedAs) + "</a>";
    }

    /** Adds every name the agent answers to: preferred form, filing form and aliases. */
    private void addAgent(Set<String> terms, AgentId agent) {
        terms.add(agents.sortNameOf(agent));
        agents.find(agent).ifPresent(known -> known.names().forEach(terms::add));
    }

    private static String json(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // ------------------------------------------------------------ layout

    /**
     * The masthead names the catalogue on every page and the colophon closes it with what the catalogue actually is — a
     * count of records and the file they came from. Neither invents anything: both are read off the catalogue.
     */
    private String shell(String title, String root, String body) {
        return shell(title, root, body, false);
    }

    /**
     * @param wordmarkLeads true on the index, where the catalogue's own name is the page's subject and so its
     *     {@code h1}. On a record page the record is the subject, so the wordmark steps down to a paragraph and the
     *     record keeps the {@code h1} — headings stay in order either way.
     */
    private String shell(String title, String root, String body, boolean wordmarkLeads) {
        int expressions =
                catalog.works().stream().mapToInt(w -> w.expressions().size()).sum();
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                  <title>%s — Alexandria</title>
                  <link rel="stylesheet" href="%s/catalog.css">
                </head>
                <body>
                  <header class="mast">
                    <%s class="mast-name"><a href="%s/index.html">Alexandria</a></%s>
                    <p class="mast-line">%s · %s</p>
                    <hr class="mast-rule" aria-hidden="true">
                  </header>
                  <main>
                %s
                  </main>
                  <footer class="colophon">
                    <p>%s · %s · %s · %s · %s.
                       A personal catalogue after IFLA-LRM, kept as JSON and rendered without a database.</p>
                  </footer>
                  <script src="%s/catalog.js"></script>
                </body>
                </html>
                """.formatted(
                        Escape.html(title),
                        root,
                        wordmarkLeads ? "h1" : "p",
                        root,
                        wordmarkLeads ? "h1" : "p",
                        count(catalog.works().size(), "work"),
                        count(catalog.agents().size(), "agent"),
                        body,
                        count(catalog.works().size(), "work"),
                        count(expressions, "expression"),
                        count(catalog.manifestations().size(), "manifestation"),
                        count(catalog.items().size(), "item"),
                        count(catalog.agents().size(), "agent"),
                        root);
    }

    private String holdingsSummary(Work work) {
        long editions = work.expressions().stream()
                .mapToLong(e -> catalog.manifestationsOf(e.id()).size())
                .sum();
        long copies = work.expressions().stream()
                .flatMap(e -> catalog.manifestationsOf(e.id()).stream())
                .mapToLong(m -> catalog.copiesOf(m.id()).size())
                .sum();
        String languages = work.expressions().stream()
                .map(e -> e.language().displayName())
                .distinct()
                .collect(Collectors.joining(", "));
        return languages + " · " + editions + " edition" + (editions == 1 ? "" : "s") + " · " + copies + " cop"
                + (copies == 1 ? "y" : "ies");
    }

    private static Path write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = SiteGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing bundled resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
