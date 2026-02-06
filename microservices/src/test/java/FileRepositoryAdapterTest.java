import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.microservices.file.FileRepositoryAdapter;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategies;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileRepositoryAdapterTest {

    @TempDir
    Path tempDir;

    FileRepositoryAdapter<TestEntity, String> adapter;

    @BeforeEach
    void setup() {
        adapter = new FileRepositoryAdapter<>(
                TestEntity.class,
                String.class,
                tempDir,
                FileFormat.JSON,
                false,
                CompressionType.GZIP,
                false,
                0,
            IndexPathStrategies.underBase());
        adapter.createRepository(true);
    }

    @Test
    void insertAndFindById() {
        TestEntity entity = new TestEntity("1", "Alice");

        adapter.insert(entity);

        TestEntity loaded = adapter.findById("1");
        assertNotNull(loaded);
        assertEquals("Alice", loaded.getName());
    }

    @Test
    void deleteRemovesEntity() {
        TestEntity entity = new TestEntity("2", "Bob");

        adapter.insert(entity);
        adapter.deleteById("2");

        assertNull(adapter.findById("2"));
    }

    @Test
    void findReturnsAllEntities() {
        adapter.insert(new TestEntity("1", "A"));
        adapter.insert(new TestEntity("2", "B"));

        List<TestEntity> all = adapter.find();

        System.out.println(all);
        assertEquals(2, all.size());
    }

    @Test
    void cacheIsUsedAfterFirstRead() {
        TestEntity entity = new TestEntity("cached", "CacheMe");
        adapter.insert(entity);

        TestEntity first = adapter.findById("cached");
        TestEntity second = adapter.findById("cached");

        assertSame(first, second, "Entity should be served from cache");
    }

    @Test
    void clearRemovesAllData() {
        adapter.insert(new TestEntity("1", "A"));
        adapter.insert(new TestEntity("2", "B"));

        adapter.clear();

        assertTrue(adapter.find().isEmpty());
    }

    @Test
    void jsonField_roundTrip_and_jsonSelectOption_filtering() {
        FileRepositoryAdapter<JsonTestEntity, String> jsonAdapter = new FileRepositoryAdapter<>(
            JsonTestEntity.class,
            String.class,
            tempDir,
            FileFormat.JSON,
            false,
            CompressionType.GZIP,
            false,
            0,
            IndexPathStrategies.underBase()
        );
        jsonAdapter.createRepository(true);

        JsonTestEntity entity = new JsonTestEntity(
            "1",
            new JsonTestEntity.Payload("Alice", 21)
        );
        jsonAdapter.insert(entity);

        JsonTestEntity loaded = jsonAdapter.findById("1");
        assertNotNull(loaded);
        assertNotNull(loaded.getPayload());
        assertEquals("Alice", loaded.getPayload().getName());
        assertEquals(21, loaded.getPayload().getAge());

        List<JsonTestEntity> filtered = jsonAdapter.find(Query.select()
            .whereJson("payload", "$.name").eq("Alice")
            .build());
        assertEquals(1, filtered.size());
        assertEquals("1", filtered.getFirst().getId());
    }
}
