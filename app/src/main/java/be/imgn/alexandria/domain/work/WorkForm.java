package be.imgn.alexandria.domain.work;

import be.imgn.alexandria.domain.shared.Guard;

/** The literary form of a Work. */
public sealed interface WorkForm {

    String label();

    record Novel() implements WorkForm {
        @Override
        public String label() {
            return "novel";
        }
    }

    record Novella() implements WorkForm {
        @Override
        public String label() {
            return "novella";
        }
    }

    record ShortStories() implements WorkForm {
        @Override
        public String label() {
            return "short stories";
        }
    }

    record Poetry() implements WorkForm {
        @Override
        public String label() {
            return "poetry";
        }
    }

    record Drama() implements WorkForm {
        @Override
        public String label() {
            return "drama";
        }
    }

    record Essay() implements WorkForm {
        @Override
        public String label() {
            return "essay";
        }
    }

    record Nonfiction() implements WorkForm {
        @Override
        public String label() {
            return "nonfiction";
        }
    }

    record Reference() implements WorkForm {
        @Override
        public String label() {
            return "reference";
        }
    }

    record Comics() implements WorkForm {
        @Override
        public String label() {
            return "comics";
        }
    }

    record Other(String label) implements WorkForm {
        public Other {
            Guard.notBlank(label, "label");
        }
    }

    WorkForm NOVEL = new Novel();
    WorkForm NONFICTION = new Nonfiction();
}
