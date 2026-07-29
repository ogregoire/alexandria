package be.imgn.alexandria.application.lookup;

import be.imgn.alexandria.domain.manifestation.Identifier;

import java.util.Optional;

/**
 * Looks an ISBN up somewhere outside the catalogue.
 *
 * <p>A port, so the editor never knows which service answered and the tests never touch the
 * network. Implementations must not throw for a book they simply do not have: a miss is an
 * empty result, and only a genuine failure — no network, a malformed reply — is an
 * exception.
 */
public interface BookLookup {

    Optional<BookDraft> byIsbn(Identifier isbn);

    /** What to show the user when naming where a prefill came from. */
    String name();

    /** Used when lookups are switched off; answers nothing and reaches nowhere. */
    static BookLookup offline() {
        return new BookLookup() {
            @Override
            public Optional<BookDraft> byIsbn(Identifier isbn) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return "offline";
            }
        };
    }
}
