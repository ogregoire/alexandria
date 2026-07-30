package be.imgn.alexandria.infrastructure.json.codec;

import java.time.LocalDate;
import java.util.Optional;

import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.Money;

/**
 * Reads and writes an {@link Item} without a serialisation library.
 *
 * <p>A prototype for comparison with {@code infrastructure/json/Mixins}, which maps fifteen sealed hierarchies by
 * listing their variants in annotations. The point of the comparison is the failure mode, not the line count:
 * <strong>every switch below is exhaustive over a sealed type, so adding a variant stops the build.</strong> A missing
 * {@code @JsonSubTypes.Type} entry does not — it throws when a file happens to contain that variant, which may be
 * months later.
 *
 * <p>Item was chosen because it is the awkward one: three sealed hierarchies, an enum, and two value objects, all with
 * optional fields.
 */
public final class ItemCodec {

    private ItemCodec() {}

    // ------------------------------------------------------------------- writing

    public static String write(Item item) {
        return JsonOut.document(out -> out.text("id", item.id().value())
                .text("embodiedIn", item.embodiedIn().value())
                .object("acquisition", nested -> acquisition(nested, item.acquisition()))
                .object("location", nested -> location(nested, item.location()))
                .object("reading", nested -> reading(nested, item.reading()))
                .text("condition", item.condition().name())
                .text("notes", item.notes()));
    }

    private static void acquisition(JsonOut out, Acquisition acquisition) {
        switch (acquisition) {
            case Acquisition.Purchased(Optional<LocalDate> date, Optional<Money> price, Optional<String> from) ->
                out.text("type", "purchased")
                        .text("date", date.map(LocalDate::toString))
                        .text("price", price.map(Money::text))
                        .text("from", from);
            case Acquisition.Gift(Optional<LocalDate> date, Optional<String> from) ->
                out.text("type", "gift")
                        .text("date", date.map(LocalDate::toString))
                        .text("from", from);
            case Acquisition.Inherited(Optional<LocalDate> date, Optional<String> from) ->
                out.text("type", "inherited")
                        .text("date", date.map(LocalDate::toString))
                        .text("from", from);
            case Acquisition.Borrowed(String from, Optional<LocalDate> since, Optional<LocalDate> due) ->
                out.text("type", "borrowed")
                        .text("from", from)
                        .text("since", since.map(LocalDate::toString))
                        .text("due", due.map(LocalDate::toString));
            case Acquisition.Unrecorded() -> out.text("type", "unrecorded");
        }
    }

    private static void location(JsonOut out, Location location) {
        switch (location) {
            case Location.Shelf(String name, Optional<String> position) ->
                out.text("type", "shelf").text("name", name).text("position", position);
            case Location.Box(String label) -> out.text("type", "box").text("label", label);
            case Location.LentTo(String person, Optional<LocalDate> since) ->
                out.text("type", "lent-to").text("person", person).text("since", since.map(LocalDate::toString));
            case Location.Device(String name) -> out.text("type", "device").text("name", name);
            case Location.Missing() -> out.text("type", "missing");
        }
    }

    private static void reading(JsonOut out, ReadingProgress reading) {
        switch (reading) {
            case ReadingProgress.Unread() -> out.text("type", "unread");
            case ReadingProgress.Reading(Optional<LocalDate> since, Optional<Integer> page) ->
                out.text("type", "reading")
                        .text("since", since.map(LocalDate::toString))
                        .number("page", page);
            case ReadingProgress.Finished(Optional<LocalDate> on, Optional<Rating> rating) ->
                out.text("type", "finished")
                        .text("on", on.map(LocalDate::toString))
                        .number("rating", rating.map(Rating::stars));
            case ReadingProgress.Abandoned(Optional<LocalDate> on, Optional<Integer> atPage, String why) ->
                out.text("type", "abandoned")
                        .text("on", on.map(LocalDate::toString))
                        .number("atPage", atPage)
                        .text("why", why);
        }
    }

    // ------------------------------------------------------------------- reading

    public static Item read(String json) {
        JsonIn in = JsonIn.parse(json);
        return new Item(
                ItemId.of(in.text("id")),
                ManifestationId.of(in.text("embodiedIn")),
                acquisition(in.object("acquisition")),
                location(in.object("location")),
                reading(in.object("reading")),
                Condition.valueOf(in.text("condition")),
                in.optionalText("notes"));
    }

    private static Acquisition acquisition(JsonIn in) {
        return switch (in.type()) {
            case "purchased" ->
                new Acquisition.Purchased(
                        in.optionalDate("date"), in.optionalText("price").map(Money::parse), in.optionalText("from"));
            case "gift" -> new Acquisition.Gift(in.optionalDate("date"), in.optionalText("from"));
            case "inherited" -> new Acquisition.Inherited(in.optionalDate("date"), in.optionalText("from"));
            case "borrowed" ->
                new Acquisition.Borrowed(in.text("from"), in.optionalDate("since"), in.optionalDate("due"));
            case "unrecorded" -> Acquisition.UNRECORDED;
            default -> throw unknown("acquisition", in.type());
        };
    }

    private static Location location(JsonIn in) {
        return switch (in.type()) {
            case "shelf" -> new Location.Shelf(in.text("name"), in.optionalText("position"));
            case "box" -> new Location.Box(in.text("label"));
            case "lent-to" -> new Location.LentTo(in.text("person"), in.optionalDate("since"));
            case "device" -> new Location.Device(in.text("name"));
            case "missing" -> Location.MISSING;
            default -> throw unknown("location", in.type());
        };
    }

    private static ReadingProgress reading(JsonIn in) {
        return switch (in.type()) {
            case "unread" -> ReadingProgress.UNREAD;
            case "reading" -> new ReadingProgress.Reading(in.optionalDate("since"), in.optionalInt("page"));
            case "finished" ->
                new ReadingProgress.Finished(
                        in.optionalDate("on"), in.optionalInt("rating").map(Rating::of));
            case "abandoned" ->
                new ReadingProgress.Abandoned(in.optionalDate("on"), in.optionalInt("atPage"), in.text("why"));
            default -> throw unknown("reading progress", in.type());
        };
    }

    private static IllegalArgumentException unknown(String what, String variant) {
        return new IllegalArgumentException("unknown " + what + " variant '" + variant + "'");
    }
}
