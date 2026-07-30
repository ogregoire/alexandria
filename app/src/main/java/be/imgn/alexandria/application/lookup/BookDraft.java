package be.imgn.alexandria.application.lookup;

import java.util.List;
import java.util.Objects;

import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.shared.Language;

/**
 * What a lookup service could tell us about one ISBN, before any of it becomes a catalogue record.
 *
 * <p>Deliberately not domain types: this is unverified third-party data on its way to a form the user will correct.
 * Only {@code title} is required, because that is all some providers reliably return; every other field is
 * {@link Suggested}, which is to say the provider either reported it or did not.
 *
 * @param title the edition's title, which for a translation is the translated one
 * @param originalTitle the work's title in its original language, when the provider models works separately from
 *     editions
 * @param originalYear when the work was first published, as opposed to this printing
 */
public record BookDraft(
        String title,
        Suggested<String> subtitle,
        Suggested<String> originalTitle,
        List<String> authors,
        List<String> translators,
        Suggested<String> publisher,
        Suggested<Integer> publishedYear,
        Suggested<Integer> originalYear,
        Suggested<Language> language,
        Suggested<Integer> pages,
        Suggested<String> series,
        Suggested<String> seriesNumber,
        List<String> subjects,
        Identifier identifier,
        String source) {

    public BookDraft {
        Objects.requireNonNull(title, "title");
        subtitle = orSilent(subtitle);
        originalTitle = orSilent(originalTitle);
        authors = authors == null ? List.of() : List.copyOf(authors);
        translators = translators == null ? List.of() : List.copyOf(translators);
        publisher = orSilent(publisher);
        publishedYear = orSilent(publishedYear);
        originalYear = orSilent(originalYear);
        language = orSilent(language);
        pages = orSilent(pages);
        series = orSilent(series);
        seriesNumber = orSilent(seriesNumber);
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
                || originalTitle instanceof Suggested.Given(String original) && !original.equalsIgnoreCase(title);
    }

    private static <T> Suggested<T> orSilent(Suggested<T> value) {
        return value == null ? Suggested.silent() : value;
    }

    /** Providers fill in whatever they have; the rest stays absent. */
    public static final class Builder {

        private final String title;
        private final Identifier identifier;
        private final String source;
        private Suggested<String> subtitle = Suggested.silent();
        private Suggested<String> originalTitle = Suggested.silent();
        private List<String> authors = List.of();
        private List<String> translators = List.of();
        private Suggested<String> publisher = Suggested.silent();
        private Suggested<Integer> publishedYear = Suggested.silent();
        private Suggested<Integer> originalYear = Suggested.silent();
        private Suggested<Language> language = Suggested.silent();
        private Suggested<Integer> pages = Suggested.silent();
        private Suggested<String> series = Suggested.silent();
        private Suggested<String> seriesNumber = Suggested.silent();
        private List<String> subjects = List.of();

        private Builder(String title, Identifier identifier, String source) {
            this.title = title;
            this.identifier = identifier;
            this.source = source;
        }

        public Builder subtitle(String value) {
            this.subtitle = Suggested.ofText(value);
            return this;
        }

        public Builder originalTitle(String value) {
            this.originalTitle = Suggested.ofText(value);
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
            this.publisher = Suggested.ofText(value);
            return this;
        }

        public Builder publishedYear(Integer value) {
            this.publishedYear = Suggested.of(value);
            return this;
        }

        public Builder originalYear(Integer value) {
            this.originalYear = Suggested.of(value);
            return this;
        }

        public Builder language(Language value) {
            this.language = Suggested.of(value);
            return this;
        }

        public Builder pages(Integer value) {
            this.pages = Suggested.of(value);
            return this;
        }

        public Builder series(String value) {
            this.series = Suggested.ofText(value);
            return this;
        }

        public Builder seriesNumber(String value) {
            this.seriesNumber = Suggested.ofText(value);
            return this;
        }

        public Builder subjects(List<String> value) {
            this.subjects = value;
            return this;
        }

        public BookDraft build() {
            return new BookDraft(
                    title,
                    subtitle,
                    originalTitle,
                    authors,
                    translators,
                    publisher,
                    publishedYear,
                    originalYear,
                    language,
                    pages,
                    series,
                    seriesNumber,
                    subjects,
                    identifier,
                    source);
        }
    }
}
