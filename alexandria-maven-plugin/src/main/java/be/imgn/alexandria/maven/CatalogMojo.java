package be.imgn.alexandria.maven;

import be.imgn.alexandria.domain.catalog.Catalog;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;
import be.imgn.alexandria.site.SiteGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the catalogue into the site directory during {@code mvn site}.
 *
 * <p>A plain mojo rather than a {@code MavenReport}. It was a report once, and being one cost
 * more than it gave: Maven surrounds reports with a report index, a skin and a set of
 * project-information pages, none of which belong in front of a library. The catalogue needs
 * its own layout and a JSON search index, neither of which a Doxia sink can express, so it
 * was writing its pages straight to disk anyway and using the sink only for a landing page
 * nobody asked for.
 *
 * <p>What is published is now exactly the catalogue: an index, a page per work, a page per
 * agent, a stylesheet, a script and the search index.
 */
@Mojo(name = "catalog", defaultPhase = LifecyclePhase.SITE, requiresProject = true, threadSafe = true)
public class CatalogMojo extends AbstractMojo {

    /** Directory holding the JSON catalogue: the {@code works}, {@code agents} and other folders. */
    @Parameter(defaultValue = "${project.basedir}/data", property = "alexandria.data")
    private File dataDirectory;

    /** Where to write the site. Defaults to the directory Maven publishes. */
    @Parameter(defaultValue = "${project.reporting.outputDirectory}", property = "alexandria.output")
    private File outputDirectory;

    /** Fail the build when a record points at something that is not there. */
    @Parameter(defaultValue = "true", property = "alexandria.failOnBrokenReferences")
    private boolean failOnBrokenReferences;

    /** Set on the modules that hold no catalogue of their own. */
    @Parameter(defaultValue = "false", property = "alexandria.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        Path data = dataDirectory == null ? null : dataDirectory.toPath();
        if (skip || data == null || !Files.isDirectory(data.resolve("works"))) {
            getLog().debug("No catalogue at " + data + "; nothing to publish.");
            return;
        }

        Catalog catalog = new JsonCatalog(data);
        List<ReferentialIntegrity.Violation> violations = ReferentialIntegrity.check(catalog);
        if (!violations.isEmpty() && failOnBrokenReferences) {
            throw new MojoExecutionException("the catalogue has broken references:\n  " + join(violations));
        }
        violations.forEach(violation -> getLog().warn("catalogue: " + violation));

        Path output = outputDirectory.toPath();
        List<Path> written;
        try {
            written = new SiteGenerator(catalog).generateInto(output);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("cannot generate the catalogue: " + e.getMessage(), e);
        }

        getLog().info("Alexandria published %d works, %d expressions, %d manifestations, %d items as %d files in %s"
                .formatted(
                        catalog.works().size(),
                        catalog.works().stream().mapToInt(work -> work.expressions().size()).sum(),
                        catalog.manifestations().size(),
                        catalog.items().size(),
                        written.size(),
                        output));
    }

    private static String join(List<ReferentialIntegrity.Violation> violations) {
        return violations.stream()
                .map(ReferentialIntegrity.Violation::toString)
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
