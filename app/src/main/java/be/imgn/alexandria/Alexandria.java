package be.imgn.alexandria;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import be.imgn.alexandria.application.CatalogService;
import be.imgn.alexandria.application.lookup.BookLookup;
import be.imgn.alexandria.domain.catalog.ReferentialIntegrity;
import be.imgn.alexandria.infrastructure.json.JsonCatalog;
import be.imgn.alexandria.infrastructure.lookup.ChainedLookup;
import be.imgn.alexandria.infrastructure.web.Editor;
import be.imgn.alexandria.site.SiteGenerator;

/**
 * Entry point for the local editor.
 *
 * <pre>
 *   alexandria [--data DIR] [--port N] [--no-browser] [--offline]
 *              [--contact you@example.org]
 *   alexandria --site DIR [--data DIR]
 * </pre>
 */
public final class Alexandria {

    private Alexandria() {}

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        JsonCatalog catalog = new JsonCatalog(arguments.data());

        if (arguments.site() != null) {
            var written = new SiteGenerator(catalog).generateInto(arguments.site());
            System.out.println("Wrote " + written.size() + " files into " + arguments.site());
            return;
        }

        for (ReferentialIntegrity.Violation violation : ReferentialIntegrity.check(catalog)) {
            System.out.println("warning: " + violation);
        }

        CatalogService service = new CatalogService(catalog);
        BookLookup lookup = arguments.offline() ? BookLookup.offline() : ChainedLookup.standard(arguments.contact());
        Editor editor = new Editor(service, lookup);
        int port = editor.start(arguments.port());
        String url = "http://127.0.0.1:" + port + "/";

        Runtime.getRuntime().addShutdownHook(new Thread(editor::stop));
        System.out.println("Alexandria is editing " + arguments.data().toAbsolutePath());
        System.out.println("  " + url);
        System.out.println("Press Ctrl-C to stop.");
        if (arguments.openBrowser()) {
            openBrowser(url);
        }
        Thread.currentThread().join();
    }

    private record Arguments(
            Path data, int port, boolean openBrowser, Path site, boolean offline, Optional<String> contact) {

        static Arguments parse(String[] args) {
            Path data = Path.of("data");
            int port = 4242;
            boolean openBrowser = true;
            Path site = null;
            boolean offline = false;
            String contact = System.getenv("ALEXANDRIA_CONTACT");

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--data" -> data = Path.of(next(args, ++i, "--data"));
                    case "--port" -> port = Integer.parseInt(next(args, ++i, "--port"));
                    case "--site" -> site = Path.of(next(args, ++i, "--site"));
                    case "--no-browser" -> openBrowser = false;
                    case "--offline" -> offline = true;
                    case "--contact" -> contact = next(args, ++i, "--contact");
                    default -> throw new IllegalArgumentException("unknown option " + args[i]);
                }
            }
            return new Arguments(
                    data,
                    port,
                    openBrowser,
                    site,
                    offline,
                    Optional.ofNullable(contact).filter(value -> !value.isBlank()));
        }

        private static String next(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return args[index];
        }
    }

    private static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command = os.contains("mac")
                ? new String[] {"open", url}
                : os.contains("win")
                        ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                        : new String[] {"xdg-open", url};
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            System.out.println("Open " + url + " yourself — " + e.getMessage());
        }
    }
}
