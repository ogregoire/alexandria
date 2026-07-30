package be.imgn.alexandria.infrastructure;

import java.util.Locale;

/**
 * One naming rule for sum-type variants, shared by the JSON files and the editor forms: the record's simple name in
 * kebab-case. {@code MassMarket} is {@code mass-market} everywhere, so a value seen in a diff and a value seen in a
 * form are the same word.
 */
public final class VariantNames {

    private VariantNames() {}

    public static String of(Object variant) {
        return of(variant.getClass());
    }

    public static String of(Class<?> variant) {
        return variant.getSimpleName().replaceAll("(?<=.)(?=\\p{Upper})", "-").toLowerCase(Locale.ROOT);
    }
}
