package be.imgn.alexandria.infrastructure.json.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every record in the sample library, read and written back, required to be byte-identical to the file on disk.
 *
 * <p>Those files were written by Jackson before the codecs replaced it, so this is what keeps the guarantee that the
 * change was invisible on disk — no migration, no reformatting commit. It also means any future change to the writing
 * format has to be deliberate: it will fail here first, against thirty-one real records covering every variant the
 * model has.
 */
class CatalogFilesTest {

    private static final Path LIBRARY = Path.of("..", "examples", "library");

    private static List<Path> filesIn(String folder) throws Exception {
        try (Stream<Path> files = Files.list(LIBRARY.resolve(folder))) {
            return files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static <T> void roundTrips(String folder, Function<String, T> read, Function<T, String> write)
            throws Exception {
        List<Path> files = filesIn(folder);
        assertThat(files).as("%s should not be empty", folder).isNotEmpty();
        for (Path file : files) {
            String onDisk = Files.readString(file);
            assertThat(write.apply(read.apply(onDisk)))
                    .as("%s must round-trip unchanged", file.getFileName())
                    .isEqualTo(onDisk);
        }
    }

    @Test
    void agentsRoundTripUnchanged() throws Exception {
        roundTrips("agents", AgentCodec::read, AgentCodec::write);
    }

    @Test
    void worksRoundTripUnchanged() throws Exception {
        roundTrips("works", WorkCodec::read, WorkCodec::write);
    }

    @Test
    void manifestationsRoundTripUnchanged() throws Exception {
        roundTrips("manifestations", ManifestationCodec::read, ManifestationCodec::write);
    }

    @Test
    void itemsRoundTripUnchanged() throws Exception {
        roundTrips("items", ItemCodec::read, ItemCodec::write);
    }

    /**
     * The corpus is only a guarantee if it is broad. These counts are the variants the sample library was built to
     * cover: an omnibus, a narration, an audiobook measured in playing time, a borrowed copy, a pseudonym, and a gift
     * with nothing recorded about it.
     */
    @Test
    void theCorpusCoversTheVariantsThatMatter() throws Exception {
        assertThat(filesIn("agents")).hasSizeGreaterThanOrEqualTo(14);
        assertThat(filesIn("works")).hasSizeGreaterThanOrEqualTo(5);
        assertThat(filesIn("manifestations")).hasSizeGreaterThanOrEqualTo(6);
        assertThat(filesIn("items")).hasSizeGreaterThanOrEqualTo(6);

        String everything = Stream.of("agents", "works", "manifestations", "items")
                .flatMap(folder -> {
                    try {
                        return filesIn(folder).stream();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .reduce("", String::concat);

        assertThat(everything)
                .contains("\"type\" : \"playtime\"")
                .contains("\"type\" : \"narration\"")
                .contains("\"type\" : \"borrowed\"")
                .contains("\"type\" : \"abandoned\"")
                .contains("\"type\" : \"volumes\"")
                .contains("\"type\" : \"asin\"")
                .contains("\"type\" : \"mass-market\"")
                .contains("\"type\" : \"circa\"")
                .contains("\"type\" : \"between\"")
                .contains("\"type\" : \"device\"")
                .contains("\"type\" : \"lent-to\"");
    }
}
