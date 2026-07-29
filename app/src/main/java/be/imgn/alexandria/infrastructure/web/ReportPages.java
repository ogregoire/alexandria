package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.infrastructure.h2.H2Projection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The reason H2 is in the stack. These questions cut across all four WEMI levels at once,
 * which is exactly where SQL beats walking object graphs.
 */
final class ReportPages {

    private record Report(String title, String explanation, String sql) {
    }

    private static final Map<String, Report> REPORTS = reports();

    private final CatalogService service;

    ReportPages(CatalogService service) {
        this.service = service;
    }

    String index() {
        String links = REPORTS.entrySet().stream()
                .map(entry -> """
                        <li>%s<span class="hint">%s</span></li>
                        """.formatted(
                        Html.link("/reports/" + entry.getKey(), entry.getValue().title()),
                        Html.escape(entry.getValue().explanation())))
                .collect(Collectors.joining());
        return Html.page("Reports", Html.link("/", "Home") + " / Reports",
                "<h1>Reports</h1><ul class=\"reports\">" + links + "</ul>");
    }

    Optional<String> show(String name) {
        Report report = REPORTS.get(name);
        if (report == null) {
            return Optional.empty();
        }
        H2Projection.Row result = service.projection().query(report.sql());
        List<List<String>> rows = result.rows().stream()
                .map(row -> row.stream().map(Html::escape).toList())
                .toList();
        return Optional.of(Html.page(report.title(),
                Html.link("/reports", "Reports") + " / " + Html.escape(report.title()), """
                        <h1>%s</h1>
                        <p class="hint">%s</p>
                        %s
                        <details><summary>Query</summary><pre>%s</pre></details>
                        """.formatted(
                        Html.escape(report.title()),
                        Html.escape(report.explanation()),
                        Html.table(result.columns(), rows),
                        Html.escape(report.sql()))));
    }

