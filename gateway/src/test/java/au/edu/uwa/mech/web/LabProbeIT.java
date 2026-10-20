package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** {@link LabProbe}: its state machine with a fake check, and the MOM check against the real lab. */
class LabProbeIT {
    static final List<String> CAMERAS = List.of("Colour", "YCam", "ZCam");

    @Test
    void stateFollowsTheLastCheckAndAFailingCheckIsStarting() {
        List<Object> answers = new ArrayList<>(List.of(false, true, new IllegalStateException("MOM down"), true));
        LabProbe p = new LabProbe(() -> {
            Object a = answers.remove(0);
            if (a instanceof RuntimeException e) throw e;
            return (Boolean) a;
        });
        assertEquals("starting", p.state(), "starting until a check says otherwise");
        assertFalse(p.probe());
        assertEquals("starting", p.state());
        assertTrue(p.probe());
        assertEquals("ready", p.state());
        assertFalse(p.probe(), "an exception is not ready, and is not thrown");
        assertEquals("starting", p.state());
        assertTrue(p.probe());
        assertEquals("ready", p.state());
    }

    @Test
    void startChecksAtOnceThenAgainUntilReady() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ManualTime time = new ManualTime();
        try (LabProbe p = new LabProbe(() -> calls.incrementAndGet() >= 2, time.scheduler())) {
            p.start();
            time.advance(0);
            assertEquals(1, calls.get(), "checked at once");
            assertEquals("starting", p.state());
            time.advance(LabProbe.STARTING_POLL_MILLIS - 1);
            assertEquals(1, calls.get(), "not again before the starting interval");
            time.advance(1);
            assertEquals(2, calls.get());
            assertEquals("ready", p.state());
            time.advance(LabProbe.READY_POLL_MILLIS - 1);
            assertEquals(2, calls.get(), "then only on the slower ready interval");
            time.advance(1);
            assertEquals(3, calls.get());
        }
    }

    @Test
    void theMomCheckSeesTheRobotProxyAndTheThreeCameras() throws Exception {
        LabFixture lab = LabFixture.shared();
        try (LabProbe.Check c = LabProbe.mom("127.0.0.1", lab.momPort(), CAMERAS)) {
            assertTrue(c.ready(), "the in-process lab has robot/robot.token, robot/robot.channel and three camera channels");
            assertTrue(c.ready(), "and again on the same connection");
        }
    }

    @Test
    void aMissingCameraIsNotReady() throws Exception {
        LabFixture lab = LabFixture.shared();
        try (LabProbe.Check c = LabProbe.mom("127.0.0.1", lab.momPort(), List.of("Colour", "NoSuchCam"))) {
            assertFalse(c.ready(), "the camera server has not published NoSuchCam");
        }
    }

    @Test
    void anUnreachableMomIsStarting() throws Exception {
        int dead;
        try (ServerSocket s = new ServerSocket(0)) {
            dead = s.getLocalPort();
        }
        try (LabProbe p = new LabProbe(LabProbe.mom("127.0.0.1", dead, CAMERAS))) {
            assertFalse(p.probe());
            assertEquals("starting", p.state());
        }
    }
}
