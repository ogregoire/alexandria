package be.imgn.alexandria.infrastructure;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills named slots in an HTML fragment, escaping what it inserts.
 *
 * <p>The pages were built with {@code String.formatted} and positional {@code %s}. That works until a template grows
 * past a handful of slots, at which point the arguments are a list of expressions whose only connection to the markup
 * is their order — and an argument inserted in the middle silently shifts everything after it. That has already
 * produced two bugs here: a masthead that rendered the body where a count belonged, and a shell that put the wrong root
 * on a stylesheet link.
 *
 * <p>So slots are named. A template holding <code>&lt;h1&gt;{title}&lt;/h1&gt;</code> is filled by
 * {@code .with("title", work.title().main())}, and the name in the markup is the name at the call site.
 *
 * <p>Three rules make it hard to misuse:
 *
 * <ul>
 *   <li>{@link #with} escapes. Text out of the catalogue is a book's title, and a book's title may contain an ampersand
 *       or a less-than sign — escaping by default is what stops that becoming a tag.
 *   <li>{@link #withMarkup} does not escape, and is named so that it reads as a decision at the call site. It is for
 *       fragments this class already produced.
 *   <li>Both a slot with no value and a value with no slot are errors, thrown at {@link #render}. A typo in either
 *       direction stops the build instead of quietly emitting a page with a hole in it.
 * </ul>
 *
 * <p>A slot is <code>{name}</code> where the name is a letter followed by letters, digits, dots, dashes or underscores.
 * A brace followed by anything else — a CSS rule inside a {@code <style>} block, a JavaScript object — is left exactly
 * as it is.
 *
 * <p>Not reusable: {@link #of} returns a fresh binder each time and {@link #render} consumes it.
 */
public final class Template {

    private static final Pattern SLOT = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_.-]*)\\}");

    private final String source;
    private final Set<String> slots;
    private final Map<String, String> values = new LinkedHashMap<>();

    private Template(String source) {
        this.source = source;
        this.slots = slotsIn(source);
    }

    public static Template of(String source) {
        if (source == null) {
            throw new IllegalArgumentException("a template needs a source");
        }
        return new Template(source);
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

    public String render() {
        Set<String> missing = new LinkedHashSet<>(slots);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("no value for " + missing + " in template");
        }
        StringBuilder out = new StringBuilder(source.length() + 64);
        Matcher matcher = SLOT.matcher(source);
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(values.get(matcher.group(1))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private Template put(String name, String value) {
        if (!slots.contains(name)) {
            throw new IllegalArgumentException("template has no slot '" + name + "'; it has " + slots);
        }
        values.put(name, value);
        return this;
    }

    private static Set<String> slotsIn(String source) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = SLOT.matcher(source);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
