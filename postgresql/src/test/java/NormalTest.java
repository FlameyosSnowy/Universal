import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
}
