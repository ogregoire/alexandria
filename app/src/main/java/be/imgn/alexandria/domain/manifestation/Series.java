package be.imgn.alexandria.domain.manifestation;

import java.util.Optional;

import be.imgn.alexandria.domain.shared.Guard;

/** A publisher's series, e.g. "Penguin Classics" no. 42. */
public record Series(String name, Optional<String> number) {

    public Series {
        Guard.notBlank(name, "name");
        number = number == null ? Optional.empty() : number.filter(n -> !n.isBlank());
    }

    public static Series of(String name) {
        return new Series(name, Optional.empty());
    }

    public String display() {
        return number.map(n -> name + " " + n).orElse(name);
    }
}
