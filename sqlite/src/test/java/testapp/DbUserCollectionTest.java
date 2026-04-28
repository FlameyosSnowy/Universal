package testapp;

import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sqlite.SQLiteRepositoryAdapter;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DbUserCollectionTest {

    @Test
    void listCollectionField_shouldRoundTrip() {
        Logging.ENABLED = true;
        Logging.DEEP = true;

        Path dbPath = Path.of("/home/flameyosflow/test.db");
        SQLiteRepositoryAdapter<DbUser, UUID> adapter = SQLiteRepositoryAdapter
            .builder(DbUser.class, UUID.class)
            .withCredentials(new SQLiteCredentials(dbPath.toString()))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS users;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();
        List<String> hobbies = List.of("Coding", "Sleeping");
        DbUser inserted = new DbUser(id, "Flameyos", 17, new Password("123456"), hobbies);
        adapter.insert(inserted);

        List<DbUser> users = adapter.find();
        assertEquals(1, users.size(), "Should have 1 user");

        DbUser found = users.get(0);
        assertEquals("Flameyos", found.getUsername());
        assertEquals(17, found.getAge());

        System.out.println(found);
        assertNotNull(found.getHobbies(), "hobbies should NOT be null");
        assertEquals(hobbies, found.getHobbies(), "hobbies should match");

        System.out.println("SUCCESS: hobbies = " + found.getHobbies());
    }

    @Test
    void setCollectionField_shouldRoundTrip() {
        Path dbPath = Path.of("/home/flameyosflow/test_set.db");
        SQLiteRepositoryAdapter<DbUser, UUID> adapter = SQLiteRepositoryAdapter
            .builder(DbUser.class, UUID.class)
            .withCredentials(new SQLiteCredentials(dbPath.toString()))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS users;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();
        Set<String> tags = Set.of("developer", "java", "database");
        DbUser inserted = new DbUser(id, "Flameyos", 17, new Password("123456"),
            null, tags, null);
        adapter.insert(inserted);

        List<DbUser> users = adapter.find();
        assertEquals(1, users.size(), "Should have 1 user");

        DbUser found = users.get(0);
        assertNotNull(found.getTags(), "tags should NOT be null");
        assertEquals(tags, found.getTags(), "tags should match");

        System.out.println("SUCCESS: tags = " + found.getTags());
    }

    @Test
    void mapField_shouldRoundTrip() {
        Path dbPath = Path.of("/home/flameyosflow/test_map.db");
        SQLiteRepositoryAdapter<DbUser, UUID> adapter = SQLiteRepositoryAdapter
            .builder(DbUser.class, UUID.class)
            .withCredentials(new SQLiteCredentials(dbPath.toString()))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS users;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("timezone", "UTC");
        metadata.put("theme", "dark");
        metadata.put("language", "en");

        DbUser inserted = new DbUser(id, "Flameyos", 17, new Password("123456"),
            null, null, metadata);
        adapter.insert(inserted);

        List<DbUser> users = adapter.find();
        assertEquals(1, users.size(), "Should have 1 user");

        DbUser found = users.get(0);
        assertNotNull(found.getMetadata(), "metadata should NOT be null");
        assertEquals(metadata, found.getMetadata(), "metadata should match");

        System.out.println("SUCCESS: metadata = " + found.getMetadata());
    }

    @Test
    void allCollectionsCombined_shouldRoundTrip() {
        Path dbPath = Path.of("/home/flameyosflow/test_all.db");
        SQLiteRepositoryAdapter<DbUser, UUID> adapter = SQLiteRepositoryAdapter
            .builder(DbUser.class, UUID.class)
            .withCredentials(new SQLiteCredentials(dbPath.toString()))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS users;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();
        List<String> hobbies = List.of("Coding", "Gaming", "Reading");
        Set<String> tags = Set.of("dev", "java", "backend");
        Map<String, String> metadata = Map.of(
            "created", "2024-01-01",
            "source", "test",
            "version", "1.0"
        );

        DbUser inserted = new DbUser(id, "Flameyos", 17, new Password("123456"),
            hobbies, tags, metadata);
        adapter.insert(inserted);

        List<DbUser> users = adapter.find();
        assertEquals(1, users.size(), "Should have 1 user");

        DbUser found = users.get(0);
        assertNotNull(found.getHobbies(), "hobbies should NOT be null");
        assertNotNull(found.getTags(), "tags should NOT be null");
        assertNotNull(found.getMetadata(), "metadata should NOT be null");

        assertEquals(hobbies, found.getHobbies(), "hobbies should match");
        assertEquals(tags, found.getTags(), "tags should match");
        assertEquals(metadata, found.getMetadata(), "metadata should match");

        System.out.println("SUCCESS: all collections round-tripped correctly");
        System.out.println("hobbies = " + found.getHobbies());
        System.out.println("tags = " + found.getTags());
        System.out.println("metadata = " + found.getMetadata());
    }
}