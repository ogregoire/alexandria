package be.imgn.alexandria.domain.manifestation;

import be.imgn.alexandria.domain.shared.Slug;

/** Identity of the {@link Manifestation} aggregate. Doubles as the on-disk file name. */
public record ManifestationId(String value) {

    public ManifestationId {
        Slug.validate(value, "ManifestationId");
    }

    public static ManifestationId of(String value) {
        return new ManifestationId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
