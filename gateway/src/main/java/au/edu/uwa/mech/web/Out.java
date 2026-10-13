package au.edu.uwa.mech.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;

/** Gateway to browser messages. Each serializes with {@code "t"} first (see protocol.md). */
@JsonPropertyOrder("t")
public sealed interface Out {

    @JsonProperty("t")
    String t();

    static String json(Object message) {
        try {
            return Protocol.mapper().writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize " + message.getClass().getSimpleName(), e);
        }
    }

    record Limits(int script, int waypoints, int chat) {}

    record Welcome(String nick, String level, List<String> cameras, String commit, Limits limits) implements Out {
        public String t() { return "welcome"; }
    }

    record RobotState(double x, double y, double z, double spin, double tilt, boolean gripperOpen,
                      boolean moving) implements Out {
        public String t() { return "robotState"; }
    }

    record Controller(String nick) implements Out {
        public String t() { return "controller"; }
    }

    record CommandStatus(String id, String state, String script, String text) implements Out {
        public String t() { return "commandStatus"; }
    }

    record Users(List<String> nicks) implements Out {
        public String t() { return "users"; }
    }

    record Chat(String id, String nick, String text, long time) implements Out {
        public String t() { return "chat"; }
    }

    record Point(String id, double x, double y, double z, String label, String owner) {}

    record Workspace(List<Point> points) implements Out {
        public String t() { return "workspace"; }
    }

    record Image(String camera, int seq, int width, int height, List<Double> calibration) implements Out {
        public String t() { return "image"; }
    }

    record CameraSettings(String camera, boolean on, int width, int height, double zoom, int quality,
                          boolean fixed) implements Out {
        public String t() { return "cameraSettings"; }
    }

    record Archived(String kind, String id, String url) implements Out {
        public String t() { return "archived"; }
    }

    record Lab(String state, String message) implements Out {
        public String t() { return "lab"; }
    }

    record Console(String text) implements Out {
        public String t() { return "console"; }
    }

    record Error(String code, String message) implements Out {
        public String t() { return "error"; }
    }
}
