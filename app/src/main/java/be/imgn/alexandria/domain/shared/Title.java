package be.imgn.alexandria.domain.shared;

import java.util.Optional;

/** A title proper, optionally qualified by a subtitle. */
public record Title(String main, Optional<String> subtitle) {

    public Title {
        Guard.notBlank(main, "main");
        subtitle = subtitle == null ? Optional.empty() : subtitle.filter(s -> !s.isBlank());
    }

    public static Title of(String main) {
        return new Title(main, Optional.empty());
    }

    public static Title of(String main, String subtitle) {
        return new Title(main, Optional.ofNullable(subtitle));
    }

    /** "Main : subtitle", the ISBD-flavoured single-line rendering. */
    public String full() {
        return subtitle.map(s -> main + " : " + s).orElse(main);
    }
}
