package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.manifestation.ManifestationId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * FRBR Item: the single copy on your shelf — this printing, this dust jacket, this coffee ring.
 *
 * <p>Aggregate root, and the only level at which a personal library differs from a
 * bibliography: everything below (acquisition, shelving, condition, reading) is true of
 * one copy and of no other.
 */
public record Item(
        ItemId id,
        ManifestationId embodiedIn,
        Acquisition acquisition,
        Location location,
        ReadingProgress reading,
        Condition condition,
        Optional<String> notes) {

    public Item {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(embodiedIn, "embodiedIn");
        Objects.requireNonNull(acquisition, "acquisition");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(reading, "reading");
        Objects.requireNonNull(condition, "condition");
        notes = notes == null ? Optional.empty() : notes.filter(n -> !n.isBlank());

        if (!acquisition.owned() && location instanceof Location.LentTo(String person, var ignored)) {
            throw new IllegalArgumentException(
                    "cannot lend " + id + " to " + person + ": it is itself on loan to you");
        }
    }

    public static Item shelved(ItemId id, ManifestationId embodiedIn, Acquisition acquisition, String shelf) {
        return new Item(id, embodiedIn, acquisition, Location.shelf(shelf),
                ReadingProgress.UNREAD, Condition.UNGRADED, Optional.empty());
    }

    public Item startReading(LocalDate on) {
        return withReading(new ReadingProgress.Reading(Optional.ofNullable(on), Optional.empty()));
    }

    public Item reachedPage(int page) {
        if (!(reading instanceof ReadingProgress.Reading(Optional<LocalDate> since, var ignored))) {
            throw new IllegalStateException("item " + id + " is not currently being read");
        }
        return withReading(new ReadingProgress.Reading(since, Optional.of(page)));
    }

    /** A null date records that it was read without claiming to know when. */
    public Item finishReading(LocalDate on, Rating rating) {
        return withReading(new ReadingProgress.Finished(
                Optional.ofNullable(on), Optional.ofNullable(rating)));
    }

    public Item abandonReading(LocalDate on, String why) {
        Optional<Integer> atPage =
                reading instanceof ReadingProgress.Reading(var ignored, Optional<Integer> page)
                        ? page
                        : Optional.empty();
        return withReading(new ReadingProgress.Abandoned(Optional.ofNullable(on), atPage, why));
    }

    public Item lendTo(String person, LocalDate on) {
        return withLocation(Location.LentTo.to(person, on));
    }

    public Item shelveAt(String shelf) {
        return withLocation(Location.shelf(shelf));
    }

    public Item withReading(ReadingProgress newReading) {
        return new Item(id, embodiedIn, acquisition, location, newReading, condition, notes);
    }

    public Item withLocation(Location newLocation) {
        return new Item(id, embodiedIn, acquisition, newLocation, reading, condition, notes);
    }

    public Item withCondition(Condition newCondition) {
        return new Item(id, embodiedIn, acquisition, location, reading, newCondition, notes);
    }

    public Item withNotes(String newNotes) {
        return new Item(id, embodiedIn, acquisition, location, reading, condition, Optional.ofNullable(newNotes));
    }
}
