package io.github.flameyossnowy.universal.microservices.file;

import java.nio.file.Path;

/**
 * Context for file-based operations.
 */
public record FileContext(Path basePath, boolean inTransaction) {
}
