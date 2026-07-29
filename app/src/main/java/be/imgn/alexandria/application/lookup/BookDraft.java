package be.imgn.alexandria.application.lookup;

import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.shared.Language;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a lookup service could tell us about one ISBN, before any of it becomes a catalogue
 * record.
 *
 * <p>Deliberately not domain types: this is unverified third-party data on its way to a
 * form the user will correct. Only {@code title} is required, because that is all some
 * providers reliably return; everything else is optional and simply leaves its field blank.
 *
 * @param title        the edition's title, which for a translation is the translated one
 * @param originalTitle the work's title in its original language, when the provider models
 *                     works separately from editions
 * @param originalYear when the work was first published, as opposed to this printing
 */
public record BookDraft(
        String title,
        Optional<String> subtitle,
        Optional<String> originalTitle,
        List<String> authors,
        List<String> translators,
        Optional<String> publisher,
        Optional<Integer> publishedYear,
        Optional<Integer> originalYear,
        Optional<Language> language,
        Optional<Integer> pages,
        Optional<String> series,
        Optional<String> seriesNumber,
        List<String> subjects,
        Identifier identifier,
        String source) {

    public BookDraft {
        Objects.requireNonNull(title, "title");
        subtitle = orEmpty(subtitle);
        originalTitle = orEmpty(originalTitle);
        authors = authors == null ? List.of() : List.copyOf(authors);
        translators = translators == null ? List.of() : List.copyOf(translators);
        publisher = orEmpty(publisher);
        publishedYear = orEmpty(publishedYear);
        originalYear = orEmpty(originalYear);
        language = orEmpty(language);
        pages = orEmpty(pages);
        series = orEmpty(series);
        seriesNumber = orEmpty(seriesNumber);
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(source, "source");
    }

    public static Builder of(String title, Identifier identifier, String source) {
        return new Builder(title, identifier, source);
    }

    /** The title to give the Work: the original when known, otherwise this edition's. */
    public String workTitle() {
        return originalTitle.orElse(title);
    }

    /** True when the edition is not in the language the work was written in. */
    public boolean looksTranslated() {
        return !translators.isEmpty()
                || originalTitle.filter(original -> !original.equalsIgnoreCase(title)).isPresent();
    }

    private static <T> Optional<T> orEmpty(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    /** Providers fill in whatever they have; the rest stays absent. */
    public static final class Builder {

        private final String title;
        private final Identifier identifier;
        private final String source;
        private Optional<String> subtitle = Optional.empty();
        private Optional<String> originalTitle = Optional.empty();
        private List<String> authors = List.of();
        private List<String> translators = List.of();
        private Optional<String> publisher = Optional.empty();
        private Optional<Integer> publishedYear = Optional.empty();
        private Optional<Integer> originalYear = Optional.empty();
        private Optional<Language> language = Optional.empty();
        private Optional<Integer> pages = Optional.empty();
        private Optional<String> series = Optional.empty();
        private Optional<String> seriesNumber = Optional.empty();
        private List<String> subjects = List.of();

        private Builder(String title, Identifier identifier, String source) {
            this.title = title;
            this.identifier = identifier;
            this.source = source;
        }

        public Builder subtitle(String value) {
            this.subtitle = blankToEmpty(value);
            return this;
        }

        public Builder originalTitle(String value) {
            this.originalTitle = blankToEmpty(value);
            return this;
        }

        public Builder authors(List<String> value) {
            this.authors = value;
            return this;
        }

        public Builder translators(List<String> value) {
            this.translators = value;
            return this;
        }

        public Builder publisher(String value) {
            this.publisher = blankToEmpty(value);
            return this;
        }

        public Builder publishedYear(Optional<Integer> value) {
            this.publishedYear = value;
            return this;
        }

        public Builder originalYear(Optional<Integer> value) {
            this.originalYear = value;
            return this;
        }

        public Builder language(Optional<Language> value) {
            this.language = value;
            return this;
        }

        public Builder pages(Optional<Integer> value) {
            this.pages = value;
            return this;
        }

        public Builder series(String value) {
            this.series = blankToEmpty(value);
            return this;
        }

        public Builder seriesNumber(String value) {
            this.seriesNumber = blankToEmpty(value);
            return this;
        }

        public Builder subjects(List<String> value) {
            this.subjects = value;
            return this;
        }

        public BookDraft build() {
            return new BookDraft(title, subtitle, originalTitle, authors, translators, publisher,
                    publishedYear, originalYear, language, pages, series, seriesNumber, subjects,
                    identifier, source);
        }

        private static Optional<String> blankToEmpty(String value) {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
        }
    }
}
