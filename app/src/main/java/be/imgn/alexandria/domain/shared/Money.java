package be.imgn.alexandria.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** What a copy cost. */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    /** Reads the {@code "28.50 EUR"} form used in the files and in the editor. */
    public static Money parse(String raw) {
        String[] parts = Guard.notBlank(raw, "money").trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected '<amount> <currency>' but got '" + raw + "'");
        }
        return of(parts[0], parts[1]);
    }

    /** The round-trip partner of {@link #parse}; {@link #display} is for humans only. */
    public String text() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    public String display() {
        return currency.getSymbol() + amount.toPlainString();
    }
}
