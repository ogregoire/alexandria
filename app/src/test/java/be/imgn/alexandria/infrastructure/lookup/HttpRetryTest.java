package be.imgn.alexandria.infrastructure.lookup;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real {@link Http} against a local server, so the retry and pacing behaviour is
 * exercised for real rather than asserted about. Intervals are kept tiny to stay fast.
 */
class HttpRetryTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<Long> arrivals = new CopyOnWriteArrayList<>();

    private String start(Responder responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            arrivals.add(System.nanoTime());
            responder.respond(exchange, requests.incrementAndGet());
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/book";
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private interface Responder {
        void respond(HttpExchange exchange, int requestNumber) throws IOException;
    }

    private static void reply(HttpExchange exchange, int status, String body, String retryAfter)
            throws IOException {
        if (retryAfter != null) {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Test
    void returnsTheBodyOfASuccessfulRequest() throws Exception {
        String url = start((exchange, number) -> reply(exchange, 200, "{\"ok\":true}", null));

        assertThat(new Http(Duration.ZERO, "test").get(url)).contains("{\"ok\":true}");
    }

    @Test
    void retriesAfterA429AndSucceedsOnTheSecondTry() throws Exception {
        String url = start((exchange, number) ->
                reply(exchange, number == 1 ? 429 : 200, number == 1 ? "" : "recovered", null));

        Optional<String> body = new Http(Duration.ZERO, "test").get(url);

        assertThat(body).contains("recovered");
        assertThat(requests).hasValue(2);
    }

    @Test
    void honoursRetryAfterRatherThanItsOwnBackoff() throws Exception {
        String url = start((exchange, number) ->
                reply(exchange, number == 1 ? 429 : 200, number == 1 ? "" : "recovered", "1"));

        long before = System.nanoTime();
        assertThat(new Http(Duration.ZERO, "test").get(url)).contains("recovered");

        assertThat(Duration.ofNanos(System.nanoTime() - before))
                .as("Retry-After: 1 must be waited out")
                .isGreaterThanOrEqualTo(Duration.ofMillis(900));
    }

    @Test
    void givesUpAfterTheRetryBudgetIsSpent() throws Exception {
        String url = start((exchange, number) -> reply(exchange, 429, "", null));

        assertThat(new Http(Duration.ZERO, "test").get(url)).isEmpty();
        assertThat(requests).as("the first attempt plus two retries").hasValue(3);
    }

    @Test
    void doesNotRetryAStatusThatWillNotChange() throws Exception {
        String url = start((exchange, number) -> reply(exchange, 404, "", null));

        assertThat(new Http(Duration.ZERO, "test").get(url)).isEmpty();
        assertThat(requests).hasValue(1);
    }

    @Test
    void pacesConsecutiveRequestsToTheSameHost() throws Exception {
        String url = start((exchange, number) -> reply(exchange, 200, "ok", null));
        Http http = new Http(Duration.ofMillis(300), "test");

        http.get(url);
        http.get(url);
        http.get(url);

        assertThat(arrivals).hasSize(3);
        assertThat(Duration.ofNanos(arrivals.get(1) - arrivals.get(0)))
                .isGreaterThanOrEqualTo(Duration.ofMillis(280));
        assertThat(Duration.ofNanos(arrivals.get(2) - arrivals.get(1)))
                .isGreaterThanOrEqualTo(Duration.ofMillis(280));
    }

    @Test
    void sendsTheUserAgentItWasGiven() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        String url = start((exchange, number) -> {
            seen.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            reply(exchange, 200, "ok", null);
        });

        new Http(Duration.ZERO, UserAgent.identifiedBy("me@example.org").header()).get(url);

        assertThat(seen).singleElement().satisfies(agent ->
                assertThat(agent).contains("Alexandria/1.0").contains("me@example.org"));
    }
}