    private static Map<String, Report> reports() {
        Map<String, Report> reports = new LinkedHashMap<>();

        reports.put("unread", new Report("Unread copies",
                "Copies on the shelf that have never been started.", """
                        SELECT w.title AS work, e.summary AS expression, m.imprint AS edition, i.location_shown AS where_
                          FROM item i
                          JOIN manifestation m ON m.id = i.manifestation_id
                          JOIN manifestation_expression me ON me.manifestation_id = m.id
                          JOIN expression e ON e.id = me.expression_id
                          JOIN work w ON w.id = e.work_id
                         WHERE i.reading_kind = 'unread'
                         ORDER BY w.sort_key
                        """));

        reports.put("reading", new Report("Currently reading",
                "Started and not yet finished.", """
                        SELECT w.title AS work, i.reading_shown AS progress, i.location_shown AS where_
                          FROM item i
                          JOIN manifestation m ON m.id = i.manifestation_id
                          JOIN manifestation_expression me ON me.manifestation_id = m.id
                          JOIN expression e ON e.id = me.expression_id
                          JOIN work w ON w.id = e.work_id
                         WHERE i.reading_kind = 'reading'
                         ORDER BY w.sort_key
                        """));

        reports.put("loans", new Report("Out and in",
                "Copies lent to someone, and copies borrowed that are not yours.", """
                        SELECT CASE WHEN i.owned THEN 'lent out' ELSE 'borrowed' END AS direction,
                               w.title AS work, i.location_shown AS detail, i.acquired_on AS since
                          FROM item i
                          JOIN manifestation m ON m.id = i.manifestation_id
                          JOIN manifestation_expression me ON me.manifestation_id = m.id
                          JOIN expression e ON e.id = me.expression_id
                          JOIN work w ON w.id = e.work_id
                         WHERE i.location_kind = 'lent-to' OR NOT i.owned
                         ORDER BY direction, w.sort_key
                        """));

        reports.put("publishers", new Report("Publishers",
                "Which houses the shelf is actually made of.", """
                        SELECT a.name AS publisher, COUNT(DISTINCT m.id) AS editions, COUNT(i.id) AS copies
                          FROM agent a
                          JOIN manifestation m ON m.publisher_id = a.id
                          LEFT JOIN item i ON i.manifestation_id = m.id
                         GROUP BY a.name
                         ORDER BY copies DESC, editions DESC, publisher
                        """));

        reports.put("people", new Report("People",
                "Everyone in the registry and what they did, aliases included.", """
                        SELECT a.sort_name AS files_under, a.name,
                               LISTAGG(DISTINCT r.role, ', ') WITHIN GROUP (ORDER BY r.role) AS roles,
                               COUNT(DISTINCT r.owner) AS records,
                               (SELECT LISTAGG(al.alias, ' · ') WITHIN GROUP (ORDER BY al.alias)
                                  FROM agent_alias al WHERE al.agent_id = a.id) AS aliases
                          FROM agent a
                          LEFT JOIN (
                                SELECT agent_id, role, work_id AS owner FROM work_creator
                                 UNION ALL
                                SELECT agent_id, role, expression_id AS owner FROM expression_contributor
                          ) r ON r.agent_id = a.id
                         WHERE a.kind = 'person'
                         GROUP BY a.id, a.sort_name, a.name
                         ORDER BY files_under
                        """));

        reports.put("orphan-agents", new Report("Agents nothing refers to",
                "Usually a name typed once with a typo. Safe to delete.", """
                        SELECT a.name, a.kind
                          FROM agent a
                         WHERE NOT EXISTS (SELECT 1 FROM work_creator wc WHERE wc.agent_id = a.id)
                           AND NOT EXISTS (SELECT 1 FROM expression_contributor ec WHERE ec.agent_id = a.id)
                           AND NOT EXISTS (SELECT 1 FROM manifestation m WHERE m.publisher_id = a.id)
                         ORDER BY a.name
                        """));

        reports.put("languages", new Report("By language",
                "Which expressions the library actually holds, by language.", """
                        SELECT e.language_shown AS language, COUNT(DISTINCT e.id) AS expressions,
                               COUNT(i.id) AS copies
                          FROM expression e
                          LEFT JOIN manifestation_expression me ON me.expression_id = e.id
                          LEFT JOIN item i ON i.manifestation_id = me.manifestation_id
                         GROUP BY e.language_shown
                         ORDER BY copies DESC, language
                        """));

        reports.put("translations", new Report("Works held in translation",
                "Works where what is on the shelf is not the original language.", """
                        SELECT w.title AS work, e.source_language AS from_, e.language_shown AS into_,
                               e.summary AS expression
                          FROM expression e
                          JOIN work w ON w.id = e.work_id
                         WHERE e.kind = 'translation'
                         ORDER BY w.sort_key
                        """));

        reports.put("decades", new Report("By decade",
                "When the works were created, not when the copies were printed.", """
                        SELECT (w.created_year / 10) * 10 AS decade, COUNT(*) AS works
                          FROM work w
                         WHERE w.created_year IS NOT NULL
                         GROUP BY decade
                         ORDER BY decade
                        """));

        reports.put("ratings", new Report("Ratings",
                "Everything finished and rated, best first.", """
                        SELECT i.rating, w.title AS work, e.summary AS expression, i.finished_on
                          FROM item i
                          JOIN manifestation m ON m.id = i.manifestation_id
                          JOIN manifestation_expression me ON me.manifestation_id = m.id
                          JOIN expression e ON e.id = me.expression_id
                          JOIN work w ON w.id = e.work_id
                         WHERE i.rating IS NOT NULL
                         ORDER BY i.rating DESC, i.finished_on DESC
                        """));

        reports.put("shelves", new Report("Shelves",
                "How the physical library is distributed.", """
                        SELECT i.location_shown AS location, COUNT(*) AS copies
                          FROM item i
                         GROUP BY i.location_shown
                         ORDER BY copies DESC, location
                        """));

        reports.put("spending", new Report("Spending",
                "What the library cost, by year and currency.", """
                        SELECT YEAR(i.acquired_on) AS year, i.price_currency AS currency,
                               SUM(i.price) AS spent, COUNT(*) AS copies
                          FROM item i
                         WHERE i.price IS NOT NULL
                         GROUP BY year, currency
                         ORDER BY year DESC
                        """));

        return java.util.Collections.unmodifiableMap(reports);
    }
}
