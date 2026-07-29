package be.imgn.alexandria.application;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentKind;
import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.Work;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The questions worth asking of a personal library, answered over the aggregates.
 *
 * <p>These were SQL against a projected copy of the catalogue. The projection was 476 lines
 * whose only job was to make this file possible, and everything else in the application had
 * long since gone back to walking the aggregates directly, so the joining moved into
 * {@link Holding} and the database went away.
 */
public final class Reports {

    /** A report's identity and prose, without computing it. */
    public record Definition(String id, String title, String explanation) {
    }

    /** A computed report, ready for a table. */
    public record Table(Definition definition, List<String> columns, List<List<String>> rows) {
    }

    private record Spec(Definition definition, Function<Catalog, Table> compute) {
    }

    private static final List<Spec> SPECS = specs();

    private Reports() {
    }

    public static List<Definition> index() {
        return SPECS.stream().map(Spec::definition).toList();
    }

    public static Optional<Table> compute(Catalog catalog, String id) {
        return SPECS.stream()
                .filter(spec -> spec.definition().id().equals(id))
                .findFirst()
                .map(spec -> spec.compute().apply(catalog));
    }

    // ------------------------------------------------------------ the reports

    private static List<Spec> specs() {
        List<Spec> specs = new ArrayList<>();

        specs.add(report("unread", "Unread copies",
                "Copies on the shelf that have never been started.",
                List.of("Work", "Expression", "Edition", "Where"),
                catalog -> byReading(catalog, ReadingProgress.Unread.class).stream()
                        .map(h -> List.of(
                                h.work().title().main(),
                                h.expression().describe(),
                                h.manifestation().imprint(catalog.directory()),
                                h.item().location().display()))
                        .toList()));

        specs.add(report("reading", "Currently reading",
                "Started and not yet finished.",
                List.of("Work", "Progress", "Where"),
                catalog -> byReading(catalog, ReadingProgress.Reading.class).stream()
                        .map(h -> List.of(
                                h.work().title().main(),
                                h.item().reading().display(),
                                h.item().location().display()))
                        .toList()));

        specs.add(report("loans", "Out and in",
                "Copies lent to someone, and copies borrowed that are not yours.",
                List.of("Direction", "Work", "Detail", "Since"),
                catalog -> sorted(Holding.of(catalog)).stream()
                        .filter(h -> h.item().location() instanceof Location.LentTo
                                || !h.item().acquisition().owned())
                        .map(h -> List.of(
                                h.item().acquisition().owned() ? "lent out" : "borrowed",
                                h.work().title().main(),
                                h.item().location().display(),
                                h.item().acquisition().on().map(LocalDate::toString).orElse("")))
                        .toList()));

        specs.add(report("publishers", "Publishers",
                "Which houses the shelf is actually made of.",
                List.of("Publisher", "Editions", "Copies"),
                catalog -> {
                    AgentDirectory agents = catalog.directory();
                    Map<String, long[]> tally = new TreeMap<>();
                    for (Manifestation manifestation : catalog.manifestations()) {
                        manifestation.publisher().ifPresent(publisher -> {
                            long[] counts = tally.computeIfAbsent(agents.nameOf(publisher), k -> new long[2]);
                            counts[0]++;
                            counts[1] += catalog.copiesOf(manifestation.id()).size();
                        });
                    }
                    return tally.entrySet().stream()
                            .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(
                                            e -> -e.getValue()[1])
                                    .thenComparing(Map.Entry::getKey))
                            .map(e -> List.of(e.getKey(),
                                    String.valueOf(e.getValue()[0]),
                                    String.valueOf(e.getValue()[1])))
                            .toList();
                }));

        specs.add(report("people", "People",
                "Everyone in the registry and what they did, aliases included.",
                List.of("Files under", "Name", "Roles", "Works", "Aliases"),
                catalog -> catalog.agents().stream()
                        .filter(agent -> agent.kind() instanceof AgentKind.Person)
                        .map(agent -> {
                            var credits = catalog.creditsOf(agent.id());
                            String roles = credits.stream()
                                    .map(credit -> credit.role().label())
                                    .distinct()
                                    .sorted()
                                    .collect(Collectors.joining(", "));
                            long works = credits.stream().map(c -> c.work().id()).distinct().count();
                            return List.of(
                                    agent.sortName(),
                                    agent.name(),
                                    roles,
                                    String.valueOf(works),
                                    String.join(" · ", agent.aliases()));
                        })
                        .toList()));

        specs.add(report("orphan-agents", "Agents nothing refers to",
                "Usually a name typed once with a typo. Safe to delete.",
                List.of("Name", "Kind"),
                catalog -> catalog.agents().stream()
                        .filter(agent -> catalog.referencesTo(agent.id()).isEmpty())
                        .sorted(Comparator.comparing(Agent::name))
                        .map(agent -> List.of(agent.name(), agent.kind().label()))
                        .toList()));

