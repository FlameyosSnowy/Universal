package io.github.flameyossnowy.universal.api.exceptions;

public class DeleteRepositoryException extends RepositoryException {
    public DeleteRepositoryException(String message) {
        super(message);
    }

    public DeleteRepositoryException(String message, Exception exception) {
        super(message, exception);
    }
}
