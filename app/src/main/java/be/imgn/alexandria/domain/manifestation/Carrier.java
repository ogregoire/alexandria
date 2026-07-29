package be.imgn.alexandria.domain.manifestation;

import be.imgn.alexandria.domain.shared.Guard;

/**
 * The physical or digital carrier a Manifestation is issued on. This is what makes two
 * printings of the same Expression two Manifestations.
 */
public sealed interface Carrier {

    String label();

    /** True when a copy of this Manifestation occupies shelf space. */
    boolean physical();

    record Hardcover() implements Carrier {
        @Override
        public String label() {
            return "hardcover";
        }

        @Override
        public boolean physical() {
            return true;
        }
    }

    record Paperback() implements Carrier {
        @Override
        public String label() {
            return "paperback";
        }

        @Override
        public boolean physical() {
            return true;
        }
    }

    record MassMarket() implements Carrier {
        @Override
        public String label() {
            return "mass-market paperback";
        }

        @Override
        public boolean physical() {
            return true;
        }
    }

    record Ebook(EbookFormat format) implements Carrier {
        public Ebook {
            if (format == null) {
                throw new IllegalArgumentException("format is required");
            }
        }

        @Override
        public String label() {
            return "ebook (" + format.name().toLowerCase(java.util.Locale.ROOT) + ")";
        }

        @Override
        public boolean physical() {
            return false;
        }
    }

    record Audiobook(String medium) implements Carrier {
        public Audiobook {
            Guard.notBlank(medium, "medium");
        }

        @Override
        public String label() {
            return "audiobook (" + medium + ")";
        }

        @Override
        public boolean physical() {
            return !"download".equalsIgnoreCase(medium);
        }
    }

    record Other(String label) implements Carrier {
        public Other {
            Guard.notBlank(label, "label");
        }

        @Override
        public boolean physical() {
            return true;
        }
    }

    enum EbookFormat {
        EPUB, PDF, MOBI, AZW3, DJVU
    }

    Carrier HARDCOVER = new Hardcover();
    Carrier PAPERBACK = new Paperback();
    Carrier EPUB = new Ebook(EbookFormat.EPUB);
}
