package be.imgn.alexandria.infrastructure.lookup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.infrastructure.json.codec.JsonIn;

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
        Optional<JsonIn> edition = fetch(base + "/isbn/" + digits.get() + ".json");
        Optional<JsonIn> data = fetch(base + "/api/books?bibkeys=ISBN:" + digits.get() + "&format=json&jscmd=data")
                .flatMap(body -> body.optionalObject("ISBN:" + digits.get()));
        if (edition.isEmpty() && data.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(draft(isbn, edition.orElseGet(JsonIn::empty), data.orElseGet(JsonIn::empty)));
    }

    private BookDraft draft(Identifier isbn, JsonIn edition, JsonIn data) {
        Optional<JsonIn> work = workRecord(edition);

        String title = data.optionalText("title")
                .or(() -> edition.optionalText("title"))
                .orElse("Untitled");
        BookDraft.Builder draft = BookDraft.of(title, isbn, name())
                .subtitle(data.optionalText("subtitle")
                        .or(() -> edition.optionalText("subtitle"))
                        .orElse(null))
                .authors(data.textsOrField("authors", "name"))
                .translators(contributorsIn(edition, "translator"))
                .publisher(data.textsOrField("publishers", "name").stream()
                        .findFirst()
                        .orElse(null))
                .publishedYear(year(data.optionalText("publish_date").or(() -> edition.optionalText("publish_date"))))
                .pages(data.optionalInt("number_of_pages").or(() -> edition.optionalInt("number_of_pages")))
                .language(language(edition))
                .subjects(data.textsOrField("subjects", "name"));

        series(edition).ifPresent(parts -> {
            draft.series(parts.name());
            draft.seriesNumber(parts.number());
        });

        work.ifPresent(record -> {
            record.optionalText("title").ifPresent(draft::originalTitle);
            draft.originalYear(year(record.optionalText("first_publish_date")));
        });
        return draft.build();
    }

    private Optional<JsonIn> workRecord(JsonIn edition) {
        return edition.objects("works").stream()
                .findFirst()
                .flatMap(work -> work.optionalText("key"))
                .flatMap(key -> fetch(base + key + ".json"));
    }

    /** Open Library writes languages as {@code {"key": "/languages/fre"}}, ISO 639-2/B. */
    private static Optional<Language> language(JsonIn edition) {
        return edition.objects("languages").stream()
                .findFirst()
                .flatMap(entry -> entry.optionalText("key"))
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
    private static List<String> contributorsIn(JsonIn edition, String role) {
        List<String> found = new ArrayList<>();
        for (String entry : edition.texts("contributions")) {
            Matcher matcher = CONTRIBUTION.matcher(entry);
            if (matcher.matches() && matcher.group(2).trim().equalsIgnoreCase(role)) {
                found.add(matcher.group(1).trim());
            }
        }
        return found;
    }

    private record Series(String name, String number) {}

    /** {@code "Collection Folio No. 2"} and {@code "The Farseer Trilogy, 1"} both occur. */
    private static Optional<Series> series(JsonIn edition) {
        Optional<String> first = edition.texts("series").stream().findFirst();
        if (first.isEmpty()) {
            return Optional.empty();
        }
        String raw = first.get().trim();
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

    private static Optional<Integer> year(Optional<String> raw) {
        return raw.flatMap(value -> {
            Matcher matcher = YEAR.matcher(value);
            return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
        });
    }

    private Optional<JsonIn> fetch(String url) {
        return http.get(url).flatMap(OpenLibraryLookup::parse);
    }

    /** A payload we cannot parse is a miss, like any other. */
    private static Optional<JsonIn> parse(String body) {
        try {
            return Optional.of(JsonIn.parse(body));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
