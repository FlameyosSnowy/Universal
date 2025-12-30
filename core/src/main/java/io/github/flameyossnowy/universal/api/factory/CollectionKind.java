package io.github.flameyossnowy.universal.api.factory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public enum CollectionKind {
    LIST(ArrayList::new),
    SET(HashSet::new),
    QUEUE(ArrayDeque::new),
    DEQUE(ArrayDeque::new),
    OTHER(ArrayList::new); // fallback

    private final CollectionFactory factory;

    CollectionKind(CollectionFactory factory) {
        this.factory = factory;
    }

    public <T> Collection<T> create(int size) {
        return factory.create(size);
    }

    public interface CollectionFactory {
        <T> Collection<T> create(int size);
    }
}
