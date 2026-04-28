package testapp;

import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sqlite.SQLiteRepositoryAdapter;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserJsonFieldTest {

    @Test
    void jsonCollectionField_shouldRoundTrip() {
        Logging.ENABLED = true;
        Logging.DEEP = true;

        Path dbPath = Path.of("/home/flameyosflow/test.db");
        SQLiteRepositoryAdapter<User, UUID> adapter = SQLiteRepositoryAdapter
            .builder(User.class, UUID.class)
            .withCredentials(new SQLiteCredentials(dbPath.toString()))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS users;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();
        List<String> hobbies = List.of("Coding", "Sleeping");
        User inserted = new User(id, "Flameyos", 17, new Password("123456"), hobbies);
        adapter.insert(inserted);

        List<User> users = adapter.find();
        assertEquals(1, users.size(), "Should have 1 user");

        User found = users.get(0);
        assertEquals("Flameyos", found.getUsername());
        assertEquals(17, found.getAge());

        System.out.println(found);
        assertNotNull(found.getHobbies(), "hobbies should NOT be null");
        assertEquals(hobbies, found.getHobbies(), "hobbies should match");

        System.out.println("SUCCESS: hobbies = " + found.getHobbies());
    }
}