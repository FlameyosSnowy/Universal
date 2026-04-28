package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.exceptions.RepositoryException;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgresJsonAndNamedIntegrationTest {

    @Test
    void jsonField_round_trips_and_jsonPath_query_works_on_real_database() throws Exception {
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "root");

        PostgreSQLRepositoryAdapter<PostgresJsonEntity, String> adapter = PostgreSQLRepositoryAdapter
            .builder(PostgresJsonEntity.class, String.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS \"postgres-json-entity\" CASCADE;");
        adapter.createRepository(true);

        String id = UUID.randomUUID().toString();
        PostgresJsonEntity entity = new PostgresJsonEntity(id, new Payload("Flow", 21));
        adapter.insert(entity);

        List<PostgresJsonEntity> found = adapter.find(Query.select().where("id").eq(id).build());
        assertEquals(1, found.size());
        assertNotNull(found.getFirst().getPayload());
        assertEquals("Flow", found.getFirst().getPayload().getName());
        assertEquals(21, found.getFirst().getPayload().getAge());

        List<PostgresJsonEntity> jsonFound = adapter.find(
            Query.select().whereJson("payload", "$.n").eq("Flow").build()
        );
        assertEquals(1, jsonFound.size());
        assertEquals(id, jsonFound.getFirst().getId());

        String url = "jdbc:postgresql://" + credentials.getHost() + ":" + credentials.getPort() + "/" + credentials.getDatabase();
        try (Connection c = DriverManager.getConnection(url, credentials.getUsername(), credentials.getPassword());
             PreparedStatement ps = c.prepareStatement("SELECT payload FROM \"postgres-json-entity\" WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String raw = rs.getString(1);
                assertNotNull(raw);
                System.out.println(raw);
                assertTrue(raw.contains("\"n\": \"Flow\""));
                assertTrue(raw.contains("\"a\": 21"));
            }
        }
    }

    @Test
    void jsonVersioned_increments_version_and_enforces_optimistic_locking() throws Exception {
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "root");

        PostgreSQLRepositoryAdapter<PostgresJsonVersionedEntity, String> adapter = PostgreSQLRepositoryAdapter
            .builder(PostgresJsonVersionedEntity.class, String.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS \"postgres-json-versioned-entity\" CASCADE;");
        adapter.createRepository(true);

        String id = UUID.randomUUID().toString();
        PostgresJsonVersionedEntity e = new PostgresJsonVersionedEntity(
            id,
            new PostgresJsonVersionedEntity.Payload("v1"),
            null
        );

        adapter.insert(e);
        assertEquals(1, e.getPayloadVersion());

        e.setPayload(new PostgresJsonVersionedEntity.Payload("v2"));
        adapter.updateAll(e);
        assertEquals(2, e.getPayloadVersion());

        PostgresJsonVersionedEntity stale = new PostgresJsonVersionedEntity(
            id,
            new PostgresJsonVersionedEntity.Payload("stale"),
            1
        );
        assertThrows(IllegalStateException.class, () -> adapter.updateAll(stale).get());
    }

    @Test
    void named_annotation_changes_physical_column_name_in_postgres() throws Exception {
        PostgreSQLCredentials credentials = new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "root");

        PostgreSQLRepositoryAdapter<PostgresNamedEntity, Long> adapter = PostgreSQLRepositoryAdapter
            .builder(PostgresNamedEntity.class, Long.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS postgres_named_entity CASCADE;");
        adapter.createRepository(true);

        String url = "jdbc:postgresql://" + credentials.getHost() + ":" + credentials.getPort() + "/" + credentials.getDatabase();
        try (Connection c = DriverManager.getConnection(url, credentials.getUsername(), credentials.getPassword());
             PreparedStatement ps = c.prepareStatement(
                 """
                 SELECT column_name
                 FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'postgres_named_entity'
                 """
             )) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean hasCustomName = false;
                boolean hasDefaultName = false;
                while (rs.next()) {
                    String col = rs.getString(1);
                    if ("custom_name".equalsIgnoreCase(col)) hasCustomName = true;
                    if ("name".equalsIgnoreCase(col)) hasDefaultName = true;
                }
                assertTrue(hasCustomName, "Expected column 'custom_name' to exist due to @Named");
                assertFalse(hasDefaultName, "Did not expect column 'name' to exist when @Named is used");
            }
        }

        PostgresNamedEntity e = new PostgresNamedEntity();
        e.setName("abc");
        adapter.insert(e);
        assertNotNull(e.getId());

        try (Connection c = DriverManager.getConnection(url, credentials.getUsername(), credentials.getPassword());
             PreparedStatement ps = c.prepareStatement(
                 "SELECT custom_name FROM postgres_named_entity WHERE id = ?"
             )) {
            ps.setLong(1, e.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("abc", rs.getString(1));
            }
        }
    }
}
