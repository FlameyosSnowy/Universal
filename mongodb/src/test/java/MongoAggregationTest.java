import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MongoAggregationTest {

    static MongoClient mongoClient;
    static MongoDatabase mongoDatabase;
    static MongoCollection<Document> userCollection;

    MongoRepositoryAdapter<Main.User, UUID> adapter;

    @BeforeAll
    static void initMongo() {
        Logging.ENABLED = true;

        mongoClient = MongoClients.create(MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(
                "mongodb+srv://flameyosflow:87654321@testingjava.vmol6.mongodb.net/?retryWrites=true&w=majority&appName=TestingJava"
            ))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .build());

        mongoDatabase = mongoClient.getDatabase("users");
        userCollection = mongoDatabase.getCollection("users_old");
    }

    @AfterAll
    static void closeMongo() {
        mongoClient.close();
    }

    @BeforeEach
    void setup() {
        userCollection.deleteMany(new Document());

        adapter = MongoRepositoryAdapter
            .builder(Main.User.class, UUID.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();

        adapter.createRepository(true);

        Instant now = Instant.now();
        adapter.insert(new Main.User(UUID.randomUUID(), "Alice", 20, now));
        adapter.insert(new Main.User(UUID.randomUUID(), "Alice", 21, now));
        adapter.insert(new Main.User(UUID.randomUUID(), "Bob", 22, now));
        adapter.insert(new Main.User(UUID.randomUUID(), "Charlie", 23, now));
        adapter.insert(new Main.User(UUID.randomUUID(), "Charlie", 24, now));
        adapter.insert(new Main.User(UUID.randomUUID(), "Charlie", 25, now));
    }

    @Test
    void groupBy_count_having_order_limit() {
        var builder = Query.aggregate()
            .select(
                Query.field("username"),
                Query.field("id").count().as("cnt")
            )
            .groupBy("username");

        builder.having().field("id").count().gt(1);

        var query = builder
            .orderBy("cnt", SortOrder.DESCENDING)
            .limit(2)
            .build();

        List<Map<String, Object>> rows = adapter.aggregate(query);

        assertEquals(2, rows.size());
        assertEquals("Charlie", rows.getFirst().get("username"));
        assertEquals(3L, ((Number) rows.getFirst().get("cnt")).longValue());

        assertEquals("Alice", rows.get(1).get("username"));
        assertEquals(2L, ((Number) rows.get(1).get("cnt")).longValue());
    }

    @Test
    void conditional_countIf_without_groupBy() {
        var query = Query.aggregate()
            .select(
                Query.field("username").countIf(Query.eq("Alice")).as("alice_count"),
                Query.field("username").countIf(Query.eq("Charlie")).as("charlie_count")
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
