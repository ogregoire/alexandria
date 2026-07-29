package be.imgn.alexandria.infrastructure.lookup;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * The one place that makes outbound calls, and the one place that respects the services'
 * rate limits.
 *
 * <p>Everything here is deliberately meek: a short timeout, a bounded number of retries, and
 * a miss for any status that is not 200. A lookup is a convenience while cataloguing a book
 * by hand, so a slow, absent or annoyed service must degrade to an empty form rather than
 * hang the editor.
 */
class Http {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /** Two attempts after the first, so an interactive lookup can never stall for long. */
    private static final int RETRIES = 2;

    /** However long a service asks us to wait, we will not block the editor for longer. */
    private static final Duration MAXIMUM_BACKOFF = Duration.ofSeconds(4);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Throttle throttle;
    private final String userAgent;

    Http() {
        this(Duration.ZERO, UserAgent.unidentified());
    }

    Http(Duration minimumInterval, String userAgent) {
        this.throttle = new Throttle(minimumInterval);
        this.userAgent = userAgent;
    }

    /**
     * The body of a 200 response, or empty for anything else.
     *
     * <p>Waits for this host's next free slot before asking, and on a 429 or a 503 backs the
     * host off — honouring {@code Retry-After} when the service sends one — before trying
     * again. Overridden in tests, which never reach the network and so never sleep.
     */
    Optional<String> get(String url) {
        String host = hostOf(url);
        for (int attempt = 0; attempt <= RETRIES; attempt++) {
            if (!sleep(throttle.reserveMillis(host))) {
                return Optional.empty();
            }
            Optional<HttpResponse<String>> response = send(url);
            if (response.isEmpty()) {
                return Optional.empty();
            }
            HttpResponse<String> received = response.get();
            if (received.statusCode() == 200) {
                return Optional.of(received.body());
            }
            if (!isWorthRetrying(received.statusCode()) || attempt == RETRIES) {
                return Optional.empty();
            }
            throttle.backOff(host, backoffFor(received, attempt));
        }
        return Optional.empty();
    }

    private Optional<HttpResponse<String>> send(String url) {
        try {
            return Optional.of(client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("User-Agent", userAgent)
                            .header("Accept", "application/json, application/xml;q=0.8, */*;q=0.5")
                            .timeout(TIMEOUT)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()));
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** 429 is the rate limit; 503 is usually a service asking to be left alone briefly. */
    private static boolean isWorthRetrying(int status) {
        return status == 429 || status == 503;
    }

    /**
     * {@code Retry-After} if the service sent one, otherwise doubling — the exponential
     * backoff Google asks for on a 429 — and never longer than {@link #MAXIMUM_BACKOFF}.
     */
    private static Duration backoffFor(HttpResponse<String> response, int attempt) {
        Duration requested = response.headers().firstValue("Retry-After")
                .flatMap(Http::parseRetryAfter)
                .orElse(Duration.ofMillis(500L << attempt));
        return requested.compareTo(MAXIMUM_BACKOFF) > 0 ? MAXIMUM_BACKOFF : requested;
    }

    private static Optional<Duration> parseRetryAfter(String header) {
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(header.trim())));
        } catch (NumberFormatException e) {
            // The HTTP-date form is legal too, but no service here sends it; treat as absent.
            return Optional.empty();
        }
    }

    /** @return false when the wait was interrupted, meaning the caller should give up */
    private static boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
