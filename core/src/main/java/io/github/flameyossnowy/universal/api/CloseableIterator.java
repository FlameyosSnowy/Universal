package io.github.flameyossnowy.universal.api;

import java.util.Iterator;

public interface CloseableIterator<T> extends Iterator<T>, AutoCloseable {
}
