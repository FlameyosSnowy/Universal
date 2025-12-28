package io.github.flameyossnowy.universal.microservices.file.indexes;

import java.nio.file.Path;

/**
 * Strategy for resolving the index root path.
 * @author flameyosflow
 * @version 6.0.0
 */
@FunctionalInterface
public interface IndexPathStrategy {
    Path resolveIndexRoot(Path basePath, Class<?> entityType);
}
