package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentKind;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.WorkForm;
import be.imgn.alexandria.infrastructure.VariantNames;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Renders each sealed hierarchy as a form and reads it back.
 *
 * <p>Every {@code read*} method is exhaustive over the variant names produced by
 * {@link VariantNames}, so adding a variant to the domain surfaces here as a form that
 * cannot round-trip it rather than as a silent default.
 */
public final class VariantForms {

    private VariantForms() {
    }

    // ------------------------------------------------------------ dates

    public static String date(String name, String label, BibliographicDate current) {
        Map<String, String> variants = ordered(
                "year", "Year",
                "exact", "Exact date",
                "circa", "Circa",
                "between", "Between two years",
                "unknown", "Unknown");
        String selected = VariantNames.of(current);
        Map<String, String> payloads = new LinkedHashMap<>();
        payloads.put("year", Html.numberField(f(name, "year", "value"), "Year",
                current instanceof BibliographicDate.Year(int value) ? String.valueOf(value) : ""));
        payloads.put("exact", Html.dateField(f(name, "exact", "date"), "Date",
                current instanceof BibliographicDate.Exact(LocalDate on) ? on.toString() : ""));
        payloads.put("circa", Html.numberField(f(name, "circa", "value"), "Approximate year",
                current instanceof BibliographicDate.Circa(int value) ? String.valueOf(value) : ""));
        payloads.put("between",
                Html.numberField(f(name, "between", "from"), "From",
                        current instanceof BibliographicDate.Between(int from, int ignored) ? String.valueOf(from) : "")
                        + Html.numberField(f(name, "between", "to"), "To",
                        current instanceof BibliographicDate.Between(int ignored, int to) ? String.valueOf(to) : ""));
        payloads.put("unknown", "");
        return Html.variantField(name, label, variants, selected, payloads);
    }

