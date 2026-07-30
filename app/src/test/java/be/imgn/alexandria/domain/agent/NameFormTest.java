package be.imgn.alexandria.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameFormTest {

    // The case that started this: the BnF hands out inverted names.

    @Test
    void readsAnInvertedNameThroughItsComma() {
        NameForm name = NameForm.ofPerson("Tolkien, John Ronald Reuel");

        assertThat(name.filingWord()).as("not Reuel").isEqualTo("Tolkien");
        assertThat(name.display()).isEqualTo("John Ronald Reuel Tolkien");
        assertThat(name.sortName()).isEqualTo("Tolkien, John Ronald Reuel");
    }

    @Test
    void readsATitlePageNameInTheOtherOrder() {
        NameForm name = NameForm.ofPerson("J. R. R. Tolkien");

        assertThat(name.filingWord()).isEqualTo("Tolkien");
        assertThat(name.display()).isEqualTo("J. R. R. Tolkien");
        assertThat(name.sortName()).isEqualTo("Tolkien, J. R. R.");
    }

    @Test
    void bothOrdersAgreeOnTheSurname() {
        assertThat(NameForm.ofPerson("Lauzon, Daniel").filingWord())
                .isEqualTo(NameForm.ofPerson("Daniel Lauzon").filingWord())
                .isEqualTo("Lauzon");
    }

    // Particles: the case of the particle carries the filing convention.

    @Test
    void keepsACapitalisedParticleInTheSurname() {
        NameForm name = NameForm.ofPerson("Ursula K. Le Guin");

        assertThat(name.filingWord()).isEqualTo("Le Guin");
        assertThat(name.sortName()).isEqualTo("Le Guin, Ursula K.");
    }

    @Test
    void movesALowercaseParticleAfterTheSurname() {
        NameForm name = NameForm.ofPerson("Miguel de Cervantes");

        assertThat(name.filingWord()).isEqualTo("Cervantes");
        assertThat(name.sortName()).isEqualTo("Cervantes, Miguel de");
    }

    @Test
    void followsTheSameRuleForFlemishAndDutchUsage() {
        assertThat(NameForm.ofPerson("Jean-Claude Van Damme").filingWord()).isEqualTo("Van Damme");
        assertThat(NameForm.ofPerson("Vincent van Gogh").filingWord()).isEqualTo("Gogh");
    }

    @Test
    void handlesAMononym() {
        NameForm name = NameForm.ofPerson("Tacitus");

        assertThat(name.filingWord()).isEqualTo("Tacitus");
        assertThat(name.sortName()).isEqualTo("Tacitus");
        assertThat(name.display()).isEqualTo("Tacitus");
    }

    @Test
    void leavesAJointCreditOnItsSharedSurname() {
        assertThat(NameForm.ofPerson("Willa and Edwin Muir").filingWord()).isEqualTo("Muir");
    }

    @Test
    void toleratesRaggedWhitespace() {
        assertThat(NameForm.ofPerson("  Tolkien,   John  Ronald Reuel ").display())
                .isEqualTo("John Ronald Reuel Tolkien");
    }

    // Organisations: the name is kept, the identifier drops the kind-of-thing words.

    @Test
    void dropsAPublishersRoleWordFromTheIdentifier() {
        NameForm name = NameForm.ofOrganisation("Christian Bourgois éditeur");

        assertThat(name.filingWord()).as("not éditeur").isEqualTo("Bourgois");
        assertThat(name.display()).as("the name itself is untouched").isEqualTo("Christian Bourgois éditeur");
        assertThat(name.sortName()).isEqualTo("Christian Bourgois éditeur");
    }

    @Test
    void dropsEnglishTypeWordsToo() {
        assertThat(NameForm.ofOrganisation("Penguin Books").filingWord()).isEqualTo("Penguin");
        assertThat(NameForm.ofOrganisation("Harvard University Press").filingWord())
                .isEqualTo("University");
        assertThat(NameForm.ofOrganisation("Allen & Unwin Ltd.").filingWord()).isEqualTo("Unwin");
    }

    @Test
    void doesNotStripAwayTheWholeName() {
        assertThat(NameForm.ofOrganisation("Editions").filingWord()).isEqualTo("Editions");
        assertThat(NameForm.ofOrganisation("Ecco").filingWord()).isEqualTo("Ecco");
    }

    @Test
    void removesAnApostropheThatWouldSlugIntoAStraySegment() {
        assertThat(NameForm.ofOrganisation("Everyman's Library").filingWord()).isEqualTo("Everymans");
    }

    @Test
    void picksTheRuleFromTheKind() {
        assertThat(NameForm.of("Penguin Books", AgentKind.ORGANISATION).filingWord())
                .isEqualTo("Penguin");
        assertThat(NameForm.of("Penguin Books", AgentKind.PERSON).filingWord()).isEqualTo("Books");
    }
}
