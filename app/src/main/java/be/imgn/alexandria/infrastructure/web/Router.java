package be.imgn.alexandria.infrastructure.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enough routing for a single-user local editor: literal segments and {@code {name}}
 * placeholders, matched in registration order.
 */
public final class Router {

    /** What a handler produces: a status, a content type and a body. */
    public record Response(int status, String contentType, byte[] body, Map<String, String> headers) {

        public static Response html(String body) {
            return new Response(200, "text/html; charset=utf-8",
                    body.getBytes(StandardCharsets.UTF_8), Map.of());
        }

        public static Response json(String body) {
            return new Response(200, "application/json; charset=utf-8",
                    body.getBytes(StandardCharsets.UTF_8), Map.of());
        }

        public static Response asset(String contentType, byte[] body) {
            return new Response(200, contentType, body, Map.of());
        }

        public static Response seeOther(String location) {
            return new Response(303, "text/plain; charset=utf-8", new byte[0], Map.of("Location", location));
        }

        public static Response error(int status, String message) {
            return new Response(status, "text/html; charset=utf-8",
                    Html.page("Error", "", "<h1>" + status + "</h1><pre class=\"error\">"
                            + Html.escape(message) + "</pre>").getBytes(StandardCharsets.UTF_8),
                    Map.of());
        }
    }

    /** The request as a handler sees it: path parameters, query string and parsed body. */
    public record Request(Map<String, String> path, FormData query, FormData body) {

        public String param(String name) {
            String value = path.get(name);
            if (value == null) {
                throw new IllegalArgumentException("no path parameter '" + name + "'");
            }
            return value;
        }
    }

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request);
    }

    private record Route(String method, List<String> segments, Handler handler) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String pattern, Handler handler) {
        return add("GET", pattern, handler);
    }

    public Router post(String pattern, Handler handler) {
        return add("POST", pattern, handler);
    }

    private Router add(String method, String pattern, Handler handler) {
        routes.add(new Route(method, split(pattern), handler));
        return this;
    }

    public Response dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        List<String> actual = split(exchange.getRequestURI().getPath());
        FormData query = FormData.parse(exchange.getRequestURI().getRawQuery());
        FormData body = "POST".equals(method) ? readBody(exchange) : FormData.parse("");

        for (Route route : routes) {
            if (!route.method().equals(method)) {
                continue;
            }
            Optional<Map<String, String>> parameters = match(route.segments(), actual);
            if (parameters.isPresent()) {
                return route.handler().handle(new Request(parameters.get(), query, body));
            }
        }
        return Response.error(404, "No route for " + method + " " + exchange.getRequestURI().getPath());
    }

    private static FormData readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return FormData.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Optional<Map<String, String>> match(List<String> pattern, List<String> actual) {
        if (pattern.size() != actual.size()) {
            return Optional.empty();
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            String expected = pattern.get(i);
            if (expected.startsWith("{") && expected.endsWith("}")) {
                parameters.put(expected.substring(1, expected.length() - 1),
                        URLDecoder.decode(actual.get(i), StandardCharsets.UTF_8));
            } else if (!expected.equals(actual.get(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(parameters);
    }

    private static List<String> split(String path) {
        return java.util.Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
    }
}
