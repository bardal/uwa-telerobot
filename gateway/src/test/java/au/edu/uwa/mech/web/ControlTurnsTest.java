package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import au.edu.uwa.mech.web.Identity.Level;

/** The lab's take-over rule (HUWManager(180)), checked in the gateway so a refusal can say why and for how long. */
class ControlTurnsTest {
    private final ManualTime time = new ManualTime();
    private final ControlTurns turns = new ControlTurns(time);

    private Optional<String> ask(String me, Level mine, Level holders) {
        return turns.refusal(me, mine, nick -> Optional.of(holders));
    }

    @Test
    void aFreeRobotIsNeverRefused() {
        assertEquals(Optional.empty(), ask("bob", Level.GUEST, Level.GUEST));
        turns.changed("ann");
        turns.changed(null);
        assertEquals(Optional.empty(), ask("bob", Level.GUEST, Level.GUEST));
    }

    @Test
    void anEqualHasToWaitForTheThreeMinutesAndIsToldHowLong() {
        turns.changed("ann");
        time.advance(80_000);
        assertEquals(Optional.of("Sorry, ann is using the robot. You can take over in 1 min 40 s (everyone gets 3 minutes)."),
            ask("bob", Level.GUEST, Level.GUEST));
        time.advance(99_500);
        assertEquals(Optional.of("Sorry, ann is using the robot. You can take over in 1 s (everyone gets 3 minutes)."),
            ask("bob", Level.REGISTERED, Level.REGISTERED));
        time.advance(500);
        assertEquals(Optional.empty(), ask("bob", Level.GUEST, Level.GUEST));
    }

    @Test
    void aHigherLevelTakesOverAtOnceAndALowerOneWaits() {
        turns.changed("ann");
        assertEquals(Optional.empty(), ask("bob", Level.REGISTERED, Level.GUEST));
        assertEquals(Optional.empty(), ask("bob", Level.ADMIN, Level.REGISTERED));
        assertTrue(ask("bob", Level.GUEST, Level.REGISTERED).isPresent());
    }

    @Test
    void theClockStartsWhenControlChangesHandsNotWhenTheSameDriverIsReported() {
        turns.changed("ann");
        time.advance(170_000);
        turns.changed("ann");   // every session hears the same news
        time.advance(10_000);
        assertEquals(Optional.empty(), ask("bob", Level.GUEST, Level.GUEST));
    }

    @Test
    void theDriverThemselvesOrAnUnknownDriverIsLeftToTheLab() {
        turns.changed("ann");
        assertEquals(Optional.empty(), ask("ann", Level.GUEST, Level.GUEST));
        assertEquals(Optional.empty(), turns.refusal("bob", Level.GUEST, nick -> Optional.empty()));
    }
}
