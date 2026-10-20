package au.edu.uwa.mech.web;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The one place a test waits for another thread: poll a condition until it holds. The deadline is a
 * ceiling for a broken build, never a pause a passing test sits through, so no test passes because
 * time went by. {@link NoSleepInTestsTest} keeps {@code Thread.sleep} out of every other test.
 */
public final class Await {
    public static final long DEFAULT_MILLIS = 10_000;

    private Await() { }

    public static void until(String what, BooleanSupplier condition) {
        until(what, condition, DEFAULT_MILLIS);
    }

    public static void until(String what, BooleanSupplier condition, long millis) {
        long end = System.nanoTime() + millis * 1_000_000;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("timed out after " + millis + " ms waiting until " + what);
            pause();
        }
    }

    /** As {@link #until}, with a description of what was seen instead, for the failure message. */
    public static void until(Supplier<String> seen, BooleanSupplier condition, long millis) {
        long end = System.nanoTime() + millis * 1_000_000;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("timed out after " + millis + " ms; " + seen.get());
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
