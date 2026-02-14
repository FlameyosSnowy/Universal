package io.github.flameyossnowy.universal.api.exceptions.json;

public class JacksonJsonLocation {
    public static JsonLocation from(com.fasterxml.jackson.core.JsonLocation location) {
        return new JsonLocation(location.getLineNr(), location.getColumnNr(), location.getCharOffset(), location.getByteOffset());
    }
}
