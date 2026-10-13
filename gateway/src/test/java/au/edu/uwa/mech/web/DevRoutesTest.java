package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dev-only HTTP extras, and that production answers 404 to every one of them. */
class DevRoutesTest {
    static final String POLICY = "default-src 'self'; object-src 'none'";
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @TempDir Path tmp;

    Path ui(boolean withCsp) throws Exception {
        Path ui = Files.createDirectories(tmp.resolve(withCsp ? "ui" : "ui-plain"));
        Files.writeString(ui.resolve("index.html"), "<h1>hi</h1>");
        Files.writeString(ui.resolve("app.js"), "console.log(1)");
        if (withCsp) Files.writeString(ui.resolve("csp.json"), "{\"csp\": \"" + POLICY + "\"}\n");
        return ui;
    }

    Path archive() throws Exception {
        Path a = tmp.resolve("archive");
        Files.createDirectories(a.resolve("public/index"));
        Files.writeString(a.resolve("public/index/days.json"), "{\"days\":[]}");
        Files.createDirectories(a.resolve("private/audit"));
        Files.writeString(a.resolve("private/audit/2026-10-13.jsonl"), "{\"sub\":\"secret\"}\n");
        return a;
    }

    GatewayServer server(String... args) throws Exception {
        GatewayConfig c = GatewayConfig.parse(args, Map.of());
        GatewayServer s = new GatewayServer(c, new StubSessionHandler(c), System::currentTimeMillis);
        s.start();
        return s;
    }

