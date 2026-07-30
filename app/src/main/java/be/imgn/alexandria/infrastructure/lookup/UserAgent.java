package be.imgn.alexandria.infrastructure.lookup;

/**
 * How Alexandria introduces itself, and whether that introduction earns the faster tier.
 *
 * <p>Open Library allows one request per second to callers it cannot identify and three to those sending a User-Agent
 * naming the application and a contact address. Claiming the faster tier without actually supplying a contact would be
 * dishonest and would breach the limit, so the two cases are two shapes and the interval is read off which one is in
 * hand rather than off a flag beside an Optional.
 */
sealed interface UserAgent {

    String PRODUCT = "Alexandria/1.0";

    static UserAgent anonymous() {
        return new Anonymous();
    }

    /**
     * @param contact an email address or URL the service can use to reach the operator; blank, or nothing at all, is
     *     treated as no contact and yields an {@link Anonymous} caller
     */
    static UserAgent identifiedBy(String contact) {
        if (contact == null) {
            return anonymous();
        }
        String cleaned = sanitise(contact);
        return cleaned.isBlank() ? anonymous() : new Identified(cleaned);
    }

    /** True only for a caller that really did supply a contact — the faster tier depends on it. */
    boolean identified();

    String header();

    static String unidentified() {
        return anonymous().header();
    }

    /** A header value may not carry control characters, whatever the user typed. */
    private static String sanitise(String value) {
        return value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[()]", "").trim();
    }

    record Identified(String contact) implements UserAgent {

        @Override
        public boolean identified() {
            return true;
        }

        @Override
        public String header() {
            return PRODUCT + " (personal book catalogue; " + contact + ")";
        }
    }

    record Anonymous() implements UserAgent {

        @Override
        public boolean identified() {
            return false;
        }

        @Override
        public String header() {
            return PRODUCT + " (personal book catalogue; no contact given)";
        }
    }
}
