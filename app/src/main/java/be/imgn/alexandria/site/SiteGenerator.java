package be.imgn.alexandria.site;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import be.imgn.alexandria.application.Reports;
import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.catalog.Credit;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.TitleFormat;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.infrastructure.Escape;
import be.imgn.alexandria.infrastructure.Template;
import be.imgn.alexandria.infrastructure.VariantNames;

/**
 * Renders the catalogue as a static site: a searchable index of the editions held and the agents behind them, a page
 * per edition, work and agent, and a JSON index the page filters in the browser.
 *
 * <p>No server, no build step in the page — the output is meant to be committed by CI and served by GitHub Pages.
 */
public final class SiteGenerator {

    /**
     * Articles a title is filed past rather than under.
     *
     * <p>Definite and indefinite articles only. French "de" and "du" look like the others and are not articles but
     * prepositions — filing <em>Du côté de chez Swann</em> under "côté" would be wrong. Forms ending in an apostrophe
     * are not followed by a space; the rest are.
     */
    private static final List<String> NON_FILING = List.of(
            "the", "a", "an", "le", "la", "les", "l'", "l’", "un", "une", "der", "die", "das", "ein", "eine", "el",
            "los", "las", "il", "lo", "gli", "het", "een");

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
            Files.createDirectories(output.resolve("editions"));
            Files.createDirectories(output.resolve("agents"));
            written.add(write(output.resolve("index.html"), indexPage()));
            written.add(write(output.resolve("statistics.html"), statisticsPage()));
            written.add(write(output.resolve("search-index.json"), searchIndex()));
            written.add(write(output.resolve("tokens.css"), resource("/tokens.css")));
            written.add(write(output.resolve("catalog.css"), resource("/site/catalog.css")));
            written.add(write(output.resolve("catalog.js"), resource("/site/catalog.js")));
            for (Work work : catalog.works()) {
                written.add(write(output.resolve("works/" + work.id().value() + ".html"), workPage(work)));
            }
            for (Manifestation edition : catalog.manifestations()) {
                written.add(write(output.resolve("editions/" + edition.id().value() + ".html"), editionPage(edition)));
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
     * The index is the whole site: one search field over one alphabetical list holding editions and agents together.
     *
     * <p>Editions rather than works, because an edition is the thing on the shelf — a personal catalogue is a record of
     * what was bought and read, not of abstractions. The work is still reachable, from the edition that embodies it.
     *
     * <p>Agents are listed beside them rather than beneath, because "who translated this" is as good a way into a
     * catalogue as "what do I own" — and because an agent page nothing links to may as well not have been generated.
     * Interleaving the two is what makes one field enough: there is no track to choose first.
     *
     * <p>The list is delivered whole and hidden by the stylesheet until something is typed — hidden by the stylesheet
     * and not by the script, so it is never painted and then snatched away. Delivering it rather than building rows
     * from the search index on demand is what keeps the page working with no JavaScript at all: the marker the
     * stylesheet keys on is set by the script itself, so when the script is absent the catalogue is simply all there,
     * in order.
     */
    private String indexPage() {
        String rows = Stream.concat(
                        catalog.manifestations().stream().map(this::editionRow),
                        catalog.agents().stream().map(this::agentRow))
                .sorted(Comparator.comparing(Row::filing))
                .map(Row::html)
                .collect(Collectors.joining());

        return shell("The library", ".", """
                <form class="find" role="search" onsubmit="return false">
                  <label for="q">Search</label>
                  <input type="search" id="q" autocomplete="off" autofocus
                         placeholder="A title, a name, a publisher, a subject, a shelf…">
                  <p class="count" id="count" role="status"></p>
                </form>
                <ul class="entries" id="entries">%s</ul>
                <p class="empty" id="empty" hidden>Nothing matches.</p>
                """.formatted(rows), true);
    }

    /**
     * An edition's title, punctuated for the language it was printed in — French holds a space before the colon that
     * separates a subtitle, English closes it up. The language is the one the expression it embodies is in.
     *
     * <p>A work's title gets the cataloguing form instead: nothing in the model records what language a work is in, and
     * guessing would be worse than the neutral punctuation the standard prescribes for exactly this reason.
     */
    private String titleOf(Manifestation edition) {
        Locale locale = edition.embodies().stream()
                .map(reference -> catalog.work(reference.work()).flatMap(work -> work.expression(reference)))
                .flatMap(Optional::stream)
                .findFirst()
                .map(expression -> Locale.forLanguageTag(expression.language().code()))
                .orElse(Locale.ROOT);
        return TitleFormat.display(edition.title(), locale);
    }

    /** One line of the index, carrying the key it files under. */
    private record Row(String filing, String html) {}

    /**
     * A held edition, which is the thing on the shelf: the index lists what is owned rather than the abstract works
     * behind it. The work's own title is carried in the line beneath whenever the edition renamed it, so searching "The
     * Eye of the World" still reaches the French printing of it.
     */
    private Row editionRow(Manifestation edition) {
        List<Work> behind = worksBehind(edition);
        String byline = behind.stream().map(Work::byline).distinct().collect(Collectors.joining(", "));
        String original = behind.stream()
                .map(work -> TitleFormat.isbd(work.title()))
                .filter(title -> !title.equals(titleOf(edition)))
                .distinct()
                .collect(Collectors.joining(", "));

        return new Row(
                filing(edition.title().main()),
                Template.of("""
                        <li class="entry" data-id="edition:{id}">
                          <a class="title" href="editions/{id}">{title}</a>
                          <p class="meta">{byline} · {imprint}</p>
                          <p class="holdings">{original}{copies}</p>
                        </li>
                        """)
                        .with("id", edition.id().value())
                        .with("title", titleOf(edition))
                        .with("byline", byline.isEmpty() ? "Anonymous" : byline)
                        .with("imprint", edition.imprint(agents))
                        .withMarkup("original", original.isEmpty() ? "" : Escape.html(original) + " · ")
                        .with("copies", copiesSummary(edition))
                        .render());
    }

    /** The works an edition embodies — several, when it is an omnibus. */
    private List<Work> worksBehind(Manifestation edition) {
        return edition.embodies().stream()
                .map(reference -> catalog.work(reference.work()))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private String copiesSummary(Manifestation edition) {
        int copies = catalog.copiesOf(edition.id()).size();
        return copies == 0 ? "not held" : count(copies, "copy", "copies");
    }

    private Row agentRow(Agent agent) {
        return new Row(filing(agent.sortName()), """
                <li class="entry person" data-id="agent:%s">
                  <a class="title" href="agents/%s">%s</a>
                  <p class="meta">%s</p>
                </li>
                """.formatted(
                        Escape.html(agent.id().value()),
                        Escape.html(agent.id().value()),
                        Escape.html(agent.name()),
                        Escape.html(standingOf(agent))));
    }

    /**
     * The key a line files under: lowercased, stripped of accents so "Mallé" sits with "Malle", and without the article
     * a title happens to open with.
     *
     * <p>A catalogue files <em>The Eye of the World</em> under E and <em>L'Œil du monde</em> under O. Sorting on the
     * displayed string instead would gather half the shelf under T and L, which is why library catalogues have always
     * dropped the non-filing article.
     */
    private static String filing(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        for (String article : NON_FILING) {
            if (article.endsWith("'")) {
                if (plain.startsWith(article)) {
                    return plain.substring(article.length()).trim();
                }
            } else if (plain.startsWith(article + " ")) {
                return plain.substring(article.length() + 1).trim();
            }
        }
        return plain;
    }

    /** "1 work", "12 works" — a catalogue holding one of something should not read as a bug. */
    private static String count(int n, String noun) {
        return count(n, noun, noun + "s");
    }

    private static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /**
     * The one page that counts things.
     *
     * <p>Everything here is read off the catalogue and nothing is inferred: a distribution with one entry says so
     * rather than being padded out, and a shelf of two books is allowed to look like a shelf of two books.
     */
    private String statisticsPage() {
        Map<String, Long> counts = Reports.counts(catalog);

        String tallies = Stream.of("works", "expressions", "manifestations", "items", "agents")
                .map(kind -> """
                        <li><span class="figure">%d</span> <span class="of">%s</span></li>
                        """.formatted(counts.getOrDefault(kind, 0L), Escape.html(kind)))
                .collect(Collectors.joining());

        String languages = distribution(
                "Languages",
                "the expressions, by the language they realise the work in",
                catalog.works().stream()
                        .flatMap(work -> work.expressions().stream())
                        .collect(Collectors.groupingBy(
                                expression -> expression.language().displayName(),
                                TreeMap::new,
                                Collectors.counting())));

        String forms = distribution(
                "Forms",
                "the works, by what kind of thing they are",
                catalog.works().stream()
                        .collect(Collectors.groupingBy(
                                work -> work.form().label(), TreeMap::new, Collectors.counting())));

        String carriers = distribution(
                "Carriers",
                "the editions, by what they were printed or pressed as",
                catalog.manifestations().stream()
                        .collect(Collectors.groupingBy(
                                edition -> edition.carrier().label(), TreeMap::new, Collectors.counting())));

        String reading = distribution(
                "Reading",
                "the copies, by how far through them I am",
                catalog.items().stream()
                        .collect(Collectors.groupingBy(
                                copy -> VariantNames.of(copy.reading()), TreeMap::new, Collectors.counting())));

        return shell("Statistics", ".", """
                <p class="crumb"><a href="./">The library</a></p>
                <h1>What is in it</h1>
                <ul class="tallies">%s</ul>
                %s
                %s
                %s
                %s
                """.formatted(tallies, languages, forms, carriers, reading));
    }

    /**
     * One named distribution, or nothing at all when there is none — an empty table tells the reader less than a gap.
     */
    private static String distribution(String heading, String explanation, Map<String, Long> tally) {
        if (tally.isEmpty()) {
            return "";
        }
        long total = tally.values().stream().mapToLong(Long::longValue).sum();
        return Template.of("""
                <section class="stat">
                  <h2>{heading}</h2>
                  <p class="hint">{explanation}</p>
                  <table><tbody>{#each rows}<tr>
                          <th scope="row">{name}</th>
                          <td>{count}</td>
                          <td class="bar"><span style="--share: {share}%" aria-hidden="true"></span></td>
                        </tr>
                        {/each}</tbody></table>
                </section>
                """)
                .with("heading", heading)
                .with("explanation", explanation)
                .each(
                        "rows",
                        tally.entrySet(),
                        (row, entry) -> row.with("name", entry.getKey())
                                .with("count", Math.toIntExact(entry.getValue()))
                                .with("share", (int) Math.round(entry.getValue() * 100.0 / total)))
                .render();
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

    /**
     * One page per edition: what was printed, by whom, in what shape, and which copies of it are on the shelf.
     *
     * <p>The expressions it embodies are listed rather than folded away, because that list is the whole reason a
     * Manifestation is its own aggregate root — an omnibus embodies expressions of several works and cannot belong to
     * any one of them. Each links up to the work behind it.
     */
    private String editionPage(Manifestation edition) {
        String contents = edition.embodies().stream()
                .map(reference -> catalog.work(reference.work())
                        .flatMap(work -> work.expression(reference).map(expression -> """
                                <li>
                                  <a href="../works/%s">%s</a>
                                  <span class="detail">%s</span>
                                </li>
                                """.formatted(
                                        Escape.html(work.id().value()),
                                        Escape.html(TitleFormat.isbd(work.title())),
                                        Escape.html(expression.describe()))))
                        .orElse(""))
                .collect(Collectors.joining());

        // The components, not the imprint: the imprint is the same facts run together for a
        // one-line summary, and printing both would say everything twice.
        String publisher = edition.publisher()
                .agent()
                .flatMap(agents::find)
                .map(Agent::name)
                .orElse("");
        String facts = Stream.of(
                        fact("Publisher", publisher),
                        fact("Published", edition.published().display()),
                        fact("Carrier", edition.carrier().label()),
                        fact("Extent", edition.extent().display()),
                        fact("Series", edition.series().display()),
                        fact("Identifier", edition.identifier().display()))
                .filter(row -> !row.isEmpty())
                .collect(Collectors.joining());

        List<Item> copies = catalog.copiesOf(edition.id());

        // The byline, which the facts table has no row for: the author belongs beside the title,
        // the way it sits on a cover.
        String byline =
                worksBehind(edition).stream().map(this::bylineLinks).distinct().collect(Collectors.joining(", "));

        return shell(
                edition.title().main(),
                "..",
                Template.of("""
                <p class="crumb"><a href="../">The library</a></p>
                <h1>{title}</h1>
                <p class="meta">{byline}</p>
                <table class="facts"><tbody>{facts}</tbody></table>
                {#if credited}{madeIt}{/if}
                <section class="as">
                  <h2>{contentsHeading}</h2>
                  <ul class="works">{contents}</ul>
                </section>
                <section class="as">
                  <h2>On the shelf</h2>
                  {#if held}{#each copies}<p class="copy">{reading} · {where}{note}</p>
                  {/each}{#else}<p class="none">No copy of this edition is held.</p>
                  {/if}
                </section>
                """)
                        .with("title", titleOf(edition))
                        .withMarkup("byline", byline.isEmpty() ? "Anonymous" : byline)
                        .withMarkup("facts", facts)
                        // Whoever made this printing rather than the text — a cover artist.
                        .when("credited", !edition.contributors().isEmpty())
                        .withMarkup("madeIt", contributors(edition.contributors()))
                        .with("contentsHeading", edition.embodies().size() == 1 ? "What it is" : "What it collects")
                        .withMarkup("contents", contents)
                        .when("held", !copies.isEmpty())
                        .each(
                                "copies",
                                copies,
                                (row, copy) -> row.with(
                                                "reading", copy.reading().display())
                                        .with("where", copy.location().display())
                                        .withMarkup(
                                                "note",
                                                copy.notes().text().isEmpty()
                                                        ? ""
                                                        : " · "
                                                                + Escape.html(copy.notes()
                                                                        .text())))
                        .render());
    }

    private static String fact(String name, String value) {
        return value == null || value.isBlank() ? "" : """
                <tr><th scope="row">%s</th><td>%s</td></tr>
                """.formatted(Escape.html(name), Escape.html(value));
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
                <p class="crumb"><a href="../">The library</a></p>
                <h1>%s</h1>
                <p class="meta">%s · %s · %s</p>
                %s
                %s
                """.formatted(
                        Escape.html(TitleFormat.isbd(work.title())),
                        bylineLinks(work),
                        Escape.html(work.created().display()),
                        Escape.html(work.form().label()),
                        subjects,
                        expressions));
    }

    /**
     * Where a credit points. One on a work or on an expression of it goes to the work; one on a printing goes to that
     * edition, because a cover belongs to the printing and an omnibus has no single work to send it to.
     */
    private static String creditHref(Credit credit) {
        return switch (credit) {
            case Credit.OnWork(Work work, var ignoredRole, var ignoredAs) ->
                "../works/" + Escape.html(work.id().value());
            case Credit.OnExpression(Work work, var ignoredExpression, var ignoredRole, var ignoredAs) ->
                "../works/" + Escape.html(work.id().value());
            case Credit.OnEdition(Manifestation edition, var ignoredRole, var ignoredAs) ->
                "../editions/" + Escape.html(edition.id().value());
        };
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
                    .sorted(Comparator.comparing(Credit::subject))
                    .map(credit -> """
                            <li><a href="%s">%s</a>
                              <span class="detail">%s · %s</span></li>
                            """.formatted(
                                    creditHref(credit),
                                    Escape.html(credit.subject()),
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
                    .map(edition -> """
                    <li><a href="../editions/%s">%s</a> <span class="detail">%s</span></li>
                    """.formatted(
                                    Escape.html(edition.id().value()),
                                    Escape.html(titleOf(edition)),
                                    Escape.html(edition.imprint(agents))))
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
                <p class="crumb"><a href="../">The library</a></p>
                <h1>%s</h1>
                <p class="meta">%s</p>
                %s
                %s
                """.formatted(
                        Escape.html(agent.name()), Escape.html(agent.kind().label()), otherNames, sections));
    }

    /**
     * The titles a credit was actually sold under.
     *
     * <p>A work is catalogued under the title it was written as and sold under the one on the cover, and for a
     * translation those are different sentences: "New Spring" against "Nouveau Printemps : La Préquelle de la Roue du
     * temps". Indexing an agent by the catalogued title alone meant searching the words on the cover reached the
     * publisher and the cover artist — whose credits happen to sit on the printing — while the author and the
     * translator, whose credits sit above it, were missing from their own book.
     */
    private Stream<String> soldAs(Credit credit) {
        return switch (credit) {
            case Credit.OnWork(Work work, var ignoredRole, var ignoredAs) ->
                work.expressions().stream()
                        .flatMap(expression -> catalog.manifestationsOf(expression.id()).stream())
                        .map(this::titleOf);
            case Credit.OnExpression(var ignoredWork, Expression expression, var ignoredRole, var ignoredAs) ->
                catalog.manifestationsOf(expression.id()).stream().map(this::titleOf);
            case Credit.OnEdition(Manifestation edition, var ignoredRole, var ignoredAs) -> Stream.of(titleOf(edition));
        };
    }

    private String editionsOf(Expression expression) {
        List<Manifestation> editions = catalog.manifestationsOf(expression.id());
        if (editions.isEmpty()) {
            return "<p class=\"none\">No edition held.</p>";
        }
        return editions.stream()
                // Reading order, which is not alphabetical order and not the order they were catalogued in.
                .sorted(Comparator.comparingInt(
                                (Manifestation edition) -> edition.series().position())
                        .thenComparing(this::titleOf))
                .map(edition -> Template.of("""
                        <div class="edition">
                          <p class="edition-title"><a href="../editions/{id}">{title}</a></p>
                          <p class="imprint">{imprint}{identifier}</p>
                          {series}
                          {copies}
                        </div>
                        """)
                        .with("id", edition.id().value())
                        // The edition's own title, which is the one fact about it the work page
                        // could not otherwise show: a translation is usually sold under a name
                        // the work never had, and naming the link after the imprint hid it.
                        .with("title", titleOf(edition))
                        .with("imprint", edition.imprint(agents))
                        .withMarkup(
                                "identifier",
                                edition.identifier().display().isEmpty()
                                        ? ""
                                        : " · "
                                                + Escape.html(
                                                        edition.identifier().display()))
                        .withMarkup(
                                "series",
                                edition.series().display().isEmpty()
                                        ? ""
                                        : "<p class=\"series\">"
                                                + Escape.html(edition.series().display()) + "</p>")
                        .withMarkup("copies", copiesOf(edition))
                        .render())
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
                                copy.notes().text().isEmpty()
                                        ? ""
                                        : " · " + Escape.html(copy.notes().text())))
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
     * One entry per edition, carrying every string a reader might search by — the work behind it, its author, the
     * translator of the expression it embodies, the publisher, the series, the ISBN, and the shelf its copies sit on.
     * So "Grossman" or "Penguin" finds the edition even though neither word is on its title page, and the work's
     * original title finds the printing that renamed it.
     *
     * <p>Every alias an agent is registered under goes in too: someone who knows the author as "U. K. Le Guin" should
     * not have to guess the form the catalogue prefers.
     */
    private String searchIndex() {
        String editionEntries = catalog.manifestations().stream()
                .map(edition -> {
                    Set<String> terms = new LinkedHashSet<>();
                    terms.add(titleOf(edition));
                    edition.publisher().agent().ifPresent(publisher -> addAgent(terms, publisher));
                    terms.add(edition.series().display());
                    terms.add(edition.identifier().display());
                    terms.add(edition.carrier().label());
                    terms.add(edition.published().display());
                    for (var reference : edition.embodies()) {
                        catalog.work(reference.work()).ifPresent(work -> {
                            terms.add(TitleFormat.isbd(work.title()));
                            terms.add(work.byline());
                            terms.add(work.form().label());
                            terms.add(work.created().display());
                            terms.addAll(work.subjects());
                            work.creators().forEach(c -> addAgent(terms, c.agent()));
                            work.expression(reference).ifPresent(expression -> {
                                terms.add(expression.language().displayName());
                                terms.add(expression.describe());
                                expression.contributors().forEach(c -> addAgent(terms, c.agent()));
                            });
                        });
                    }
                    catalog.copiesOf(edition.id()).forEach(copy -> {
                        terms.add(copy.location().display());
                        terms.add(copy.reading().display());
                    });
                    return """
                    {"id":%s,"title":%s,"text":%s}""".formatted(
                                    json("edition:" + edition.id().value()),
                                    // The work's title counts as a heading match too: someone searching
                                    // "The Eye of the World" means this book, whatever the cover calls it.
                                    json((titleOf(edition) + " "
                                                    + worksBehind(edition).stream()
                                                            .map(work -> TitleFormat.isbd(work.title()))
                                                            .collect(Collectors.joining(" ")))
                                            .toLowerCase(Locale.ROOT)),
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
                        terms.add(credit.subject());
                        terms.add(credit.publishedAs());
                        credit.realisation().ifPresent(terms::add);
                        soldAs(credit).forEach(terms::add);
                    });
                    catalog.publishedBy(agent.id()).forEach(edition -> terms.add(titleOf(edition)));
                    return """
                    {"id":%s,"title":%s,"text":%s}""".formatted(
                                    json("agent:" + agent.id().value()),
                                    // Every name the agent answers to is a heading match: arriving by an
                                    // alias should rank them as highly as arriving by the name on the spine.
                                    json(Stream.concat(agent.names(), Stream.of(agent.sortName()))
                                            .collect(Collectors.joining(" "))
                                            .toLowerCase(Locale.ROOT)),
                                    json(String.join(" ", terms).toLowerCase(Locale.ROOT)));
                })
                .collect(Collectors.joining(",\n  "));

        String all = Stream.of(editionEntries, agentEntries)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(",\n  "));
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
        return "<a href=\"../agents/" + Escape.html(agent.value()) + "\">" + Escape.html(publishedAs) + "</a>";
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
     * The masthead names the catalogue on every page; the colophon says what it is and points at the one page where the
     * numbers live. The counts used to sit in both, on every page, where they were noise — a reader looking at a book
     * does not need to be told how many books there are.
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
        String wordmark = wordmarkLeads ? "h1" : "p";
        return Template.of("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                  <title>{title} — Alexandria</title>
                  <link rel="stylesheet" href="{root}/catalog.css">
                  <!-- In the head and not deferred: the script's first line marks the document
                       so the stylesheet can hide the list before it is ever painted. -->
                  <script src="{root}/catalog.js"></script>
                </head>
                <body>
                  <header class="mast">
                    <{wordmark} class="mast-name"><a href="{root}/">Alexandria</a></{wordmark}>
                    <hr class="mast-rule" aria-hidden="true">
                  </header>
                  <main>
                {body}
                  </main>
                  <footer class="colophon">
                    <p>A personal catalogue after IFLA-LRM, kept as JSON and rendered without a
                       database. <a href="{root}/statistics">What is in it</a>.</p>
                  </footer>
                </body>
                </html>
                """)
                .with("title", title)
                .withMarkup("root", root)
                .withMarkup("wordmark", wordmark)
                .withMarkup("body", body)
                .render();
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
