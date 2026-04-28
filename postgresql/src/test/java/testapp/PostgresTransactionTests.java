package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresTransactionTests {

    @Test
    void insert_all_should_be_atomic() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(ConstraintEntity.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS constraint_entity CASCADE;");
        adapter.createRepository(true);

        ConstraintEntity good = new ConstraintEntity();
        good.setName("valid");

        ConstraintEntity bad = new ConstraintEntity();
        bad.setName(null); // violates @NonNull

        try {
            adapter.insertAll(List.of(good, bad)).get();
        } catch (Throwable ignored) {}

        // should rollback everything
        assertEquals(0, adapter.find().size());
    }
}