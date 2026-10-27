package au.edu.uwa.mech.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtocolTest {

    @Test
    void everyInTypeParsesFromItsNormativeExample() throws Exception {
        assertEquals(new In.Hello("bob", "tok", "jwt", new In.Dev("u1", List.of("admin"))),
            Protocol.parse("{\"t\":\"hello\",\"nick\":\"bob\",\"turnstile\":\"tok\",\"idToken\":\"jwt\","
                + "\"dev\":{\"sub\":\"u1\",\"groups\":[\"admin\"]}}"));
        assertEquals(new In.Hello("bob", null, null, null), Protocol.parse("{\"t\":\"hello\",\"nick\":\"bob\"}"));
        assertEquals(new In.Command("MOVE 1 2 3"), Protocol.parse("{\"t\":\"command\",\"script\":\"MOVE 1 2 3\"}"));
        assertInstanceOf(In.TakeControl.class, Protocol.parse("{\"t\":\"takeControl\"}"));
        assertInstanceOf(In.Release.class, Protocol.parse("{\"t\":\"release\"}"));
        assertEquals(new In.Chat("hi"), Protocol.parse("{\"t\":\"chat\",\"text\":\"hi\"}"));
        assertEquals(new In.SelectCamera("cam1"), Protocol.parse("{\"t\":\"selectCamera\",\"camera\":\"cam1\"}"));
        assertEquals(new In.CameraSettings("cam1", true, 320.0, 240.0, 1.5, 80.0, false),
            Protocol.parse("{\"t\":\"cameraSettings\",\"camera\":\"cam1\",\"on\":true,\"width\":320,"
                + "\"height\":240,\"zoom\":1.5,\"quality\":80,\"fixed\":false}"));
        assertEquals(new In.NewImage("cam1"), Protocol.parse("{\"t\":\"newImage\",\"camera\":\"cam1\"}"));
        assertEquals(new In.WorkspaceAdd(1.0, 2.0, 3.5, "p"),
            Protocol.parse("{\"t\":\"workspaceAdd\",\"x\":1,\"y\":2,\"z\":3.5,\"label\":\"p\"}"));
        assertEquals(new In.WorkspaceDelete("w1"), Protocol.parse("{\"t\":\"workspaceDelete\",\"id\":\"w1\"}"));
        assertInstanceOf(In.ArchiveImages.class, Protocol.parse("{\"t\":\"archiveImages\"}"));
        assertInstanceOf(In.Activity.class, Protocol.parse("{\"t\":\"activity\"}"));
        assertEquals(new In.Admin("kick", "bob", null), Protocol.parse("{\"t\":\"admin\",\"action\":\"kick\",\"nick\":\"bob\"}"));
        assertEquals(new In.Admin("deleteChat", null, "c1"),
            Protocol.parse("{\"t\":\"admin\",\"action\":\"deleteChat\",\"id\":\"c1\"}"));
        for (String a : List.of("add", "randomise", "reset"))
            assertEquals(new In.Blocks(a), Protocol.parse("{\"t\":\"blocks\",\"action\":\"" + a + "\"}"));
    }

    @Test
    void rejectsMalformedMessages() {
        assertBad("{\"t\":\"nope\"}");
        assertBad("{\"nick\":\"bob\"}");
        assertBad("{\"t\":5}");
        assertBad("{\"t\":\"blocks\"}");
        assertBad("{\"t\":\"blocks\",\"action\":\"topple\"}");
        assertBad("{\"t\":\"command\",\"script\":\"x\",\"extra\":1}");
        assertBad("{\"t\":\"takeControl\",\"extra\":1}");
        assertBad("{\"t\":\"command\",\"script\":5}");
        assertBad("{\"t\":\"command\",\"script\":null}");
        assertBad("{\"t\":\"command\"}");
        assertBad("{\"t\":\"hello\"}");
        assertBad("{\"t\":\"chat\",\"text\":{\"a\":1}}");
        assertBad("{\"t\":\"cameraSettings\",\"camera\":\"c\",\"on\":\"true\",\"width\":1,\"height\":1,\"zoom\":1,\"quality\":1,\"fixed\":false}");
        assertBad("{\"t\":\"cameraSettings\",\"camera\":\"c\",\"on\":true,\"width\":\"1\",\"height\":1,\"zoom\":1,\"quality\":1,\"fixed\":false}");
        assertBad("{\"t\":\"cameraSettings\",\"camera\":\"c\",\"on\":true,\"height\":1,\"zoom\":1,\"quality\":1,\"fixed\":false}");
        assertBad("{\"t\":\"admin\",\"action\":\"explode\"}");
        assertBad("{\"t\":\"activity\"} {\"t\":\"activity\"}");
        assertBad("{\"t\":\"activity\"}garbage");
        assertBad("[]");
        assertBad("");
        assertBad("not json");
        assertBad((String) null);
    }

    @Test
    void rejectsOversizeMessages() {
        String big = "{\"t\":\"chat\",\"text\":\"" + "a".repeat(4096) + "\"}";
        assertBad(big);
        // exactly at the limit is fine
        String head = "{\"t\":\"chat\",\"text\":\"";
        String tail = "\"}";
        String ok = head + "a".repeat(4096 - head.length() - tail.length()) + tail;
        assertEquals(4096, ok.length());
        assertDoesNotThrow(() -> Protocol.parse(ok));
        // the limit is in bytes, not characters
        String multi = head + "é".repeat(2100) + tail;
        assertTrue(multi.length() < 4096);
        assertBad(multi);
    }

    @Test
    void javaSerializationStreamIsRefused() {
        byte[] stream = {(byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x73, 0x72, 0x00, 0x04};
        assertThrows(BadMessage.class, () -> Protocol.parse(stream));
        assertBad(new String(stream, StandardCharsets.ISO_8859_1));
        assertBad(new String(stream, StandardCharsets.UTF_8));
    }

    @Test
    void outRecordsSerializeToNormativeJson() {
        assertEquals("{\"t\":\"welcome\",\"nick\":\"bob\",\"level\":\"guest\",\"cameras\":[\"a\",\"b\"],\"commit\":\"abc\","
                + "\"limits\":{\"script\":400,\"waypoints\":30,\"chat\":300}}",
            Out.json(new Out.Welcome("bob", "guest", List.of("a", "b"), "abc", new Out.Limits(400, 30, 300))));
        assertEquals("{\"t\":\"robotState\",\"x\":1.0,\"y\":2.0,\"z\":3.0,\"spin\":4.0,\"tilt\":5.0,\"gripperOpen\":true,\"moving\":false}",
            Out.json(new Out.RobotState(1, 2, 3, 4, 5, true, false)));
        assertEquals("{\"t\":\"controller\",\"nick\":null}", Out.json(new Out.Controller(null)));
        assertEquals("{\"t\":\"commandStatus\",\"id\":\"1\",\"state\":\"queued\",\"script\":\"s\",\"text\":\"\"}",
            Out.json(new Out.CommandStatus("1", "queued", "s", "")));
        assertEquals("{\"t\":\"users\",\"nicks\":[\"a\"]}", Out.json(new Out.Users(List.of("a"))));
        assertEquals("{\"t\":\"chat\",\"id\":\"c\",\"nick\":\"n\",\"text\":\"hi\",\"time\":1700000000000}",
            Out.json(new Out.Chat("c", "n", "hi", 1700000000000L)));
        assertEquals("{\"t\":\"workspace\",\"points\":[{\"id\":\"p\",\"x\":1.0,\"y\":2.0,\"z\":3.0,\"label\":\"l\",\"owner\":\"o\"}]}",
            Out.json(new Out.Workspace(List.of(new Out.Point("p", 1, 2, 3, "l", "o")))));
        assertEquals("{\"t\":\"image\",\"camera\":\"c\",\"seq\":7,\"width\":320,\"height\":240,\"calibration\":[1.0,2.0]}",
            Out.json(new Out.Image("c", 7, 320, 240, List.of(1.0, 2.0))));
        assertEquals("{\"t\":\"cameraSettings\",\"camera\":\"c\",\"on\":true,\"width\":320,\"height\":240,\"zoom\":1.0,\"quality\":80,\"fixed\":false}",
            Out.json(new Out.CameraSettings("c", true, 320, 240, 1.0, 80, false)));
        assertEquals("{\"t\":\"archived\",\"kind\":\"move\",\"id\":\"i\",\"url\":\"/u\"}", Out.json(new Out.Archived("move", "i", "/u")));
        assertEquals("{\"t\":\"lab\",\"state\":\"ready\",\"message\":\"m\"}", Out.json(new Out.Lab("ready", "m")));
        assertEquals("{\"t\":\"console\",\"text\":\"x\"}", Out.json(new Out.Console("x")));
        assertEquals("{\"t\":\"error\",\"code\":\"unauthorised\",\"message\":\"no\"}", Out.json(new Out.Error("unauthorised", "no")));
    }

    private static void assertBad(String json) {
        assertThrows(BadMessage.class, () -> Protocol.parse(json), String.valueOf(json));
    }
}
