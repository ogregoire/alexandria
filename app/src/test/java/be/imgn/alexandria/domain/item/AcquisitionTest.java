package be.imgn.alexandria.domain.item;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Provenance is frequently half-remembered. That a book was a gift is worth recording on its
 * own, so nothing here demands a date or a name — except the lender of a borrowed copy.
 */
class AcquisitionTest {

    @Test
    void recordsAGiftWithNeitherDateNorGiver() {
        Acquisition gift = Acquisition.Gift.unattributed();

        assertThat(gift.owned()).isTrue();
        assertThat(gift.on()).isEmpty();
        assertThat(((Acquisition.Gift) gift).from()).isEmpty();
    }

    @Test
    void recordsAGiftFromSomeoneOnNoParticularDate() {
        Acquisition.Gift gift = Acquisition.Gift.from("Marie", null);

        assertThat(gift.from()).contains("Marie");
        assertThat(gift.on()).isEmpty();
    }

    @Test
    void recordsAGiftOnADateFromNobodyRemembered() {
        Acquisition.Gift gift = new Acquisition.Gift(
                Optional.of(LocalDate.of(2021, 12, 25)), Optional.empty());

        assertThat(gift.on()).contains(LocalDate.of(2021, 12, 25));
        assertThat(gift.from()).isEmpty();
    }

    @Test
    void treatsABlankGiverAsNoGiver() {
        assertThat(new Acquisition.Gift(Optional.empty(), Optional.of("   ")).from()).isEmpty();
    }

    @Test
    void allowsAPurchaseAndAnInheritanceToBeAsVague() {
        assertThat(Acquisition.Purchased.on(null, null, null).on()).isEmpty();
        assertThat(Acquisition.Inherited.from(null, null).from()).isEmpty();
    }

    @Test
    void stillDemandsTheLenderOfABorrowedCopy() {
        assertThatThrownBy(() -> new Acquisition.Borrowed(
                " ", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    void borrowsWithoutKnowingWhen() {
        Acquisition.Borrowed borrowed = new Acquisition.Borrowed(
                "Hugo", Optional.empty(), Optional.empty());

        assertThat(borrowed.owned()).isFalse();
        assertThat(borrowed.on()).isEmpty();
    }

    @Test
    void stillRejectsADueDateBeforeTheLoanBegan() {
        assertThatThrownBy(() -> new Acquisition.Borrowed("Hugo",
                Optional.of(LocalDate.of(2026, 5, 1)),
                Optional.of(LocalDate.of(2026, 4, 1))))
                .hasMessageContaining("must not precede");
    }

    @Test
    void lendsOutWithoutRecordingWhen() {
        Location.LentTo lent = Location.LentTo.to("Thomas", null);

        assertThat(lent.display()).isEqualTo("lent to Thomas");
        assertThat(lent.athand()).isFalse();
    }

    @Test
    void namesTheBorrowerWithTheDateWhenThereIsOne() {
        assertThat(Location.LentTo.to("Thomas", LocalDate.of(2025, 9, 3)).display())
                .isEqualTo("lent to Thomas since 2025-09-03");
    }
}
