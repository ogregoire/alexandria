package be.imgn.alexandria.infrastructure.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.application.Reports;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentResolution;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkId;

/**
 * The local editing app: the JDK's own HTTP server, server-rendered forms, no framework.
 *
 * <p>It binds to loopback only. This is a single-user tool editing files in a git working copy, so it has no
 * authentication and must not be reachable from the network.
 */
public final class Editor {

    private final CatalogService service;
    private final BookLookup lookup;
    private final ImportPages imports;
    private final AgentPages agents;
    private final WorkPages works;
    private final ManifestationPages manifestations;
    private final ItemPages items;
    private final ReportPages reports;
    private final Router router = new Router();
    private HttpServer server;

    public Editor(CatalogService service) {
        this(service, BookLookup.offline());
    }

    public Editor(CatalogService service, BookLookup lookup) {
        this.service = service;
        this.lookup = lookup;
        this.imports = new ImportPages(service, lookup);
        this.agents = new AgentPages(service);
        this.works = new WorkPages(service);
        this.manifestations = new ManifestationPages(service);
        this.items = new ItemPages(service);
        this.reports = new ReportPages(service);
        routes();
    }

    public int start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server.getAddress().getPort();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot start the editor on port " + port, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void routes() {
        router.get("/", request -> Router.Response.html(home()));

        router.get(
                "/import",
                request -> Router.Response.html(imports.ask(request.query().orEmpty("isbn"), null)));
        router.post("/import", request -> {
            String typed = request.body().orEmpty("isbn");
            Identifier isbn;
            try {
                isbn = Identifier.isbn(typed);
            } catch (RuntimeException e) {
                return Router.Response.html(
                        imports.ask(typed, "'" + typed + "' is not a valid ISBN: " + e.getMessage()));
            }
            return Router.Response.html(
                    imports.review(isbn, lookup.byIsbn(isbn), request.body().checked("addItem")));
        });
        // A rejected form comes back as HTML holding what was typed, not as an error page.
        router.post("/import/save", request -> {
            AgentResolution resolution = service.newResolution();
            return switch (imports.read(request.body(), resolution)) {
                case ImportPages.Outcome.Rejected(FormState state) -> Router.Response.html(imports.reviewAgain(state));
                case ImportPages.Outcome.Book(Work work, Manifestation manifestation, var copy) -> {
                    try {
                        service.saveNewBook(work, manifestation, copy, resolution);
                        yield Router.Response.seeOther("/works/" + work.id().value());
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        FormProblems clash = new FormProblems();
                        clash.general(e.getMessage() == null ? e.toString() : e.getMessage());
                        yield Router.Response.html(imports.reviewAgain(FormState.submitted(request.body(), clash)));
                    }
                }
            };
        });

        router.get("/agents", request -> Router.Response.html(agents.list()));
        router.get("/agents/new", request -> Router.Response.html(agents.edit(Optional.empty())));
        router.get("/agents/{id}", request -> service.catalog()
                .agent(AgentId.of(request.param("id")))
                .map(agent -> Router.Response.html(agents.edit(Optional.of(agent))))
                .orElseGet(() -> Router.Response.error(404, "No agent " + request.param("id"))));
        router.post("/agents/{id}", request -> {
            service.save(agents.read(request.body()));
            return Router.Response.seeOther("/agents");
        });
        router.post("/agents/{id}/delete", request -> {
            service.deleteAgent(AgentId.of(request.param("id")));
            return Router.Response.seeOther("/agents");
        });

        router.get("/works", request -> Router.Response.html(works.list()));
        router.get("/works/new", request -> Router.Response.html(works.edit(Optional.empty())));
        router.get("/works/{id}", request -> service.catalog()
                .work(WorkId.of(request.param("id")))
                .map(work -> Router.Response.html(works.edit(Optional.of(work))))
                .orElseGet(() -> Router.Response.error(404, "No work " + request.param("id"))));
        router.post("/works/{id}", request -> {
            AgentResolution resolution = service.newResolution();
            Work work = works.read(request.body(), resolution);
            service.save(work, resolution);
            return Router.Response.seeOther("/works");
        });
        router.post("/works/{id}/delete", request -> {
            service.deleteWork(WorkId.of(request.param("id")));
            return Router.Response.seeOther("/works");
        });

