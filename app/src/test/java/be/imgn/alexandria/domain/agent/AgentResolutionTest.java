package be.imgn.alexandria.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AgentResolutionTest {

    private static final Agent CERVANTES = new Agent(
            AgentId.of("miguel-de-cervantes"),
            AgentKind.PERSON,
            "Miguel de Cervantes",
            "Cervantes, Miguel de",
            Set.of("Cervantes"));

    private AgentResolution resolution() {
        return new AgentResolution(AgentDirectory.of(List.of(CERVANTES)));
    }

    @Test
    void reusesSomebodyAlreadyRegistered() {
        AgentResolution resolution = resolution();

        assertThat(resolution.resolve("Miguel de Cervantes", AgentKind.PERSON)).isEqualTo(CERVANTES.id());
        assertThat(resolution.created()).isEmpty();
    }

    @Test
    void reusesSomebodyRegisteredUnderAnAlias() {
        assertThat(resolution().resolve("Cervantes", AgentKind.PERSON)).isEqualTo(CERVANTES.id());
    }

    @Test
    void mintsARecordForSomebodyNew() {
        AgentResolution resolution = resolution();

        AgentId id = resolution.resolve("Edith Grossman", AgentKind.PERSON);

        assertThat(id.value()).isEqualTo("edith-grossman");
        assertThat(resolution.created()).singleElement().satisfies(agent -> {
            assertThat(agent.name()).isEqualTo("Edith Grossman");
            assertThat(agent.sortName()).isEqualTo("Grossman, Edith");
            assertThat(agent.kind()).isEqualTo(AgentKind.PERSON);
        });
    }

    @Test
    void mintsOrganisationsUnderTheirOwnNameWithoutInvertingIt() {
        AgentResolution resolution = resolution();

        resolution.resolve("Penguin Classics", AgentKind.ORGANISATION);

        assertThat(resolution.created())
                .singleElement()
                .satisfies(agent -> assertThat(agent.sortName()).isEqualTo("Penguin Classics"));
    }

    @Test
    void namesTheSameNewcomerOnlyOncePerForm() {
        AgentResolution resolution = resolution();

        AgentId first = resolution.resolve("Willa Muir", AgentKind.PERSON);
        AgentId second = resolution.resolve("willa  muir", AgentKind.PERSON);

        assertThat(second).isEqualTo(first);
        assertThat(resolution.created()).hasSize(1);
    }

    @Test
    void refusesABlankName() {
        assertThatThrownBy(() -> resolution().resolve("  ", AgentKind.PERSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a name");
    }
}
