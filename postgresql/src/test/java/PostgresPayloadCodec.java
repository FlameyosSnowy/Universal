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
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    private static int extractInt(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return 0;
        }
        start += needle.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
