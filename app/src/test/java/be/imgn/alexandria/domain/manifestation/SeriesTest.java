package be.imgn.alexandria.domain.manifestation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeriesTest {

    @Test
    void readsAnArabicNumber() {
        assertThat(Series.of("La Roue du temps", "10").position()).isEqualTo(10);
    }

    /** A spine that says "Tome IV" means the fourth, and has to sort as the fourth. */
    @Test
    void readsARomanNumeral() {
        assertThat(Series.of("Le Seigneur des anneaux", "IV").position()).isEqualTo(4);
        assertThat(Series.of("s", "I").position()).isEqualTo(1);
        assertThat(Series.of("s", "IX").position()).isEqualTo(9);
        assertThat(Series.of("s", "XIV").position()).isEqualTo(14);
        assertThat(Series.of("s", "XL").position()).isEqualTo(40);
        assertThat(Series.of("s", "MCMXCIV").position()).isEqualTo(1994);
    }

    @Test
    void readsALowercaseRomanNumeralToo() {
        assertThat(Series.of("s", "iii").position()).isEqualTo(3);
    }

    /**
     * The near-misses all sum to something plausible, and none is how the number is written. Writing the total back out
     * and comparing is what rejects them.
     */
    @Test
    void refusesAMalformedRomanNumeral() {
        assertThat(Series.of("s", "IIII").position()).isEqualTo(Series.UNPLACED);
        assertThat(Series.of("s", "IC").position()).isEqualTo(Series.UNPLACED);
        assertThat(Series.of("s", "VV").position()).isEqualTo(Series.UNPLACED);
        assertThat(Series.of("s", "XXXX").position()).isEqualTo(Series.UNPLACED);
    }

    @Test
    void leavesUnplacedWhatItCannotRead() {
        assertThat(Series.of("s", "2 bis").position()).isEqualTo(Series.UNPLACED);
        assertThat(Series.of("s", "hors-série").position()).isEqualTo(Series.UNPLACED);
        assertThat(Series.of("s").position()).as("unnumbered").isEqualTo(Series.UNPLACED);
        assertThat(Series.STANDALONE.position()).as("no series at all").isEqualTo(Series.UNPLACED);
    }

    /** Whatever it makes of the number, the page still shows what was printed. */
    @Test
    void showsTheNumberAsItWasPrinted() {
        assertThat(Series.of("Le Seigneur des anneaux", "IV").display()).isEqualTo("Le Seigneur des anneaux IV");
        assertThat(Series.of("s", "2 bis").display()).isEqualTo("s 2 bis");
    }
}
