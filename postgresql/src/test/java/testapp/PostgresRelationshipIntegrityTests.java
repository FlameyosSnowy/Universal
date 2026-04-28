package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresRelationshipIntegrityTests {

    @Test
    void relationships_should_be_fully_materialized_correctly() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var somethingAdapter = PostgreSQLRepositoryAdapter
            .builder(Something.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        var factionAdapter = PostgreSQLRepositoryAdapter
            .builder(Faction.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        factionAdapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS Factions CASCADE;");
        factionAdapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS Something CASCADE;");

        somethingAdapter.createRepository(true);
        factionAdapter.createRepository(true);

        Something s = new Something();
        s.setName("S1");

        Faction f = new Faction();
        f.setName("F1");

        s.setFaction(List.of(f));
        f.setSomething(s);

        somethingAdapter.insert(s);
        factionAdapter.insert(f);

        Faction loaded = factionAdapter.find().get(0);
        System.out.println(loaded);

        assertEquals("F1", loaded.getName());
        assertNotNull(loaded.getSomething());
        assertEquals("S1", loaded.getSomething().getName());
    }
}