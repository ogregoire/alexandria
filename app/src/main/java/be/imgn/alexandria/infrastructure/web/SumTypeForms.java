package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.manifestation.Carrier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a sum type from a {@link FormState}: one select for the variant, one fieldset of
 * payload fields per variant, each carrying its own error.
 *
 * <p>Declarative on purpose. The variants and their payloads are described once as data, so
 * the same description drives a fresh form, a prefilled one and a rejected one, and a field
 * that failed validation comes back with its value and its reason intact.
 */
final class SumTypeForms {

    /**
     * One payload field of one variant.
     *
     * @param options when non-empty, render a select over these instead of an input
     */
    record Field(String leaf, String label, String type, String constraint, Map<String, String> options) {

        static Field text(String leaf, String label) {
            return new Field(leaf, label, "text", null, Map.of());
        }

        static Field text(String leaf, String label, String constraint) {
            return new Field(leaf, label, "text", constraint, Map.of());
        }

        static Field number(String leaf, String label) {
            return new Field(leaf, label, "number", null, Map.of());
        }

        static Field date(String leaf, String label) {
            return new Field(leaf, label, "date", null, Map.of());
        }

        static Field select(String leaf, String label, Map<String, String> options) {
            return new Field(leaf, label, "select", null, options);
        }
    }

    /** A sum type: its variants in display order, its default, and each variant's payload. */
    record Shape(Map<String, String> variants, String fallback, Map<String, List<Field>> payloads) {
    }

    private SumTypeForms() {
    }

    static String render(FormState state, String name, String label, Shape shape) {
        String selected = state.valueOr(name + ".type", shape.fallback());
        String panels = shape.variants().keySet().stream()
                .map(variant -> """
                        <fieldset class="variant" data-variant-of="%s" data-variant="%s"%s>%s</fieldset>
                        """.formatted(
                        Html.escape(name), Html.escape(variant),
                        variant.equals(selected) ? "" : " hidden",
                        payload(state, name, variant, shape.payloads().getOrDefault(variant, List.of()))))
                .collect(Collectors.joining());
        return """
                <div class="sum" data-sum="%s">
                  %s
                  %s
                </div>
                """.formatted(
                Html.escape(name),
                Html.choice(state, name + ".type", label, shape.variants(), shape.fallback()),
                panels);
    }

    private static String payload(FormState state, String name, String variant, List<Field> fields) {
        return fields.stream().map(field -> {
            String qualified = name + "." + variant + "." + field.leaf();
            return field.options().isEmpty()
                    ? Html.input(state, qualified, field.label(), field.type(), field.constraint())
                    : Html.choice(state, qualified, field.label(), field.options(), "");
        }).collect(Collectors.joining());
    }

