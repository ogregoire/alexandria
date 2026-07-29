package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Slug;

/** Identity of the {@link Item} aggregate. Doubles as the on-disk file name. */
public record ItemId(String value) {

    public ItemId {
        Slug.validate(value, "ItemId");
    }

    public static ItemId of(String value) {
        return new ItemId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
