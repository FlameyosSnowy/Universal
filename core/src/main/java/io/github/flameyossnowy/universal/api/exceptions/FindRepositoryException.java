package io.github.flameyossnowy.universal.api.exceptions;

public class FindRepositoryException extends RepositoryException {
    public FindRepositoryException(String message) {
        super(message);
    }

    public FindRepositoryException(String message, Exception exception) {
        super(message, exception);
    }
}