        specs.add(report("languages", "By language",
                "Which expressions the library actually holds, by language.",
                List.of("Language", "Expressions", "Copies"),
                catalog -> {
                    Map<String, long[]> tally = new TreeMap<>();
                    for (Work work : catalog.works()) {
                        for (Expression expression : work.expressions()) {
                            long[] counts = tally.computeIfAbsent(
                                    expression.language().displayName(), k -> new long[2]);
                            counts[0]++;
                            counts[1] += catalog.manifestationsOf(expression.id()).stream()
                                    .mapToLong(m -> catalog.copiesOf(m.id()).size())
                                    .sum();
                        }
                    }
                    return tally.entrySet().stream()
                            .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(
                                            e -> -e.getValue()[1])
                                    .thenComparing(Map.Entry::getKey))
                            .map(e -> List.of(e.getKey(),
                                    String.valueOf(e.getValue()[0]),
                                    String.valueOf(e.getValue()[1])))
                            .toList();
                }));

        specs.add(report("translations", "Works held in translation",
                "Works where what is on the shelf is not the original language.",
                List.of("Work", "From", "Into", "Expression"),
                catalog -> catalog.works().stream()
                        .flatMap(work -> work.expressions().stream()
                                .filter(e -> e.kind() instanceof ExpressionKind.Translation)
                                .map(e -> List.of(
                                        work.title().main(),
                                        e.kind() instanceof ExpressionKind.Translation(var from)
                                                ? from.displayName() : "",
                                        e.language().displayName(),
                                        e.describe())))
                        .toList()));

        specs.add(report("decades", "By decade",
                "When the works were created, not when the copies were printed.",
                List.of("Decade", "Works"),
                catalog -> {
                    Map<Integer, Long> tally = new TreeMap<>();
                    for (Work work : catalog.works()) {
                        work.created().sortYear().ifPresent(year ->
                                tally.merge(Math.floorDiv(year, 10) * 10, 1L, Long::sum));
                    }
                    return tally.entrySet().stream()
                            .map(e -> List.of(String.valueOf(e.getKey()), String.valueOf(e.getValue())))
                            .toList();
                }));

        specs.add(report("ratings", "Ratings",
                "Everything finished and rated, best first.",
                List.of("Rating", "Work", "Expression", "Finished"),
                catalog -> Holding.of(catalog).stream()
                        .filter(h -> rating(h.item()).isPresent())
                        .sorted(Comparator
                                .comparingInt((Holding h) -> -rating(h.item()).orElseThrow().stars())
                                .thenComparing(h -> finished(h.item()).map(LocalDate::toString).orElse(""),
                                        Comparator.reverseOrder()))
                        .map(h -> List.of(
                                String.valueOf(rating(h.item()).orElseThrow().stars()),
                                h.work().title().main(),
                                h.expression().describe(),
                                finished(h.item()).map(LocalDate::toString).orElse("")))
                        .toList()));

        specs.add(report("shelves", "Shelves",
                "How the physical library is distributed.",
                List.of("Location", "Copies"),
                catalog -> {
                    Map<String, Long> tally = new TreeMap<>();
                    catalog.items().forEach(item ->
                            tally.merge(item.location().display(), 1L, Long::sum));
                    return tally.entrySet().stream()
                            .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(
                                            e -> -e.getValue())
                                    .thenComparing(Map.Entry::getKey))
                            .map(e -> List.of(e.getKey(), String.valueOf(e.getValue())))
                            .toList();
                }));

        specs.add(report("spending", "Spending",
                "What the library cost, by year and currency.",
                List.of("Year", "Currency", "Spent", "Copies"),
                catalog -> {
                    record Bucket(int year, String currency) implements Comparable<Bucket> {
                        @Override
                        public int compareTo(Bucket other) {
                            int byYear = Integer.compare(other.year, year);
                            return byYear != 0 ? byYear : currency.compareTo(other.currency);
                        }
                    }
                    Map<Bucket, BigDecimal> spent = new TreeMap<>();
                    Map<Bucket, Long> copies = new TreeMap<>();
                    for (Item item : catalog.items()) {
                        if (item.acquisition() instanceof Acquisition.Purchased(
                                LocalDate on, Optional<Money> price, var ignored)
                                && price.isPresent()) {
                            Bucket bucket = new Bucket(on.getYear(), price.get().currency().getCurrencyCode());
                            spent.merge(bucket, price.get().amount(), BigDecimal::add);
                            copies.merge(bucket, 1L, Long::sum);
                        }
                    }
                    return spent.entrySet().stream()
                            .map(e -> List.of(
                                    String.valueOf(e.getKey().year()),
                                    e.getKey().currency(),
                                    e.getValue().toPlainString(),
                                    String.valueOf(copies.get(e.getKey()))))
                            .toList();
                }));

        return List.copyOf(specs);
    }

    // ----------------------------------------------------------------- helpers

    private static Spec report(String id, String title, String explanation,
                               List<String> columns, Function<Catalog, List<List<String>>> rows) {
        Definition definition = new Definition(id, title, explanation);
        return new Spec(definition, catalog -> new Table(definition, columns, rows.apply(catalog)));
    }

    private static List<Holding> byReading(Catalog catalog, Class<? extends ReadingProgress> state) {
        return sorted(Holding.of(catalog)).stream()
                .filter(holding -> state.isInstance(holding.item().reading()))
                .toList();
    }

    private static List<Holding> sorted(List<Holding> holdings) {
        return holdings.stream()
                .sorted(Comparator.comparing(h -> h.work().title().main()))
                .toList();
    }

    private static Optional<Rating> rating(Item item) {
        return item.reading() instanceof ReadingProgress.Finished(var ignored, Optional<Rating> rating)
                ? rating
                : Optional.empty();
    }

    private static Optional<LocalDate> finished(Item item) {
        return item.reading() instanceof ReadingProgress.Finished(LocalDate on, var ignored)
                ? Optional.of(on)
                : Optional.empty();
    }

    /** Kept for the home page, which only wants the five counts. */
    public static Map<String, Long> counts(Catalog catalog) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("agents", (long) catalog.agents().size());
        counts.put("works", (long) catalog.works().size());
        counts.put("expressions", catalog.works().stream()
                .mapToLong(work -> work.expressions().size()).sum());
        counts.put("manifestations", (long) catalog.manifestations().size());
        counts.put("items", (long) catalog.items().size());
        return counts;
    }
}
