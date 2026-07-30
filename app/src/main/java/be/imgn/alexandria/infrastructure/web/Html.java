package be.imgn.alexandria.infrastructure.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import be.imgn.alexandria.infrastructure.Escape;

/** Minimal HTML assembly. Everything user-supplied goes through {@link #escape}. */
public final class Html {

    private Html() {}

    public static String escape(String raw) {
        return Escape.html(raw);
    }

    public static String page(String title, String breadcrumb, String body) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s — Alexandria</title>
                  <link rel="stylesheet" href="/assets/editor.css">
                </head>
                <body>
                  <header>
                    <a class="brand" href="/">Alexandria</a>
                    <nav>
                      <a href="/import">Add from ISBN</a>
                      <a href="/agents">Agents</a>
                      <a href="/works">Works</a>
                      <a href="/manifestations">Manifestations</a>
                      <a href="/items">Items</a>
                      <a href="/reports">Reports</a>
                    </nav>
                  </header>
                  <main>
                    <p class="crumb">%s</p>
                    %s
                  </main>
                  <script src="/assets/editor.js"></script>
                </body>
                </html>
                """.formatted(escape(title), breadcrumb, body);
    }

    // ---------------------------------------------------------- state-driven fields
    //
    // These read their value and their problem from a FormState, so one call site renders
    // both a fresh form and the same form coming back after rejection. Everything the import
    // form shows goes through here.

    /**
     * A field carrying its own error message.
     *
     * @param constraint a client-side rule for {@code editor.js} — "required", "slug", "isbn", "language", or several
     *     separated by a space
     */
    public static String input(FormState state, String name, String label, String type, String constraint) {
        return labelled(state, name, label, """
                <input type="%s" name="%s" value="%s"%s%s>
                """.formatted(
                        escape(type),
                        escape(name),
                        escape(state.value(name)),
                        constraint == null || constraint.isBlank() ? "" : " data-check=\"" + escape(constraint) + "\"",
                        invalid(state, name)));
    }

    public static String input(FormState state, String name, String label, String type) {
        return input(state, name, label, type, null);
    }

    public static String suggest(FormState state, String name, String label, String listId, String constraint) {
        return labelled(state, name, label, """
                <input type="text" name="%s" value="%s" list="%s" autocomplete="off"%s%s>
                """.formatted(
                        escape(name),
                        escape(state.value(name)),
                        escape(listId),
                        constraint == null || constraint.isBlank() ? "" : " data-check=\"" + escape(constraint) + "\"",
                        invalid(state, name)));
    }

    public static String choice(
            FormState state, String name, String label, Map<String, String> options, String fallback) {
        String selected = state.valueOr(name, fallback);
        String body = options.entrySet().stream()
                .map(option -> "<option value=\"%s\"%s>%s</option>"
                        .formatted(
                                escape(option.getKey()),
                                option.getKey().equals(selected) ? " selected" : "",
                                escape(option.getValue())))
                .collect(Collectors.joining("\n      "));
        return labelled(
                state,
                name,
                label,
                "<select name=\"%s\"%s>\n      %s\n    </select>\n"
                        .formatted(escape(name), invalid(state, name), body));
    }

    public static String area(FormState state, String name, String label) {
        return labelled(
                state, name, label, """
                <textarea name="%s" rows="3"%s>%s</textarea>
                """.formatted(escape(name), invalid(state, name), escape(state.value(name))));
    }

    /** Wraps a control in its label and, when the field was rejected, its reason. */
    private static String labelled(FormState state, String name, String label, String control) {
        String problem = state.problemAt(name)
                .map(message -> "<strong class=\"field-error\" id=\"%s-error\">%s</strong>"
                        .formatted(escape(name), escape(message)))
                .orElse("");
        return """
                <label%s><span>%s</span>%s%s</label>
                """.formatted(
                        state.problemAt(name).isPresent() ? " class=\"bad\"" : "", escape(label), control, problem);
    }

    private static String invalid(FormState state, String name) {
        return state.problemAt(name).isPresent()
                ? " aria-invalid=\"true\" aria-errormessage=\"" + escape(name) + "-error\""
                : "";
    }

    /** The summary at the top of a rejected form, linking to each field that needs attention. */
    public static String problemSummary(FormState state, Map<String, String> fieldLabels) {
        if (!state.hasProblems()) {
            return "";
        }
        String general = state.problems().generalProblems().stream()
                .map(problem -> "<li>" + escape(problem) + "</li>")
                .collect(Collectors.joining());
        String fields = fieldLabels.entrySet().stream()
                .filter(entry -> state.problemAt(entry.getKey()).isPresent())
                .map(entry -> "<li><a href=\"#\" data-focus=\"%s\">%s</a> — %s</li>"
                        .formatted(
                                escape(entry.getKey()),
                                escape(entry.getValue()),
                                escape(state.problemAt(entry.getKey()).orElseThrow())))
                .collect(Collectors.joining());
        String orphans = state.problems().orphanedBeyond(List.copyOf(fieldLabels.keySet())).stream()
                .map(problem -> "<li>" + escape(problem) + "</li>")
                .collect(Collectors.joining());
        int count = state.problems().count();
        return """
                <div class="error" role="alert">
                  <h2>%d thing%s to fix</h2>
                  <p class="hint">Everything you typed is still here. Correct these and save again.</p>
                  <ul>%s%s%s</ul>
                </div>
                """.formatted(count, count == 1 ? "" : "s", general, fields, orphans);
    }

    /**
     * A text field that completes against a datalist but still accepts anything typed — which is exactly the "match
     * what exists, allow something new" behaviour the agent fields need, with no JavaScript.
     */
    public static String suggestField(String name, String label, String value, String listId) {
        return """
                <label><span>%s</span><input type="text" name="%s" value="%s" list="%s"
                       autocomplete="off"></label>
                """.formatted(escape(label), escape(name), escape(value), escape(listId));
    }

    /** Emitted once per page; several {@link #suggestField} inputs share it. */
    public static String datalist(String id, List<String> options) {
        String body = options.stream()
                .map(option -> "<option value=\"" + escape(option) + "\">")
                .collect(Collectors.joining("\n  "));
        return "<datalist id=\"" + escape(id) + "\">\n  " + body + "\n</datalist>\n";
    }

    public static String textField(String name, String label, String value) {
        return """
                <label><span>%s</span><input type="text" name="%s" value="%s"></label>
                """.formatted(escape(label), escape(name), escape(value));
    }

    public static String dateField(String name, String label, String value) {
        return """
                <label><span>%s</span><input type="date" name="%s" value="%s"></label>
                """.formatted(escape(label), escape(name), escape(value));
    }

    public static String numberField(String name, String label, String value) {
        return """
                <label><span>%s</span><input type="number" name="%s" value="%s"></label>
                """.formatted(escape(label), escape(name), escape(value));
    }

    public static String textArea(String name, String label, String value) {
        return """
                <label><span>%s</span><textarea name="%s" rows="3">%s</textarea></label>
                """.formatted(escape(label), escape(name), escape(value));
    }

    public static String select(String name, String label, Map<String, String> options, String selected) {
        String body = options.entrySet().stream()
                .map(o -> "<option value=\"%s\"%s>%s</option>"
                        .formatted(
                                escape(o.getKey()),
                                o.getKey().equals(selected) ? " selected" : "",
                                escape(o.getValue())))
                .collect(Collectors.joining("\n      "));
        return """
                <label><span>%s</span><select name="%s">
                      %s
                    </select></label>
                """.formatted(escape(label), escape(name), body);
    }

    /**
     * A sum-type editor: one select for the variant, one fieldset per variant holding its payload. {@code editor.js}
     * shows only the fieldset matching the current selection, so the browser never presents fields that do not apply to
     * the chosen variant.
     */
    public static String variantField(
            String name, String label, Map<String, String> variants, String selected, Map<String, String> payloads) {
        String panels = variants.keySet().stream()
                .map(variant -> """
                        <fieldset class="variant" data-variant-of="%s" data-variant="%s"%s>%s</fieldset>
                        """.formatted(
                                escape(name),
                                escape(variant),
                                variant.equals(selected) ? "" : " hidden",
                                payloads.getOrDefault(variant, "")))
                .collect(Collectors.joining());
        return """
                <div class="sum" data-sum="%s">
                  %s
                  %s
                </div>
                """.formatted(escape(name), select(name + ".type", label, variants, selected), panels);
    }

    public static String table(List<String> headers, List<List<String>> rows) {
        String head = headers.stream().map(h -> "<th>" + escape(h) + "</th>").collect(Collectors.joining());
        String body = rows.stream()
                .map(row -> "<tr>"
                        + row.stream().map(cell -> "<td>" + cell + "</td>").collect(Collectors.joining()) + "</tr>")
                .collect(Collectors.joining("\n"));
        return "<div class=\"scroll\"><table><thead><tr>" + head + "</tr></thead><tbody>" + body
                + "</tbody></table></div>";
    }

    public static String link(String href, String text) {
        return "<a href=\"" + escape(href) + "\">" + escape(text) + "</a>";
    }
}
