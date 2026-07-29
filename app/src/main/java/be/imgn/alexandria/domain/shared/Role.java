package be.imgn.alexandria.domain.shared;

/**
 * The relationship an {@link Agent} has to a Work, Expression or Manifestation.
 * Open-ended by nature, hence the {@link Other} escape hatch rather than an enum.
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

    record Illustrator() implements Role {
        @Override
        public String label() {
            return "illustrator";
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
    Role NARRATOR = new Narrator();
    Role PUBLISHER = new Publisher();
}
