package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;

import java.time.LocalDate;
import java.util.Optional;

/** Where the copy currently is. */
public sealed interface Location {

    String display();

    /** True when the copy is within reach right now. */
    boolean athand();

    record Shelf(String name, Optional<String> position) implements Location {
        public Shelf {
            Guard.notBlank(name, "name");
            position = position == null ? Optional.empty() : position.filter(p -> !p.isBlank());
        }

        @Override
        public String display() {
            return position.map(p -> name + " (" + p + ")").orElse(name);
        }

        @Override
        public boolean athand() {
            return true;
        }
    }

    record Box(String label) implements Location {
        public Box {
            Guard.notBlank(label, "label");
        }

        @Override
        public String display() {
            return "box " + label;
        }

        @Override
        public boolean athand() {
            return false;
        }
    }

    /** The borrower is required: an unnamed borrower is a lost book, not a loan. */
    record LentTo(String person, Optional<LocalDate> since) implements Location {
        public LentTo {
            Guard.notBlank(person, "person");
            since = since == null ? Optional.empty() : since;
        }

        public static LentTo to(String person, LocalDate since) {
            return new LentTo(person, Optional.ofNullable(since));
        }

        @Override
        public String display() {
            return "lent to " + person + since.map(date -> " since " + date).orElse("");
        }

        @Override
        public boolean athand() {
            return false;
        }
    }

    /** For ebooks and audio files: the device or library holding the file. */
    record Device(String name) implements Location {
        public Device {
            Guard.notBlank(name, "name");
        }

        @Override
        public String display() {
            return name;
        }

        @Override
        public boolean athand() {
            return true;
        }
    }

    record Missing() implements Location {
        @Override
        public String display() {
            return "missing";
        }

        @Override
        public boolean athand() {
            return false;
        }
    }

    Location MISSING = new Missing();

    static Location shelf(String name) {
        return new Shelf(name, Optional.empty());
    }
}
