package be.imgn.alexandria.domain.agent;

/**
 * The LRM split between a person and a collective agent. A publishing house is a
 * collective agent, and so is a committee, an institute or a band.
 */
public sealed interface AgentKind {

    String label();

    record Person() implements AgentKind {
        @Override
        public String label() {
            return "person";
        }
    }

    record Organisation() implements AgentKind {
        @Override
        public String label() {
            return "organisation";
        }
    }

    AgentKind PERSON = new Person();
    AgentKind ORGANISATION = new Organisation();
}
