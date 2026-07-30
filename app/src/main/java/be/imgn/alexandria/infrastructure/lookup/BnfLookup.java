package be.imgn.alexandria.infrastructure.lookup;

import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Isbn;
import be.imgn.alexandria.domain.shared.Language;

/**
 * The Bibliothèque nationale de France, via its SRU endpoint. Authoritative for French publishing in a way no
 * general-purpose service is.
 *
 * <p>Two quirks drive the code. The catalogue indexes the <em>ISBN-10</em> form for anything published before the 2007
 * changeover, so a search for 9782070360024 returns nothing while 2070360024 returns the record — both are tried. And
 * its Dublin Core is prose rather than fields: the author arrives as "Camus, Albert (1913-1960). Auteur du texte", the
 * extent as "1 volume 191 p ; 18 cm", the series as "Collection : Folio ; 2", so each is picked apart with a pattern.
 */
public final class BnfLookup implements BookLookup {

    private static final String BASE = "https://catalogue.bnf.fr/api/SRU";

    /**
     * The BnF publishes no rate limit for this endpoint. Two requests a second is a self-imposed courtesy, and it also
     * paces the ISBN-13-then-ISBN-10 retry.
     */
    private static final Duration INTERVAL = Duration.ofMillis(500);

    private static final Pattern AGENT = Pattern.compile("^([^(.]+?)\\s*(?:\\(([^)]*)\\))?\\s*\\.\\s*(.*)$");
    private static final Pattern PAGES = Pattern.compile("(\\d+)\\s*p\\b");
    private static final Pattern SERIES =
            Pattern.compile("Collection\\s*:\\s*([^;]+)(?:;\\s*(\\S+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

    private final Http http;
    private final String base;

    public BnfLookup() {
        this(UserAgent.anonymous());
    }

    public BnfLookup(UserAgent caller) {
        this(new Http(INTERVAL, caller.header()), BASE);
    }

    BnfLookup(Http http, String base) {
        this.http = http;
        this.base = base;
    }

    @Override
    public String name() {
        return "BnF";
    }

    @Override
    public Optional<BookDraft> byIsbn(Identifier isbn) {
        for (String candidate : searchForms(isbn)) {
            Optional<BookDraft> found = search(candidate, isbn);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** The ISBN as given, then its other length, because the BnF indexes the older one. */
    private static List<String> searchForms(Identifier isbn) {
        Optional<String> digits = isbn.isbnDigits();
        if (digits.isEmpty()) {
            return List.of();
        }
        Set<String> forms = new LinkedHashSet<>();
        forms.add(digits.get());
        (digits.get().length() == 13 ? Isbn.toIsbn10(digits.get()) : Isbn.toIsbn13(digits.get())).ifPresent(forms::add);
        return List.copyOf(forms);
    }

    private Optional<BookDraft> search(String isbnForm, Identifier isbn) {
        String query = Http.encode("bib.isbn all \"" + isbnForm + "\"");
        String url = base + "?version=1.2&operation=searchRetrieve&query=" + query
                + "&recordSchema=dublincore&maximumRecords=1";
        return http.get(url).flatMap(this::parse).flatMap(document -> draft(document, isbn));
    }

    private Optional<BookDraft> draft(Document document, Identifier isbn) {
        List<String> titles = values(document, "title");
        if (titles.isEmpty()) {
            return Optional.empty();
        }

        // "L'Étranger / Albert Camus" — the statement of responsibility rides along.
        String title = titles.getFirst();
        int slash = title.indexOf(" / ");
        if (slash > 0) {
            title = title.substring(0, slash).trim();
        }

        String extent = String.join(" ", values(document, "format"));
        String description = String.join(" ", values(document, "description"));

        BookDraft.Builder draft = BookDraft.of(title, isbn, name())
                .authors(agents(values(document, "creator")))
                .translators(translators(values(document, "contributor")))
                .publisher(publisher(values(document, "publisher")).orElse(null))
                .publishedYear(firstYear(values(document, "date")))
                .pages(pages(extent))
                .language(language(values(document, "language")))
                .subjects(values(document, "subject"));

        Matcher series = SERIES.matcher(description);
        if (series.find()) {
            draft.series(series.group(1).trim());
            draft.seriesNumber(series.group(2) == null ? null : series.group(2).trim());
        }
        return Optional.of(draft.build());
    }

    /** "Camus, Albert (1913-1960). Auteur du texte" becomes "Camus, Albert". */
    private static List<String> agents(List<String> raw) {
        List<String> names = new ArrayList<>();
        for (String entry : raw) {
            Matcher matcher = AGENT.matcher(entry.trim());
            names.add(matcher.matches() ? matcher.group(1).trim() : entry.trim());
        }
        return names;
    }

    /** Contributors carry their role in French; only the translators are wanted here. */
    private static List<String> translators(List<String> raw) {
        List<String> names = new ArrayList<>();
        for (String entry : raw) {
            if (entry.toLowerCase(Locale.ROOT).contains("traduct")) {
                Matcher matcher = AGENT.matcher(entry.trim());
                names.add(matcher.matches() ? matcher.group(1).trim() : entry.trim());
            }
        }
        return names;
    }

    /** "Gallimard (Paris)" — the place is not part of the name. */
    private static Optional<String> publisher(List<String> raw) {
        return raw.stream()
                .findFirst()
                .map(value -> value.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim());
    }

    private static Optional<Integer> pages(String extent) {
        Matcher matcher = PAGES.matcher(extent);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    private static Optional<Integer> firstYear(List<String> dates) {
        for (String date : dates) {
            Matcher matcher = YEAR.matcher(date);
            if (matcher.find()) {
                return Optional.of(Integer.parseInt(matcher.group(1)));
            }
        }
        return Optional.empty();
    }

    private static Optional<Language> language(List<String> codes) {
        for (String code : codes) {
            try {
                return Optional.of(new Language(Iso639.toTwoLetter(code)));
            } catch (IllegalArgumentException ignored) {
                // a code the JDK does not know; try the next one
            }
        }
        return Optional.empty();
    }

    /** Dublin Core elements, matched on local name so the namespace prefix does not matter. */
    private static List<String> values(Document document, String element) {
        List<String> found = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element tag && element.equals(localName(tag))) {
                String text = tag.getTextContent();
                if (text != null && !text.isBlank()) {
                    found.add(text.trim());
                }
            }
        }
        return found;
    }

    private static String localName(Element element) {
        String name = element.getLocalName();
        if (name != null) {
            return name;
        }
        String tag = element.getTagName();
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    /**
     * Parsed with external entities and DTDs switched off — this is third-party XML arriving over the network, and an
     * entity-expansion payload must not be able to read local files or hang the editor.
     */
    private Optional<Document> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            return Optional.of(factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
