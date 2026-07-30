package be.imgn.alexandria.infrastructure.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Driven by a fake clock, so the rate-limit arithmetic is checked without the suite ever sleeping. */
class ThrottleTest {

    private final AtomicLong now = new AtomicLong(0);
    private final Throttle throttle = new Throttle(Duration.ofMillis(1000), now::get);

    private void advance(long millis) {
        now.addAndGet(millis * 1_000_000);
    }

    @Test
    void letsTheFirstRequestThroughAtOnce() {
        assertThat(throttle.reserveMillis("openlibrary.org")).isZero();
    }

    /** The case that matters: one Open Library lookup issues three calls in a row. */
    @Test
    void spacesThreeImmediateRequestsAFullSecondApart() {
        assertThat(throttle.reserveMillis("openlibrary.org")).isZero();
        assertThat(throttle.reserveMillis("openlibrary.org")).isEqualTo(1000);
        assertThat(throttle.reserveMillis("openlibrary.org")).isEqualTo(2000);
    }

    @Test
    void chargesNothingWhenTheIntervalHasAlreadyElapsed() {
        throttle.reserveMillis("openlibrary.org");
        advance(1500);

        assertThat(throttle.reserveMillis("openlibrary.org")).isZero();
    }

    @Test
    void chargesOnlyTheRemainderOfAPartlyElapsedInterval() {
        throttle.reserveMillis("openlibrary.org");
        advance(400);

        assertThat(throttle.reserveMillis("openlibrary.org")).isEqualTo(600);
    }

    @Test
    void throttlesEachHostIndependently() {
        throttle.reserveMillis("openlibrary.org");

        assertThat(throttle.reserveMillis("catalogue.bnf.fr"))
                .as("a busy Open Library must not delay the BnF")
                .isZero();
    }

    @Test
    void pushesTheNextSlotOutAfterBackingOff() {
        throttle.reserveMillis("www.googleapis.com");
        throttle.backOff("www.googleapis.com", Duration.ofMillis(3000));

        assertThat(throttle.reserveMillis("www.googleapis.com")).isEqualTo(3000);
    }

    @Test
    void roundsAPartialMillisecondUpRatherThanDown() {
        Throttle fine = new Throttle(Duration.ofNanos(1_500_000), now::get);
        fine.reserveMillis("host");

        assertThat(fine.reserveMillis("host"))
                .as("sleeping 1ms for a 1.5ms interval would breach it")
                .isEqualTo(2);
    }

    @Test
    void identifiesItselfOnlyWhenAContactIsReallyGiven() {
        assertThat(UserAgent.anonymous().identified()).isFalse();
        assertThat(UserAgent.identifiedBy("  ").identified()).isFalse();
        assertThat(UserAgent.identifiedBy("me@example.org").identified()).isTrue();
        assertThat(UserAgent.identifiedBy("me@example.org").header())
                .contains("Alexandria/1.0")
                .contains("me@example.org");
    }

    @Test
    void stripsControlCharactersOutOfAContactBeforeItReachesAHeader() {
        assertThat(UserAgent.identifiedBy("me@example.org\r\nX-Evil: 1").header())
                .doesNotContain("\r")
                .doesNotContain("\n");
    }
}
