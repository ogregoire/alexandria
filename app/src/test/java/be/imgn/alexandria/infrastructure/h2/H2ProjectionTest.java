package be.imgn.alexandria.infrastructure.h2;

import be.imgn.alexandria.CatalogFixture;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class H2ProjectionTest {

    @Test
    void projectsEveryAggregateIntoTheReadModel(@TempDir Path root) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        try (H2Projection projection = H2Projection.inMemory()) {
            projection.rebuildFrom(catalog);

            assertThat(projection.count("agent")).isEqualTo(3);
            assertThat(projection.count("agent_alias")).isEqualTo(1);
            assertThat(projection.count("work")).isEqualTo(1);
            assertThat(projection.count("expression")).isEqualTo(2);
            assertThat(projection.count("manifestation")).isEqualTo(1);
            assertThat(projection.count("item")).isEqualTo(1);
            assertThat(projection.count("manifestation_expression")).isEqualTo(1);
        }
    }

    @Test
    void joinsContributionsAndPublishersBackToTheAgentTable(@TempDir Path root) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        try (H2Projection projection = H2Projection.inMemory()) {
            projection.rebuildFrom(catalog);

            assertThat(projection.query("""
                    SELECT a.name, wc.role
                      FROM work_creator wc JOIN agent a ON a.id = wc.agent_id
                    """).rows())
                    .containsExactly(java.util.List.of("Miguel de Cervantes", "author"));

            assertThat(projection.query("""
                    SELECT a.name
                      FROM manifestation m JOIN agent a ON a.id = m.publisher_id
                    """).rows())
                    .containsExactly(java.util.List.of("Ecco"));

            assertThat(projection.query("SELECT alias FROM agent_alias").rows())
                    .containsExactly(java.util.List.of("Cervantes"));
        }
    }

    @Test
    void isRebuildableWithoutLeavingStaleRows(@TempDir Path root) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        try (H2Projection projection = H2Projection.inMemory()) {
            projection.rebuildFrom(catalog);
            projection.rebuildFrom(catalog);

            assertThat(projection.count("work")).isEqualTo(1);
        }
    }

    @Test
    void flattensSumTypesUsingTheSameNamesAsTheJsonFiles(@TempDir Path root) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        try (H2Projection projection = H2Projection.inMemory()) {
            projection.rebuildFrom(catalog);

            assertThat(projection.query("SELECT kind FROM expression ORDER BY kind").rows())
                    .containsExactly(java.util.List.of("original"), java.util.List.of("translation"));
            assertThat(projection.query("SELECT carrier_kind FROM manifestation").rows())
                    .containsExactly(java.util.List.of("hardcover"));
            assertThat(projection.query("SELECT acquisition_kind, reading_kind, location_kind FROM item").rows())
                    .containsExactly(java.util.List.of("purchased", "finished", "shelf"));
        }
    }

    @Test
    void answersTheQuestionsTheEditorAsksInSql(@TempDir Path root) {
        JsonCatalog catalog = CatalogFixture.writeInto(root);

        try (H2Projection projection = H2Projection.inMemory()) {
            projection.rebuildFrom(catalog);

            var read = projection.query("""
                    SELECT w.title, i.rating
                      FROM item i
                      JOIN manifestation_expression me ON me.manifestation_id = i.manifestation_id
                      JOIN expression e ON e.id = me.expression_id
                      JOIN work w ON w.id = e.work_id
                     WHERE i.reading_kind = 'finished'
                    """);

            assertThat(read.columns()).containsExactly("title", "rating");
            assertThat(read.rows()).containsExactly(java.util.List.of("Don Quixote", "5"));
        }
    }
}
