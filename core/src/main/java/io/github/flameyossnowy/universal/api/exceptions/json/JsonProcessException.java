package io.github.flameyossnowy.universal.api.exceptions.json;

public class JsonProcessException extends RuntimeException {
    private final JsonLocation location;

    public JsonProcessException(String message, JsonLocation location) {
        super(message);
        this.location = location;
    }

    public JsonProcessException(Throwable cause, JsonLocation location) {
        super(cause);
        this.location = location;
    }

    public JsonProcessException(String message, Throwable cause, JsonLocation location) {
        super(message, cause);
        this.location = location;
    }

    public JsonLocation getLocation() {
        return location;
    }
}
