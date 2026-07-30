package be.imgn.alexandria.infrastructure.json.codec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import be.imgn.alexandria.domain.manifestation.Carrier;
import be.imgn.alexandria.domain.manifestation.EditionStatement;
import be.imgn.alexandria.domain.manifestation.Extent;
import be.imgn.alexandria.domain.manifestation.Identifier;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.manifestation.Publisher;
import be.imgn.alexandria.domain.manifestation.Series;
import be.imgn.alexandria.domain.work.ExpressionId;

/** Reads and writes a {@link Manifestation}. */
public final class ManifestationCodec {

    private ManifestationCodec() {}

    public static String write(Manifestation manifestation) {
        List<String> embodies =
                manifestation.embodies().stream().map(ExpressionId::qualified).toList();
        return JsonOut.document(out -> {
            out.text("id", manifestation.id().value())
                    .texts("embodies", embodies)
                    .object("title", nested -> SharedCodec.title(nested, manifestation.title()))
                    .textIfAny("publisher", manifestation.publisher().stored())
                    .object("published", nested -> SharedCodec.date(nested, manifestation.published()))
                    .object("carrier", nested -> carrier(nested, manifestation.carrier()))
                    .object("identifier", nested -> identifier(nested, manifestation.identifier()))
                    .object("extent", nested -> extent(nested, manifestation.extent()));
            if (!(manifestation.series() instanceof Series.Standalone)) {
                out.object("series", nested -> series(nested, manifestation.series()));
            }
            out.numberIfAny("editionStatement", manifestation.editionStatement().stored());
        });
    }

    public static Manifestation read(String json) {
        JsonIn in = JsonIn.parse(json);
        List<ExpressionId> embodies = new ArrayList<>();
        for (String reference : in.texts("embodies")) {
            embodies.add(ExpressionId.parse(reference));
        }
        return new Manifestation(
                ManifestationId.of(in.text("id")),
                embodies,
                SharedCodec.title(in.object("title")),
                Publisher.parse(in.orBlank("publisher")),
                SharedCodec.date(in.object("published")),
                carrier(in.object("carrier")),
                identifier(in.object("identifier")),
                extent(in.object("extent")),
                in.optionalObject("series").map(ManifestationCodec::series).orElse(Series.STANDALONE),
                EditionStatement.parse(in.numberOrBlank("editionStatement")));
    }

    // ------------------------------------------------------------------ carrier

    private static void carrier(JsonOut out, Carrier carrier) {
        switch (carrier) {
            case Carrier.Hardcover() -> out.text("type", "hardcover");
            case Carrier.Paperback() -> out.text("type", "paperback");
            case Carrier.MassMarket() -> out.text("type", "mass-market");
            case Carrier.Ebook(Carrier.EbookFormat format) ->
                out.text("type", "ebook").text("format", format.name());
            case Carrier.Audiobook(String medium) ->
                out.text("type", "audiobook").text("medium", medium);
            case Carrier.Other(String label) -> out.text("type", "other").text("label", label);
        }
    }

    private static Carrier carrier(JsonIn in) {
        return switch (in.type()) {
            case "hardcover" -> new Carrier.Hardcover();
            case "paperback" -> new Carrier.Paperback();
            case "mass-market" -> new Carrier.MassMarket();
            case "ebook" -> new Carrier.Ebook(Carrier.EbookFormat.valueOf(in.text("format")));
            case "audiobook" -> new Carrier.Audiobook(in.text("medium"));
            case "other" -> new Carrier.Other(in.text("label"));
            default -> SharedCodec.unknown("carrier", in.type());
        };
    }

    // --------------------------------------------------------------- identifier

    private static void identifier(JsonOut out, Identifier identifier) {
        switch (identifier) {
            case Identifier.Isbn13(String digits) -> out.text("type", "isbn13").text("digits", digits);
            case Identifier.Isbn10(String digits) -> out.text("type", "isbn10").text("digits", digits);
            case Identifier.Asin(String value) -> out.text("type", "asin").text("value", value);
            case Identifier.Custom(String scheme, String value) ->
                out.text("type", "custom").text("scheme", scheme).text("value", value);
            case Identifier.None() -> out.text("type", "none");
        }
    }

    private static Identifier identifier(JsonIn in) {
        return switch (in.type()) {
            case "isbn13" -> new Identifier.Isbn13(in.text("digits"));
            case "isbn10" -> new Identifier.Isbn10(in.text("digits"));
            case "asin" -> new Identifier.Asin(in.text("value"));
            case "custom" -> new Identifier.Custom(in.text("scheme"), in.text("value"));
            case "none" -> Identifier.NONE;
            default -> SharedCodec.unknown("identifier", in.type());
        };
    }

    // ------------------------------------------------------------------- extent

    private static void extent(JsonOut out, Extent extent) {
        switch (extent) {
            case Extent.Pages(int count) -> out.text("type", "pages").number("count", count);
            case Extent.Volumes(int count, int pagesTotal) ->
                out.text("type", "volumes").number("count", count).number("pagesTotal", pagesTotal);
            // ISO-8601, matching what java.time.Duration prints and parses.
            case Extent.Playtime(Duration duration) ->
                out.text("type", "playtime").text("duration", duration.toString());
            case Extent.Unspecified() -> out.text("type", "unspecified");
        }
    }

    private static Extent extent(JsonIn in) {
        return switch (in.type()) {
            case "pages" ->
                new Extent.Pages(
                        in.optionalInt("count").orElseThrow(() -> new IllegalArgumentException("pages needs 'count'")));
            case "volumes" ->
                new Extent.Volumes(
                        in.optionalInt("count")
                                .orElseThrow(() -> new IllegalArgumentException("volumes needs 'count'")),
                        in.optionalInt("pagesTotal")
                                .orElseThrow(() -> new IllegalArgumentException("volumes needs 'pagesTotal'")));
            case "playtime" -> new Extent.Playtime(Duration.parse(in.text("duration")));
            case "unspecified" -> Extent.UNSPECIFIED;
            default -> SharedCodec.unknown("extent", in.type());
        };
    }

    // ------------------------------------------------------------------- series

    private static void series(JsonOut out, Series series) {
        switch (series) {
            case Series.Standalone() -> {
                // Never written: a standalone edition has no series object at all.
            }
            case Series.Unnumbered(String name) -> out.text("name", name);
            case Series.Numbered(String name, String number) ->
                out.text("name", name).text("number", number);
        }
    }

    private static Series series(JsonIn in) {
        String name = in.text("name");
        return in.optionalText("number")
                .<Series>map(number -> new Series.Numbered(name, number))
                .orElseGet(() -> new Series.Unnumbered(name));
    }

    static Optional<Series> maybeSeries(JsonIn in) {
        return in.optionalObject("series").map(ManifestationCodec::series);
    }
}
