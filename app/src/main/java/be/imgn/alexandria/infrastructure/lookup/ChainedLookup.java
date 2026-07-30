package be.imgn.alexandria.infrastructure.lookup;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.manifestation.Identifier;

/**
 * Asks each service in turn and takes the first that answers.
 *
 * <p>First hit wins rather than merging across providers: one book's metadata coming from one catalogue is easier to
 * sanity-check than a blend of three, and the form is going to be reviewed by hand anyway. The order is Open Library,
 * then the BnF for the French publishing it knows best, then Google Books.
 *
 * <p>A provider that throws is skipped rather than allowed to sink the whole lookup — an outage at one service should
 * not stop the others answering.
 */
public final class ChainedLookup implements BookLookup {

    private final List<BookLookup> providers;

    public ChainedLookup(List<BookLookup> providers) {
        this.providers = List.copyOf(providers);
    }

    /** The default chain: free, no API key, English and French. */
    public static ChainedLookup standard() {
        return standard(null);
    }

    /**
     * @param contact an email or URL to identify this installation with. Supplying one triples the rate Open Library
     *     allows; without it every provider is held to its unidentified limit.
     */
    public static ChainedLookup standard(String contact) {
        UserAgent caller = UserAgent.identifiedBy(contact);
        return new ChainedLookup(
                List.of(new OpenLibraryLookup(caller), new BnfLookup(caller), new GoogleBooksLookup(caller)));
    }

    @Override
    public Optional<BookDraft> byIsbn(Identifier isbn) {
        for (BookLookup provider : providers) {
            try {
                Optional<BookDraft> found = provider.byIsbn(isbn);
                if (found.isPresent()) {
                    return found;
                }
            } catch (RuntimeException ignored) {
                // One provider misbehaving must not stop the next from being asked.
            }
        }
        return Optional.empty();
    }

    @Override
    public String name() {
        return providers.stream().map(BookLookup::name).collect(Collectors.joining(", then "));
    }
}
