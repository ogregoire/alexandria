package be.imgn.alexandria.infrastructure.web;

import be.imgn.alexandria.infrastructure.Escape;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal HTML assembly. Everything user-supplied goes through {@link #escape}. */
public final class Html {

    private Html() {
    }

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

    /**
     * A text field that completes against a datalist but still accepts anything typed —
     * which is exactly the "match what exists, allow something new" behaviour the agent
     * fields need, with no JavaScript.
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
                .map(o -> "<option value=\"%s\"%s>%s</option>".formatted(
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
     * A sum-type editor: one select for the variant, one fieldset per variant holding its
     * payload. {@code editor.js} shows only the fieldset matching the current selection,
     * so the browser never presents fields that do not apply to the chosen variant.
     */
    public static String variantField(String name, String label, Map<String, String> variants,
                                      String selected, Map<String, String> payloads) {
        String panels = variants.keySet().stream()
                .map(variant -> """
                        <fieldset class="variant" data-variant-of="%s" data-variant="%s"%s>%s</fieldset>
                        """.formatted(
                        escape(name), escape(variant),
                        variant.equals(selected) ? "" : " hidden",
                        payloads.getOrDefault(variant, "")))
                .collect(Collectors.joining());
        return """
                <div class="sum" data-sum="%s">
                  %s
                  %s
                </div>
                """.formatted(
                escape(name),
                select(name + ".type", label, variants, selected),
                panels);
    }

    public static String table(List<String> headers, List<List<String>> rows) {
        String head = headers.stream()
                .map(h -> "<th>" + escape(h) + "</th>")
                .collect(Collectors.joining());
        String body = rows.stream()
                .map(row -> "<tr>" + row.stream()
                        .map(cell -> "<td>" + cell + "</td>")
                        .collect(Collectors.joining()) + "</tr>")
                .collect(Collectors.joining("\n"));
        return "<div class=\"scroll\"><table><thead><tr>" + head + "</tr></thead><tbody>"
                + body + "</tbody></table></div>";
    }

    public static String link(String href, String text) {
        return "<a href=\"" + escape(href) + "\">" + escape(text) + "</a>";
    }
}
