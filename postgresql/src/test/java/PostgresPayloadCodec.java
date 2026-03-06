import io.github.flameyossnowy.universal.api.json.JsonCodec;

public class PostgresPayloadCodec implements JsonCodec<PostgresJsonEntity.Payload> {
    @Override
    public String serialize(PostgresJsonEntity.Payload value, Class<PostgresJsonEntity.Payload> type) {
        if (value == null) {
            return "null";
        }
        return "{\"n\":\"" + value.getName() + "\",\"a\":" + value.getAge() + "}";
    }

    @Override
    public PostgresJsonEntity.Payload deserialize(String json, Class<PostgresJsonEntity.Payload> type) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        String name = extractString(json, "n");
        int age = extractInt(json, "a");
        return new PostgresJsonEntity.Payload(name, age);
    }

    private static String extractString(String json, String key) {
        int keyPos = json.indexOf('"' + key + '"');
        if (keyPos < 0) return null;
        int colon = json.indexOf(':', keyPos);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') end++;
        if (end >= json.length()) return null;
        return json.substring(start, end);
    }

    private static int extractInt(String json, String key) {
        int keyPos = json.indexOf('"' + key + '"');
        if (keyPos < 0) return 0;
        int colon = json.indexOf(':', keyPos);
        if (colon < 0) return 0;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end <= start) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
