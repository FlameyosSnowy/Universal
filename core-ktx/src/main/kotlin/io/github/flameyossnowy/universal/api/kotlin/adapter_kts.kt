package io.github.flameyossnowy.universal.api.kotlin

import io.github.flameyossnowy.universal.api.IndexOptions
import io.github.flameyossnowy.universal.api.RepositoryAdapter
import io.github.flameyossnowy.universal.api.cache.DatabaseSession
import io.github.flameyossnowy.universal.api.cache.TransactionResult
import io.github.flameyossnowy.universal.api.connection.TransactionContext
import io.github.flameyossnowy.universal.api.operation.Operation
import io.github.flameyossnowy.universal.api.options.DeleteQuery
import io.github.flameyossnowy.universal.api.options.SelectQuery
import io.github.flameyossnowy.universal.api.options.UpdateQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.optionals.getOrNull

/**
 * Wraps a RepositoryAdapter to provide coroutine-friendly suspend functions.
 */
suspend fun <T, ID, C> RepositoryAdapter<T, ID, C>.transaction(
    block: suspend RepositoryAdapter<T, ID, C>.() -> Unit
) {
    val tx = beginTransaction()
    try {
        this.block()
        tx.commit()
    } catch (ex: Exception) {
        tx.rollback()
        throw ex
    }
}

fun <T, ID, C> RepositoryAdapter<T, ID, C>.asCoroutine() = object {
    suspend fun find(query: SelectQuery): List<T> =
        withContext(Dispatchers.IO) { this@asCoroutine.find(query) }

    suspend fun find(): List<T> =
        withContext(Dispatchers.IO) { this@asCoroutine.find() }

    suspend fun first(query: SelectQuery? = null): T? =
        withContext(Dispatchers.IO) { this@asCoroutine.first(query) }

    suspend fun findById(id: ID): T? =
        withContext(Dispatchers.IO) { this@asCoroutine.findById(id) }

    suspend fun findAllById(ids: Collection<ID>): Map<ID, T> =
        withContext(Dispatchers.IO) { this@asCoroutine.findAllById(ids) }

    suspend fun findIds(query: SelectQuery): List<ID> =
        withContext(Dispatchers.IO) { this@asCoroutine.findIds(query) }

    suspend fun insert(entity: T): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.insert(entity) }

    suspend fun insertAll(entities: Collection<T>): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.insertAll(entities) }

    suspend fun updateAll(entity: T): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.updateAll(entity) }

    suspend fun updateAll(query: UpdateQuery): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.updateAll(query) }

    suspend fun delete(entity: T): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.delete(entity) }

    suspend fun deleteById(id: ID): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.deleteById(id) }

    suspend fun delete(query: DeleteQuery): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.delete(query) }

    suspend fun clear(): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.clear() }

    suspend fun createIndex(index: IndexOptions): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.createIndex(index) }

    suspend fun createIndexes(vararg indexes: IndexOptions): TransactionResult<Boolean> =
        withContext(Dispatchers.IO) { this@asCoroutine.createIndexes(*indexes) }

    suspend fun <R> execute(operation: Operation<T, ID, R, C>): TransactionResult<R> =
        withContext(Dispatchers.IO) { this@asCoroutine.execute(operation) }

    suspend fun <R> execute(operation: Operation<T, ID, R, C>, tx: TransactionContext<C>): TransactionResult<R> =
        withContext(Dispatchers.IO) { this@asCoroutine.execute(operation, tx) }
}

suspend inline fun <ID, T, C, R> DatabaseSession<ID, T, C>.runSuspend(
    crossinline block: suspend DatabaseSession<ID, T, C>.() -> R
): TransactionResult<R> = withContext(Dispatchers.IO) {
    try {
        val result = block()
        val commit = commit()
        if (commit.isError) {
            rollback()
            TransactionResult.failure(commit.error.get())
        } else {
            TransactionResult.success(result)
        }
    } catch (t: Throwable) {
        rollback()
        TransactionResult.failure(t)
    }
}

fun <T> TransactionResult<T>.getOrNull(): T? =
    result.orElse(null)

inline fun <T> TransactionResult<T>.onSuccess(block: (T) -> Unit): TransactionResult<T> {
    if (isSuccess) block(result.get());
    return this;
}

inline fun <T> TransactionResult<T>.onFailure(block: (Throwable) -> Unit): TransactionResult<T> {
    if (isError) block(error.get());
    return this;
}


fun <T> TransactionResult<T>.toResult(): Result<T> = if (isSuccess) Result.success(result.get()) else Result.failure(error.get())
