package be.imgn.alexandria.domain.manifestation;

import be.imgn.alexandria.domain.shared.Guard;

import java.time.Duration;

/** How much of it there is. Pages and playing time are not interchangeable, so they are variants. */
public sealed interface Extent {

    String display();

    record Pages(int count) implements Extent {
        public Pages {
            Guard.inRange(count, 1, 100_000, "count");
        }

        @Override
        public String display() {
            return count + " pp.";
        }
    }

    record Volumes(int count, int pagesTotal) implements Extent {
        public Volumes {
            Guard.inRange(count, 1, 1_000, "count");
            Guard.inRange(pagesTotal, 1, 1_000_000, "pagesTotal");
        }

        @Override
        public String display() {
            return count + " vols., " + pagesTotal + " pp.";
        }
    }

    record Playtime(Duration duration) implements Extent {
        public Playtime {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("duration must be positive");
            }
        }

        @Override
        public String display() {
            return duration.toHours() + "h " + duration.toMinutesPart() + "m";
        }
    }

    record Unspecified() implements Extent {
        @Override
        public String display() {
            return "";
        }
    }

    Extent UNSPECIFIED = new Unspecified();

    static Extent pages(int count) {
        return new Pages(count);
    }
}
