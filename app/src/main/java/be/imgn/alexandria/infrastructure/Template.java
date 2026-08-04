package be.imgn.alexandria.infrastructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Fills named slots in an HTML fragment, escaping what it inserts, and repeats or omits blocks of it.
 *
 * <p>The pages were built with {@code String.formatted} and positional {@code %s}. That works for a fragment and fails
 * for a page: the arguments become a list whose only connection to the markup is their order, so one inserted in the
 * middle shifts every one after it, silently. That has already cost two bugs here — a masthead that rendered the page
 * body where a count belonged, and a shell that hung the wrong root on a stylesheet link.
 *
 * <h2>Slots</h2>
 *
 * <p>A slot is <code>{name}</code>. {@link #with} escapes what it inserts, because a book's title may contain an
 * ampersand and the safe thing should be the default. {@link #withMarkup} does not, and is named so that every
 * unescaped insertion is legible at the call site.
 *
 * <h2>Blocks</h2>
 *
 * <p>Repetition is <code>{#each name}…{/each}</code>, choice is <code>{#if name}…{#else}…{/if}</code>, and both nest.
 * The block sits in the markup, where the shape of the page is legible; what goes in it is bound from Java, so <code>
 * &lt;ul&gt;{#each rows}&lt;li&gt;{title}&lt;/li&gt;{/each}&lt;/ul&gt;</code> is filled by {@code .each("rows", works,
 * (row, work) -> row.with("title", work.title().main()))}.
 *
 * <p>The binder is handed a {@code Template} of the block's body and one item, so a slot inside the block is filled per
 * item, by name, and type-checked. That is why the syntax stops at blocks and grows no expression language: <code>
 * {work.title.main}</code> would have to be resolved by reflection or through a map of strings, and a model that has
 * just had every stringly-typed access taken out of it should not get one back through its templates.
 *
 * <h2>Mistakes it refuses</h2>
 *
 * <ul>
 *   <li>A slot with no value, a loop with nothing bound, a branch with no condition — thrown at {@link #render}, and
 *       only for the parts actually reached, so slots in an untaken {@code #else} cost nothing.
 *   <li>A value, loop or condition whose name is in no template — thrown at the call, which catches a rename that
 *       touched the markup and not the Java, or the reverse.
 *   <li>An unclosed or mismatched block — thrown at {@link #of}.
 * </ul>
 *
 * <p>A name is a letter followed by letters, digits, dots, dashes or underscores. A brace followed by anything else — a
 * CSS rule, a JavaScript object — is left exactly as it is.
 *
 * <p>Not reusable: {@link #of} returns a fresh binder and {@link #render} consumes it.
 */
public final class Template {

    private final List<Node> nodes;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> loops = new LinkedHashMap<>();
    private final Map<String, Boolean> branches = new LinkedHashMap<>();

    private Template(List<Node> nodes) {
        this.nodes = nodes;
    }

    public static Template of(String source) {
        if (source == null) {
            throw new IllegalArgumentException("a template needs a source");
        }
        return new Template(Parser.parse(source));
    }

    /** Fills a slot with text, escaped. */
    public Template with(String name, String text) {
        return put(name, Escape.html(text));
    }

    /** Fills a slot with a number, which needs no escaping. */
    public Template with(String name, int value) {
        return put(name, Integer.toString(value));
    }

    /**
     * Fills a slot with markup that is already HTML — a fragment this class built, or a link assembled elsewhere.
     *
     * <p>The verbose name is the point: every unescaped insertion should be visible when reading the call site.
     */
    public Template withMarkup(String name, String markup) {
        return put(name, markup == null ? "" : markup);
    }

    /**
     * Renders <code>{#each name}…{/each}</code> once per item, in the collection's own order.
     *
     * @param bind fills the block's slots for one item; the {@code Template} it is handed is that block's body
     */
    public <T> Template each(String name, Collection<T> items, BiConsumer<Template, T> bind) {
        List<Node> body = Nodes.loopBody(nodes, name)
                .orElseThrow(() -> new IllegalArgumentException("template has no '{#each " + name + "}' block"));
        StringBuilder out = new StringBuilder();
        for (T item : items) {
            Template row = new Template(body);
            bind.accept(row, item);
            out.append(row.render());
        }
        loops.put(name, out.toString());
        return this;
    }

    /** Keeps the <code>{#if name}</code> body when true, and the <code>{#else}</code> body when false. */
    public Template when(String name, boolean condition) {
        if (!Nodes.hasBranch(nodes, name)) {
            throw new IllegalArgumentException("template has no '{#if " + name + "}' block");
        }
        branches.put(name, condition);
        return this;
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        render(nodes, out);
        return out.toString();
    }

    private void render(List<Node> toRender, StringBuilder out) {
        for (Node node : toRender) {
            switch (node) {
                case Node.Text(String text) -> out.append(text);
                case Node.Slot(String name) -> {
                    String value = values.get(name);
                    if (value == null) {
                        throw new IllegalStateException("no value for slot '" + name + "'");
                    }
                    out.append(value);
                }
                case Node.Loop(String name, var ignoredBody) -> {
                    String rendered = loops.get(name);
                    if (rendered == null) {
                        throw new IllegalStateException("nothing bound to '{#each " + name + "}'");
                    }
                    out.append(rendered);
                }
                case Node.Branch(String name, List<Node> whenTrue, List<Node> whenFalse) -> {
                    Boolean taken = branches.get(name);
                    if (taken == null) {
                        throw new IllegalStateException("no condition for '{#if " + name + "}'");
                    }
                    render(taken ? whenTrue : whenFalse, out);
                }
            }
        }
    }

    private Template put(String name, String value) {
        Set<String> known = Nodes.slotNames(nodes);
        if (!known.contains(name)) {
            throw new IllegalArgumentException("template has no slot '" + name + "'; it has " + known);
        }
        values.put(name, value);
        return this;
    }

    // ------------------------------------------------------------- the shape of a template

    /** A parsed template is text, slots, and blocks holding more of the same. */
    private sealed interface Node {

        record Text(String text) implements Node {}

        record Slot(String name) implements Node {}

        record Loop(String name, List<Node> body) implements Node {}

        record Branch(String name, List<Node> whenTrue, List<Node> whenFalse) implements Node {}
    }

    private static final class Nodes {

        private Nodes() {}

        /**
         * The slots this template fills itself: everything outside a loop, whose slots belong instead to the body
         * handed to its binder. A branch shares the surrounding scope, so its slots are counted here.
         */
        static Set<String> slotNames(List<Node> nodes) {
            Set<String> names = new LinkedHashSet<>();
            for (Node node : nodes) {
                switch (node) {
                    case Node.Slot(String name) -> names.add(name);
                    case Node.Branch(var ignoredName, List<Node> whenTrue, List<Node> whenFalse) -> {
                        names.addAll(slotNames(whenTrue));
                        names.addAll(slotNames(whenFalse));
                    }
                    case Node.Loop(var ignoredName, var ignoredBody) -> {
                        // A loop's slots are its body's, not this template's.
                    }
                    case Node.Text(var ignoredText) -> {
                        // Nothing to collect.
                    }
                }
            }
            return names;
        }

        static Optional<List<Node>> loopBody(List<Node> nodes, String name) {
            for (Node node : nodes) {
                if (node instanceof Node.Loop(String found, List<Node> body)) {
                    if (found.equals(name)) {
                        return Optional.of(body);
                    }
                    Optional<List<Node>> nested = loopBody(body, name);
                    if (nested.isPresent()) {
                        return nested;
                    }
                } else if (node instanceof Node.Branch(var ignored, List<Node> whenTrue, List<Node> whenFalse)) {
                    // Look inside, but keep looking afterwards: a branch that does not hold the
                    // loop is not the end of the search, it is one node that did not have it.
                    Optional<List<Node>> inBranch = loopBody(whenTrue, name).or(() -> loopBody(whenFalse, name));
                    if (inBranch.isPresent()) {
                        return inBranch;
                    }
                }
            }
            return Optional.empty();
        }

        static boolean hasBranch(List<Node> nodes, String name) {
            for (Node node : nodes) {
                if (node instanceof Node.Branch(String found, List<Node> whenTrue, List<Node> whenFalse)) {
                    if (found.equals(name) || hasBranch(whenTrue, name) || hasBranch(whenFalse, name)) {
                        return true;
                    }
                } else if (node instanceof Node.Loop(var ignored, List<Node> body) && hasBranch(body, name)) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------- parsing

    private static final class Parser {

        private final String source;
        private int at;

        private Parser(String source) {
            this.source = source;
        }

        static List<Node> parse(String source) {
            Parser parser = new Parser(source);
            List<Node> nodes = parser.nodes(null);
            if (parser.at < source.length()) {
                throw new IllegalArgumentException(
                        "'{" + parser.tagAt(parser.at) + "}' closes a block that was never opened");
            }
            return nodes;
        }

        /** Reads to the end of the source, or to the tag that ends the block named by {@code openBlock}. */
        private List<Node> nodes(String openBlock) {
            List<Node> nodes = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            while (at < source.length()) {
                String tag = tagAt(at);
                if (tag == null) {
                    text.append(source.charAt(at));
                    at++;
                    continue;
                }
                if (tag.equals("/each") || tag.equals("/if") || tag.equals("#else")) {
                    if (openBlock == null) {
                        throw new IllegalArgumentException("'{" + tag + "}' closes a block that was never opened");
                    }
                    flush(text, nodes);
                    return nodes;
                }
                flush(text, nodes);
                at += tag.length() + 2;
                nodes.add(block(tag));
            }
            if (openBlock != null) {
                throw new IllegalArgumentException("'{#" + openBlock + "}' is never closed");
            }
            flush(text, nodes);
            return nodes;
        }

        private Node block(String tag) {
            if (tag.startsWith("#each ")) {
                String name = tag.substring("#each ".length()).trim();
                List<Node> body = nodes("each " + name);
                expect("/each");
                return new Node.Loop(name, body);
            }
            if (tag.startsWith("#if ")) {
                String name = tag.substring("#if ".length()).trim();
                List<Node> whenTrue = nodes("if " + name);
                List<Node> whenFalse = List.of();
                if ("#else".equals(tagAt(at))) {
                    at += "#else".length() + 2;
                    whenFalse = nodes("if " + name);
                }
                expect("/if");
                return new Node.Branch(name, whenTrue, whenFalse);
            }
            return new Node.Slot(tag);
        }

        private void expect(String closing) {
            String tag = tagAt(at);
            if (!closing.equals(tag)) {
                throw new IllegalArgumentException("expected '{" + closing + "}' but found "
                        + (tag == null ? "the end of the template" : "'{" + tag + "}'"));
            }
            at += closing.length() + 2;
        }

        /** The tag starting at {@code index}, or null when that brace opens no tag — a CSS rule, say. */
        private String tagAt(int index) {
            if (index >= source.length() || source.charAt(index) != '{') {
                return null;
            }
            int close = source.indexOf('}', index);
            if (close < 0) {
                return null;
            }
            String body = source.substring(index + 1, close);
            return isTag(body) ? body : null;
        }

        private static boolean isTag(String body) {
            if (body.equals("#else") || body.equals("/each") || body.equals("/if")) {
                return true;
            }
            if (body.startsWith("#each ") || body.startsWith("#if ")) {
                return isName(body.substring(body.indexOf(' ') + 1).trim());
            }
            return isName(body);
        }

        private static boolean isName(String candidate) {
            if (candidate.isEmpty() || !Character.isLetter(candidate.charAt(0))) {
                return false;
            }
            return candidate.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-');
        }

        private static void flush(StringBuilder text, List<Node> nodes) {
            if (!text.isEmpty()) {
                nodes.add(new Node.Text(text.toString()));
                text.setLength(0);
            }
        }
    }
}
