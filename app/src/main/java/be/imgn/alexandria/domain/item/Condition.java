package be.imgn.alexandria.domain.item;

/**
 * Antiquarian condition grades. A plain enum rather than a sealed interface: the grades are a closed, payload-free
 * ordinal scale, which is exactly what an enum is.
 */
public enum Condition {
    AS_NEW("as new"),
    FINE("fine"),
    VERY_GOOD("very good"),
    GOOD("good"),
    FAIR("fair"),
    POOR("poor"),
    UNGRADED("ungraded");

    private final String label;

    Condition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
