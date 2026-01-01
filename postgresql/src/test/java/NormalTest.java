import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class NormalTest {
    @Test
    public void postgresql_test() {
        Logging.ENABLED = true;

        // host, port, database, username, password
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "root");
        PostgreSQLRepositoryAdapter<Faction, Long> adapter = PostgreSQLRepositoryAdapter.builder(Faction.class, Long.class)
                .withCredentials(credentials)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        adapter.executeRawQuery("DROP TABLE IF EXISTS Factions;");

        adapter.createRepository(true)
                .expect("Should have been able to create repository.");

        Faction faction = new Faction();
        faction.setName("Test");
        adapter.insert(faction);

        System.out.println(faction);

        List<Faction> factions = adapter.find();

        System.out.println(factions);
        assertEquals(1, factions.size());
        assertEquals(faction, factions.get(0));
    }

    @Test
    public void postgresql_cache_test() {
        Logging.ENABLED = true;
        Logging.DEEP = true;

        // host, port, database, username, password
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");
        PostgreSQLRepositoryAdapter<Faction, Long> adapter = PostgreSQLRepositoryAdapter.builder(Faction.class, Long.class)
                .withCredentials(credentials)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        adapter.executeRawQuery("DROP TABLE IF EXISTS Factions;");

        adapter.createRepository(true)
                .expect("Should have been able to create repository.");

        Faction faction = new Faction();
        faction.setName("Test");

        Faction faction2 = new Faction();
        faction2.setName("Test2");

        Faction faction3 = new Faction();
        faction3.setName("Test3");

        Faction faction4 = new Faction();
        faction4.setName("Test4");

        Faction faction5 = new Faction();
        faction5.setName("Test5");
        adapter.insertAll(List.of(faction, faction2, faction3, faction4, faction5));

        List<Faction> factions = adapter.find();

        System.out.println(factions);
        assertEquals(5, factions.size());

        List<Faction> factions2 = adapter.find();

        System.out.println(factions2);
        assertEquals(5, factions2.size());
    }

    @Test
    public void postgresql_test_record() {
        Logging.ENABLED = true;

        // host, port, database, username, password
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");
        PostgreSQLRepositoryAdapter<FactionRecord, UUID> adapter = PostgreSQLRepositoryAdapter.builder(FactionRecord.class, UUID.class)
                .withCredentials(credentials)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        adapter.executeRawQuery("DROP TABLE IF EXISTS FactionsRecord;");

        adapter.createRepository(true)
                .expect("Should have been able to create repository.");

        FactionRecord faction = new FactionRecord(UUID.randomUUID(), "Test");
        adapter.insert(faction);

        System.out.println(faction);

        List<FactionRecord> factions = adapter.find();

        System.out.println(factions);
        assertEquals(1, factions.size());
        assertEquals(faction, factions.getFirst());
    }

    @Test
    public void postgresql_arrays_test() {
        Logging.ENABLED = true;

        // host, port, database, username, password
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");
        PostgreSQLRepositoryAdapter<FactionWArrays, Long> adapter = PostgreSQLRepositoryAdapter.builder(FactionWArrays.class, Long.class)
                .withCredentials(credentials)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        adapter.executeRawQuery("DROP TABLE IF EXISTS FactionsWArrays CASCADE;");

        adapter.createRepository(true)
               .expect("Should have been able to create repository.");

        FactionWArrays faction = new FactionWArrays();
        faction.setName("Test");
        faction.setMembers(new String[]{"a", "b", "c"});

        adapter.insert(faction);

        System.out.println(faction);

        List<FactionWArrays> factions = adapter.find();

        System.out.println(factions);
        assertEquals(1, factions.size());
        assertEquals(faction, factions.get(0));
    }

    @Test
    public void postgres_connection_leak_stress_test() throws Exception {
        Logging.ENABLED = false;

        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        PostgreSQLRepositoryAdapter<Faction, Long> adapter =
            PostgreSQLRepositoryAdapter.builder(Faction.class, Long.class)
                .withCredentials(credentials)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        // Baseline connections
        int baseline = countActiveConnections(credentials);

        int threads = 16;
        int iterationsPerThread = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        adapter.executeRawQuery(
                            "CREATE TABLE IF NOT EXISTS \"factions_tmp_" + UUID.randomUUID() + "\"" +
                                " (id BIGSERIAL PRIMARY KEY, name TEXT)"
                        );
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Give Hikari time to return connections
        Thread.sleep(2000);

        int after = countActiveConnections(credentials);

        System.out.println("Baseline connections: " + baseline);
        System.out.println("After stress test:   " + after);

        assertTrue(
            after <= baseline + 1,
            "Connection leak detected! Baseline=" + baseline + ", After=" + after
        );
    }

    private static int countActiveConnections(PostgreSQLCredentials credentials) throws Exception {
        String url = "jdbc:postgresql://" +
            credentials.getHost() + ":" +
            credentials.getPort() + "/" +
            credentials.getDatabase();

        try (Connection c = DriverManager.getConnection(
            url, credentials.getUsername(), credentials.getPassword());
             PreparedStatement ps = c.prepareStatement(
                 """
                 SELECT count(*)
                 FROM pg_stat_activity
                 WHERE datname = ?
                   AND pid <> pg_backend_pid()
                 """
             )) {

            ps.setString(1, credentials.getDatabase());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
