package be.imgn.alexandria.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AgentDirectoryTest {

    private static final Agent LE_GUIN = new Agent(
            AgentId.of("ursula-k-le-guin"),
            AgentKind.PERSON,
            "Ursula K. Le Guin",
            "Le Guin, Ursula K.",
            Set.of("U. K. Le Guin", "Ursula Le Guin"));

    private static final Agent PENGUIN = new Agent(
            AgentId.of("penguin-books"),
            AgentKind.ORGANISATION,
            "Penguin Books",
            "Penguin Books",
            Set.of("Penguin", "Penguin Classics"));

    private final AgentDirectory directory = AgentDirectory.of(List.of(LE_GUIN, PENGUIN));

    @Test
    void findsAnAgentByItsPreferredName() {
        assertThat(directory.resolve("Ursula K. Le Guin")).contains(LE_GUIN);
    }

    @Test
    void findsAnAgentByAnAlias() {
        assertThat(directory.resolve("Penguin")).contains(PENGUIN);
        assertThat(directory.resolve("Penguin Classics")).contains(PENGUIN);
    }

    @Test
    void ignoresCaseAccentsAndPunctuationWhenMatching() {
        assertThat(directory.resolve("ursula k le guin")).contains(LE_GUIN);
        assertThat(directory.resolve("URSULA K. LE GUIN")).contains(LE_GUIN);
        assertThat(directory.resolve("Ursula  K.  Le  Guin")).contains(LE_GUIN);
    }

    @Test
    void doesNotInventAMatchForSomebodyUnknown() {
        assertThat(directory.resolve("China Miéville")).isEmpty();
    }

    @Test
    void offersEveryNameAndAliasAsACompletion() {
        assertThat(directory.suggestions())
                .containsExactly(
                        "Penguin",
                        "Penguin Books",
                        "Penguin Classics",
                        "U. K. Le Guin",
                        "Ursula K. Le Guin",
                        "Ursula Le Guin");
    }

    @Test
    void reportsTwoAgentsAnsweringToOneName() {
        AgentDirectory clashing = AgentDirectory.of(List.of(
                PENGUIN,
                new Agent(
                        AgentId.of("penguin-random-house"),
                        AgentKind.ORGANISATION,
                        "Penguin Random House",
                        "Penguin Random House",
                        Set.of("Penguin"))));

        assertThat(clashing.conflicts()).singleElement().satisfies(conflict -> assertThat(conflict.toString())
                .contains("penguin-books")
                .contains("penguin-random-house"));
    }

    @Test
    void derivesAFreeIdWhenTwoDifferentPeopleSlugAlike() {
        assertThat(directory.freeId("Penguin Books").value()).isEqualTo("penguin-books-2");
        assertThat(directory.freeId("China Miéville").value()).isEqualTo("china-mieville");
    }

    @Test
    void namesADanglingReferenceInsteadOfRenderingItBlank() {
        assertThat(directory.nameOf(AgentId.of("nobody"))).isEqualTo("nobody (unknown)");
    }

    @Test
    void dropsAnAliasThatMerelyRepeatsThePreferredName() {
        Agent agent = new Agent(
                AgentId.of("kafka"), AgentKind.PERSON, "Franz Kafka", "Kafka, Franz", Set.of("franz  kafka", "Kafka"));

        assertThat(agent.aliases()).containsExactly("Kafka");
    }
}