    public static BibliographicDate readDate(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "year" -> new BibliographicDate.Year(Integer.parseInt(in.required("value")));
            case "exact" -> new BibliographicDate.Exact(in.requiredDate("date"));
            case "circa" -> new BibliographicDate.Circa(Integer.parseInt(in.required("value")));
            case "between" -> new BibliographicDate.Between(
                    Integer.parseInt(in.required("from")), Integer.parseInt(in.required("to")));
            case "unknown" -> BibliographicDate.UNKNOWN;
            default -> throw unknown("date", variant);
        };
    }

    // ------------------------------------------------------- work form

    public static String workForm(String name, String label, WorkForm current) {
        Map<String, String> variants = ordered(
                "novel", "Novel",
                "novella", "Novella",
                "short-stories", "Short stories",
                "poetry", "Poetry",
                "drama", "Drama",
                "essay", "Essay",
                "nonfiction", "Nonfiction",
                "reference", "Reference",
                "comics", "Comics",
                "other", "Other");
        Map<String, String> payloads = Map.of("other", Html.textField(f(name, "other", "label"), "Form",
                current instanceof WorkForm.Other(String value) ? value : ""));
        return Html.variantField(name, label, variants, VariantNames.of(current), payloads);
    }

    public static WorkForm readWorkForm(FormData form, String name) {
        String variant = form.variant(name);
        return switch (variant) {
            case "novel" -> new WorkForm.Novel();
            case "novella" -> new WorkForm.Novella();
            case "short-stories" -> new WorkForm.ShortStories();
            case "poetry" -> new WorkForm.Poetry();
            case "drama" -> new WorkForm.Drama();
            case "essay" -> new WorkForm.Essay();
            case "nonfiction" -> new WorkForm.Nonfiction();
            case "reference" -> new WorkForm.Reference();
            case "comics" -> new WorkForm.Comics();
            case "other" -> new WorkForm.Other(form.in(name, "other").required("label"));
            default -> throw unknown("work form", variant);
        };
    }

    // ------------------------------------------------- expression kind

    public static String expressionKind(String name, String label, ExpressionKind current) {
        Map<String, String> variants = ordered(
                "original", "Original",
                "translation", "Translation",
                "revision", "Revision",
                "abridgement", "Abridgement",
                "adaptation", "Adaptation",
                "narration", "Narration");
        Map<String, String> payloads = Map.of(
                "translation", Html.textField(f(name, "translation", "from"), "Translated from (language code)",
                        current instanceof ExpressionKind.Translation(Language from) ? from.code() : ""),
                "revision", Html.textField(f(name, "revision", "label"), "Revision label",
                        current instanceof ExpressionKind.Revision(String value) ? value : ""),
                "adaptation", Html.textField(f(name, "adaptation", "into"), "Adapted as",
                        current instanceof ExpressionKind.Adaptation(String into) ? into : ""));
        return Html.variantField(name, label, variants, VariantNames.of(current), payloads);
    }

    public static ExpressionKind readExpressionKind(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "original" -> new ExpressionKind.Original();
            case "translation" -> new ExpressionKind.Translation(new Language(in.required("from")));
            case "revision" -> new ExpressionKind.Revision(in.required("label"));
            case "abridgement" -> new ExpressionKind.Abridgement();
            case "adaptation" -> new ExpressionKind.Adaptation(in.required("into"));
            case "narration" -> new ExpressionKind.Narration();
            default -> throw unknown("expression kind", variant);
        };
    }

    // ------------------------------------------------------- carrier

    public static String carrier(String name, String label, Carrier current) {
        Map<String, String> variants = ordered(
                "paperback", "Paperback",
                "hardcover", "Hardcover",
                "mass-market", "Mass-market paperback",
                "ebook", "Ebook",
                "audiobook", "Audiobook",
                "other", "Other");
        Map<String, String> formats = new LinkedHashMap<>();
        for (Carrier.EbookFormat format : Carrier.EbookFormat.values()) {
            formats.put(format.name(), format.name());
        }
        Map<String, String> payloads = Map.of(
                "ebook", Html.select(f(name, "ebook", "format"), "File format", formats,
                        current instanceof Carrier.Ebook(Carrier.EbookFormat format) ? format.name() : "EPUB"),
                "audiobook", Html.textField(f(name, "audiobook", "medium"), "Medium (CD, download, cassette)",
                        current instanceof Carrier.Audiobook(String medium) ? medium : "download"),
                "other", Html.textField(f(name, "other", "label"), "Carrier",
                        current instanceof Carrier.Other(String value) ? value : ""));
        return Html.variantField(name, label, variants, VariantNames.of(current), payloads);
    }

    public static Carrier readCarrier(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "paperback" -> new Carrier.Paperback();
            case "hardcover" -> new Carrier.Hardcover();
            case "mass-market" -> new Carrier.MassMarket();
            case "ebook" -> new Carrier.Ebook(Carrier.EbookFormat.valueOf(in.required("format")));
            case "audiobook" -> new Carrier.Audiobook(in.required("medium"));
            case "other" -> new Carrier.Other(in.required("label"));
            default -> throw unknown("carrier", variant);
        };
    }

    // ---------------------------------------------------- identifier

    public static String identifier(String name, String label, Identifier current) {
        Map<String, String> variants = ordered(
                "isbn13", "ISBN-13",
                "isbn10", "ISBN-10",
                "asin", "ASIN",
                "custom", "Other scheme",
                "none", "None");
        Map<String, String> payloads = Map.of(
                "isbn13", Html.textField(f(name, "isbn13", "digits"), "ISBN-13",
                        current instanceof Identifier.Isbn13(String digits) ? digits : ""),
                "isbn10", Html.textField(f(name, "isbn10", "digits"), "ISBN-10",
                        current instanceof Identifier.Isbn10(String digits) ? digits : ""),
                "asin", Html.textField(f(name, "asin", "value"), "ASIN",
                        current instanceof Identifier.Asin(String value) ? value : ""),
                "custom", Html.textField(f(name, "custom", "scheme"), "Scheme",
                        current instanceof Identifier.Custom(String scheme, String ignored) ? scheme : "")
                        + Html.textField(f(name, "custom", "value"), "Value",
                        current instanceof Identifier.Custom(String ignored, String value) ? value : ""));
        return Html.variantField(name, label, variants, VariantNames.of(current), payloads);
    }

    public static Identifier readIdentifier(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "isbn13" -> new Identifier.Isbn13(in.required("digits"));
            case "isbn10" -> new Identifier.Isbn10(in.required("digits"));
            case "asin" -> new Identifier.Asin(in.required("value"));
            case "custom" -> new Identifier.Custom(in.required("scheme"), in.required("value"));
            case "none" -> Identifier.NONE;
            default -> throw unknown("identifier", variant);
        };
    }

    // -------------------------------------------------------- extent

    public static String extent(String name, String label, Extent current) {
        Map<String, String> variants = ordered(
                "pages", "Pages",
                "volumes", "Volumes",
                "playtime", "Playing time",
                "unspecified", "Unspecified");
        Duration playtime = current instanceof Extent.Playtime(Duration duration) ? duration : null;
        Map<String, String> payloads = Map.of(
                "pages", Html.numberField(f(name, "pages", "count"), "Pages",
                        current instanceof Extent.Pages(int count) ? String.valueOf(count) : ""),
                "volumes", Html.numberField(f(name, "volumes", "count"), "Volumes",
                        current instanceof Extent.Volumes(int count, int ignored) ? String.valueOf(count) : "")
                        + Html.numberField(f(name, "volumes", "pagesTotal"), "Pages in total",
                        current instanceof Extent.Volumes(int ignored, int total) ? String.valueOf(total) : ""),
                "playtime", Html.numberField(f(name, "playtime", "hours"), "Hours",
                        playtime == null ? "" : String.valueOf(playtime.toHours()))
                        + Html.numberField(f(name, "playtime", "minutes"), "Minutes",
                        playtime == null ? "" : String.valueOf(playtime.toMinutesPart())));
        return Html.variantField(name, label, variants, VariantNames.of(current), payloads);
    }

    public static Extent readExtent(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "pages" -> new Extent.Pages(Integer.parseInt(in.required("count")));
            case "volumes" -> new Extent.Volumes(
                    Integer.parseInt(in.required("count")), Integer.parseInt(in.required("pagesTotal")));
            case "playtime" -> new Extent.Playtime(Duration.ofHours(in.optionalInt("hours").orElse(0))
                    .plusMinutes(in.optionalInt("minutes").orElse(0)));
            case "unspecified" -> Extent.UNSPECIFIED;
            default -> throw unknown("extent", variant);
        };
    }

    // --------------------------------------------------- acquisition

    public static String acquisition(String name, String label, Acquisition current) {
        Map<String, String> variants = ordered(
                "purchased", "Purchased",
                "gift", "Gift",
                "inherited", "Inherited",
                "borrowed", "Borrowed",
                "unrecorded", "Unrecorded");
        Map<String, String> payloads = new LinkedHashMap<>();
        payloads.put("purchased",
                Html.dateField(f(name, "purchased", "date"), "Date",
                        current instanceof Acquisition.Purchased(Optional<LocalDate> on, var p1, var p2)
                                ? on.map(LocalDate::toString).orElse("") : "")
                        + Html.textField(f(name, "purchased", "price"), "Price (e.g. 28.50 EUR)",
                        current instanceof Acquisition.Purchased(var p3, Optional<Money> price, var p4)
                                ? price.map(Money::text).orElse("") : "")
                        + Html.textField(f(name, "purchased", "from"), "Bought from",
                        current instanceof Acquisition.Purchased(var p5, var p6, Optional<String> from)
                                ? from.orElse("") : ""));
        payloads.put("gift",
                Html.dateField(f(name, "gift", "date"), "Date",
                        current instanceof Acquisition.Gift(Optional<LocalDate> on, var g1)
                                ? on.map(LocalDate::toString).orElse("") : "")
                        + Html.textField(f(name, "gift", "from"), "From",
                        current instanceof Acquisition.Gift(var g2, Optional<String> from)
                                ? from.orElse("") : ""));
        payloads.put("inherited",
                Html.dateField(f(name, "inherited", "date"), "Date",
                        current instanceof Acquisition.Inherited(Optional<LocalDate> on, var i1)
                                ? on.map(LocalDate::toString).orElse("") : "")
                        + Html.textField(f(name, "inherited", "from"), "From",
                        current instanceof Acquisition.Inherited(var i2, Optional<String> from)
                                ? from.orElse("") : ""));
        payloads.put("borrowed",
                Html.textField(f(name, "borrowed", "from"), "Lender",
                        current instanceof Acquisition.Borrowed(String from, var b1, var b2)
                                ? from : "")
                        + Html.dateField(f(name, "borrowed", "since"), "Since",
                        current instanceof Acquisition.Borrowed(var b3, Optional<LocalDate> since, var b4)
                                ? since.map(LocalDate::toString).orElse("") : "")
                        + Html.dateField(f(name, "borrowed", "due"), "Due back",
                        current instanceof Acquisition.Borrowed(var b5, var b6, Optional<LocalDate> due)
                                ? due.map(LocalDate::toString).orElse("") : ""));
        payloads.put("unrecorded", "");
        return Html.variantField(name, label, variants, selected(current, "unrecorded"), payloads);
    }

    public static Acquisition readAcquisition(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "purchased" -> new Acquisition.Purchased(
                    in.optionalDate("date"), in.optional("price").map(Money::parse), in.optional("from"));
            case "gift" -> new Acquisition.Gift(in.optionalDate("date"), in.optional("from"));
            case "inherited" -> new Acquisition.Inherited(in.optionalDate("date"), in.optional("from"));
            // The lender stays required; everything else about a loan may be forgotten.
            case "borrowed" -> new Acquisition.Borrowed(
                    in.required("from"), in.optionalDate("since"), in.optionalDate("due"));
            case "unrecorded" -> Acquisition.UNRECORDED;
            default -> throw unknown("acquisition", variant);
        };
    }

    // ------------------------------------------------------ location

    public static String location(String name, String label, Location current) {
        Map<String, String> variants = ordered(
                "shelf", "On a shelf",
                "box", "In a box",
                "lent-to", "Lent out",
                "device", "On a device",
                "missing", "Missing");
        Map<String, String> payloads = new LinkedHashMap<>();
        payloads.put("shelf",
                Html.textField(f(name, "shelf", "name"), "Shelf",
                        current instanceof Location.Shelf(String shelf, var ignored) ? shelf : "")
                        + Html.textField(f(name, "shelf", "position"), "Position",
                        current instanceof Location.Shelf(var ignoredName, Optional<String> position)
                                ? position.orElse("") : ""));
        payloads.put("box", Html.textField(f(name, "box", "label"), "Box",
                current instanceof Location.Box(String value) ? value : ""));
        payloads.put("lent-to",
                Html.textField(f(name, "lent-to", "person"), "Borrower",
                        current instanceof Location.LentTo(String person, var ignored) ? person : "")
                        + Html.dateField(f(name, "lent-to", "since"), "Since",
                        current instanceof Location.LentTo(var l1, Optional<LocalDate> since)
                                ? since.map(LocalDate::toString).orElse("") : ""));
        payloads.put("device", Html.textField(f(name, "device", "name"), "Device or library",
                current instanceof Location.Device(String value) ? value : ""));
        payloads.put("missing", "");
        return Html.variantField(name, label, variants, selected(current, "shelf"), payloads);
    }

    public static Location readLocation(FormData form, String name) {
        String variant = form.variant(name);
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "shelf" -> new Location.Shelf(in.required("name"), in.optional("position"));
            case "box" -> new Location.Box(in.required("label"));
            case "lent-to" -> new Location.LentTo(in.required("person"), in.optionalDate("since"));
            case "device" -> new Location.Device(in.required("name"));
            case "missing" -> Location.MISSING;
            default -> throw unknown("location", variant);
        };
    }

    // ------------------------------------------------------- reading

    public static String reading(String name, String label, ReadingProgress current) {
        Map<String, String> variants = ordered(
                "unread", "Unread",
                "reading", "Reading",
                "finished", "Finished",
                "abandoned", "Abandoned");
        Map<String, String> payloads = new LinkedHashMap<>();
        payloads.put("unread", "");
        payloads.put("reading",
                Html.dateField(f(name, "reading", "since"), "Started",
                        current instanceof ReadingProgress.Reading(Optional<LocalDate> since, var ignored)
                                ? since.map(LocalDate::toString).orElse("") : "")
                        + Html.numberField(f(name, "reading", "page"), "Page",
                        current instanceof ReadingProgress.Reading(var ignoredSince, Optional<Integer> page)
                                ? page.map(String::valueOf).orElse("") : ""));
        payloads.put("finished",
                Html.dateField(f(name, "finished", "on"), "Finished",
                        current instanceof ReadingProgress.Finished(Optional<LocalDate> on, var ignored)
                                ? on.map(LocalDate::toString).orElse("") : "")
                        + Html.select(f(name, "finished", "rating"), "Rating", ratings(),
                        current instanceof ReadingProgress.Finished(var ignoredOn, Optional<Rating> rating)
                                ? rating.map(r -> String.valueOf(r.stars())).orElse("") : ""));
        payloads.put("abandoned",
                Html.dateField(f(name, "abandoned", "on"), "Given up",
                        current instanceof ReadingProgress.Abandoned(Optional<LocalDate> on, var ignored1, var ignored2)
                                ? on.map(LocalDate::toString).orElse("") : "")
                        + Html.numberField(f(name, "abandoned", "atPage"), "At page",
                        current instanceof ReadingProgress.Abandoned(var ignoredOn, Optional<Integer> atPage,
                                var ignored3) ? atPage.map(String::valueOf).orElse("") : "")
                        + Html.textField(f(name, "abandoned", "why"), "Why",
                        current instanceof ReadingProgress.Abandoned(var ignoredOn2, var ignored4, String why)
                                ? why : ""));
        return Html.variantField(name, label, variants, selected(current, "unread"), payloads);
    }

    /** A form that says nothing about reading means the copy is unread, not that it is invalid. */
    public static ReadingProgress readReading(FormData form, String name) {
        String variant = form.variantOr(name, "unread");
        FormData in = form.in(name, variant);
        return switch (variant) {
            case "unread" -> ReadingProgress.UNREAD;
            case "reading" -> new ReadingProgress.Reading(
                    in.optionalDate("since"), in.optionalInt("page"));
            case "finished" -> new ReadingProgress.Finished(
                    in.optionalDate("on"), in.optionalInt("rating").map(Rating::of));
            case "abandoned" -> new ReadingProgress.Abandoned(
                    in.optionalDate("on"), in.optionalInt("atPage"), in.required("why"));
            default -> throw unknown("reading progress", variant);
        };
    }

    // ------------------------------------------------- contributions

    /** The id of the datalist every agent field completes against. */
    public static final String AGENT_LIST = "known-agents";

    /**
     * A repeating group of agent-plus-role rows, always with two blank rows to fill in.
     * The name field completes against the registry but accepts anything typed; a name
     * nobody is on file under becomes a new agent when the form is read.
     */
    public static String contributions(String group, String legend,
                                       List<Contribution> current, AgentDirectory agents) {
        StringBuilder out = new StringBuilder("<fieldset><legend>" + Html.escape(legend) + "</legend>");
        out.append("<p class=\"hint\">Start typing to reuse someone already in the registry — "
                + "an alias works too, and the book keeps the name you type. "
                + "An unknown name creates a new agent; the kind only applies then.</p>");
        int index = 0;
        for (Contribution contribution : current) {
            out.append(contributionRow(group, index++, contribution, agents));
        }
        out.append(contributionRow(group, index++, null, agents));
        out.append(contributionRow(group, index, null, agents));
        return out.append("</fieldset>").toString();
    }

    private static String contributionRow(String group, int index,
                                          Contribution contribution, AgentDirectory agents) {
        String prefix = group + "[" + index + "].";
        String name = contribution == null ? "" : contribution.publishedAs();
        Role role = contribution == null ? Role.AUTHOR : contribution.role();
        String kind = contribution == null
                ? "person"
                : agents.find(contribution.agent())
                .map(agent -> VariantNames.of(agent.kind()))
                .orElse("person");
        return """
                <div class="row">
                  %s%s%s
                </div>
                """.formatted(
                Html.suggestField(prefix + "name", "Name", name, AGENT_LIST),
                Html.select(prefix + "kind", "If new", agentKinds(), kind),
                Html.select(prefix + "role", "Role", roles(), role.label()));
    }

    public static List<Contribution> readContributions(FormData form, String group, AgentResolution agents) {
        List<Contribution> contributions = new ArrayList<>();
        for (int index = 0; index < form.size(group); index++) {
            FormData row = form.at(group, index);
            Optional<String> name = row.optional("name");
            if (name.isEmpty()) {
                continue;
            }
            // The typed name is kept as the published one: type "Megan Lindholm" and the
            // credit resolves to the same agent as "Robin Hobb" but the book still says
            // Lindholm.
            AgentId agent = agents.resolve(name.get(), readAgentKind(row.orEmpty("kind")));
            contributions.add(new Contribution(agent, readRole(row.orEmpty("role")), name.get()));
        }
        return List.copyOf(contributions);
    }

    public static Map<String, String> agentKinds() {
        return ordered("person", "Person", "organisation", "Organisation");
    }

    public static AgentKind readAgentKind(String value) {
        return "organisation".equals(value) ? AgentKind.ORGANISATION : AgentKind.PERSON;
    }

    private static Role readRole(String label) {
        return switch (label) {
            case "author" -> Role.AUTHOR;
            case "translator" -> Role.TRANSLATOR;
            case "editor" -> Role.EDITOR;
            case "illustrator" -> Role.ILLUSTRATOR;
            case "narrator" -> Role.NARRATOR;
            case "publisher" -> Role.PUBLISHER;
            case "" -> Role.AUTHOR;
            default -> new Role.Other(label);
        };
    }

    // -------------------------------------------------------- helpers

    public static Map<String, String> conditions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Condition condition : Condition.values()) {
            options.put(condition.name(), condition.label());
        }
        return options;
    }

    private static Map<String, String> roles() {
        return ordered(
                "author", "Author",
                "translator", "Translator",
                "editor", "Editor",
                "illustrator", "Illustrator",
                "narrator", "Narrator",
                "publisher", "Publisher");
    }

    private static Map<String, String> ratings() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("", "not rated");
        for (int stars = 1; stars <= 5; stars++) {
            options.put(String.valueOf(stars), Rating.of(stars).display());
        }
        return options;
    }

    /**
     * The variant to preselect. A null current value means a record being created from
     * nothing, so the caller's default is used and every payload renders blank — which is
     * why the payload builders above match with {@code instanceof} rather than switching on
     * a value that may not be there.
     */
    private static String selected(Object current, String fallback) {
        return current == null ? fallback : VariantNames.of(current);
    }

    private static String f(String base, String variant, String leaf) {
        return base + "." + variant + "." + leaf;
    }

    private static Map<String, String> ordered(String... keysAndLabels) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < keysAndLabels.length; i += 2) {
            options.put(keysAndLabels[i], keysAndLabels[i + 1]);
        }
        return options;
    }

    private static IllegalArgumentException unknown(String what, String variant) {
        return new IllegalArgumentException("unknown " + what + " variant '" + variant + "'");
    }
}
