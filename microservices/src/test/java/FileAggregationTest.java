import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.microservices.file.FileRepositoryAdapter;
import io.github.flameyossnowy.universal.microservices.file.indexes.IndexPathStrategies;
import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileAggregationTest {

    @TempDir
    Path tempDir;

    FileRepositoryAdapter<TestEntity, String> adapter;

    @BeforeEach
    void setup() {
        ModelsBootstrap.init();

        adapter = FileRepositoryAdapter.builder(TestEntity.class, String.class)
                .basePath(tempDir)
                .sharding(false)
                .format(FileFormat.JSON)
                .compressionType(CompressionType.GZIP)
                .compressed(false)
                .shardCount(0)
                .indexPathStrategy(IndexPathStrategies.underBase())
                .build();

        adapter.createRepository(true);

        adapter.insert(new TestEntity("1", "Alice"));
        adapter.insert(new TestEntity("2", "Alice"));
        adapter.insert(new TestEntity("3", "Bob"));
        adapter.insert(new TestEntity("4", "Charlie"));
        adapter.insert(new TestEntity("5", "Charlie"));
        adapter.insert(new TestEntity("6", "Charlie"));
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
            .orderBy("cnt", io.github.flameyossnowy.universal.api.options.SortOrder.DESCENDING)
            .limit(2)
            .build();

        List<Map<String, Object>> rows = adapter.aggregate(query);

        assertEquals(2, rows.size());

        // Charlie has 3, Alice has 2
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

        List<Map<String, Object>> rows = adapter.aggregate(query);
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

        Long total = adapter.aggregateScalar(query, "total", Long.class);
        assertEquals(6L, total);
    }
}
