package au.edu.uwa.mech.web;

import java.util.List;
import java.util.Set;

/**
 * Browser to gateway messages. One record per normative type (see protocol.md). Required fields are
 * checked in the canonical constructors, so a missing or null field is a {@link BadMessage} once
 * {@link Protocol} wraps the failure.
 */
public sealed interface In {

    Set<String> ADMIN_ACTIONS = Set.of("kick", "ban", "reset", "deleteMove", "deleteSnapshot", "deleteChat");

    record Dev(String sub, List<String> groups) {
        public Dev {
            req(sub, "sub");
            req(groups, "groups");
            for (String g : groups) req(g, "groups[]");
            groups = List.copyOf(groups);
        }
    }

    record Hello(String nick, String turnstile, String idToken, Dev dev) implements In {
        public Hello {
            req(nick, "nick");
        }
    }

    record Command(String script) implements In {
        public Command {
            req(script, "script");
        }
    }

    record TakeControl() implements In {}

    record Release() implements In {}

    record Chat(String text) implements In {
        public Chat {
            req(text, "text");
        }
    }

    record SelectCamera(String camera) implements In {
        public SelectCamera {
            req(camera, "camera");
        }
    }

    record CameraSettings(String camera, Boolean on, Double width, Double height, Double zoom, Double quality,
                          Boolean fixed) implements In {
        public CameraSettings {
            req(camera, "camera");
            req(on, "on");
            req(width, "width");
            req(height, "height");
            req(zoom, "zoom");
            req(quality, "quality");
            req(fixed, "fixed");
            finite(width, height, zoom, quality);
        }
    }

    record NewImage(String camera) implements In {
        public NewImage {
            req(camera, "camera");
        }
    }

    record WorkspaceAdd(Double x, Double y, Double z, String label) implements In {
        public WorkspaceAdd {
            req(x, "x");
            req(y, "y");
            req(z, "z");
            req(label, "label");
            finite(x, y, z);
        }
    }

    record WorkspaceDelete(String id) implements In {
        public WorkspaceDelete {
            req(id, "id");
        }
    }

    record ArchiveImages() implements In {}

    record Activity() implements In {}

    record Admin(String action, String nick, String id) implements In {
        public Admin {
            req(action, "action");
            if (!ADMIN_ACTIONS.contains(action)) throw new IllegalArgumentException("unknown admin action");
        }
    }

    private static void req(Object o, String field) {
        if (o == null) throw new IllegalArgumentException("missing " + field);
    }

    private static void finite(Double... ds) {
        for (Double d : ds) {
            if (d.isNaN() || d.isInfinite()) throw new IllegalArgumentException("not finite");
        }
    }
}
