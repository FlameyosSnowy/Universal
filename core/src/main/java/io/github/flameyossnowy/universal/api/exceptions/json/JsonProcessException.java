package io.github.flameyossnowy.universal.api.exceptions.json;

public class JsonProcessException extends RuntimeException {
    public JsonProcessException(String message) {
        super(message);
    }

    public JsonProcessException(Throwable cause) {
        super(cause);
    }

    public JsonProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