    /** Every field name this shape can render, so the summary can link to the failing one. */
    static Map<String, String> fieldLabels(String name, String label, Shape shape) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(name + ".type", label);
        shape.payloads().forEach((variant, fields) -> fields.forEach(field ->
                labels.put(name + "." + variant + "." + field.leaf(), label + " — " + field.label())));
        return labels;
    }

    // ------------------------------------------------------------ the shapes

    static final Shape DATE = new Shape(
            ordered("year", "Year", "exact", "Exact date", "circa", "Circa",
                    "between", "Between two years", "unknown", "Unknown"),
            "unknown",
            Map.of(
                    "year", List.of(Field.number("value", "Year")),
                    "exact", List.of(Field.date("date", "Date")),
                    "circa", List.of(Field.number("value", "Approximate year")),
                    "between", List.of(Field.number("from", "From"), Field.number("to", "To")),
                    "unknown", List.of()));

    static final Shape WORK_FORM = new Shape(
            ordered("novel", "Novel", "novella", "Novella", "short-stories", "Short stories",
                    "poetry", "Poetry", "drama", "Drama", "essay", "Essay",
                    "nonfiction", "Nonfiction", "reference", "Reference",
                    "comics", "Comics", "other", "Other"),
            "novel",
            Map.of("other", List.of(Field.text("label", "Form"))));

    static final Shape EXPRESSION_KIND = new Shape(
            ordered("original", "Original", "translation", "Translation", "revision", "Revision",
                    "abridgement", "Abridgement", "adaptation", "Adaptation", "narration", "Narration"),
            "original",
            Map.of(
                    "translation", List.of(Field.text("from", "Translated from (language code)", "language")),
                    "revision", List.of(Field.text("label", "Revision label")),
                    "adaptation", List.of(Field.text("into", "Adapted as"))));

    static final Shape CARRIER = new Shape(
            ordered("paperback", "Paperback", "hardcover", "Hardcover",
                    "mass-market", "Mass-market paperback", "ebook", "Ebook",
                    "audiobook", "Audiobook", "other", "Other"),
            "paperback",
            Map.of(
                    "ebook", List.of(Field.select("format", "File format", ebookFormats())),
                    "audiobook", List.of(Field.text("medium", "Medium (CD, download, cassette)")),
                    "other", List.of(Field.text("label", "Carrier"))));

    static final Shape IDENTIFIER = new Shape(
            ordered("isbn13", "ISBN-13", "isbn10", "ISBN-10", "asin", "ASIN",
                    "custom", "Other scheme", "none", "None"),
            "none",
            Map.of(
                    "isbn13", List.of(Field.text("digits", "ISBN-13", "isbn")),
                    "isbn10", List.of(Field.text("digits", "ISBN-10", "isbn")),
                    "asin", List.of(Field.text("value", "ASIN")),
                    "custom", List.of(Field.text("scheme", "Scheme"), Field.text("value", "Value")),
                    "none", List.of()));

    static final Shape EXTENT = new Shape(
            ordered("pages", "Pages", "volumes", "Volumes",
                    "playtime", "Playing time", "unspecified", "Unspecified"),
            "unspecified",
            Map.of(
                    "pages", List.of(Field.number("count", "Pages")),
                    "volumes", List.of(Field.number("count", "Volumes"),
                            Field.number("pagesTotal", "Pages in total")),
                    "playtime", List.of(Field.number("hours", "Hours"), Field.number("minutes", "Minutes")),
                    "unspecified", List.of()));

    static final Shape ACQUISITION = new Shape(
            ordered("purchased", "Purchased", "gift", "Gift", "inherited", "Inherited",
                    "borrowed", "Borrowed", "unrecorded", "Unrecorded"),
            "unrecorded",
            Map.of(
                    "purchased", List.of(Field.date("date", "Date"),
                            Field.text("price", "Price (e.g. 28.50 EUR)", "money"),
                            Field.text("from", "Bought from")),
                    "gift", List.of(Field.date("date", "Date"), Field.text("from", "From", "required")),
                    "inherited", List.of(Field.date("date", "Date"), Field.text("from", "From", "required")),
                    "borrowed", List.of(Field.text("from", "Lender", "required"),
                            Field.date("since", "Since"), Field.date("due", "Due back")),
                    "unrecorded", List.of()));

    static final Shape LOCATION = new Shape(
            ordered("shelf", "On a shelf", "box", "In a box", "lent-to", "Lent out",
                    "device", "On a device", "missing", "Missing"),
            "shelf",
            Map.of(
                    "shelf", List.of(Field.text("name", "Shelf", "required"),
                            Field.text("position", "Position")),
                    "box", List.of(Field.text("label", "Box", "required")),
                    "lent-to", List.of(Field.text("person", "Borrower", "required"),
                            Field.date("since", "Since")),
                    "device", List.of(Field.text("name", "Device or library", "required")),
                    "missing", List.of()));

    static final Shape READING = new Shape(
            ordered("unread", "Unread", "reading", "Reading",
                    "finished", "Finished", "abandoned", "Abandoned"),
            "unread",
            Map.of(
                    "unread", List.of(),
                    "reading", List.of(Field.date("since", "Started"), Field.number("page", "Page")),
                    "finished", List.of(Field.date("on", "Finished"),
                            Field.select("rating", "Rating", ratings())),
                    "abandoned", List.of(Field.date("on", "Given up"),
                            Field.number("atPage", "At page"),
                            Field.text("why", "Why", "required"))));

    static Map<String, String> conditions() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Condition condition : Condition.values()) {
            options.put(condition.name(), condition.label());
        }
        return options;
    }

    private static Map<String, String> ebookFormats() {
        Map<String, String> options = new LinkedHashMap<>();
        for (Carrier.EbookFormat format : Carrier.EbookFormat.values()) {
            options.put(format.name(), format.name());
        }
        return options;
    }

    private static Map<String, String> ratings() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("", "not rated");
        for (int stars = 1; stars <= 5; stars++) {
            options.put(String.valueOf(stars), Rating.of(stars).display());
        }
        return options;
    }

    private static Map<String, String> ordered(String... keysAndLabels) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < keysAndLabels.length; i += 2) {
            options.put(keysAndLabels[i], keysAndLabels[i + 1]);
        }
        return options;
    }
}
