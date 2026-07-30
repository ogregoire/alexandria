package be.imgn.alexandria.infrastructure.json.codec;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A read JSON object, and the recursive-descent parser that produces one.
 *
 * <p>Prototype. This is the half that a library really does save: about a hundred lines of tokenising that has to be
 * right about escapes, numbers and nesting. It is deliberately strict — trailing content, a missing brace or an unknown
 * escape is an error rather than something quietly tolerated — because the input is a file this application wrote.
 *
 * <p>Accessors are typed and total: a field that is absent or of the wrong shape is {@link Optional#empty()}, and
 * {@link #text(String)} throws only where the model does.
 */
public final class JsonIn {

    private final Map<String, Object> fields;

    private JsonIn(Map<String, Object> fields) {
        this.fields = fields;
    }

    /**
     * A reader with no fields at all.
     *
     * <p>For merging two payloads where either may be absent: every accessor then reports the field as missing, so the
     * caller needs no null check.
     */
    public static JsonIn empty() {
        return new JsonIn(Map.of());
    }

    public static JsonIn parse(String json) {
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        Object value = parser.value();
        parser.skipWhitespace();
        if (!parser.done()) {
            throw new IllegalArgumentException("trailing content at offset " + parser.at());
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("expected a JSON object at the top level");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) value;
        return new JsonIn(object);
    }

    public Optional<String> optionalText(String key) {
        return fields.get(key) instanceof String value ? Optional.of(value) : Optional.empty();
    }

    public String text(String key) {
        return optionalText(key).orElseThrow(() -> new IllegalArgumentException("missing string field '" + key + "'"));
    }

    public Optional<Integer> optionalInt(String key) {
        return fields.get(key) instanceof Number value ? Optional.of(value.intValue()) : Optional.empty();
    }

    public Optional<LocalDate> optionalDate(String key) {
        return optionalText(key).map(LocalDate::parse);
    }

    public Optional<JsonIn> optionalObject(String key) {
        if (fields.get(key) instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) nested;
            return Optional.of(new JsonIn(object));
        }
        return Optional.empty();
    }

    public JsonIn object(String key) {
        return optionalObject(key)
                .orElseThrow(() -> new IllegalArgumentException("missing object field '" + key + "'"));
    }

    public List<String> texts(String key) {
        if (!(fields.get(key) instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof String value) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /** One reader per object in an array; empty when the field is absent. */
    public List<JsonIn> objects(String key) {
        if (!(fields.get(key) instanceof List<?> items)) {
            return List.of();
        }
        List<JsonIn> readers = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) nested;
                readers.add(new JsonIn(object));
            }
        }
        return List.copyOf(readers);
    }

    /**
     * Strings in an array, plus the named field of any objects in it.
     *
     * <p>For third-party payloads that mix the two in one array — Open Library writes subjects as bare strings in one
     * endpoint and as {@code {"name": …}} objects in another.
     */
    public List<String> textsOrField(String key, String field) {
        if (!(fields.get(key) instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof String value) {
                values.add(value);
            } else if (item instanceof Map<?, ?> nested && nested.get(field) instanceof String value) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    /** The discriminator every sum type in the catalogue carries. */
    public String type() {
        return text("type");
    }

    private static final class Parser {

        private final String json;
        private int at;

        Parser(String json) {
            this.json = json;
        }

        int at() {
            return at;
        }

        boolean done() {
            return at >= json.length();
        }

        Object value() {
            char c = peek();
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                at++;
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                object.put(key, value());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return object;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> items = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                at++;
                return items;
            }
            while (true) {
                skipWhitespace();
                items.add(value());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return items;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > json.length()) {
                            throw error("truncated \\u escape");
                        }
                        out.append((char) Integer.parseInt(json.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw error("unknown escape \\" + escape);
                }
            }
        }

        private Number number() {
            int start = at;
            while (!done() && "+-.eE0123456789".indexOf(json.charAt(at)) >= 0) {
                at++;
            }
            String text = json.substring(start, at);
            if (text.isEmpty()) {
                throw error("expected a value");
            }
            return text.contains(".") || text.contains("e") || text.contains("E")
                    ? Double.valueOf(text)
                    : Long.valueOf(text);
        }

        private Object literal(String word, Object value) {
            if (!json.startsWith(word, at)) {
                throw error("expected " + word);
            }
            at += word.length();
            return value;
        }

        void skipWhitespace() {
            while (!done() && Character.isWhitespace(json.charAt(at))) {
                at++;
            }
        }

        private char peek() {
            if (done()) {
                throw error("unexpected end of input");
            }
            return json.charAt(at);
        }

        private char next() {
            char c = peek();
            at++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) {
                at--;
                throw error("expected '" + expected + "'");
            }
        }

        private IllegalArgumentException error(String problem) {
            return new IllegalArgumentException(problem + " at offset " + at);
        }
    }
}
