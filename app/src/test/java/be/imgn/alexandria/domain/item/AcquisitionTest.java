package be.imgn.alexandria.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import be.imgn.alexandria.domain.shared.EventDate;
import be.imgn.alexandria.domain.shared.Note;

/**
 * Provenance is frequently half-remembered. That a book was a gift is worth recording on its own, so nothing here
 * demands a date or a name — except the lender of a borrowed copy.
 */
class AcquisitionTest {

    @Test
    void recordsAGiftWithNeitherDateNorGiver() {
        Acquisition gift = Acquisition.Gift.unattributed();

        assertThat(gift.owned()).isTrue();
        assertThat(gift.on()).isEqualTo(EventDate.UNRECORDED);
        assertThat(((Acquisition.Gift) gift).from()).isEqualTo(Note.NOTHING);
    }

    @Test
    void recordsAGiftFromSomeoneOnNoParticularDate() {
        Acquisition.Gift gift = Acquisition.Gift.from("Marie", null);

        assertThat(gift.from().text()).isEqualTo("Marie");
        assertThat(gift.on()).isEqualTo(EventDate.UNRECORDED);
    }

    @Test
    void recordsAGiftOnADateFromNobodyRemembered() {
        Acquisition.Gift gift = new Acquisition.Gift(EventDate.on(LocalDate.of(2021, 12, 25)), Note.NOTHING);

        assertThat(gift.on()).isEqualTo(EventDate.on(LocalDate.of(2021, 12, 25)));
        assertThat(gift.from()).isEqualTo(Note.NOTHING);
    }

    @Test
    void treatsABlankGiverAsNoGiver() {
        assertThat(new Acquisition.Gift(EventDate.UNRECORDED, Note.of("   ")).from())
                .isEqualTo(Note.NOTHING);
    }

    @Test
    void allowsAPurchaseAndAnInheritanceToBeAsVague() {
        assertThat(Acquisition.Purchased.on(null, null, null).on()).isEqualTo(EventDate.UNRECORDED);
        assertThat(Acquisition.Inherited.from(null, null).from()).isEqualTo(Note.NOTHING);
    }

    @Test
    void stillDemandsTheLenderOfABorrowedCopy() {
        assertThatThrownBy(() -> new Acquisition.Borrowed(" ", EventDate.UNRECORDED, EventDate.UNRECORDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    void borrowsWithoutKnowingWhen() {
        Acquisition.Borrowed borrowed = new Acquisition.Borrowed("Hugo", EventDate.UNRECORDED, EventDate.UNRECORDED);

        assertThat(borrowed.owned()).isFalse();
        assertThat(borrowed.on()).isEqualTo(EventDate.UNRECORDED);
    }

    @Test
    void stillRejectsADueDateBeforeTheLoanBegan() {
        assertThatThrownBy(() -> new Acquisition.Borrowed(
                        "Hugo", EventDate.on(LocalDate.of(2026, 5, 1)), EventDate.on(LocalDate.of(2026, 4, 1))))
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
