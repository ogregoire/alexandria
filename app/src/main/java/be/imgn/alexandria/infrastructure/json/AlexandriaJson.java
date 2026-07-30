package be.imgn.alexandria.infrastructure.json;

import java.io.IOException;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.item.Rating;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.shared.Language;
import be.imgn.alexandria.domain.shared.Money;
import be.imgn.alexandria.domain.work.ExpressionId;
import be.imgn.alexandria.domain.work.WorkId;

/**
 * The one place that knows how the catalogue is written to disk.
 *
 * <p>Every setting here exists to make the committed files behave under git: keys in a fixed order, two-space indent,
 * LF endings, absent optionals omitted instead of written as nulls, and identifiers rendered as plain strings rather
 * than wrapper objects. A one-field change should produce a one-line diff.
 */
public final class AlexandriaJson {

    private AlexandriaJson() {}

    public static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule())
                .registerModule(valueObjects());
        Mixins.applyTo(mapper);

        mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        return mapper;
    }

    /** Pretty-printer pinned to two spaces and LF so the files diff identically on every OS. */
    public static ObjectWriter writer() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return mapper().writer(printer);
    }

    private static SimpleModule valueObjects() {
        SimpleModule module = new SimpleModule("alexandria-value-objects");
        text(module, AgentId.class, AgentId::value, AgentId::of);
        text(module, WorkId.class, WorkId::value, WorkId::of);
        text(module, ExpressionId.class, ExpressionId::qualified, ExpressionId::parse);
        text(module, ManifestationId.class, ManifestationId::value, ManifestationId::of);
        text(module, ItemId.class, ItemId::value, ItemId::of);
        text(module, Language.class, Language::code, Language::new);
        text(module, Money.class, Money::text, Money::parse);

        module.addSerializer(Rating.class, new JsonSerializer<>() {
            @Override
            public void serialize(Rating value, JsonGenerator json, SerializerProvider provider) throws IOException {
                json.writeNumber(value.stars());
            }
        });
        module.addDeserializer(Rating.class, new JsonDeserializer<>() {
            @Override
            public Rating deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return Rating.of(parser.getIntValue());
            }
        });
        return module;
    }

    private static <T> void text(
            SimpleModule module, Class<T> type, Function<T, String> write, Function<String, T> read) {
        module.addSerializer(type, new JsonSerializer<>() {
            @Override
            public void serialize(T value, JsonGenerator json, SerializerProvider provider) throws IOException {
                json.writeString(write.apply(value));
            }
        });
        module.addDeserializer(type, new JsonDeserializer<>() {
            @Override
            public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return read.apply(parser.getValueAsString());
            }
        });
    }
}
