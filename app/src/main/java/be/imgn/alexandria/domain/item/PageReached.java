package be.imgn.alexandria.domain.item;

import be.imgn.alexandria.domain.shared.Guard;

/** How far into a book a reader got, when they noted it down. */
public sealed interface PageReached {

    PageReached UNRECORDED = new Unrecorded();

    static PageReached at(int page) {
        return new AtPage(page);
    }

    /** Nothing typed in the field means nothing to record, not page zero. */
    static PageReached parse(String page) {
        return page == null || page.isBlank() ? UNRECORDED : new AtPage(Integer.parseInt(page.trim()));
    }

    /** For a form field and for the codec: the number, or blank. */
    String stored();

    record AtPage(int page) implements PageReached {

        public AtPage {
            Guard.inRange(page, 1, 100_000, "page");
        }

        @Override
        public String stored() {
            return String.valueOf(page);
        }
    }

    record Unrecorded() implements PageReached {

        @Override
        public String stored() {
            return "";
        }
    }
}
