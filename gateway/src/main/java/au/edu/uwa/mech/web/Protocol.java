package au.edu.uwa.mech.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Strict parser for browser messages. Everything not in protocol.md is a {@link BadMessage}: unknown
 * {@code t}, unknown or duplicate fields, wrong JSON types, missing required fields, trailing text,
 * oversize input and Java serialization streams. The mapper never uses polymorphic typing; the type
 * is chosen by a fixed map from the {@code t} string.
 */
public final class Protocol {
    /** Maximum size of one incoming message in bytes. */
    public static final int MAX_MESSAGE_BYTES = 4096;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .build();

    static {
        // Strings are only ever read from JSON strings: no number or boolean to text coercion.
        var textual = MAPPER.coercionConfigFor(LogicalType.Textual);
        textual.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        textual.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        textual.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    private static final Map<String, Class<? extends In>> TYPES = Map.ofEntries(
        Map.entry("hello", In.Hello.class),
        Map.entry("command", In.Command.class),
        Map.entry("takeControl", In.TakeControl.class),
        Map.entry("release", In.Release.class),
        Map.entry("chat", In.Chat.class),
        Map.entry("selectCamera", In.SelectCamera.class),
        Map.entry("cameraSettings", In.CameraSettings.class),
        Map.entry("newImage", In.NewImage.class),
        Map.entry("workspaceAdd", In.WorkspaceAdd.class),
        Map.entry("workspaceDelete", In.WorkspaceDelete.class),
        Map.entry("archiveImages", In.ArchiveImages.class),
        Map.entry("activity", In.Activity.class),
        Map.entry("admin", In.Admin.class));

    private Protocol() {}

    /** Shared mapper for the outgoing side (no typing features, plain records). */
    static ObjectMapper mapper() {
        return MAPPER;
    }

    public static In parse(byte[] bytes) throws BadMessage {
        if (bytes == null) throw new BadMessage("null message");
        if (bytes.length > MAX_MESSAGE_BYTES) throw new BadMessage("message too large");
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xAC && (bytes[1] & 0xFF) == 0xED) {
            throw new BadMessage("not json (serialization stream)");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    public static In parse(String json) throws BadMessage {
        if (json == null) throw new BadMessage("null message");
        // Cheap size screen first (a char is at most 3 UTF-8 bytes), then the exact byte count.
        if (json.length() > MAX_MESSAGE_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new BadMessage("message too large");
        }
        String s = json.strip();
        if (s.isEmpty() || s.charAt(0) != '{') throw new BadMessage("not a json object");
        try {
            JsonNode tree = MAPPER.readTree(s);
            if (!(tree instanceof ObjectNode obj)) throw new BadMessage("not a json object");
            JsonNode t = obj.remove("t");
            if (t == null || !t.isTextual()) throw new BadMessage("missing or invalid t");
            Class<? extends In> type = TYPES.get(t.textValue());
            if (type == null) throw new BadMessage("unknown t");
            return MAPPER.treeToValue(obj, type);
        } catch (BadMessage e) {
            throw e;
        } catch (Exception e) {
            // Jackson wraps constructor failures; never echo its text to the client.
            throw new BadMessage("invalid message", e);
        }
    }
}
