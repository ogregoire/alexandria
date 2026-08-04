package be.imgn.alexandria.domain.shared;

/**
 * The relationship an {@link Agent} has to a Work, Expression or Manifestation. Open-ended by nature, hence the
 * {@link Other} escape hatch rather than an enum.
 */
public sealed interface Role {

    String label();

    record Author() implements Role {
        @Override
        public String label() {
            return "author";
        }
    }

    record Translator() implements Role {
        @Override
        public String label() {
            return "translator";
        }
    }

    record Editor() implements Role {
        @Override
        public String label() {
            return "editor";
        }
    }

    /** Illustrated the text itself. Plates bound with a text make a new Expression of it, not a new printing. */
    record Illustrator() implements Role {
        @Override
        public String label() {
            return "illustrator";
        }
    }

    /**
     * Painted the cover of one printing. Distinct from {@link Illustrator} on purpose: a jacket is a property of the
     * Manifestation, so the same translation reissued behind new art is the same Expression and a different
     * Manifestation. Collapsing the two would credit a cover artist with having illustrated a text they never touched.
     */
    record CoverArtist() implements Role {
        @Override
        public String label() {
            return "cover artist";
        }
    }

    record Narrator() implements Role {
        @Override
        public String label() {
            return "narrator";
        }
    }

    record Publisher() implements Role {
        @Override
        public String label() {
            return "publisher";
        }
    }

    record Other(String label) implements Role {
        public Other {
            Guard.notBlank(label, "label");
        }
    }

    Role AUTHOR = new Author();
    Role TRANSLATOR = new Translator();
    Role EDITOR = new Editor();
    Role ILLUSTRATOR = new Illustrator();
    Role COVER_ARTIST = new CoverArtist();
    Role NARRATOR = new Narrator();
    Role PUBLISHER = new Publisher();
}
