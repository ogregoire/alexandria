package be.imgn.alexandria.infrastructure.json.codec;

import java.util.LinkedHashSet;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.agent.AgentKind;

/** Reads and writes an {@link Agent}. */
public final class AgentCodec {

    private AgentCodec() {}

    public static String write(Agent agent) {
        return JsonOut.document(out -> out.text("id", agent.id().value())
                .object("kind", nested -> kind(nested, agent.kind()))
                .text("name", agent.name())
                .text("sortName", agent.sortName())
                .texts("aliases", agent.aliases()));
    }

    public static Agent read(String json) {
        JsonIn in = JsonIn.parse(json);
        return new Agent(
                AgentId.of(in.text("id")),
                kind(in.object("kind")),
                in.text("name"),
                in.text("sortName"),
                new LinkedHashSet<>(in.texts("aliases")));
    }

    private static void kind(JsonOut out, AgentKind kind) {
        switch (kind) {
            case AgentKind.Person() -> out.text("type", "person");
            case AgentKind.Organisation() -> out.text("type", "organisation");
        }
    }

    private static AgentKind kind(JsonIn in) {
        return switch (in.type()) {
            case "person" -> AgentKind.PERSON;
            case "organisation" -> AgentKind.ORGANISATION;
            default -> SharedCodec.unknown("agent kind", in.type());
        };
    }
}
