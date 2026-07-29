package be.imgn.alexandria.site;

import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.infrastructure.Escape;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the catalogue as a static site: one page listing every Work, one page per Work
 * showing the full WEMI descent, and a JSON index the page filters in the browser.
 *
 * <p>No server, no build step in the page — the output is meant to be committed by CI and
 * served by GitHub Pages.
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

    private String indexPage() {
        String rows = catalog.works().stream().map(work -> """
                <li class="entry" data-id="%s">
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
                Escape.html(holdingsSummary(work)))).collect(Collectors.joining());

        return shell("The library", ".", """
                <h1>The library</h1>
                <p class="lede">%d works, %d expressions, %d manifestations, %d items.</p>
                <input type="search" id="q" placeholder="Search title, author, translator, publisher, subject…"
                       autocomplete="off" autofocus>
                <p class="count" id="count"></p>
                <ul class="entries" id="entries">%s</ul>
                <p class="empty" id="empty" hidden>Nothing matches.</p>
                """.formatted(
                catalog.works().size(),
                catalog.works().stream().mapToInt(w -> w.expressions().size()).sum(),
                catalog.manifestations().size(),
                catalog.items().size(),
                rows));
    }

    private String workPage(Work work) {
        String expressions = work.expressions().stream().map(expression -> """
                <section class="expression">
                  <h3>%s</h3>
                  %s
                  %s
                </section>
                """.formatted(
                Escape.html(expression.describe()),
                contributors(expression.contributors()),
                editionsOf(expression))).collect(Collectors.joining());

        String subjects = work.subjects().isEmpty() ? "" : """
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
     * One page per agent, listing everything they are credited on, sectioned by the name
     * each book was published under — so arriving here from "Megan Lindholm" shows both the
     * Lindholm books and the Robin Hobb ones, and arriving from "Robin Hobb" shows the same
     * page.
     */
    private String agentPage(Agent agent) {
        StringBuilder sections = new StringBuilder();
        var byName = catalog.creditsByName(agent.id());

        byName.forEach((name, credits) -> {
            String works = credits.stream()
                    .sorted(java.util.Comparator.comparing(c -> c.work().title().main()))
                    .map(credit -> """
                            <li><a href="../works/%s.html">%s</a>
                              <span class="detail">%s · %s</span></li>
                            """.formatted(
                            Escape.html(credit.work().id().value()),
                            Escape.html(credit.work().title().full()),
                            Escape.html(credit.role().label()),
                            Escape.html(credit.work().created().display())))
                    .collect(Collectors.joining());
            sections.append("""
                    <section class="as">
                      <h2>as %s%s</h2>
                      <ul class="works">%s</ul>
                    </section>
                    """.formatted(
                    Escape.html(name),
                    name.equals(agent.name()) ? "" : " <span class=\"detail\">— other name</span>",
                    works));
        });

        List<Manifestation> published = catalog.publishedBy(agent.id());
        if (!published.isEmpty()) {
            String editions = published.stream().map(edition -> """
                    <li>%s <span class="detail">%s</span></li>
                    """.formatted(
                    Escape.html(edition.title().full()),
                    Escape.html(edition.imprint(agents)))).collect(Collectors.joining());
            sections.append("""
                    <section class="as"><h2>published</h2><ul class="works">%s</ul></section>
                    """.formatted(editions));
        }

        if (sections.isEmpty()) {
            sections.append("<p class=\"none\">Nothing in the catalogue credits this agent.</p>");
        }

        String otherNames = agent.aliases().isEmpty() ? "" : """
                <p class="meta">also known as %s</p>
                """.formatted(Escape.html(String.join(", ", agent.aliases())));

        return shell(agent.name(), "..", """
                <p class="crumb"><a href="../index.html">The library</a></p>
                <h1>%s</h1>
                <p class="meta">%s</p>
                %s
                %s
                """.formatted(
                Escape.html(agent.name()),
                Escape.html(agent.kind().label()),
                otherNames,
                sections));
    }

    private String editionsOf(Expression expression) {
        List<Manifestation> editions = catalog.manifestationsOf(expression.id());
        if (editions.isEmpty()) {
            return "<p class=\"none\">No edition held.</p>";
        }
        return editions.stream().map(edition -> """
                <div class="edition">
                  <p class="imprint">%s%s</p>
                  %s
                  %s
                </div>
                """.formatted(
                Escape.html(edition.imprint(agents)),
                edition.identifier().display().isEmpty()
                        ? "" : " · " + Escape.html(edition.identifier().display()),
                edition.series().map(s -> "<p class=\"series\">" + Escape.html(s.display()) + "</p>").orElse(""),
                copiesOf(edition))).collect(Collectors.joining());
    }

    private String copiesOf(Manifestation edition) {
        List<Item> copies = catalog.copiesOf(edition.id());
        if (copies.isEmpty()) {
            return "<p class=\"none\">Not held.</p>";
        }
        return copies.stream().map(copy -> """
                <p class="copy">%s · %s%s</p>
                """.formatted(
                Escape.html(copy.reading().display()),
                Escape.html(copy.location().display()),
                copy.notes().map(n -> " · " + Escape.html(n)).orElse(""))).collect(Collectors.joining());
    }

    private String contributors(List<Contribution> contributions) {
        if (contributions.isEmpty()) {
            return "";
        }
        return "<p class=\"contributors\">" + contributions.stream()
                .map(c -> agentLink(c.agent(), c.publishedAs()) + " (" + Escape.html(c.role().label()) + ")")
                .collect(Collectors.joining(", ")) + "</p>";
    }

    // ------------------------------------------------------------ search

    /**
     * One entry per Work, carrying every string a reader might search by — including the
     * translator, publisher and shelf of copies below it, so that "Grossman" or "Penguin"
     * finds the Work even though neither word appears in its title.
     *
     * <p>Every alias an agent is registered under goes in too: someone who knows the author
     * as "U. K. Le Guin" should not have to guess the form the catalogue prefers.
     */
    private String searchIndex() {
        String entries = catalog.works().stream().map(work -> {
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
                    {"id":%s,"title":%s,"author":%s,"text":%s}""".formatted(
                    json(work.id().value()),
                    json(work.title().full()),
                    json(work.byline()),
                    json(String.join(" ", terms).toLowerCase(java.util.Locale.ROOT)));
        }).collect(Collectors.joining(",\n  "));
        return "[\n  " + entries + "\n]\n";
    }

    /** The author line, each name linking to the agent behind it. */
    private String bylineLinks(Work work) {
        List<Contribution> authors = work.creators().stream()
                .filter(c -> c.role().equals(be.imgn.alexandria.domain.shared.Role.AUTHOR))
                .toList();
        if (authors.isEmpty()) {
            return "Anonymous";
        }
        return authors.stream()
                .map(c -> agentLink(c.agent(), c.publishedAs()))
                .collect(Collectors.joining(", "));
    }

    /** Links a credit to its agent page while showing the name the book was published under. */
    private String agentLink(AgentId agent, String publishedAs) {
        if (agents.find(agent).isEmpty()) {
            return Escape.html(publishedAs);
        }
        return "<a href=\"../agents/" + Escape.html(agent.value()) + ".html\">"
                + Escape.html(publishedAs) + "</a>";
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

    private static String shell(String title, String root, String body) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s — Alexandria</title>
                  <link rel="stylesheet" href="%s/catalog.css">
                </head>
                <body>
                  <main>
                %s
                  </main>
                  <script src="%s/catalog.js"></script>
                </body>
                </html>
                """.formatted(Escape.html(title), root, body, root);
    }

    private String holdingsSummary(Work work) {
        long editions = work.expressions().stream()
                .mapToLong(e -> catalog.manifestationsOf(e.id()).size()).sum();
        long copies = work.expressions().stream()
                .flatMap(e -> catalog.manifestationsOf(e.id()).stream())
                .mapToLong(m -> catalog.copiesOf(m.id()).size()).sum();
        String languages = work.expressions().stream()
                .map(e -> e.language().displayName())
                .distinct()
                .collect(Collectors.joining(", "));
        return languages + " · " + editions + " edition" + (editions == 1 ? "" : "s")
                + " · " + copies + " cop" + (copies == 1 ? "y" : "ies");
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
