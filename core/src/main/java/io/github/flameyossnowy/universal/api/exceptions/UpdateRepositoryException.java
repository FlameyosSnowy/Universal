package io.github.flameyossnowy.universal.api.exceptions;

public class UpdateRepositoryException extends RepositoryException {
    public UpdateRepositoryException(String message) {
        super(message);
    }

    public UpdateRepositoryException(String message, Exception exception) {
        super(message, exception);
    }
}
