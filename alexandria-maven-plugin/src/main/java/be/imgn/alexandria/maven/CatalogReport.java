package be.imgn.alexandria.maven;

import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;
import be.imgn.alexandria.site.SiteGenerator;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Publishes the catalogue during {@code mvn site}.
 *
 * <p>The generated pages are written straight to disk rather than through Doxia: the
 * catalogue needs its own layout and a JSON search index, neither of which a Doxia sink
 * can express. What goes through the sink is the report page Maven puts in the site
 * navigation, which links into the generated catalogue and reports on its health.
 */
@Mojo(name = "catalog", requiresProject = true, threadSafe = true)
public class CatalogReport extends AbstractMavenReport {

    /** Directory holding the JSON catalogue: the {@code works}, {@code manifestations} and {@code items} folders. */
    @Parameter(defaultValue = "${project.basedir}/data", property = "alexandria.data")
    private File dataDirectory;

    /** Subdirectory of the site the catalogue is generated into. */
    @Parameter(defaultValue = "library", property = "alexandria.subdirectory")
    private String subdirectory;

    /** Fail the build when a manifestation or item points at something that is not there. */
    @Parameter(defaultValue = "true", property = "alexandria.failOnBrokenReferences")
    private boolean failOnBrokenReferences;

    @Override
    public String getOutputName() {
        return "catalog";
    }

    @Override
    public String getName(Locale locale) {
        return "Library catalogue";
    }

    @Override
    public String getDescription(Locale locale) {
        return "The works, expressions, manifestations and items held in this library.";
    }

    @Override
    public boolean canGenerateReport() {
        return dataDirectory != null && Files.isDirectory(dataDirectory.toPath().resolve("works"));
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        Path data = dataDirectory.toPath();
        Path output = getReportOutputDirectory().toPath().resolve(subdirectory);

        Catalog catalog = new JsonCatalog(data);
        List<ReferentialIntegrity.Violation> violations = ReferentialIntegrity.check(catalog);
        if (!violations.isEmpty() && failOnBrokenReferences) {
            throw new MavenReportException("the catalogue has broken references:\n  "
                    + join(violations));
        }
        violations.forEach(violation -> getLog().warn("catalogue: " + violation));

        List<Path> written;
        try {
            written = new SiteGenerator(catalog).generateInto(output);
        } catch (RuntimeException e) {
            throw new MavenReportException("cannot generate the catalogue: " + e.getMessage(), e);
        }
        getLog().info("Alexandria wrote " + written.size() + " files into " + output);

        writeLandingPage(catalog, violations);
    }

    private void writeLandingPage(Catalog catalog, List<ReferentialIntegrity.Violation> violations) {
        Sink sink = getSink();
        sink.head();
        sink.title();
        sink.text("Library catalogue");
        sink.title_();
        sink.head_();

        sink.body();
        sink.section1();
        sink.sectionTitle1();
        sink.text("Library catalogue");
        sink.sectionTitle1_();

        sink.paragraph();
        sink.link(subdirectory + "/index.html");
        sink.text("Browse and search the catalogue");
        sink.link_();
        sink.paragraph_();

        sink.paragraph();
        sink.text("%d works, realised through %d expressions, embodied in %d manifestations, held as %d items."
                .formatted(
                        catalog.works().size(),
                        catalog.works().stream().mapToInt(w -> w.expressions().size()).sum(),
                        catalog.manifestations().size(),
                        catalog.items().size()));
        sink.paragraph_();

        if (!violations.isEmpty()) {
            sink.paragraph();
            sink.text("Broken references: " + join(violations));
            sink.paragraph_();
        }

        sink.section1_();
        sink.body_();
        sink.close();
    }

    private static String join(List<ReferentialIntegrity.Violation> violations) {
        return violations.stream().map(ReferentialIntegrity.Violation::toString)
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