        router.get("/manifestations", request -> Router.Response.html(manifestations.list()));
        router.get("/manifestations/new", request -> Router.Response.html(manifestations.edit(Optional.empty())));
        router.get("/manifestations/{id}", request -> service.catalog()
                .manifestation(ManifestationId.of(request.param("id")))
                .map(manifestation -> Router.Response.html(manifestations.edit(Optional.of(manifestation))))
                .orElseGet(() -> Router.Response.error(404, "No manifestation " + request.param("id"))));
        router.post("/manifestations/{id}", request -> {
            AgentResolution resolution = service.newResolution();
            Manifestation manifestation = manifestations.read(request.body(), resolution);
            service.save(manifestation, resolution);
            return Router.Response.seeOther("/manifestations");
        });
        router.post("/manifestations/{id}/delete", request -> {
            service.deleteManifestation(ManifestationId.of(request.param("id")));
            return Router.Response.seeOther("/manifestations");
        });

        router.get("/items", request -> Router.Response.html(items.list()));
        router.get("/items/new", request -> Router.Response.html(items.edit(Optional.empty())));
        router.get("/items/{id}", request -> service.catalog()
                .item(ItemId.of(request.param("id")))
                .map(item -> Router.Response.html(items.edit(Optional.of(item))))
                .orElseGet(() -> Router.Response.error(404, "No item " + request.param("id"))));
        router.post("/items/{id}", request -> {
            service.save(items.read(request.body()));
            return Router.Response.seeOther("/items");
        });
        router.post("/items/{id}/delete", request -> {
            service.deleteItem(ItemId.of(request.param("id")));
            return Router.Response.seeOther("/items");
        });

        router.get("/reports", request -> Router.Response.html(reports.index()));
        router.get("/reports/{name}", request -> reports.show(request.param("name"))
                .map(Router.Response::html)
                .orElseGet(() -> Router.Response.error(404, "No report " + request.param("name"))));

        router.get("/assets/{file}", request -> asset(request.param("file")));
    }

    private String home() {
        var counts = Reports.counts(service.catalog());
        String problems = service.problems().isEmpty()
                ? "<p class=\"ok\">The catalogue is consistent.</p>"
                : "<div class=\"error\"><h2>Problems</h2><ul>"
                        + service.problems().stream()
                                .map(problem -> "<li>" + Html.escape(problem.toString()) + "</li>")
                                .collect(Collectors.joining())
                        + "</ul></div>";

        return Html.page("Alexandria", "Home", """
                <h1>Alexandria</h1>
                <p class="hint">Four levels: a <em>work</em> is realised through an <em>expression</em>
                   (a translation, an abridgement), which is embodied in a <em>manifestation</em>
                   (an edition), which is exemplified by an <em>item</em> (your copy).</p>
                %s
                <div class="tiles">
                  <a class="tile" href="/agents"><strong>%d</strong><span>agents</span></a>
                  <a class="tile" href="/works"><strong>%d</strong><span>works</span></a>
                  <a class="tile" href="/works"><strong>%d</strong><span>expressions</span></a>
                  <a class="tile" href="/manifestations"><strong>%d</strong><span>manifestations</span></a>
                  <a class="tile" href="/items"><strong>%d</strong><span>items</span></a>
                </div>
                <p><a class="button" href="/import">Add a book from its ISBN</a></p>
                <p>Saving writes a JSON file under <code>data/</code>. Commit that file and the
                   change is in the catalogue's history.</p>
                """.formatted(
                        problems,
                        counts.get("agents"),
                        counts.get("works"),
                        counts.get("expressions"),
                        counts.get("manifestations"),
                        counts.get("items")));
    }

    private Router.Response asset(String file) {
        String resource = "/web/" + file;
        try (InputStream in = Editor.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Router.Response.error(404, "No asset " + file);
            }
            String type = file.endsWith(".css")
                    ? "text/css; charset=utf-8"
                    : file.endsWith(".js") ? "text/javascript; charset=utf-8" : "application/octet-stream";
            return Router.Response.asset(type, in.readAllBytes());
        } catch (IOException e) {
            return Router.Response.error(500, e.getMessage());
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        Router.Response response;
        try {
            response = router.dispatch(exchange);
        } catch (IllegalArgumentException | IllegalStateException e) {
            response = Router.Response.error(400, e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (RuntimeException e) {
            response = Router.Response.error(500, describe(e));
        }
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            exchange.getResponseHeaders().add(header.getKey(), header.getValue());
        }
        exchange.getResponseHeaders().add("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
        if (response.body().length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response.body());
            }
        }
        exchange.close();
    }

    private static String describe(RuntimeException e) {
        List<String> frames = Arrays.stream(e.getStackTrace())
                .limit(8)
                .map(StackTraceElement::toString)
                .toList();
        return e + "\n  at " + String.join("\n  at ", frames);
    }
}
