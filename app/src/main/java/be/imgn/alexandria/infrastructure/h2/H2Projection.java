package be.imgn.alexandria.infrastructure.h2;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.shared.Contribution;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.work.Expression;
import be.imgn.alexandria.domain.work.ExpressionKind;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.infrastructure.VariantNames;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A disposable H2 read model rebuilt from the JSON files on every start.
 *
 * <p>It exists for the questions that are awkward in Java and obvious in SQL — what is
 * unread, what is out on loan, how the shelf breaks down by language or decade. Nothing
 * writes to it: the editor saves JSON and asks for a rebuild.
 */
public final class H2Projection implements AutoCloseable {

    private final Connection connection;

    private H2Projection(Connection connection) {
        this.connection = connection;
    }

    public static H2Projection inMemory() {
        return open("jdbc:h2:mem:alexandria;DB_CLOSE_DELAY=-1");
    }

    /** File-backed so the catalogue can be opened with any SQL tool while the editor runs. */
    public static H2Projection at(java.nio.file.Path file) {
        return open("jdbc:h2:" + file.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
    }

    private static H2Projection open(String url) {
        try {
            return new H2Projection(DriverManager.getConnection(url, "alexandria", ""));
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open the H2 projection: " + e.getMessage(), e);
        }
    }

    /** Drops everything and reloads from the catalogue. Cheap: a personal library is small. */
    public void rebuildFrom(Catalog catalog) {
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP ALL OBJECTS");
                statement.execute(Schema.DDL);
            }
            AgentDirectory directory = catalog.directory();
            for (Agent agent : catalog.agents()) {
                insertAgent(agent);
            }
            for (Work work : catalog.works()) {
                insertWork(work, directory);
            }
            for (Manifestation manifestation : catalog.manifestations()) {
                insertManifestation(manifestation, directory);
            }
            for (Item item : catalog.items()) {
                insertItem(item);
            }
            connection.commit();
        } catch (SQLException e) {
            rollback();
            throw new IllegalStateException("cannot rebuild the projection: " + e.getMessage(), e);
        } finally {
            autoCommit();
        }
    }

    // ---------------------------------------------------------------- queries

    public record Row(List<String> columns, List<List<String>> rows) {
    }

    /** Runs a read-only query and returns it as strings, ready for a table. */
    public Row query(String sql, Object... parameters) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet results = statement.executeQuery()) {
                int width = results.getMetaData().getColumnCount();
                List<String> columns = new ArrayList<>(width);
                for (int i = 1; i <= width; i++) {
                    columns.add(results.getMetaData().getColumnLabel(i).toLowerCase(java.util.Locale.ROOT));
                }
                List<List<String>> rows = new ArrayList<>();
                while (results.next()) {
                    List<String> row = new ArrayList<>(width);
                    for (int i = 1; i <= width; i++) {
                        Object value = results.getObject(i);
                        row.add(value == null ? "" : String.valueOf(value));
                    }
                    rows.add(List.copyOf(row));
                }
                return new Row(List.copyOf(columns), List.copyOf(rows));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed: " + e.getMessage(), e);
        }
    }

    public long count(String table) {
        return Long.parseLong(query("SELECT COUNT(*) FROM " + table).rows().getFirst().getFirst());
    }

    // ------------------------------------------------------------- projection

    private void insertAgent(Agent agent) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO agent VALUES (?,?,?,?)")) {
            statement.setString(1, agent.id().value());
            statement.setString(2, variantOf(agent.kind()));
            statement.setString(3, agent.name());
            statement.setString(4, agent.sortName());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO agent_alias VALUES (?,?)")) {
            for (String alias : agent.aliases()) {
                statement.setString(1, agent.id().value());
                statement.setString(2, alias);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertWork(Work work, AgentDirectory directory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO work VALUES (?,?,?,?,?,?,?,?)")) {
            statement.setString(1, work.id().value());
            statement.setString(2, work.title().main());
            setNullable(statement, 3, work.title().subtitle(), Function.identity());
            statement.setString(4, work.byline());
            statement.setString(5, work.sortKey(directory));
            statement.setString(6, work.form().label());
            statement.setString(7, work.created().display());
            setNullableInt(statement, 8, work.created().sortYear());
            statement.executeUpdate();
        }
        insertContributions("work_creator", work.id().value(), work.creators());
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO work_subject VALUES (?,?)")) {
            for (String subject : work.subjects()) {
                statement.setString(1, work.id().value());
                statement.setString(2, subject);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (Expression expression : work.expressions()) {
            insertExpression(work, expression, directory);
        }
    }

    private void insertExpression(Work work, Expression expression, AgentDirectory directory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO expression VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, expression.id().qualified());
            statement.setString(2, work.id().value());
            statement.setString(3, variantOf(expression.kind()));
            statement.setString(4, expression.language().code());
            statement.setString(5, expression.language().displayName());
            String source = expression.kind() instanceof ExpressionKind.Translation(Language from)
                    ? from.code()
                    : null;
            statement.setString(6, source);
            statement.setString(7, expression.realised().display());
            setNullableInt(statement, 8, expression.realised().sortYear());
            statement.setString(9, expression.describe());
            statement.executeUpdate();
        }
        insertContributions("expression_contributor", expression.id().qualified(), expression.contributors());
    }

    private void insertContributions(String table, String owner, List<Contribution> contributions)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " VALUES (?,?,?,?)")) {
            for (Contribution contribution : contributions) {
                statement.setString(1, owner);
                statement.setString(2, contribution.agent().value());
                statement.setString(3, contribution.role().label());
                statement.setString(4, contribution.publishedAs());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertManifestation(Manifestation manifestation, AgentDirectory directory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO manifestation VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, manifestation.id().value());
            statement.setString(2, manifestation.title().main());
            setNullable(statement, 3, manifestation.publisher(), AgentId::value);
            statement.setString(4, manifestation.published().display());
            setNullableInt(statement, 5, manifestation.published().sortYear());
            statement.setString(6, variantOf(manifestation.carrier()));
            statement.setString(7, manifestation.carrier().label());
            statement.setBoolean(8, manifestation.carrier().physical());
            String identifier = manifestation.identifier().display();
            statement.setString(9, identifier.isEmpty() ? null : identifier);
            String extent = manifestation.extent().display();
            statement.setString(10, extent.isEmpty() ? null : extent);
            setNullableInt(statement, 11, pagesOf(manifestation.extent()));
            setNullable(statement, 12, manifestation.series(), Series::display);
            setNullableInt(statement, 13, manifestation.editionStatement());
            statement.setString(14, manifestation.imprint(directory));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO manifestation_expression VALUES (?,?)")) {
            for (var expression : manifestation.embodies()) {
                statement.setString(1, manifestation.id().value());
                statement.setString(2, expression.qualified());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertItem(Item item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO item VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, item.id().value());
            statement.setString(2, item.embodiedIn().value());
            statement.setString(3, variantOf(item.acquisition()));
            setNullableDate(statement, 4, item.acquisition().on());
            Optional<Money> price = item.acquisition() instanceof Acquisition.Purchased(
                    LocalDate ignored, Optional<Money> paid, Optional<String> alsoIgnored) ? paid : Optional.empty();
            setNullableDecimal(statement, 5, price.map(Money::amount));
            setNullable(statement, 6, price, m -> m.currency().getCurrencyCode());
            statement.setBoolean(7, item.acquisition().owned());
            statement.setString(8, variantOf(item.location()));
            statement.setString(9, item.location().display());
            statement.setBoolean(10, item.location().athand());
            statement.setString(11, variantOf(item.reading()));
            statement.setString(12, item.reading().display());
            Optional<LocalDate> finished = item.reading() instanceof ReadingProgress.Finished(
                    LocalDate on, Optional<Rating> ignored) ? Optional.of(on) : Optional.empty();
            setNullableDate(statement, 13, finished);
            Optional<Integer> stars = item.reading() instanceof ReadingProgress.Finished(
                    LocalDate ignored, Optional<Rating> rating) ? rating.map(Rating::stars) : Optional.empty();
            setNullableInt(statement, 14, stars);
            statement.setString(15, item.condition().label());
            setNullable(statement, 16, item.notes(), Function.identity());
            statement.executeUpdate();
        }
    }

    private static Optional<Integer> pagesOf(Extent extent) {
        return switch (extent) {
            case Extent.Pages(int count) -> Optional.of(count);
            case Extent.Volumes(int ignored, int pagesTotal) -> Optional.of(pagesTotal);
            case Extent.Playtime ignored -> Optional.empty();
            case Extent.Unspecified() -> Optional.empty();
        };
    }

    private static String variantOf(Object variant) {
        return VariantNames.of(variant);
    }

    private static <T> void setNullable(PreparedStatement statement, int index, Optional<T> value,
                                        Function<T, String> render) throws SQLException {
        Optional<String> rendered = value.map(render);
        if (rendered.isPresent()) {
            statement.setString(index, rendered.get());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Optional<Integer> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setInt(index, value.get());
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private static void setNullableDate(PreparedStatement statement, int index, Optional<LocalDate> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setDate(index, Date.valueOf(value.get()));
        } else {
            statement.setNull(index, Types.DATE);
        }
    }

    private static void setNullableDecimal(PreparedStatement statement, int index, Optional<BigDecimal> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setBigDecimal(index, value.get());
        } else {
            statement.setNull(index, Types.DECIMAL);
        }
    }

    private void rollback() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the caller is already throwing; a failed rollback adds nothing
        }
    }

    private void autoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            // same
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot close the projection", e);
        }
    }
}
