package be.imgn.alexandria.infrastructure.json.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds one JSON object, in the order fields are added.
 *
 * <p>Prototype, not wired into the store. It exists to be compared against {@code infrastructure/json/Mixins}, so it
 * reproduces the committed format exactly: two-space indent, {@code " : "} between key and value, LF endings, {@code [
 * ]} for an empty array, and absent optionals omitted rather than written as null.
 *
 * <p>Omission is a property of the writer rather than a global setting: {@code textIfAny(name, "")} adds nothing, so a
 * codec reads as a list of the fields a record has and says nothing about the ones it does not.
 */
public final class JsonOut {

    private final List<String> keys = new ArrayList<>();
    private final List<String> values = new ArrayList<>();

    public static String document(Consumer<JsonOut> body) {
        JsonOut out = new JsonOut();
        body.accept(out);
        return out.render(0) + "\n";
    }

    public JsonOut text(String key, String value) {
        return value == null ? this : put(key, quote(value));
    }

    /**
     * Writes the key only when there is something to write. Blank means nothing was ever recorded, and an absent key is
     * how this format has always said so — the two-shape types render their emptiness as "".
     */
    public JsonOut textIfAny(String key, String value) {
        return value == null || value.isEmpty() ? this : put(key, quote(value));
    }

    /** The same, for a value that must stay an unquoted JSON number. */
    public JsonOut numberIfAny(String key, String digits) {
        return digits == null || digits.isEmpty() ? this : put(key, digits);
    }

    public JsonOut number(String key, int value) {
        return put(key, Integer.toString(value));
    }

    /**
     * A nested object, always written — a sum type's payload may legitimately be empty.
     *
     * <p>Rendered at depth zero and shifted into place by {@link #indent}, which is the one convention every value here
     * follows: produce it flat, let the parent position it.
     */
    public JsonOut object(String key, Consumer<JsonOut> body) {
        JsonOut nested = new JsonOut();
        body.accept(nested);
        return put(key, nested.render(0));
    }

    /** Strings in the order given; the caller sorts when the order has to be stable. */
    public JsonOut texts(String key, Collection<String> items) {
        return array(key, items, JsonOut::quote);
    }

    /** One object per item, each built by the given body. */
    public <T> JsonOut objects(String key, Collection<T> items, BiConsumer<JsonOut, T> body) {
        return array(key, items, item -> {
            JsonOut nested = new JsonOut();
            body.accept(nested, item);
            return nested.render(0);
        });
    }

    private <T> JsonOut array(String key, Collection<T> items, Function<T, String> render) {
        if (items.isEmpty()) {
            return put(key, "[ ]");
        }
        StringBuilder array = new StringBuilder("[\n");
        int index = 0;
        for (T item : items) {
            array.append("  ").append(indent(render.apply(item), "  "));
            array.append(++index == items.size() ? "\n" : ",\n");
        }
        return put(key, array.append("]").toString());
    }

    private JsonOut put(String key, String renderedValue) {
        keys.add(key);
        values.add(renderedValue);
        return this;
    }

    /** Renders at the given depth, re-indenting any nested value produced at depth zero. */
    private String render(int depth) {
        if (keys.isEmpty()) {
            return "{ }";
        }
        String pad = "  ".repeat(depth + 1);
        StringBuilder out = new StringBuilder("{\n");
        for (int i = 0; i < keys.size(); i++) {
            out.append(pad).append(quote(keys.get(i))).append(" : ").append(indent(values.get(i), pad));
            out.append(i == keys.size() - 1 ? "\n" : ",\n");
        }
        return out.append("  ".repeat(depth)).append("}").toString();
    }

    /** Shifts a value's continuation lines to sit under the key that introduced it. */
    private static String indent(String value, String pad) {
        return value.replace("\n", "\n" + pad);
    }

    static String quote(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
