package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;

/** A personal verdict, one to five. */
public record Rating(int stars) {

    public Rating {
        Guard.inRange(stars, 1, 5, "stars");
    }

    public static Rating of(int stars) {
        return new Rating(stars);
    }

    public String display() {
        return "★".repeat(stars) + "☆".repeat(5 - stars);
    }
}
