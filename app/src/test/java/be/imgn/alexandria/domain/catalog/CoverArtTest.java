package be.imgn.alexandria.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.EditionStatement;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.manifestation.Publisher;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.shared.BibliographicDate;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Role;
import be.imgn.alexandria.domain.shared.Title;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;

/**
 * A cover belongs to the printing it was wrapped around, not to the text inside it. Crediting a cover artist as an
 * illustrator of the expression says they illustrated a text they never touched, and makes the same translation look
 * like two different translations when it is reissued behind new art.
 */
class CoverArtTest {

    private static final Agent ARTIST = Agent.person("Lee Gibbons", "Gibbons, Lee");

    private JsonCatalog catalog;

    @BeforeEach
    void setUp(@TempDir Path root) {
        catalog = CatalogFixture.writeInto(root);
        catalog.save(ARTIST);
        catalog.save(withCover(CatalogFixture.manifestation()));
    }

    @Test
    void creditsTheArtistOnThePrintingRatherThanOnTheWork() {
        assertThat(catalog.creditsOf(ARTIST.id()))
                .singleElement()
                .isInstanceOf(Credit.OnEdition.class)
                .extracting(Credit::subject)
                .isEqualTo("Don Quixote");
    }

    @Test
    void datesThatCreditByThePrintingNotByTheTextBehindIt() {
        var credit = catalog.creditsOf(ARTIST.id()).getFirst();

        assertThat(credit.when().display())
                .as("the year this edition was published, not the years Cervantes wrote it")
                .isEqualTo("2003");
        assertThat(credit.realisation())
                .as("a printing is not a realisation of a work, so there is no language to name")
                .isEmpty();
    }

    /**
     * The reason the role exists at all. Two editions of one translation, different art: the expression stays one, the
     * artists attach to their own printings. Filed as illustrators this would have forced two expressions of a text
     * nobody re-translated.
     */
    @Test
    void keepsOneExpressionWhenTheSameTranslationIsReissuedBehindNewArt() {
        Agent other = Agent.person("Un Autre Peintre", "Peintre, Un Autre");
        catalog.save(other);
        Manifestation reissue = new Manifestation(
                ManifestationId.of("quixote-ecco-2019-pb"),
                List.of(CatalogFixture.GROSSMAN),
                Title.of("Don Quixote"),
                Publisher.of(CatalogFixture.ECCO_AGENT),
                BibliographicDate.year(2019),
                Carrier.PAPERBACK,
                Identifier.isbn("9780062391667"),
                Extent.pages(940),
                Series.STANDALONE,
                EditionStatement.UNSTATED,
                List.of(Contribution.of(other, Role.COVER_ARTIST)));
        catalog.save(reissue);

        assertThat(catalog.work(CatalogFixture.QUIXOTE).orElseThrow().expressions())
                .as("new art is a new printing, never a new translation")
                .hasSize(2);
        assertThat(catalog.creditsOf(ARTIST.id())).extracting(Credit::subject).containsExactly("Don Quixote");
        assertThat(catalog.creditsOf(other.id()))
                .as("each artist is credited on their own printing and no other")
                .singleElement()
                .isInstanceOfSatisfying(
                        Credit.OnEdition.class,
                        onEdition -> assertThat(onEdition.edition().id()).isEqualTo(reissue.id()));
    }

    @Test
    void survivesTheRoundTripThroughDisk(@TempDir Path root) {
        JsonCatalog store = CatalogFixture.writeInto(root);
        store.save(ARTIST);
        store.save(withCover(CatalogFixture.manifestation()));

        assertThat(new JsonCatalog(root).manifestations())
                .singleElement()
                .extracting(Manifestation::contributors)
                .isEqualTo(List.of(Contribution.of(ARTIST, Role.COVER_ARTIST)));
    }

    @Test
    void leavesAPrintingWithNoContributorsAloneInTheFile(@TempDir Path root) throws Exception {
        CatalogFixture.writeInto(root);

        assertThat(Files.readString(root.resolve("manifestations/quixote-ecco-2003-hb.json")))
                .as("an empty list is an absent optional, and absent optionals are omitted")
                .doesNotContain("contributors");
    }

    private static Manifestation withCover(Manifestation edition) {
        return new Manifestation(
                edition.id(),
                edition.embodies(),
                edition.title(),
                edition.publisher(),
                edition.published(),
                edition.carrier(),
                edition.identifier(),
                edition.extent(),
                edition.series(),
                edition.editionStatement(),
                List.of(Contribution.of(ARTIST, Role.COVER_ARTIST)));
    }
}
