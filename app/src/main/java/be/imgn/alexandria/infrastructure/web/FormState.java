package be.imgn.alexandria.infrastructure.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The values a form should display, and what is wrong with them.
 *
 * <p>One type serves both directions. A prefill from an ISBN lookup arrives as a map of field
 * names to suggested values; a rejected submission arrives as the values the user actually
 * typed plus the problems they caused. Because rendering only ever reads a {@link FormState},
 * a redisplay after an error shows the form exactly as it was left — which is the whole point:
 * a missing field should cost you that field, not the twenty you filled in correctly.
 */
final class FormState {

    private final Map<String, String> values;
    private final Map<String, List<String>> multi;
    private final FormProblems problems;

    private FormState(Map<String, String> values, Map<String, List<String>> multi, FormProblems problems) {
        this.values = values;
        this.multi = multi;
        this.problems = problems;
    }

    /** An untouched form: suggested values, nothing wrong yet. */
    static FormState prefilled(Map<String, String> values) {
        return new FormState(new LinkedHashMap<>(values), Map.of(), new FormProblems());
    }

    static FormState empty() {
        return prefilled(Map.of());
    }

    /** A form coming back after rejection, holding what was typed and why it failed. */
    static FormState submitted(FormData form, FormProblems problems) {
        return new FormState(form.flat(), form.multiValued(), problems);
    }

    String value(String field) {
        return values.getOrDefault(field, "");
    }

    /** The value, or the fallback when the field was never set — for selects with a default. */
    String valueOr(String field, String fallback) {
        String value = values.get(field);
        return value == null || value.isBlank() ? fallback : value;
    }

    List<String> all(String field) {
        List<String> many = multi.get(field);
        if (many != null) {
            return many;
        }
        String single = values.get(field);
        return single == null || single.isBlank() ? List.of() : List.of(single);
    }

    boolean checked(String field) {
        return !value(field).isBlank();
    }

    Optional<String> problemAt(String field) {
        return problems.of(field);
    }

    FormProblems problems() {
        return problems;
    }

    boolean hasProblems() {
        return problems.any();
    }

    /**
     * How many rows of a repeating group to render: enough for everything submitted, and
     * never fewer than the minimum so there is always somewhere to type.
     */
    int groupSize(String group, int minimum) {
        int highest = -1;
        for (String key : values.keySet()) {
            if (key.startsWith(group + "[")) {
                // Search past the prefix: a nested group such as
                // "expressions[0].contributors" would otherwise find the bracket of the
                // outer index and compute a backwards range.
                int close = key.indexOf(']', group.length() + 1);
                if (close > 0) {
                    try {
                        highest = Math.max(highest, Integer.parseInt(key.substring(group.length() + 1, close)));
                    } catch (NumberFormatException ignored) {
                        // not an indexed key after all
                    }
                }
            }
        }
        return Math.max(highest + 1, minimum);
    }
}
