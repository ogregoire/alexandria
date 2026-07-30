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
 * Google Books, used last.
 *
 * <p>Last for two practical reasons. Unkeyed requests draw on a quota shared by everyone leaving the same address, so a
 * 429 is ordinary rather than exceptional — it is treated as a miss like any other. And it has no notion of a work
 * distinct from an edition, so nothing it returns can fill in an original title or a first publication year, which the
 * two providers ahead of it can.
 *
 * <p>Nothing it returns is retained: the response is rendered into the review form, the user corrects it, and what
 * reaches the catalogue is what the user submitted. The provider name is shown on that form so the reader knows where
 * the suggestion came from.
 */
public final class GoogleBooksLookup implements BookLookup {

    private static final String BASE = "https://www.googleapis.com/books/v1/volumes";

    /**
     * Google documents a daily quota rather than a minimum interval, and asks for exponential backoff on a 429 — which
     * {@link Http} does. This interval is only here to stop a held-down button turning into a burst.
     */
    private static final Duration INTERVAL = Duration.ofMillis(250);

    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

    private final Http http;
    private final ObjectMapper json = new ObjectMapper();
    private final String base;

    public GoogleBooksLookup() {
        this(UserAgent.anonymous());
    }

    public GoogleBooksLookup(UserAgent caller) {
        this(new Http(INTERVAL, caller.header()), BASE);
    }

    GoogleBooksLookup(Http http, String base) {
        this.http = http;
        this.base = base;
    }

    @Override
    public String name() {
        return "Google Books";
    }

    @Override
    public Optional<BookDraft> byIsbn(Identifier isbn) {
        Optional<String> digits = isbn.isbnDigits();
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        return http.get(base + "?q=isbn:" + digits.get())
                .flatMap(this::parse)
                .map(node -> node.path("items"))
                .filter(items -> items.isArray() && !items.isEmpty())
                .map(items -> items.get(0).path("volumeInfo"))
                .filter(info -> info.path("title").isTextual())
                .map(info -> draft(info, isbn));
    }

    private BookDraft draft(JsonNode info, Identifier isbn) {
        return BookDraft.of(info.path("title").asText().trim(), isbn, name())
                .subtitle(text(info, "subtitle").orElse(null))
                .authors(strings(info.path("authors")))
                .publisher(text(info, "publisher").orElse(null))
                .publishedYear(year(text(info, "publishedDate")))
                .pages(
                        info.path("pageCount").isNumber()
                                ? Optional.of(info.path("pageCount").asInt())
                                : Optional.empty())
                .language(language(text(info, "language")))
                .subjects(strings(info.path("categories")))
                .build();
    }

    private static Optional<Language> language(Optional<String> code) {
        return code.flatMap(value -> {
            try {
                return Optional.of(new Language(Iso639.toTwoLetter(value)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode entry : array) {
            if (entry.isTextual() && !entry.asText().isBlank()) {
                values.add(entry.asText().trim());
            }
        }
        return values;
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

    private Optional<JsonNode> parse(String body) {
        try {
            return Optional.of(json.readTree(body));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
