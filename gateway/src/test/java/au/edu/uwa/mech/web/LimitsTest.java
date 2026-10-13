package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LimitsTest {
    long now = 0;
    final Limits limits = new Limits(() -> now);

    static String script(int waypoints) {
        return IntStream.rangeClosed(1, waypoints).mapToObj(i -> "x" + i).collect(Collectors.joining(";"));
    }

    @Test
    void scriptLengthBoundary() {
        String ok = "x10,y20" + " ".repeat(400 - 7);
        assertEquals(400, ok.length());
        assertTrue(limits.checkScript(ok).ok());
        Limits.Verdict v = limits.checkScript(ok + " ");
        assertFalse(v.ok());
        assertTrue(v.reason().contains("400"), v.reason());
    }

    @Test
    void scriptWaypointBoundary() {
        assertTrue(limits.checkScript(script(30)).ok());
        Limits.Verdict v = limits.checkScript(script(31));
        assertFalse(v.ok());
        assertTrue(v.reason().contains("30"), v.reason());
    }

    @Test
    void unparseableScriptIsRefusedWithMessage() {
        Limits.Verdict v = limits.checkScript("x10,,q");
        assertFalse(v.ok());
        assertTrue(v.reason().contains("'q'"), v.reason());
        assertFalse(limits.checkScript("").ok());
        assertFalse(limits.checkScript(null).ok());
    }

    @Test
    void realScriptsParse() {
        assertTrue(limits.checkScript("x100,y200,z50;rx10;go;tf;gc").ok());
    }

    @Test
    void appletGeneratedAndHandWrittenScriptsAreAccepted() {
        for (String s : new String[] {
            "x10,y20,s0,t0,z5,gopen",
            "x120,y150,z60,s-90,t0,g0;z15;g1;z80;x330,y120;z45;g0;z90",
            "gopen;gclosed", "gclose", "rz-20", "tf", "x10.5", "x10,,y20", "x10\ty20,z5\r\nx30",
            "o;c", "g0 x10 g1"})
            assertTrue(limits.checkScript(s).ok(), s + " -> " + limits.checkScript(s).reason());
    }

    @Test
    void scriptsBuiltByTheAppletCodeAreAccepted() {
        au.edu.uwa.mech.maths.Pose3d pose = new au.edu.uwa.mech.maths.Pose3d(120.4, 150.6, 60, -90, 15);
        au.edu.uwa.mech.maths.Point3d pt = new au.edu.uwa.mech.maths.Point3d(10, -20, 30);
        for (au.edu.uwa.mech.devices.robot.Gripper g : new au.edu.uwa.mech.devices.robot.Gripper[] {
            au.edu.uwa.mech.devices.robot.Gripper.newOpen(), au.edu.uwa.mech.devices.robot.Gripper.newClosed()}) {
            String state = au.edu.uwa.mech.devices.robot.MMoveCommand.stateAsGBHString(
                new au.edu.uwa.mech.devices.robot.RobotState(pose, g));
            assertTrue(limits.checkScript(state).ok(), state);
            assertTrue(limits.checkScript(state + ";" + state).ok(), state);
        }
        String p = au.edu.uwa.mech.devices.robot.MMoveCommand.poseAsGBHString(pose);
        String q = au.edu.uwa.mech.devices.robot.MMoveCommand.pointAsGBHString(pt);
        assertTrue(limits.checkScript(p).ok(), p);
        assertTrue(limits.checkScript(q).ok(), q);
    }

    @Test
    void runTogetherTermsAsTheAppletsAppendsMakeThemAreAccepted() {
        // Robot > Comand from cursor appends with no separator, as SimpleCommandPanel.appendTextCommand does
        String live = "x200,y300,z60,s-90,t0,g0;z20;g1;z120x379,y430,s-90,t0,z100";
        for (String s : new String[] {live, "z120x379", "x1y2z3", "rx10ry-5", "tfx10", "x1.5y2", "gopenx10"}) {
            Limits.Verdict v = limits.checkScript(s);
            assertTrue(v.ok(), s + " -> " + v.reason());
        }
        // what PathScript makes of the live string: the appended pose joins the last waypoint
        java.util.List<au.edu.uwa.mech.sim.RobotPose> path = au.edu.uwa.mech.sim.PathScript.parse(live,
            new au.edu.uwa.mech.sim.RobotPose(0, 0, 0, 0, 0, true));
        assertEquals(4, path.size());
        assertEquals(379, path.get(3).x, 1e-9);

        // the applet's own builders, appended one after another to whatever is already there
        au.edu.uwa.mech.maths.Pose3d pose = new au.edu.uwa.mech.maths.Pose3d(379.4, 430.2, 100, -90, 0);
        String appended = "x120,y150,z60;g1" + au.edu.uwa.mech.devices.robot.MMoveCommand.poseAsGBHString(pose)
            + au.edu.uwa.mech.devices.robot.MMoveCommand.pointAsGBHString(new au.edu.uwa.mech.maths.Point3d(10, -20, 30))
            + au.edu.uwa.mech.devices.robot.MMoveCommand.stateAsGBHString(
                new au.edu.uwa.mech.devices.robot.RobotState(pose, au.edu.uwa.mech.devices.robot.Gripper.newClosed()))
            + "gclose" + "gopen";
        assertTrue(limits.checkScript(appended).ok(), appended + " -> " + limits.checkScript(appended).reason());
    }

    @Test
    void theScannerRefusesWhatPathScriptWouldIgnore() {
        for (String s : new String[] {"X10", "x", "x+", "x1.", "r", "rg1", "O", "x10.5.3", "z120x379q", "x1&y2", "é"}) {
            assertFalse(limits.checkScript(s).ok(), s);
        }
        assertEquals("z120x379q", Limits.unrecognisedTerm("x1;z120x379q;y2"));
        assertNull(Limits.unrecognisedTerm("x1;z120x379;y2"));
    }

    @Test
    void unknownTermsAreRefusedNamingTheTerm() {
        assertTrue(limits.checkScript("x1;y2;foo").reason().contains("'foo'"));
        assertTrue(limits.checkScript("<script>").reason().contains("'<script>'"));
        assertFalse(limits.checkScript("X10").ok());
        assertFalse(limits.checkScript("x").ok());
        assertFalse(limits.checkScript("x10,q").ok());
    }

    @Test
    void chatLengthBoundaryAfterNfc() {
        assertTrue(limits.checkChat("a".repeat(300)).ok());
        assertFalse(limits.checkChat("a".repeat(301)).ok());
        // decomposed e + combining acute is 600 chars raw, 300 after NFC
        assertTrue(limits.checkChat("é".repeat(300)).ok());
        assertEquals("é".repeat(300), limits.checkChat("é".repeat(300)).text());
    }

    @Test
    void chatStripsControlAndBidi() {
        assertEquals("helloevil", limits.checkChat("hello‮evil").text());
        assertEquals("ab", limits.checkChat("a\u0000\u0007b⁦⁩").text());
        assertFalse(limits.checkChat("‪‮  ").ok());
        assertFalse(limits.checkChat("").ok());
        assertFalse(limits.checkChat(null).ok());
    }

    @Test
    void chatBlocklistIsCaseInsensitiveWholeWord() {
        assertFalse(limits.checkChat("Buy   followers now").ok());
        assertFalse(limits.checkChat("you RETARD").ok());
        assertFalse(limits.checkChat("ＶＩＡＧＲＡ").ok()); // fullwidth VIAGRA, NFKC folds it
        assertTrue(limits.checkChat("retarded motors are fine").ok()); // not a whole word
        assertTrue(limits.checkChat("scunthorpe kikeriki").ok());
    }

    @Test
    void nicks() {
        assertEquals("Ann B.", Limits.cleanNick("  Ann B.  ").orElseThrow());
        assertTrue(Limits.cleanNick("a".repeat(20)).isPresent());
        assertTrue(Limits.cleanNick("a".repeat(21)).isEmpty());
        assertTrue(Limits.cleanNick("<script>").isEmpty());
        assertTrue(Limits.cleanNick("   ").isEmpty());
        assertTrue(Limits.cleanNick(null).isEmpty());
        assertTrue(Limits.cleanNick("bob_1-x").isPresent());
        assertTrue(Limits.cleanNick("böb").isEmpty());
    }

    @Test
    void chatBucketIsOnePerSecondBurstFive() {
        for (int i = 0; i < 5; i++) assertTrue(limits.allow("chat"), "burst " + i);
        assertFalse(limits.allow("chat"));
        now += 999;
        assertFalse(limits.allow("chat"));
        now += 1;
        assertTrue(limits.allow("chat"));
        assertFalse(limits.allow("chat"));
        now += 60_000; // refill is capped at the burst
        for (int i = 0; i < 5; i++) assertTrue(limits.allow("chat"));
        assertFalse(limits.allow("chat"));
    }

    @Test
    void activityIsTwoPerSecond() {
        assertTrue(limits.allow("activity"));
        assertTrue(limits.allow("activity"));
        assertFalse(limits.allow("activity"));
        now += 499;
        assertFalse(limits.allow("activity"));
        now += 1;
        assertTrue(limits.allow("activity"));
        assertFalse(limits.allow("activity"));
    }

    @Test
    void snapshotsAreTenPerHour() {
        for (int i = 0; i < 10; i++) assertTrue(limits.allow("snapshot"), "snap " + i);
        assertFalse(limits.allow("snapshot"));
        now += 3_600_000L / 10 - 1;
        assertFalse(limits.allow("snapshot"));
        now += 1;
        assertTrue(limits.allow("snapshot"));
        assertFalse(limits.allow("snapshot"));
    }

    @Test
    void cameraRequestsAreOneASecondWithABurstOfThree() {
        for (int i = 0; i < 3; i++) assertTrue(limits.allow("camera"), "burst " + i);
        assertFalse(limits.allow("camera"));
        now += 999;
        assertFalse(limits.allow("camera"));
        now += 1;
        assertTrue(limits.allow("camera"));
        assertFalse(limits.allow("camera"));
        now += 10_000; // refills to the burst, no further
        for (int i = 0; i < 3; i++) assertTrue(limits.allow("camera"), "refilled " + i);
        assertFalse(limits.allow("camera"));
    }

    @Test
    void bucketsAreIndependentAndUnknownIsRejected() {
        for (int i = 0; i < 5; i++) limits.allow("chat");
        assertFalse(limits.allow("chat"));
        assertTrue(limits.allow("command"));
        assertTrue(limits.allow("any"));
        assertThrows(IllegalArgumentException.class, () -> limits.allow("nope"));
    }

    @Test
    void twentyRejectionsInAMinuteCloseTheSession() {
        for (int i = 0; i < 19; i++) {
            assertFalse(limits.recordRejection(), "rejection " + (i + 1));
            now += 1000;
        }
        assertTrue(limits.recordRejection()); // the 20th, 19 s after the first
    }

    @Test
    void slowRejectionsDoNotAccumulate() {
        for (int i = 0; i < 100; i++) {
            assertFalse(limits.recordRejection());
            now += 4_000; // 15 per minute
        }
    }

    @Test
    void rejectionWindowIsExactlyAMinute() {
        for (int i = 0; i < 19; i++) limits.recordRejection(); // all at t=0
        now += 60_000; // those 19 now fall outside the window
        assertFalse(limits.recordRejection());
    }
}