    static HttpResponse<String> get(GatewayServer s, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + s.port() + path)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(GatewayServer s, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + s.port() + path))
            .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json").build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void staticFilesCarryTheCspFromCspJsonAndNosniffAndIndexIsNotCached() throws Exception {
        GatewayServer s = server("--port", "0", "--dev", "--static-dir", ui(true).toString(),
            "--archive-dir", archive().toString());
        try {
            for (String path : new String[] {"/", "/index.html", "/app.js"}) {
                HttpResponse<String> r = get(s, path);
                assertEquals(200, r.statusCode(), path);
                assertEquals(POLICY, r.headers().firstValue("Content-Security-Policy").orElse(null), path);
                assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(null), path);
            }
            for (String path : new String[] {"/", "/index.html"}) {
                String cc = get(s, path).headers().firstValue("Cache-Control").orElse("");
                assertTrue(cc.contains("no-cache"), path + " Cache-Control: " + cc);
            }
            assertFalse(get(s, "/app.js").headers().firstValue("Cache-Control").orElse("").contains("no-cache"));
        } finally {
            s.stop();
        }
    }

    @Test
    void relativeDirectoriesWork() throws Exception {
        // as Task 14 runs it: --static-dir dist, relative to the working directory
        Path rel = Path.of("rel-ui-" + System.nanoTime());
        Files.createDirectories(rel);
        Files.writeString(rel.resolve("index.html"), "<h1>rel</h1>");
        Path relArchive = Path.of("rel-archive-" + System.nanoTime());
        Files.createDirectories(relArchive.resolve("public/index"));
        Files.writeString(relArchive.resolve("public/index/days.json"), "{\"days\":[]}");
        GatewayServer s = server("--port", "0", "--dev", "--static-dir", rel.toString(), "--archive-dir",
            relArchive.toString());
        try {
            assertEquals("<h1>rel</h1>", get(s, "/").body());
            assertEquals("{\"days\":[]}", get(s, "/archive/public/index/days.json").body());
        } finally {
            s.stop();
        }
    }

    @Test
    void anAliasedDirectoryIsStillServed() throws Exception {
        // a path that is not the directory's real name (Jetty serves nothing from an alias base)
        Path ui = ui(true);
        Path alias = ui.resolveSibling(ui.getFileName().toString().toUpperCase(java.util.Locale.ROOT));
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(alias), "a case-insensitive file system");
        GatewayServer s = server("--port", "0", "--dev", "--static-dir", alias.toString());
        try {
            assertEquals("<h1>hi</h1>", get(s, "/").body());
        } finally {
            s.stop();
        }
    }

    @Test
    void withoutCspJsonThereIsNoCspHeader() throws Exception {
        GatewayServer s = server("--port", "0", "--dev", "--static-dir", ui(false).toString());
        try {
            HttpResponse<String> r = get(s, "/");
            assertEquals(200, r.statusCode());
            assertTrue(r.headers().firstValue("Content-Security-Policy").isEmpty());
            assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(null));
        } finally {
            s.stop();
        }
    }

    @Test
    void fakeStatusGoesStoppedStartingRunningAndPlainStatusIsAlwaysRunning() throws Exception {
        GatewayServer s = server("--port", "0", "--dev", "--commit", "abc123");
        try {
            String running = "{\"state\":\"running\",\"healthy\":true,\"commit\":\"abc123\"}";
            assertEquals(running, get(s, "/api/status").body());
            assertEquals("{\"state\":\"stopped\",\"healthy\":false,\"commit\":null}",
                get(s, "/api/status?fakeStatus=stopped").body());
            assertEquals(running, get(s, "/api/status").body(), "without the parameter: running, sequence untouched");
            assertEquals("{\"state\":\"starting\",\"healthy\":false,\"commit\":null}",
                get(s, "/api/status?fakeStatus=stopped").body());
            HttpResponse<String> third = get(s, "/api/status?fakeStatus=stopped");
            assertEquals(running, third.body());
            assertEquals("no-store", third.headers().firstValue("Cache-Control").orElse(null));
            assertEquals(202, post(s, "/api/wake").statusCode(), "the waking page's POST is accepted");
        } finally {
            s.stop();
        }
    }

    @Test
    void devFlushCallsTheArchiveAndOnlyAcceptsPost() throws Exception {
        GatewayServer s = server("--port", "0", "--dev");
        AtomicInteger flushes = new AtomicInteger();
        s.setDevFlush(flushes::incrementAndGet);
        try {
            assertEquals(204, post(s, "/dev/flush").statusCode());
            assertEquals(1, flushes.get());
            assertEquals(405, get(s, "/dev/flush").statusCode());
            assertEquals(1, flushes.get());
        } finally {
            s.stop();
        }
    }

    @Test
    void theArchiveServesPublicOnlyNeverPrivate() throws Exception {
        Path arch = archive();
        GatewayServer s = server("--port", "0", "--dev", "--static-dir", ui(true).toString(),
            "--archive-dir", arch.toString());
        try {
            HttpResponse<String> days = get(s, "/archive/public/index/days.json");
            assertEquals(200, days.statusCode());
            assertEquals("{\"days\":[]}", days.body());
            for (String path : new String[] {"/archive/public/../private/audit/2026-10-13.jsonl",
                "/archive/public/%2e%2e/private/audit/2026-10-13.jsonl", "/archive/private/audit/2026-10-13.jsonl",
                "/archive/public/private/audit/2026-10-13.jsonl"}) {
                HttpResponse<String> r = get(s, path);
                assertNotEquals(200, r.statusCode(), path);
                assertFalse(r.body().contains("secret"), path);
            }
        } finally {
            s.stop();
        }
    }

    @Test
    void everyDevRouteIs404InProduction() throws Exception {
        GatewayServer p = server("--port", "0", "--origin-secret", "x", "--static-dir", ui(true).toString(),
            "--archive-dir", archive().toString());
        p.setDevFlush(() -> { throw new AssertionError("flush reached in production"); });
        try {
            for (String path : new String[] {"/", "/index.html", "/app.js", "/csp.json", "/api/status",
                "/api/status?fakeStatus=stopped", "/archive/public/index/days.json", "/dev/flush"}) {
                assertEquals(404, get(p, path).statusCode(), "GET " + path);
            }
            for (String path : new String[] {"/dev/flush", "/api/wake", "/api/status"}) {
                assertEquals(404, post(p, path).statusCode(), "POST " + path);
            }
            assertEquals(200, get(p, "/healthz").statusCode());
        } finally {
            p.stop();
        }
    }
}
