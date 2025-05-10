package io.github.flameyossnowy.universal.api.cache;

import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TransactionResult<T> {
    private final T result;
    private final Throwable error;

    private TransactionResult(T result, Throwable error) {
        this.result = result;
        this.error = error;
    }

    /**
     * Returns a successful TransactionResult with the given value.
     *
     * @param value the value to be returned by the successful TransactionResult
     * @return a successful TransactionResult
     */
    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull TransactionResult<T> success(T value) {
        return new TransactionResult<>(value, null);
    }

    /**
     * Returns a failed TransactionResult with the given error.
     *
     * @param error the error to be returned by the failed TransactionResult
     * @return a failed TransactionResult
     */
    @Contract(value = "_ -> new", pure = true)
    public static <T> @NotNull TransactionResult<T> failure(Throwable error) {
        return new TransactionResult<>(null, error);
    }

    /**
     * Ignores the result of the transaction and returns nothing.
     * <p>
     * It will also swallow any exceptions thrown by the transaction.
     * <p></p>
     * Please do not use this method unless you know what you're doing.
     */
    public void swallow() {}

    /**
     * Checks if the transaction resulted in a success.
     * @return true if the transaction was successful, false otherwise
     */
    public boolean isSuccess() {
        return result != null;
    }

    /**
     * Checks if the transaction resulted in an error.
     *
     * @return true if the transaction resulted in an error, false otherwise
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Retrieves the result of the transaction if it was successful.
     *
     * @return an Optional containing the result if the transaction was successful,
     *         or an empty Optional if the transaction resulted in an error.
     */
    @Contract(pure = true)
    public @NotNull Optional<T> getResult() {
        return Optional.ofNullable(result);
    }

    /**
     * Retrieves the error of the transaction if it resulted in an error.
     *
     * @return an Optional containing the error if the transaction resulted in an error,
     *         or an empty Optional if the transaction was successful.
     */
    @Contract(pure = true)
    public @NotNull Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Runs the given consumer on the result of the transaction if it was successful.
     * Does nothing if the transaction resulted in an error.
     *
     * @param runnable the consumer to be run on the result
     */
    public void ifSuccess(Consumer<T> runnable) {
        if (isSuccess()) runnable.accept(result);
    }

    /**
     * Runs the given consumer on the error of the transaction if it resulted in an error.
     * Does nothing if the transaction was successful.
     *
     * @param runnable the consumer to be run on the error
     */
    public void ifError(Consumer<Throwable> runnable) {
        if (isError()) runnable.accept(error);
    }

    /**
     * Applies the given function to the result of the transaction if it was successful,
     * or returns a failed TransactionResult with the same error if the transaction
     * resulted in an error.
     *
     * @param function the function to apply to the result of the transaction
     * @return a new TransactionResult containing the result of applying the given
     *         function to the result of the transaction, or a failed TransactionResult
     *         with the same error if the transaction resulted in an error.
     */
    public <E> TransactionResult<E> map(Function<T, E> function) {
        if (isSuccess()) return TransactionResult.success(function.apply(result));
        return TransactionResult.failure(error);
    }

    /**
     * Maps the error of this transaction result to a new result value.
     * If the transaction resulted in an error, applies the given function to the
     * error to obtain a new result, and returns a successful TransactionResult
     * containing that result. If the transaction was successful, returns this
     * TransactionResult unchanged.
     *
     * @param function the function to apply to the error of the transaction to
     *                 obtain a new result
     * @return a new TransactionResult containing the result of applying the
     *         given function to the error of the transaction if the transaction
     *         resulted in an error, or this TransactionResult unchanged if the
     *         transaction was successful.
     */
    public TransactionResult<T> mapErrToResult(Function<Throwable, T> function) {
        if (isError()) return TransactionResult.success(function.apply(error));
        return this;
    }

    /**
     * Applies the given function to the result of the transaction if it was successful,
     * or returns a failed TransactionResult with the same error if the transaction
     * resulted in an error.
     *
     * @param function the function to apply to the result of the transaction
     * @return a new TransactionResult containing the result of applying the given
     *         function to the result of the transaction, or a failed TransactionResult
     *         with the same error if the transaction resulted in an error.
     */
    public <E> TransactionResult<E> flatMap(Function<T, TransactionResult<E>> function) {
        if (isSuccess()) return function.apply(result);
        return TransactionResult.failure(error);
    }

    /**
     * If this transaction result is successful, returns the given transaction result.
     * If this transaction result resulted in an error, returns this transaction result.
     *
     * @param other the transaction result to return if this transaction result was successful
     * @return the given transaction result if this transaction result was successful,
     *         or this transaction result if it resulted in an error.
     */
    public TransactionResult<T> and(TransactionResult<T> other) {
        if (isError()) return this;
        return other;
    }

    /**
     * Retrieves the result of the transaction if it was successful.
     * <p>
     * If the transaction was successful, this method returns the result.
     * If the transaction resulted in an error, this method throws the error.
     *
     * @return the result of the transaction if successful
     * @throws Throwable if the transaction resulted in an error
     */
    public T get() throws Throwable {
        if (isSuccess()) return result;
        throw error;
    }

    /**
     * Retrieves the result of the transaction if it was successful, or returns the given
     * value if the transaction resulted in an error.
     *
     * @param result the value to return if the transaction resulted in an error
     * @return the result of the transaction if it was successful, or the given value
     *         if the transaction resulted in an error
     */
    public T getOr(T result) {
        if (isSuccess()) return this.result;
        return result;
    }

    /**
     * If this transaction result was successful, returns this transaction result.
     * If this transaction result resulted in an error, returns the given transaction result.
     *
     * @param other the transaction result to return if this transaction result resulted in an error
     * @return this transaction result if it was successful, or the given transaction result
     *         if this transaction result resulted in an error.
     */
    public TransactionResult<T> or(TransactionResult<T> other) {
        if (isSuccess()) return this;
        return other;
    }

    /**
     * Retrieves the result of the transaction if it was successful, or throws a
     * RepositoryException wrapping the error if the transaction resulted in an error.
     *
     * @return the result of the transaction if successful
     * @throws RuntimeException if the transaction resulted in an error
     */
    public T expect() {
        if (isSuccess()) return result;
        throw new RepositoryException("Transaction failed", error);
    }

    /**
     * Retrieves the result of the transaction if it was successful, or throws a
     * RepositoryException with the given message wrapping the error if the transaction
     * resulted in an error.
     *
     * @param message the message to include in the RepositoryException
     * @return the result of the transaction if successful
     * @throws RepositoryException if the transaction resulted in an error
     */
    public T expect(String message) {
        if (isSuccess()) return result;
        throw new RepositoryException(message, error);
    }
}
