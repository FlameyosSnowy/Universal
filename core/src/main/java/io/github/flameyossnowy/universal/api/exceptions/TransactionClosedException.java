package io.github.flameyossnowy.universal.api.exceptions;

public class TransactionClosedException extends RuntimeException {
    public TransactionClosedException(String message) {
        super(message);
    }
}
