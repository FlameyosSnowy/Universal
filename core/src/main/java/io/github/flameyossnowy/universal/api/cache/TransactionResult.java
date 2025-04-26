package io.github.flameyossnowy.universal.api.cache;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

public final class TransactionResult<T> {
    private final T result;
    private final Throwable error;

    private TransactionResult(T result, Throwable error) {
        this.result = result;
        this.error = error;
    }

    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull TransactionResult<T> success(T value) {
        return new TransactionResult<>(value, null);
    }

    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull TransactionResult<T> failure(Throwable error) {
        return new TransactionResult<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public boolean isError() {
        return error != null;
    }

    @Contract(pure = true)
    public @NotNull Optional<T> getResult() {
        return Optional.ofNullable(result);
    }

    @Contract(pure = true)
    public @NotNull Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    public void ifSuccess(Runnable runnable) {
        if (isSuccess()) runnable.run();
    }

    public void ifError(Runnable runnable) {
        if (isError()) runnable.run();
    }

    public <E> TransactionResult<E> map(Function<T, E> function) {
        if (isSuccess()) return TransactionResult.success(function.apply(result));
        return TransactionResult.failure(error);
    }

    public <E> TransactionResult<E> flatMap(Function<T, TransactionResult<E>> function) {
        if (isSuccess()) return function.apply(result);
        return TransactionResult.failure(error);
    }

    public T orElseThrow() {
        if (isSuccess()) return result;
        throw new RuntimeException("Transaction failed", error);
    }
}
