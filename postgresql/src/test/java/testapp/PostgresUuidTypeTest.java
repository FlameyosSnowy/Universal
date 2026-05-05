package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostgreSQL native UUID type support.
 * Verifies that UUID fields are stored using PostgreSQL's native {@code uuid} type
 * instead of VARCHAR(36).
 */
@DisplayName("PostgreSQL Native UUID Type Tests")
@EnabledIf("canConnectToPostgreSQL")
class PostgresUuidTypeTest {

    private static boolean canConnectToPostgreSQL() {
        try {
            PostgreSQLCredentials credentials = new PostgreSQLCredentials(
                "localhost", 5432, "test", "postgres", "test"
            );
            PostgreSQLRepositoryAdapter<TestUuidEntity, UUID> adapter =
                PostgreSQLRepositoryAdapter.builder(TestUuidEntity.class, UUID.class)
                    .withCredentials(credentials)
                    .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                    .build();
            adapter.getQueryExecutor().executeRawQuery("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private PostgreSQLRepositoryAdapter<TestUuidEntity, UUID> adapter;

    @BeforeEach
    void setUp() {
        PostgreSQLCredentials credentials = new PostgreSQLCredentials(
            "localhost", 5432, "test", "postgres", "test"
        );

        adapter = PostgreSQLRepositoryAdapter.builder(TestUuidEntity.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        // Clean up and create table
        adapter.getQueryExecutor().executeRawQuery(
            "DROP TABLE IF EXISTS test_uuid_entity CASCADE;"
        );
        adapter.createRepository(true);
    }

    @Test
    @DisplayName("Should use native PostgreSQL uuid type for UUID fields")
    void shouldUseNativeUuidType() {
        // Query the information_schema to verify column type
        String checkTypeQuery = """
            SELECT data_type
            FROM information_schema.columns
            WHERE table_name = 'test_uuid_entity'
            AND column_name = 'id';
            """;

        var result = adapter.getQueryExecutor()
            .executeQuery(checkTypeQuery, rs -> rs.getString("data_type"));

        assertFalse(result.isEmpty(), "Should be able to query column type");

        String actualType = result.get(0);
        assertEquals("uuid", actualType,
            "UUID column should use PostgreSQL native uuid type, but found: " + actualType);
    }

    @Test
    @DisplayName("Should not use VARCHAR(36) for UUID fields")
    void shouldNotUseVarcharForUuid() {
        String checkTypeQuery = """
            SELECT data_type, character_maximum_length
            FROM information_schema.columns
            WHERE table_name = 'test_uuid_entity'
            AND column_name = 'id';
            """;

        var result = adapter.getQueryExecutor()
            .executeQuery(checkTypeQuery, rs -> {
                String type = rs.getString("data_type");
                Integer length = (Integer) rs.getObject("character_maximum_length");
                return type + (length != null ? "(" + length + ")" : "");
            });

        assertFalse(result.isEmpty(), "Should be able to query column type");

        String actualType = result.get(0);
        assertFalse(actualType.contains("varchar") || actualType.contains("character"),
            "UUID column should NOT use VARCHAR type: " + actualType);
    }

    @Test
    @DisplayName("Should store and retrieve UUID correctly")
    void shouldStoreAndRetrieveUuid() {
        UUID testUuid = UUID.randomUUID();
        String testName = "Test Entity";

        TestUuidEntity entity = new TestUuidEntity();
        entity.setId(testUuid);
        entity.setName(testName);

        // Insert the entity
        var insertResult = adapter.insert(entity);
        assertTrue(insertResult.isSuccess(), "Should insert entity successfully");

        // Retrieve the entity
        TestUuidEntity retrieved = adapter.findById(testUuid);
        assertNotNull(retrieved, "Should retrieve the entity");
        assertEquals(testUuid, retrieved.getId(), "UUID should match");
        assertEquals(testName, retrieved.getName(), "Name should match");
    }

    @Test
    @DisplayName("Should support UUID as primary key with native type")
    void shouldSupportUuidAsPrimaryKey() {
        // Check if primary key constraint exists and uses correct type
        String checkPkQuery = """
            SELECT kcu.column_name, c.data_type
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            JOIN information_schema.columns c
                ON c.table_name = tc.table_name
                AND c.column_name = kcu.column_name
            WHERE tc.constraint_type = 'PRIMARY KEY'
            AND tc.table_name = 'test_uuid_entity';
            """;

        var result = adapter.getQueryExecutor()
            .executeQuery(checkPkQuery, rs -> {
                String column = rs.getString("column_name");
                String type = rs.getString("data_type");
                return column + ":" + type;
            });

        assertFalse(result.isEmpty(), "Should find primary key");

        String pkInfo = result.get(0);
        assertTrue(pkInfo.contains("id"), "Primary key should be on 'id' column");
        assertTrue(pkInfo.contains("uuid"), "Primary key should use uuid type: " + pkInfo);
    }

    @Test
    @DisplayName("Should support multiple UUID fields")
    void shouldSupportMultipleUuidFields() {
        // Create table with multiple UUID columns
        adapter.getQueryExecutor().executeRawQuery("""
            DROP TABLE IF EXISTS test_multiple_uuids CASCADE;
            CREATE TABLE test_multiple_uuids (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                parent_id UUID,
                reference_id UUID,
                name VARCHAR(255)
            );
            """);

        // Verify all UUID columns use native uuid type
        String checkTypesQuery = """
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = 'test_multiple_uuids'
            AND data_type = 'uuid';
            """;

        var result = adapter.getQueryExecutor()
            .executeQuery(checkTypesQuery, rs -> rs.getString("column_name"));

        assertTrue(result.size() >= 3, "Should have at least 3 UUID columns");
        assertTrue(result.contains("id"), "Should have id column as uuid");
        assertTrue(result.contains("parent_id"), "Should have parent_id column as uuid");
        assertTrue(result.contains("reference_id"), "Should have reference_id column as uuid");
    }

    @Test
    @DisplayName("Should compare UUID equality correctly in queries")
    void shouldCompareUuidEquality() {
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID uuid2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        TestUuidEntity entity1 = new TestUuidEntity();
        entity1.setId(uuid1);
        entity1.setName("Entity 1");

        TestUuidEntity entity2 = new TestUuidEntity();
        entity2.setId(uuid2);
        entity2.setName("Entity 2");

        adapter.insert(entity1);
        adapter.insert(entity2);

        // Query by UUID
        TestUuidEntity found = adapter.findById(uuid1);
        assertNotNull(found, "Should find entity by UUID");
        assertEquals("Entity 1", found.getName(), "Should find correct entity");
        assertEquals(uuid1, found.getId(), "UUID should match exactly");
    }

    /**
     * Test entity with UUID primary key
     */
    @Repository(name = "test_uuid_entity")
    public static class TestUuidEntity {

        @Id
        private UUID id;

        private String name;

        public TestUuidEntity() {}

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
