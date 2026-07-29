package be.imgn.alexandria.infrastructure.lookup;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.domain.manifestation.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Parses the exact payloads openlibrary.org returned for two real ISBNs. */
class OpenLibraryLookupTest {

    private static final Identifier QUIXOTE = Identifier.isbn("9780060188702");
    private static final Identifier ETRANGER = Identifier.isbn("9782070360024");

    private BookDraft lookUpQuixote() {
        StubHttp http = new StubHttp()
                .serve("/isbn/9780060188702.json", "openlibrary-edition-quixote.json")
                .serve("jscmd=data", "openlibrary-data-quixote.json")
                .serve("/works/", "openlibrary-work-quixote.json");
        return new OpenLibraryLookup(http, "").byIsbn(QUIXOTE).orElseThrow();
    }

    @Test
    void takesTheEditionTitleAndTheWorkTitleFromTheirOwnRecords() {
        BookDraft draft = lookUpQuixote();

        assertThat(draft.title()).isEqualTo("Don Quixote");
        assertThat(draft.originalTitle()).contains("Don Quijote de la Mancha");
        assertThat(draft.workTitle()).isEqualTo("Don Quijote de la Mancha");
    }

    @Test
    void readsTheAuthorAndPublisherAsNamesRatherThanKeys() {
        BookDraft draft = lookUpQuixote();

        assertThat(draft.authors()).containsExactly("Miguel de Cervantes Saavedra");
        assertThat(draft.publisher()).contains("Ecco");
    }

    @Test
    void findsTheTranslatorInTheContributionsList() {
        BookDraft draft = lookUpQuixote();

        assertThat(draft.translators()).containsExactly("Edith Grossman");
        assertThat(draft.looksTranslated()).isTrue();
    }

    @Test
    void separatesTheEditionYearFromTheWorksFirstPublication() {
        BookDraft draft = lookUpQuixote();

        assertThat(draft.publishedYear()).contains(2003);
        assertThat(draft.originalYear()).contains(1896);
    }

    @Test
    void convertsBibliographicLanguageCodesToTheTwoLetterForm() {
        StubHttp http = new StubHttp()
                .serve("/isbn/9782070360024.json", "openlibrary-edition-etranger.json")
                .serve("jscmd=data", "openlibrary-data-etranger.json");

        BookDraft draft = new OpenLibraryLookup(http, "").byIsbn(ETRANGER).orElseThrow();

        assertThat(draft.language()).hasValueSatisfying(language ->
                assertThat(language.code()).as("/languages/fre must become fr").isEqualTo("fr"));
    }

    @Test
    void readsAFrenchEditionEndToEnd() {
        StubHttp http = new StubHttp()
                .serve("/isbn/9782070360024.json", "openlibrary-edition-etranger.json")
                .serve("jscmd=data", "openlibrary-data-etranger.json");

        BookDraft draft = new OpenLibraryLookup(http, "").byIsbn(ETRANGER).orElseThrow();

        assertThat(draft.title()).isEqualTo("L’étranger");
        assertThat(draft.authors()).containsExactly("Albert Camus");
        assertThat(draft.publisher()).contains("Gallimard");
        assertThat(draft.pages()).contains(194);
    }

    @Test
    void splitsASeriesStatementIntoNameAndNumber() {
        StubHttp http = new StubHttp()
                .serve("/isbn/9782070360024.json", "openlibrary-edition-etranger.json")
                .serve("jscmd=data", "openlibrary-data-etranger.json");

        BookDraft draft = new OpenLibraryLookup(http, "").byIsbn(ETRANGER).orElseThrow();

        assertThat(draft.series()).as("from \"Collection Folio No. 2\"").contains("Collection Folio");
        assertThat(draft.seriesNumber()).contains("2");
    }

    @Test
    void reportsAMissRatherThanFailingWhenNothingIsFound() {
        Optional<BookDraft> found = new OpenLibraryLookup(new StubHttp(), "")
                .byIsbn(Identifier.isbn("9780306406157"));

        assertThat(found).isEmpty();
    }

    @Test
    void doesNotReachTheNetworkForANonIsbnIdentifier() {
        StubHttp http = new StubHttp();

        assertThat(new OpenLibraryLookup(http, "").byIsbn(new Identifier.Asin("B008FQ3G6M"))).isEmpty();
        assertThat(http.requested()).isEmpty();
    }
}
