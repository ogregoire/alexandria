package be.imgn.alexandria.infrastructure.json.codec;

import be.imgn.alexandria.domain.item.Acquisition;
import be.imgn.alexandria.domain.item.Condition;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Location;
import be.imgn.alexandria.domain.item.PageReached;
import be.imgn.alexandria.domain.item.ReadingProgress;
import be.imgn.alexandria.domain.item.Verdict;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.EventDate;
import be.imgn.alexandria.domain.shared.Note;
import be.imgn.alexandria.domain.shared.Price;

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
                .textIfAny("notes", item.notes().text()));
    }

    private static void acquisition(JsonOut out, Acquisition acquisition) {
        switch (acquisition) {
            case Acquisition.Purchased(EventDate date, Price price, Note from) ->
                out.text("type", "purchased")
                        .textIfAny("date", date.iso())
                        .textIfAny("price", price.stored())
                        .textIfAny("from", from.text());
            case Acquisition.Gift(EventDate date, Note from) ->
                out.text("type", "gift").textIfAny("date", date.iso()).textIfAny("from", from.text());
            case Acquisition.Inherited(EventDate date, Note from) ->
                out.text("type", "inherited").textIfAny("date", date.iso()).textIfAny("from", from.text());
            case Acquisition.Borrowed(String from, EventDate since, EventDate due) ->
                out.text("type", "borrowed")
                        .text("from", from)
                        .textIfAny("since", since.iso())
                        .textIfAny("due", due.iso());
            case Acquisition.Unrecorded() -> out.text("type", "unrecorded");
        }
    }

    private static void location(JsonOut out, Location location) {
        switch (location) {
            case Location.Shelf(String name, Note position) ->
                out.text("type", "shelf").text("name", name).textIfAny("position", position.text());
            case Location.Box(String label) -> out.text("type", "box").text("label", label);
            case Location.LentTo(String person, EventDate since) ->
                out.text("type", "lent-to").text("person", person).textIfAny("since", since.iso());
            case Location.Device(String name) -> out.text("type", "device").text("name", name);
            case Location.Missing() -> out.text("type", "missing");
        }
    }

    private static void reading(JsonOut out, ReadingProgress reading) {
        switch (reading) {
            case ReadingProgress.Unread() -> out.text("type", "unread");
            case ReadingProgress.Reading(EventDate since, PageReached page) ->
                out.text("type", "reading").textIfAny("since", since.iso()).numberIfAny("page", page.stored());
            case ReadingProgress.Finished(EventDate on, Verdict verdict) ->
                out.text("type", "finished").textIfAny("on", on.iso()).numberIfAny("rating", verdict.stored());
            case ReadingProgress.Abandoned(EventDate on, PageReached atPage, String why) ->
                out.text("type", "abandoned")
                        .textIfAny("on", on.iso())
                        .numberIfAny("atPage", atPage.stored())
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
                Note.of(in.orBlank("notes")));
    }

    private static Acquisition acquisition(JsonIn in) {
        return switch (in.type()) {
            case "purchased" ->
                new Acquisition.Purchased(
                        EventDate.parse(in.orBlank("date")),
                        Price.parse(in.orBlank("price")),
                        Note.of(in.orBlank("from")));
            case "gift" -> new Acquisition.Gift(EventDate.parse(in.orBlank("date")), Note.of(in.orBlank("from")));
            case "inherited" ->
                new Acquisition.Inherited(EventDate.parse(in.orBlank("date")), Note.of(in.orBlank("from")));
            case "borrowed" ->
                new Acquisition.Borrowed(
                        in.text("from"), EventDate.parse(in.orBlank("since")), EventDate.parse(in.orBlank("due")));
            case "unrecorded" -> Acquisition.UNRECORDED;
            default -> throw unknown("acquisition", in.type());
        };
    }

    private static Location location(JsonIn in) {
        return switch (in.type()) {
            case "shelf" -> new Location.Shelf(in.text("name"), Note.of(in.orBlank("position")));
            case "box" -> new Location.Box(in.text("label"));
            case "lent-to" -> new Location.LentTo(in.text("person"), EventDate.parse(in.orBlank("since")));
            case "device" -> new Location.Device(in.text("name"));
            case "missing" -> Location.MISSING;
            default -> throw unknown("location", in.type());
        };
    }

    private static ReadingProgress reading(JsonIn in) {
        return switch (in.type()) {
            case "unread" -> ReadingProgress.UNREAD;
            case "reading" ->
                new ReadingProgress.Reading(
                        EventDate.parse(in.orBlank("since")), PageReached.parse(in.numberOrBlank("page")));
            case "finished" ->
                new ReadingProgress.Finished(
                        EventDate.parse(in.orBlank("on")), Verdict.parse(in.numberOrBlank("rating")));
            case "abandoned" ->
                new ReadingProgress.Abandoned(
                        EventDate.parse(in.orBlank("on")),
                        PageReached.parse(in.numberOrBlank("atPage")),
                        in.text("why"));
            default -> throw unknown("reading progress", in.type());
        };
    }

    private static IllegalArgumentException unknown(String what, String variant) {
        return new IllegalArgumentException("unknown " + what + " variant '" + variant + "'");
    }
}
