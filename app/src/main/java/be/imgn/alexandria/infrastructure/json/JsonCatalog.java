package be.imgn.alexandria.infrastructure.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import be.imgn.alexandria.domain.agent.Agent;
import be.imgn.alexandria.domain.agent.AgentDirectory;
import be.imgn.alexandria.domain.agent.AgentId;
import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.item.Item;
import be.imgn.alexandria.domain.item.ItemId;
import be.imgn.alexandria.domain.manifestation.Manifestation;
import be.imgn.alexandria.domain.manifestation.ManifestationId;
import be.imgn.alexandria.domain.work.Work;
import be.imgn.alexandria.domain.work.WorkId;
import be.imgn.alexandria.infrastructure.json.codec.AgentCodec;
import be.imgn.alexandria.infrastructure.json.codec.ItemCodec;
import be.imgn.alexandria.infrastructure.json.codec.ManifestationCodec;
import be.imgn.alexandria.infrastructure.json.codec.WorkCodec;

/**
 * The catalogue as a directory of JSON files — one file per aggregate root, named after its id. These files are the
 * catalogue, and git history is its history.
 *
 * <p>The whole catalogue is held in memory. A personal library is thousands of records at most, so paying for indexes
 * or lazy loading would buy nothing.
 *
 * <p>Reading and writing go through the codecs in {@code json.codec}, which map each aggregate with an exhaustive
 * switch over its sealed types. That is the point of them: a new variant is a compile error here rather than a runtime
 * surprise on whichever file happens to contain it.
 */
public final class JsonCatalog implements Catalog {

    private static final String SUFFIX = ".json";

    private final Path root;

    private final Map<AgentId, Agent> agents = new LinkedHashMap<>();
    private final Map<WorkId, Work> works = new LinkedHashMap<>();
    private final Map<ManifestationId, Manifestation> manifestations = new LinkedHashMap<>();
    private final Map<ItemId, Item> items = new LinkedHashMap<>();

    public JsonCatalog(Path root) {
        this.root = root;
        reload();
    }

    public Path root() {
        return root;
    }

    /** Re-reads every file from disk, discarding whatever is in memory. */
    public void reload() {
        agents.clear();
        works.clear();
        manifestations.clear();
        items.clear();
        readAll(dir("agents"), AgentCodec::read).forEach(a -> agents.put(a.id(), a));
        readAll(dir("works"), WorkCodec::read).forEach(w -> works.put(w.id(), w));
        readAll(dir("manifestations"), ManifestationCodec::read).forEach(m -> manifestations.put(m.id(), m));
        readAll(dir("items"), ItemCodec::read).forEach(i -> items.put(i.id(), i));
    }

    @Override
    public List<Agent> agents() {
        return agents.values().stream()
                .sorted(Comparator.comparing(Agent::sortName))
                .toList();
    }

    @Override
    public List<Work> works() {
        AgentDirectory directory = directory();
        return works.values().stream()
                .sorted(Comparator.comparing(work -> work.sortKey(directory)))
                .toList();
    }

    @Override
    public List<Manifestation> manifestations() {
        return manifestations.values().stream()
                .sorted(Comparator.comparing(m -> m.id().value()))
                .toList();
    }

    @Override
    public List<Item> items() {
        return items.values().stream()
                .sorted(Comparator.comparing(i -> i.id().value()))
                .toList();
    }

    @Override
    public Optional<Agent> agent(AgentId id) {
        return Optional.ofNullable(agents.get(id));
    }

    @Override
    public Optional<Work> work(WorkId id) {
        return Optional.ofNullable(works.get(id));
    }

    @Override
    public Optional<Manifestation> manifestation(ManifestationId id) {
        return Optional.ofNullable(manifestations.get(id));
    }

    @Override
    public Optional<Item> item(ItemId id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public void save(Agent agent) {
        agents.put(agent.id(), agent);
        write(dir("agents").resolve(agent.id().value() + SUFFIX), AgentCodec.write(agent));
    }

    @Override
    public void save(Work work) {
        works.put(work.id(), work);
        write(dir("works").resolve(work.id().value() + SUFFIX), WorkCodec.write(work));
    }

    @Override
    public void save(Manifestation manifestation) {
        manifestations.put(manifestation.id(), manifestation);
        write(
                dir("manifestations").resolve(manifestation.id().value() + SUFFIX),
                ManifestationCodec.write(manifestation));
    }

    @Override
    public void save(Item item) {
        items.put(item.id(), item);
        write(dir("items").resolve(item.id().value() + SUFFIX), ItemCodec.write(item));
    }

    @Override
    public void deleteAgent(AgentId id) {
        agents.remove(id);
        delete(dir("agents").resolve(id.value() + SUFFIX));
    }

    @Override
    public void deleteWork(WorkId id) {
        works.remove(id);
        delete(dir("works").resolve(id.value() + SUFFIX));
    }

    @Override
    public void deleteManifestation(ManifestationId id) {
        manifestations.remove(id);
        delete(dir("manifestations").resolve(id.value() + SUFFIX));
    }

    @Override
    public void deleteItem(ItemId id) {
        items.remove(id);
        delete(dir("items").resolve(id.value() + SUFFIX));
    }

    private Path dir(String name) {
        Path path = root.resolve(name);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create " + path, e);
        }
        return path;
    }

    /**
     * Reads every record in a directory with the codec for that aggregate.
     *
     * <p>The codec arrives as a function rather than a {@code Class} token, so the type is known at the call site and
     * nothing has to be inferred at runtime.
     */
    private <T> List<T> readAll(Path directory, Function<String, T> codec) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .map(p -> read(p, codec))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory, e);
        }
    }

    private <T> T read(Path file, Function<String, T> codec) {
        try {
            return codec.apply(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** Writes via a temporary file so an interrupted save cannot leave a half-written record. */
    private void write(Path file, String json) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete " + file, e);
        }
    }
}
