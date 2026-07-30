package be.imgn.alexandria.infrastructure.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import be.imgn.alexandria.application.lookup.BookDraft;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.manifestation.Identifier;

class ChainedLookupTest {

    private static final Identifier ISBN = Identifier.isbn("9780060188702");

    private record Stub(String name, Optional<BookDraft> answer, AtomicInteger calls) implements BookLookup {

        static Stub answering(String name, String title) {
            return new Stub(name, Optional.of(BookDraft.of(title, ISBN, name).build()), new AtomicInteger());
        }

        static Stub silent(String name) {
            return new Stub(name, Optional.empty(), new AtomicInteger());
        }

        @Override
        public Optional<BookDraft> byIsbn(Identifier isbn) {
            calls.incrementAndGet();
            return answer;
        }
    }

    @Test
    void takesTheFirstProviderThatAnswers() {
        Stub first = Stub.answering("first", "From the first");
        Stub second = Stub.answering("second", "From the second");

        Optional<BookDraft> found = new ChainedLookup(List.of(first, second)).byIsbn(ISBN);

        assertThat(found).hasValueSatisfying(draft -> assertThat(draft.title()).isEqualTo("From the first"));
        assertThat(second.calls()).hasValue(0);
    }

    @Test
    void movesOnWhenAProviderHasNothing() {
        Stub empty = Stub.silent("empty");
        Stub answering = Stub.answering("answering", "Found later");

        Optional<BookDraft> found = new ChainedLookup(List.of(empty, answering)).byIsbn(ISBN);

        assertThat(found).hasValueSatisfying(draft -> assertThat(draft.title()).isEqualTo("Found later"));
        assertThat(empty.calls()).hasValue(1);
    }

    @Test
    void doesNotLetOneBrokenProviderSinkTheRest() {
        BookLookup broken = new BookLookup() {
            @Override
            public Optional<BookDraft> byIsbn(Identifier isbn) {
                throw new IllegalStateException("service on fire");
            }

            @Override
            public String name() {
                return "broken";
            }
        };
        Stub healthy = Stub.answering("healthy", "Still found");

        assertThat(new ChainedLookup(List.of(broken, healthy)).byIsbn(ISBN))
                .hasValueSatisfying(draft -> assertThat(draft.title()).isEqualTo("Still found"));
    }

    @Test
    void reportsAMissWhenNobodyHasTheBook() {
        assertThat(new ChainedLookup(List.of(Stub.silent("a"), Stub.silent("b"))).byIsbn(ISBN))
                .isEmpty();
    }

    @Test
    void offlineReachesNothingAtAll() {
        assertThat(BookLookup.offline().byIsbn(ISBN)).isEmpty();
    }

    @Test
    void triesTheProvidersInTheDocumentedOrder() {
        assertThat(ChainedLookup.standard().name()).isEqualTo("Open Library, then BnF, then Google Books");
    }
}
