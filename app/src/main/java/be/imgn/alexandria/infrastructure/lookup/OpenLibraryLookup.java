package be.imgn.alexandria.infrastructure.lookup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.shared.Language;

/**
 * Open Library, which models works and editions separately — the same split this catalogue does — so an ISBN yields
 * both the edition in hand and the work behind it.
 *
 * <p>Two endpoints are needed because neither is complete. {@code /isbn/<isbn>.json} is the edition record and is the
 * only one carrying the language, the series and the {@code contributions} list where translators hide;
 * {@code /api/books?jscmd=data} is the only one that resolves author and publisher keys to names. The work record
 * behind the edition supplies the original title and first publication year.
 */
public final class OpenLibraryLookup implements BookLookup {

    private static final String BASE = "https://openlibrary.org";

    /**
     * Open Library documents one request per second, raised to three for callers who name themselves and a contact. One
     * ISBN costs up to three calls here, so the interval is what keeps a single lookup inside the limit.
     */
    private static final Duration UNIDENTIFIED_INTERVAL = Duration.ofMillis(1000);

    private static final Duration IDENTIFIED_INTERVAL = Duration.ofMillis(334);
    private static final Pattern CONTRIBUTION = Pattern.compile("(.+?)\\s*\\((.+?)\\)");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

    private final Http http;
    private final ObjectMapper json = new ObjectMapper();
    private final String base;

    public OpenLibraryLookup() {
        this(UserAgent.anonymous());
    }

    public OpenLibraryLookup(UserAgent caller) {
        this(new Http(caller.identified() ? IDENTIFIED_INTERVAL : UNIDENTIFIED_INTERVAL, caller.header()), BASE);
    }

    OpenLibraryLookup(Http http, String base) {
        this.http = http;
        this.base = base;
    }

    @Override
    public String name() {
        return "Open Library";
    }

    @Override
    public Optional<BookDraft> byIsbn(Identifier isbn) {
        Optional<String> digits = isbn.isbnDigits();
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonNode> edition = fetch(base + "/isbn/" + digits.get() + ".json");
        Optional<JsonNode> data = fetch(base + "/api/books?bibkeys=ISBN:" + digits.get() + "&format=json&jscmd=data")
                .map(node -> node.path("ISBN:" + digits.get()));
        if (edition.isEmpty() && data.map(JsonNode::isMissingNode).orElse(true)) {
            return Optional.empty();
        }
        return Optional.of(draft(isbn, edition.orElse(json.createObjectNode()), data.orElse(json.createObjectNode())));
    }

    private BookDraft draft(Identifier isbn, JsonNode edition, JsonNode data) {
        Optional<JsonNode> work = workRecord(edition);

        String title = text(data, "title").or(() -> text(edition, "title")).orElse("Untitled");
        BookDraft.Builder draft = BookDraft.of(title, isbn, name())
                .subtitle(text(data, "subtitle")
                        .or(() -> text(edition, "subtitle"))
                        .orElse(null))
                .authors(names(data.path("authors")))
                .translators(contributorsIn(edition, "translator"))
                .publisher(firstName(data.path("publishers")).orElse(null))
                .publishedYear(year(text(data, "publish_date").or(() -> text(edition, "publish_date"))))
                .pages(integer(data, "number_of_pages").or(() -> integer(edition, "number_of_pages")))
                .language(language(edition))
                .subjects(names(data.path("subjects")));

        series(edition).ifPresent(parts -> {
            draft.series(parts.name());
            draft.seriesNumber(parts.number());
        });

        work.ifPresent(record -> {
            text(record, "title").ifPresent(draft::originalTitle);
            draft.originalYear(year(text(record, "first_publish_date")));
        });
        return draft.build();
    }

    private Optional<JsonNode> workRecord(JsonNode edition) {
        JsonNode works = edition.path("works");
        if (!works.isArray() || works.isEmpty()) {
            return Optional.empty();
        }
        return text(works.get(0), "key").flatMap(key -> fetch(base + key + ".json"));
    }

    /** Open Library writes languages as {@code {"key": "/languages/fre"}}, ISO 639-2/B. */
    private static Optional<Language> language(JsonNode edition) {
        JsonNode languages = edition.path("languages");
        if (!languages.isArray() || languages.isEmpty()) {
            return Optional.empty();
        }
        return text(languages.get(0), "key")
                .map(key -> key.substring(key.lastIndexOf('/') + 1))
                .flatMap(OpenLibraryLookup::toLanguage);
    }

    private static Optional<Language> toLanguage(String code) {
        try {
            return Optional.of(new Language(Iso639.toTwoLetter(code)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** {@code "contributions": ["Edith Grossman (Translator)"]} — the only place they appear. */
    private static List<String> contributorsIn(JsonNode edition, String role) {
        List<String> found = new ArrayList<>();
        for (JsonNode entry : edition.path("contributions")) {
            Matcher matcher = CONTRIBUTION.matcher(entry.asText(""));
            if (matcher.matches() && matcher.group(2).trim().equalsIgnoreCase(role)) {
                found.add(matcher.group(1).trim());
            }
        }
        return found;
    }

    private record Series(String name, String number) {}

    /** {@code "Collection Folio No. 2"} and {@code "The Farseer Trilogy, 1"} both occur. */
    private static Optional<Series> series(JsonNode edition) {
        JsonNode series = edition.path("series");
        if (!series.isArray() || series.isEmpty()) {
            return Optional.empty();
        }
        String raw = series.get(0).asText("").trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("^(.*?)[,;]?\\s*(?:no\\.?|n°|#)?\\s*(\\d+)$", Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (matcher.matches() && !matcher.group(1).isBlank()) {
            return Optional.of(new Series(matcher.group(1).trim(), matcher.group(2)));
        }
        return Optional.of(new Series(raw, null));
    }

    private static List<String> names(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode entry : array) {
            String value =
                    entry.isTextual() ? entry.asText() : entry.path("name").asText("");
            if (!value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private static Optional<String> firstName(JsonNode array) {
        return names(array).stream().findFirst();
    }

    private static Optional<Integer> year(Optional<String> raw) {
        return raw.flatMap(value -> {
            Matcher matcher = YEAR.matcher(value);
            return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
        });
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank()
                ? Optional.of(value.asText().trim())
                : Optional.empty();
    }

    private static Optional<Integer> integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? Optional.of(value.asInt()) : Optional.empty();
    }

    private Optional<JsonNode> fetch(String url) {
        return http.get(url).flatMap(this::parse);
    }

    private Optional<JsonNode> parse(String body) {
        try {
            return Optional.of(json.readTree(body));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
