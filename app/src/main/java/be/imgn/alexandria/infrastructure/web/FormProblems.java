package be.imgn.alexandria.infrastructure.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Everything wrong with a submitted form, gathered rather than thrown.
 *
 * <p>The domain constructors reject bad input by throwing, which is right for the model and useless for a form: the
 * first blank field would abandon the parse and the user would be told about one mistake at a time, having lost the
 * rest of their typing. {@link #read} catches each rejection, files it against the field that caused it, and carries
 * on, so one submission reports every problem at once.
 */
final class FormProblems {

    private final Map<String, String> byField = new LinkedHashMap<>();
    private final List<String> general = new ArrayList<>();

    /**
     * Runs a parse that may reject its input.
     *
     * @return the value, or empty if it was rejected — in which case the reason is now filed against {@code field}
     */
    <T> Optional<T> read(String field, Supplier<T> parse) {
        try {
            return Optional.ofNullable(parse.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            at(field, message(e));
            return Optional.empty();
        }
    }

    /** Files a problem against a field, keeping the first if it already has one. */
    void at(String field, String problem) {
        byField.putIfAbsent(field, problem);
    }

    /** A problem that belongs to the form as a whole rather than to one field. */
    void general(String problem) {
        general.add(problem);
    }

    Optional<String> of(String field) {
        return Optional.ofNullable(byField.get(field));
    }

    List<String> generalProblems() {
        return List.copyOf(general);
    }

    boolean any() {
        return !byField.isEmpty() || !general.isEmpty();
    }

    int count() {
        return byField.size() + general.size();
    }

    /** Problems filed against fields nobody renders would otherwise vanish silently. */
    List<String> orphanedBeyond(List<String> renderedFields) {
        return byField.entrySet().stream()
                .filter(entry -> !renderedFields.contains(entry.getKey()))
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
