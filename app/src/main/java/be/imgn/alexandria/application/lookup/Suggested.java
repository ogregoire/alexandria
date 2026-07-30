package be.imgn.alexandria.application.lookup;

/**
 * A single fact a lookup service either reported or stayed silent about.
 *
 * <p>One type for every field of a {@link BookDraft} rather than a two-shape sum per field, because here the shape
 * really is the same in every case — unlike the model, where an absent date and an absent price mean different things
 * and deserve different names. A draft is one uniform thing: what a provider said, or what it did not say.
 *
 * <p>It exists because {@code Optional} is documented for use as a return type, and a record component is also a
 * constructor parameter. This is that idea with a name that says what it means for a draft, and with no {@code map} or
 * {@code flatMap} to invite chaining — a caller that wants the value matches on {@link Given}, which is the point of
 * having two shapes.
 *
 * @param <T> what the provider reports for this field
 */
public sealed interface Suggested<T> {

    @SuppressWarnings("unchecked")
    static <T> Suggested<T> silent() {
        return (Suggested<T>) Silent.INSTANCE;
    }

    /** A reported value; {@code null} is silence. */
    static <T> Suggested<T> of(T value) {
        return value == null ? silent() : new Given<>(value);
    }

    /** The same for text, where a provider padding a field with spaces has still said nothing. */
    static Suggested<String> ofText(String value) {
        return value == null || value.isBlank() ? silent() : new Given<>(value.trim());
    }

    /** True when the provider reported something — for the caller that only needs to know whether to fill a field. */
    boolean given();

    T orElse(T fallback);

    record Given<T>(T value) implements Suggested<T> {

        public Given {
            if (value == null) {
                throw new IllegalArgumentException("a reported value cannot be null");
            }
        }

        @Override
        public boolean given() {
            return true;
        }

        @Override
        public T orElse(T fallback) {
            return value;
        }
    }

    record Silent<T>() implements Suggested<T> {

        private static final Silent<?> INSTANCE = new Silent<>();

        @Override
        public boolean given() {
            return false;
        }

        @Override
        public T orElse(T fallback) {
            return fallback;
        }
    }
}
