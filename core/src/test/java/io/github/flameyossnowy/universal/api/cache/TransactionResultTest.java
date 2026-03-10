package io.github.flameyossnowy.universal.api.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class TransactionResultTest {

    @Test
    void successHasResultAndNoError() {
        TransactionResult<String> r = TransactionResult.success("ok");

        assertTrue(r.isSuccess());
        assertFalse(r.isError());
        assertEquals("ok", r.getResult().orElseThrow());
        assertTrue(r.getError().isEmpty());
    }

    @Test
    void failureHasErrorAndNoResult() {
        RuntimeException err = new RuntimeException("boom");
        TransactionResult<String> r = TransactionResult.failure(err);

        assertFalse(r.isSuccess());
        assertTrue(r.isError());
        assertTrue(r.getResult().isEmpty());
        assertSame(err, r.getError().orElseThrow());
    }

    @Test
    void mapAndFlatMapPropagateError() {
        RuntimeException err = new RuntimeException("boom");
        TransactionResult<Integer> failed = TransactionResult.failure(err);

        TransactionResult<String> mapped = failed.map(Object::toString);
        assertTrue(mapped.isError());
        assertSame(err, mapped.getError().orElseThrow());

        TransactionResult<String> flatMapped = failed.flatMap(v -> TransactionResult.success("x" + v));
        assertTrue(flatMapped.isError());
        assertSame(err, flatMapped.getError().orElseThrow());
    }

    @Test
    void expectThrowsRepositoryExceptionOnFailure() {
        RuntimeException err = new RuntimeException("boom");
        TransactionResult<String> failed = TransactionResult.failure(err);

        RuntimeException thrown = assertThrows(RuntimeException.class, failed::expect);
        assertSame(err, thrown.getCause());
    }

    @Test
    void toFutureCompletesNormallyOrExceptionally() throws Exception {
        CompletableFuture<String> okFuture = TransactionResult.success("ok").toFuture();
        assertEquals("ok", okFuture.get());

        RuntimeException err = new RuntimeException("boom");
        TransactionResult<String> failure = TransactionResult.failure(err);
        CompletableFuture<String> badFuture = failure.toFuture();

        ExecutionException ex = assertThrows(ExecutionException.class, badFuture::get);
        assertSame(err, ex.getCause());
    }
}
