package au.edu.uwa.mech.web;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Who is driving the robot and since when, so a take-over the lab would refuse can be refused here with
 * the reason. The lab's rule (HUWManager(180) in devices/robot/Server): a higher user level takes control
 * at once, anyone else only 3 minutes after the driver took it. MOM's own refusal never reaches the user.
 */
final class ControlTurns {
    static final long TURN_MILLIS = 180_000;

    private final LongSupplier clock;
    private String driver;
    private long since;

    ControlTurns(LongSupplier clock) {
        this.clock = clock;
    }

    /** MOM says the robot's controller is now nick (null: nobody). Every session reports it; repeats are ignored. */
    synchronized void changed(String nick) {
        if (Objects.equals(driver, nick)) return;
        driver = nick;
        since = clock.getAsLong();
    }

    /**
     * Why me, at level, can't take control now; empty leaves it to the lab (free robot, already the driver,
     * a driver this gateway doesn't know, a higher level, or the 3 minutes are up).
     */
    synchronized Optional<String> refusal(String me, Identity.Level level, Function<String, Optional<Identity.Level>> levelOf) {
        if (driver == null || driver.equals(me)) return Optional.empty();
        Optional<Identity.Level> theirs = levelOf.apply(driver);
        if (theirs.isEmpty() || level.compareTo(theirs.get()) > 0) return Optional.empty();
        long left = since + TURN_MILLIS - clock.getAsLong();
        if (left <= 0) return Optional.empty();
        return Optional.of("Sorry, " + driver + " is using the robot. You can take over in " + duration(left)
            + " (everyone gets 3 minutes).");
    }

    /** "1 min 40 s", "40 s": whole seconds, rounded up. */
    private static String duration(long millis) {
        long s = (millis + 999) / 1000;
        return s >= 60 ? s / 60 + " min " + s % 60 + " s" : s + " s";
    }
}
