package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.application.Reports;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Questions that cut across all four WEMI levels at once. */
final class ReportPages {

    private final CatalogService service;

    ReportPages(CatalogService service) {
        this.service = service;
    }

    String index() {
        String links = Reports.index().stream()
                .map(definition -> """
                        <li>%s<span class="hint">%s</span></li>
                        """.formatted(
                        Html.link("/reports/" + definition.id(), definition.title()),
                        Html.escape(definition.explanation())))
                .collect(Collectors.joining());
        return Html.page("Reports", Html.link("/", "Home") + " / Reports",
                "<h1>Reports</h1><ul class=\"reports\">" + links + "</ul>");
    }

    Optional<String> show(String id) {
        return Reports.compute(service.catalog(), id).map(table -> {
            List<List<String>> rows = table.rows().stream()
                    .map(row -> row.stream().map(Html::escape).toList())
                    .toList();
            return Html.page(table.definition().title(),
                    Html.link("/reports", "Reports") + " / " + Html.escape(table.definition().title()),
                    """
                            <h1>%s</h1>
                            <p class="hint">%s</p>
                            %s
                            """.formatted(
                            Html.escape(table.definition().title()),
                            Html.escape(table.definition().explanation()),
                            Html.table(table.columns(), rows)));
        });
    }
}
