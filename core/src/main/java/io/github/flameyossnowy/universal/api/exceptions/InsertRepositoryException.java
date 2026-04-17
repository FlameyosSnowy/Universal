package io.github.flameyossnowy.universal.api.exceptions;

public class InsertRepositoryException extends RepositoryException {
    public InsertRepositoryException(String message) {
        super(message);
    }

    public InsertRepositoryException(String message, Exception exception) {
        super(message, exception);
    }
}
