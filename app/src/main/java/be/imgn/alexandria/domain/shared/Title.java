package be.imgn.alexandria.domain.shared;

/**
 * A title proper, and — when the title page carries one — the other title information beneath it.
 *
 * <p>Two shapes rather than one shape with an optional half. {@code Optional} is documented for use as a return type; a
 * record component is also a constructor parameter, so {@code Title(String, Optional<String>)} asked every caller to
 * build one, which is not what it is for. A title either carries a subtitle or it does not, and that is a sum.
 *
 * <p>Nothing on the wire changes: a title is written as its {@code main} plus a {@code subtitle} when there is one, and
 * read back by which keys are present. The variant carries no discriminator because nobody chooses it — you type a
 * subtitle or you leave the field empty.
 *
 * <p>There is deliberately no {@code full()} here. Joining a title to its subtitle is punctuation, and punctuation
 * belongs to a language: English closes up the colon, French holds a space before it. See {@code TitleFormat}.
 */
public sealed interface Title {

    String main();

    static Title of(String main) {
        return new Plain(main);
    }

    /** A blank or absent subtitle gives a {@link Plain} title: nothing on the title page, nothing recorded. */
    static Title of(String main, String subtitle) {
        return subtitle == null || subtitle.isBlank() ? new Plain(main) : new Subtitled(main, subtitle);
    }

    record Plain(String main) implements Title {

        public Plain {
            Guard.notBlank(main, "main");
        }
    }

    record Subtitled(String main, String subtitle) implements Title {

        public Subtitled {
            Guard.notBlank(main, "main");
            Guard.notBlank(subtitle, "subtitle");
        }
    }
}
