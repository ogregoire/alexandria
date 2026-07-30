package be.imgn.alexandria.infrastructure.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed {@code application/x-www-form-urlencoded} body.
 *
 * <p>Naming convention, matching what {@link Html#variantField} renders: {@code field.type} selects a variant of a sum
 * type and {@code field.<variant>.<name>} carries its payload. Repeating groups are indexed, {@code creators[0].name}.
 */
public final class FormData {

    private final Map<String, List<String>> values;

    private FormData(Map<String, List<String>> values) {
        this.values = values;
    }

    public static FormData parse(String body) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (body != null && !body.isBlank()) {
            for (String pair : body.split("&")) {
                int equals = pair.indexOf('=');
                String key = equals < 0 ? pair : pair.substring(0, equals);
                String value = equals < 0 ? "" : pair.substring(equals + 1);
                values.computeIfAbsent(decode(key), k -> new ArrayList<>()).add(decode(value));
            }
        }
        return new FormData(values);
    }

    private static String decode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    /** Present and non-blank, or empty. Blank inputs are how a browser says "nothing here". */
    public Optional<String> optional(String key) {
        return values.getOrDefault(key, List.of()).stream()
                .filter(v -> !v.isBlank())
                .map(String::trim)
                .findFirst();
    }

    public String required(String key) {
        return optional(key).orElseThrow(() -> new IllegalArgumentException("missing required field '" + key + "'"));
    }

    public String orEmpty(String key) {
        return optional(key).orElse("");
    }

    public Optional<Integer> optionalInt(String key) {
        return optional(key).map(Integer::parseInt);
    }

    public Optional<LocalDate> optionalDate(String key) {
        return optional(key).map(LocalDate::parse);
    }

    public LocalDate requiredDate(String key) {
        return LocalDate.parse(required(key));
    }

    public List<String> all(String key) {
        return values.getOrDefault(key, List.of()).stream()
                .filter(v -> !v.isBlank())
                .map(String::trim)
                .toList();
    }

    /** The variant chosen for a sum-typed field. */
    public String variant(String field) {
        return required(field + ".type");
    }

    /**
     * The variant chosen, or a default when the field was not submitted at all.
     *
     * <p>Only for sum types whose default carries no payload, so that omitting the field entirely is a meaningful state
     * rather than a missing answer.
     */
    public String variantOr(String field, String fallback) {
        return optional(field + ".type").orElse(fallback);
    }

    /** Reads a payload field of the chosen variant: {@code acquisition.purchased.date}. */
    public FormData in(String field, String variant) {
        return scope(field + "." + variant + ".");
    }

    /**
     * Everything under a prefix, with the prefix stripped — so a combined form can nest a whole aggregate's fields
     * under {@code manifestation.} and hand the inner view to the same readers that parse a standalone one.
     */
    public FormData scope(String prefix) {
        Map<String, List<String>> scoped = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                scoped.put(key.substring(prefix.length()), value);
            }
        });
        return new FormData(scoped);
    }

    /** True when a checkbox with this name was ticked. */
    public boolean checked(String key) {
        return optional(key).isPresent();
    }

    /** Every field with its first value — what a form needs to redisplay itself. */
    public Map<String, String> flat() {
        Map<String, String> flat = new LinkedHashMap<>();
        values.forEach((key, value) -> flat.put(key, value.isEmpty() ? "" : value.getFirst()));
        return flat;
    }

    /** Only the fields submitted more than once, such as a group of checkboxes. */
    public Map<String, List<String>> multiValued() {
        Map<String, List<String>> many = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value.size() > 1) {
                many.put(key, List.copyOf(value));
            }
        });
        return many;
    }

    /** Scopes to one element of a repeating group: {@code creators[2].}. */
    public FormData at(String group, int index) {
        String prefix = group + "[" + index + "].";
        Map<String, List<String>> scoped = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                scoped.put(key.substring(prefix.length()), value);
            }
        });
        return new FormData(scoped);
    }

    /** How many elements the browser submitted for a repeating group. */
    public int size(String group) {
        int highest = -1;
        for (String key : values.keySet()) {
            if (key.startsWith(group + "[")) {
                int close = key.indexOf(']', group.length() + 1);
                if (close > 0) {
                    highest = Math.max(highest, Integer.parseInt(key.substring(group.length() + 1, close)));
                }
            }
        }
        return highest + 1;
    }

    public boolean isEmpty() {
        return values.values().stream().flatMap(List::stream).allMatch(String::isBlank);
    }
}
