import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostgreSQLOneToOneUuidTest {

    static PostgreSQLCredentials credentials;

    PostgreSQLRepositoryAdapter<OneToOneFactionUuid, UUID> factions;
    PostgreSQLRepositoryAdapter<OneToOneWarpUuid, UUID> warps;

    @BeforeAll
    static void beforeAll() {
        Logging.ENABLED = true;

        PostgreSQLCredentials c1 = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "root");
        PostgreSQLCredentials c2 = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        credentials = canConnect(c1) ? c1 : (canConnect(c2) ? c2 : null);
        assumeTrue(credentials != null, "PostgreSQL not available on localhost:5432/test for configured credentials");
    }

    @BeforeEach
    void setup() {
        warps = PostgreSQLRepositoryAdapter.builder(OneToOneWarpUuid.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .setAutoCreate(false)
            .build();

        factions = PostgreSQLRepositoryAdapter.builder(OneToOneFactionUuid.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .setAutoCreate(false)
            .build();

        // Drop in reverse order of foreign key dependencies
        factions.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS \"one_to_one_factions\" CASCADE;");
        warps.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS \"one_to_one_warps\" CASCADE;");

        // Create warps table FIRST (it's referenced by factions)
        // Create factions table FIRST (parent)
        factions.createRepository(true).expect("Should have been able to create repository.");
// Then create warps table (child referencing factions)
        warps.createRepository(true).expect("Should have been able to create repository.");
    }

    @AfterEach
    void tearDown() {
        if (factions != null) factions.close();
        if (warps != null) warps.close();
    }

    @Test
    void oneToOne_insert_and_findById_loads_both_directions() {
        OneToOneFactionUuid faction = new OneToOneFactionUuid();
        faction.setId(UUID.randomUUID());
        faction.setName("Faction-1");

        OneToOneWarpUuid warp = new OneToOneWarpUuid();
        warp.setId(UUID.randomUUID());
        warp.setName("Warp-1");

        warp.setFaction(faction);
        faction.setWarp(warp);

        factions.insert(faction).expect("Insert faction should succeed");
        warps.insert(warp).expect("Insert warp should succeed");

        OneToOneFactionUuid fetchedFaction = factions.findById(faction.getId());
        assertNotNull(fetchedFaction);
        assertNotNull(fetchedFaction.getWarp());
        assertEquals(warp.getId(), fetchedFaction.getWarp().getId());

        OneToOneWarpUuid fetchedWarp = warps.findById(warp.getId());
        assertNotNull(fetchedWarp);
        assertNotNull(fetchedWarp.getFaction());
        assertEquals(faction.getId(), fetchedWarp.getFaction().getId());
    }

    @Test
    void oneToOne_find_loads_1000_entities_with_relationships() {
        int count = 1000;
        List<OneToOneFactionUuid> factionList = new ArrayList<>(count);
        List<OneToOneWarpUuid> warpList = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            OneToOneFactionUuid faction = new OneToOneFactionUuid();
            faction.setId(UUID.randomUUID());
            faction.setName("Faction-" + i);

            OneToOneWarpUuid warp = new OneToOneWarpUuid();
            warp.setId(UUID.randomUUID());
            warp.setName("Warp-" + i);

            warp.setFaction(faction);
            faction.setWarp(warp);

            factionList.add(faction);
            warpList.add(warp);
        }

        factions.insertAll(factionList).expect("Insert factions should succeed");
        warps.insertAll(warpList).expect("Insert warps should succeed");

        List<OneToOneFactionUuid> factionsFetched = factions.find();
        assertEquals(count, factionsFetched.size());

        for (OneToOneFactionUuid f : factionsFetched) {
            assertNotNull(f.getWarp());
            assertNotNull(f.getWarp().getFaction());
            assertEquals(f.getId(), f.getWarp().getFaction().getId());
        }
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
