package be.imgn.alexandria.infrastructure.lookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the captured responses in {@code src/test/resources/lookup} instead of reaching the network, so the parsers
 * are tested against exactly what the real services returned and the suite stays offline.
 */
final class StubHttp extends Http {

    private final Map<String, String> byUrlFragment = new LinkedHashMap<>();
    private final List<String> requested = new ArrayList<>();

    StubHttp serve(String urlFragment, String fixture) {
        byUrlFragment.put(urlFragment, read(fixture));
        return this;
    }

    @Override
    Optional<String> get(String url) {
        requested.add(url);
        return byUrlFragment.entrySet().stream()
                .filter(entry -> url.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    List<String> requested() {
        return List.copyOf(requested);
    }

    static String read(String fixture) {
        try (InputStream in = StubHttp.class.getResourceAsStream("/lookup/" + fixture)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture /lookup/" + fixture);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
