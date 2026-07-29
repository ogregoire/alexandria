package be.imgn.alexandria.infrastructure.lookup;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Keeps requests to one host at least a fixed interval apart.
 *
 * <p>Open Library documents one request per second for unidentified callers, and a single
 * ISBN lookup there needs three calls — the edition, the resolved names, the work — so
 * without this the very first lookup would breach the limit three times over.
 *
 * <p>It reserves slots rather than merely checking the clock: each caller is told how long to
 * wait and the next slot is booked immediately, so several calls issued at once queue behind
 * each other instead of all seeing an idle host and firing together.
 */
final class Throttle {

    private final long minimumIntervalNanos;
    private final LongSupplier clock;
    private final Map<String, Long> nextFreeByHost = new ConcurrentHashMap<>();

    Throttle(Duration minimumInterval) {
        this(minimumInterval, System::nanoTime);
    }

    Throttle(Duration minimumInterval, LongSupplier clock) {
        this.minimumIntervalNanos = minimumInterval.toNanos();
        this.clock = clock;
    }

    /**
     * Books the next slot for this host.
     *
     * @return how many milliseconds the caller must wait before making its request
     */
    synchronized long reserveMillis(String host) {
        long now = clock.getAsLong();
        long nextFree = nextFreeByHost.getOrDefault(host, now);
        long slot = Math.max(now, nextFree);
        nextFreeByHost.put(host, slot + minimumIntervalNanos);
        long waitNanos = slot - now;
        // Rounded up: sleeping a fraction of a millisecond short would breach the interval.
        return waitNanos <= 0 ? 0 : (waitNanos + 999_999) / 1_000_000;
    }

    /**
     * Holds a host off for at least this long from now, after a 429 or a 503.
     *
     * <p>Measured from now rather than added to the slot already booked, so a
     * {@code Retry-After: 3} means three seconds from now — which is what the service asked
     * for — instead of three on top of whatever interval was pending.
     */
    synchronized void backOff(String host, Duration extra) {
        long now = clock.getAsLong();
        long nextFree = nextFreeByHost.getOrDefault(host, now);
        nextFreeByHost.put(host, Math.max(nextFree, now + extra.toNanos()));
    }
}
