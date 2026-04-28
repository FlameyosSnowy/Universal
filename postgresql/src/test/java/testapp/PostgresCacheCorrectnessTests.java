package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresCacheCorrectnessTests {

    @Test
    void cache_should_return_consistent_results() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(Faction.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        var somethingAdapter = PostgreSQLRepositoryAdapter
            .builder(Something.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS Factions CASCADE;");
        adapter.createRepository(true);

        Faction f = new Faction();
        f.setName("A");

        adapter.insert(f);

        List<Faction> first = adapter.find();
        List<Faction> second = adapter.find();

        assertEquals(first.size(), second.size());

        assertEquals(first.get(0).getId(), second.get(0).getId());
    }

    @Test
    void cache_should_invalidate_on_insert() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(Faction.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        var somethingAdapter = PostgreSQLRepositoryAdapter
            .builder(Something.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS Factions CASCADE;");
        adapter.createRepository(true);

        adapter.find(); // warm cache

        Faction f = new Faction();
        f.setName("B");
        adapter.insert(f);

        List<Faction> result = adapter.find();
        assertEquals(1, result.size());
    }
}