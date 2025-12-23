package io.github.flameyossnowy.universal.microservices.file.indexes;

import java.nio.file.Path;

@FunctionalInterface
public interface IndexPathStrategy {
    Path resolveIndexRoot(Path basePath, Class<?> entityType);
}
