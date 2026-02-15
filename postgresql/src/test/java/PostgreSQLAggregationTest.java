import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgreSQLAggregationTest {

    static PostgreSQLCredentials credentials;

    PostgreSQLRepositoryAdapter<Faction, Long> factionAdapter;
    PostgreSQLRepositoryAdapter<Something, Long> somethingAdapter;

    @BeforeAll
    static void beforeAll() {
        Logging.ENABLED = true;

        // Try the credentials used by existing tests.
        PostgreSQLCredentials c1 = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "root");
        PostgreSQLCredentials c2 = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        credentials = canConnect(c1) ? c1 : (canConnect(c2) ? c2 : null);
        assumeTrue(credentials != null, "PostgreSQL not available on localhost:5432/test for configured credentials");
    }

    @BeforeEach
    void setup() {
        factionAdapter = PostgreSQLRepositoryAdapter.builder(Faction.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        somethingAdapter = PostgreSQLRepositoryAdapter.builder(Something.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        // Drop in reverse order (child first, then parent)
        factionAdapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS \"Factions\" CASCADE;");
        factionAdapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS \"Something\" CASCADE;");

        somethingAdapter.createRepository(true)
            .expect("Should have been able to create repository.");
        factionAdapter.createRepository(true)
            .expect("Should have been able to create repository.");

        insertFaction("Alice");
        insertFaction("Alice");
        insertFaction("Bob");
        insertFaction("Charlie");
        insertFaction("Charlie");
        insertFaction("Charlie");
    }

    @Test
    void groupBy_count_having_order_limit() {
        var builder = Query.aggregate()
            .select(
                Query.field("name"),
                Query.field("id").count().as("cnt")
            )
            .groupBy("name");

        builder.having().field("id").count().gt(1);

        var query = builder
            .orderBy("cnt", SortOrder.DESCENDING)
            .limit(2)
            .build();

        List<Map<String, Object>> rows = factionAdapter.aggregate(query);

        assertEquals(2, rows.size());
        assertEquals("Charlie", rows.getFirst().get("name"));
        assertEquals(3L, ((Number) rows.getFirst().get("cnt")).longValue());

        assertEquals("Alice", rows.get(1).get("name"));
        assertEquals(2L, ((Number) rows.get(1).get("cnt")).longValue());
    }

    @Test
    void conditional_countIf_without_groupBy() {
        var query = Query.aggregate()
            .select(
                Query.field("name").countIf(Query.eq("Alice")).as("alice_count"),
                Query.field("name").countIf(Query.eq("Charlie")).as("charlie_count")
            )
            .build();

        List<Map<String, Object>> rows = factionAdapter.aggregate(query);
        assertEquals(1, rows.size());

        Map<String, Object> row = rows.getFirst();
        assertEquals(2L, ((Number) row.get("alice_count")).longValue());
        assertEquals(3L, ((Number) row.get("charlie_count")).longValue());
    }

    @Test
    void aggregateScalar_returns_first_row_field() {
        var query = Query.aggregate()
            .select(Query.field("id").count().as("total"))
            .build();

        Long total = factionAdapter.aggregateScalar(query, "total", Long.class);
        assertEquals(6L, total);
    }

    private void insertFaction(String name) {
        Faction f = new Faction();
        f.setName(name);
        factionAdapter.insert(f).ifError(Throwable::printStackTrace);
    }

    private static boolean canConnect(PostgreSQLCredentials c) {
        try (Connection ignored = DriverManager.getConnection(
            "jdbc:postgresql://" + c.getHost() + ":" + c.getPort() + "/" + c.getDatabase(),
            c.getUsername(),
            c.getPassword()
        )) {
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
