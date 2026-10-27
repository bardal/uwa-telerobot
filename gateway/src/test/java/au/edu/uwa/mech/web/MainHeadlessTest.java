package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/** The gateway never opens a window: the in-process 2000 servers' status windows stay hidden. */
class MainHeadlessTest {
    @Test
    void headlessUnlessSaidOtherwise() {
        Properties p = new Properties();
        Main.headlessByDefault(p);
        assertEquals("true", p.getProperty("java.awt.headless"));
    }

    @Test
    void anExplicitChoiceStands() {
        Properties p = new Properties();
        p.setProperty("java.awt.headless", "false");
        Main.headlessByDefault(p);
        assertEquals("false", p.getProperty("java.awt.headless"));
    }
}
