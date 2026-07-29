package be.imgn.alexandria.infrastructure.h2;

/**
 * The read model. Every column here is derived from the JSON files and can be thrown away
 * and rebuilt, which is why the database file is not committed.
 *
 * <p>Sum types are flattened the same way everywhere: a {@code *_kind} column carrying the
 * variant name, plus nullable columns for that variant's payload. Nothing reconstructs a
 * domain object from these rows — the aggregates are read from JSON — so the flattening
 * costs no type safety.
 */
final class Schema {

    private Schema() {
    }

    static final String DDL = """
            CREATE TABLE agent (
              id        VARCHAR(200) PRIMARY KEY,
              kind      VARCHAR(40)  NOT NULL,
              name      VARCHAR(300) NOT NULL,
              sort_name VARCHAR(300) NOT NULL
            );

            CREATE TABLE agent_alias (
              agent_id VARCHAR(200) NOT NULL REFERENCES agent(id),
              alias    VARCHAR(300) NOT NULL
            );

            CREATE TABLE work (
              id            VARCHAR(200) PRIMARY KEY,
              title         VARCHAR(500) NOT NULL,
              subtitle      VARCHAR(500),
              byline        VARCHAR(500) NOT NULL,
              sort_key      VARCHAR(700) NOT NULL,
              form          VARCHAR(80)  NOT NULL,
              created_shown VARCHAR(40)  NOT NULL,
              created_year  INT
            );

            CREATE TABLE work_creator (
              work_id      VARCHAR(200) NOT NULL REFERENCES work(id),
              agent_id     VARCHAR(200) NOT NULL REFERENCES agent(id),
              role         VARCHAR(80)  NOT NULL,
              published_as VARCHAR(300) NOT NULL
            );

            CREATE TABLE work_subject (
              work_id VARCHAR(200) NOT NULL REFERENCES work(id),
              subject VARCHAR(200) NOT NULL
            );

            CREATE TABLE expression (
              id             VARCHAR(400) PRIMARY KEY,
              work_id        VARCHAR(200) NOT NULL REFERENCES work(id),
              kind           VARCHAR(40)  NOT NULL,
              language       VARCHAR(8)   NOT NULL,
              language_shown VARCHAR(80)  NOT NULL,
              source_language VARCHAR(8),
              realised_shown VARCHAR(40)  NOT NULL,
              realised_year  INT,
              summary        VARCHAR(500) NOT NULL
            );

            CREATE TABLE expression_contributor (
              expression_id VARCHAR(400) NOT NULL REFERENCES expression(id),
              agent_id      VARCHAR(200) NOT NULL REFERENCES agent(id),
              role          VARCHAR(80)  NOT NULL,
              published_as  VARCHAR(300) NOT NULL
            );

            CREATE TABLE manifestation (
              id              VARCHAR(200) PRIMARY KEY,
              title           VARCHAR(500) NOT NULL,
              publisher_id    VARCHAR(200) REFERENCES agent(id),
              published_shown VARCHAR(40)  NOT NULL,
              published_year  INT,
              carrier_kind    VARCHAR(40)  NOT NULL,
              carrier_shown   VARCHAR(80)  NOT NULL,
              physical        BOOLEAN      NOT NULL,
              identifier      VARCHAR(80),
              extent_shown    VARCHAR(40),
              pages           INT,
              series          VARCHAR(300),
              edition         INT,
              imprint         VARCHAR(700) NOT NULL
            );

            CREATE TABLE manifestation_expression (
              manifestation_id VARCHAR(200) NOT NULL REFERENCES manifestation(id),
              expression_id    VARCHAR(400) NOT NULL REFERENCES expression(id)
            );

            CREATE TABLE item (
              id               VARCHAR(200) PRIMARY KEY,
              manifestation_id VARCHAR(200) NOT NULL REFERENCES manifestation(id),
              acquisition_kind VARCHAR(40)  NOT NULL,
              acquired_on      DATE,
              price            DECIMAL(12,2),
              price_currency   VARCHAR(8),
              owned            BOOLEAN      NOT NULL,
              location_kind    VARCHAR(40)  NOT NULL,
              location_shown   VARCHAR(300) NOT NULL,
              at_hand          BOOLEAN      NOT NULL,
              reading_kind     VARCHAR(40)  NOT NULL,
              reading_shown    VARCHAR(300) NOT NULL,
              finished_on      DATE,
              rating           INT,
              condition_grade  VARCHAR(40)  NOT NULL,
              notes            VARCHAR(4000)
            );

            CREATE INDEX idx_agent_alias ON agent_alias(agent_id);
            CREATE INDEX idx_work_creator ON work_creator(agent_id);
            CREATE INDEX idx_expression_contributor ON expression_contributor(agent_id);
            CREATE INDEX idx_expression_work ON expression(work_id);
            CREATE INDEX idx_manifestation_expression ON manifestation_expression(expression_id);
            CREATE INDEX idx_item_manifestation ON item(manifestation_id);
            """;
}
