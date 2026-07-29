package be.imgn.alexandria.infrastructure.lookup;

import java.util.Optional;

/**
 * How Alexandria introduces itself, and whether that introduction earns the faster tier.
 *
 * <p>Open Library allows one request per second to callers it cannot identify and three to
 * those sending a User-Agent naming the application and a contact address. Claiming the
 * faster tier without actually supplying a contact would be dishonest and would breach the
 * limit, so the interval is derived from whether a contact is really present rather than
 * assumed.
 */
record UserAgent(Optional<String> contact) {

    private static final String PRODUCT = "Alexandria/1.0";

    static UserAgent anonymous() {
        return new UserAgent(Optional.empty());
    }

    /**
     * @param contact an email address or URL the service can use to reach the operator;
     *                blank is treated as no contact at all
     */
    static UserAgent identifiedBy(String contact) {
        return new UserAgent(Optional.ofNullable(contact)
                .map(UserAgent::sanitise)
                .filter(value -> !value.isBlank()));
    }

    boolean identified() {
        return contact.isPresent();
    }

    String header() {
        return contact
                .map(value -> PRODUCT + " (personal book catalogue; " + value + ")")
                .orElse(PRODUCT + " (personal book catalogue; no contact given)");
    }

    static String unidentified() {
        return anonymous().header();
    }

    /** A header value may not carry control characters, whatever the user typed. */
    private static String sanitise(String value) {
        return value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[()]", "").trim();
    }
}
