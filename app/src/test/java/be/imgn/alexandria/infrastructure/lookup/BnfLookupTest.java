package be.imgn.alexandria.infrastructure.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.domain.manifestation.Identifier;

/** Parses the exact SRU response catalogue.bnf.fr returned for a real French ISBN. */
class BnfLookupTest {

    private static final Identifier ETRANGER = Identifier.isbn("9782070360024");

    @Test
    void picksTheAuthorOutOfTheDublinCoreProse() {
        BookDraft draft = lookUp();

        assertThat(draft.authors())
                .as("from \"Camus, Albert (1913-1960). Auteur du texte\"")
                .containsExactly("Camus, Albert");
    }

    @Test
    void dropsThePlaceFromThePublisher() {
        assertThat(lookUp().publisher()).as("from \"Gallimard (Paris)\"").contains("Gallimard");
    }

    @Test
    void takesTheTitleWithoutTheStatementOfResponsibility() {
        assertThat(lookUp().title()).as("from \"L'Étranger / Albert Camus\"").isEqualTo("L'Étranger");
    }

    @Test
    void readsTheExtentAndSeriesOutOfFreeText() {
        BookDraft draft = lookUp();

        assertThat(draft.pages()).as("from \"1 volume 191 p ; 18 cm\"").contains(191);
        assertThat(draft.series()).as("from \"Collection : Folio ; 2\"").contains("Folio");
        assertThat(draft.seriesNumber()).contains("2");
    }

    @Test
    void convertsTheBibliographicLanguageCode() {
        assertThat(lookUp().language())
                .hasValueSatisfying(language -> assertThat(language.code()).isEqualTo("fr"));
    }

    /**
     * The decisive behaviour for French books: the BnF indexes the pre-2007 ISBN-10, so the ISBN-13 search returns
     * nothing and the second attempt has to find it.
     */
    @Test
    void retriesWithTheIsbn10FormBecauseThatIsWhatTheBnfIndexes() {
        // The quotes matter: "2070360024" is a substring of "9782070360024".
        StubHttp http = new StubHttp()
                .serve("%229782070360024%22", "bnf-empty.xml")
                .serve("%222070360024%22", "bnf-etranger.xml");

        BookDraft draft = new BnfLookup(http, "").byIsbn(ETRANGER).orElseThrow();

        assertThat(draft.title()).isEqualTo("L'Étranger");
        assertThat(http.requested())
                .as("the ISBN-13 is tried first, then the ISBN-10")
                .hasSize(2);
        assertThat(http.requested().getFirst()).contains("9782070360024");
        assertThat(http.requested().getLast()).contains("2070360024");
    }

    @Test
    void reportsAMissForAnEmptyResultSet() {
        StubHttp http = new StubHttp().serve("bib.isbn", "bnf-empty.xml");

        assertThat(new BnfLookup(http, "").byIsbn(ETRANGER)).isEmpty();
    }

    @Test
    void refusesToResolveExternalEntitiesInTheResponse() {
        String attack = """
                <?xml version="1.0"?>
                <!DOCTYPE root [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <srw:searchRetrieveResponse xmlns:srw="http://www.loc.gov/zing/srw/">
                  <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">&xxe;</dc:title>
                </srw:searchRetrieveResponse>
                """;
        Http http = new Http() {
            @Override
            Optional<String> get(String url) {
                return Optional.of(attack);
            }
        };

        assertThat(new BnfLookup(http, "").byIsbn(ETRANGER))
                .as("a doctype declaration must make the parse fail, not leak a file")
                .isEmpty();
    }

    private static BookDraft lookUp() {
        return new BnfLookup(new StubHttp().serve("bib.isbn", "bnf-etranger.xml"), "")
                .byIsbn(ETRANGER)
                .orElseThrow();
    }
}
