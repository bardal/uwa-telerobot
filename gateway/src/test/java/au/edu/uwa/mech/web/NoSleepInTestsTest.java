package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests do not run on wall-clock time: no test sleeps to let something happen or to show that it
 * did not. Time comes from {@link ManualTime} or the lab's stepped arm, and waiting for another
 * thread goes through {@link Await}, the only place allowed to sleep.
 */
class NoSleepInTestsTest {
    static final Pattern SLEEP = Pattern.compile("\\bThread\\.sleep\\(|\\bTimeUnit\\.\\w+\\.sleep\\(|\\.sleep\\(\\s*\\d");
    static final List<String> ALLOWED = List.of("Await.java");

    /** The gateway module: its compiled tests are in target/test-classes. */
    static Path module() {
        try {
            return Path.of(NoSleepInTestsTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent().getParent();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void onlyAwaitSleeps() throws IOException {
        assertEquals(List.of(), sleepsUnder(module().resolve(Path.of("src", "test", "java")), false),
            "wait with Await.until or move a ManualTime instead of sleeping");
    }

    /** The lab module's tests too (its manual tools under it/, run by hand with main(), are not tests). */
    @Test
    void theLabTestsDoNotSleepEither() throws IOException {
        Path lab = module().resolveSibling("lab").resolve(Path.of("src", "test", "java"));
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(lab), "the lab module is next to the gateway");
        assertEquals(List.of(), sleepsUnder(lab, true), "step the arm with SteppedTime instead of sleeping");
    }

    static List<String> sleepsUnder(Path root, boolean testClassesOnly) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !ALLOWED.contains(p.getFileName().toString()))
                .filter(p -> !testClassesOnly || contains(p, "@Test"))
                .flatMap(NoSleepInTestsTest::sleeps)
                .toList();
        }
    }

    static boolean contains(Path file, String text) {
        try {
            return Files.readString(file).contains(text);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static Stream<String> sleeps(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return java.util.stream.IntStream.range(0, lines.size())
                .filter(i -> SLEEP.matcher(lines.get(i)).find())
                .mapToObj(i -> file.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
