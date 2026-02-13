import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.github.flameyossnowy.universal.api.options.FilterOption;
import io.github.flameyossnowy.universal.api.options.JsonSelectOption;
import io.github.flameyossnowy.universal.api.options.SelectOption;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MongoRepositoryAdapterTest {

    static MongoClient mongoClient;
    static MongoDatabase mongoDatabase;
    static MongoCollection<Document> userCollection;
    static MongoCollection<Document> jsonCollection;
    static MongoCollection<Document> teamCollection;
    static MongoCollection<Document> playerCollection;
    static MongoCollection<Document> factionCollection;
    static MongoCollection<Document> warpCollection;

    MongoRepositoryAdapter<Main.User, UUID> adapter;
    MongoRepositoryAdapter<MongoJsonEntity, String> jsonAdapter;
    MongoRepositoryAdapter<Main.TeamRel, UUID> teamAdapter;
    MongoRepositoryAdapter<Main.PlayerRel, UUID> playerAdapter;
    MongoRepositoryAdapter<Main.FactionRel, UUID> factionAdapter;
    MongoRepositoryAdapter<Main.WarpRel, UUID> warpAdapter;

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
        jsonCollection = mongoDatabase.getCollection("mongo-json-entity");
        teamCollection = mongoDatabase.getCollection("teams_rel");
        playerCollection = mongoDatabase.getCollection("players_rel");
        factionCollection = mongoDatabase.getCollection("factions_rel");
        warpCollection = mongoDatabase.getCollection("warps_rel");
    }

    @AfterAll
    static void closeMongo() {
        mongoClient.close();
    }

    @BeforeEach
    void setup() {
        userCollection.deleteMany(new Document());
        jsonCollection.deleteMany(new Document());
        teamCollection.deleteMany(new Document());
        playerCollection.deleteMany(new Document());
        factionCollection.deleteMany(new Document());
        warpCollection.deleteMany(new Document());

        adapter = MongoRepositoryAdapter
            .builder(Main.User.class, UUID.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();

        jsonAdapter = MongoRepositoryAdapter
            .builder(MongoJsonEntity.class, String.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();

        teamAdapter = MongoRepositoryAdapter
            .builder(Main.TeamRel.class, UUID.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();

        playerAdapter = MongoRepositoryAdapter
            .builder(Main.PlayerRel.class, UUID.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();

        factionAdapter = MongoRepositoryAdapter
            .builder(Main.FactionRel.class, UUID.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();

        warpAdapter = MongoRepositoryAdapter
            .builder(Main.WarpRel.class, UUID.class)
            .withClient(mongoClient)
            .setDatabase("users")
            .build();
    }

    @Test
    void insert_new_user_inserts_document() {
        Main.User user = new Main.User(
            UUID.randomUUID(),
            "Flow",
            21,
            Instant.now()
        );

        adapter.insert(user)
            .ifError(Throwable::printStackTrace);

        Document found = userCollection
            .find(new Document("_id", user.getId()))
            .first();

        assertNotNull(found);
    }

    @Test
    void insert_existing_user_does_not_insert() {
        UUID id = UUID.randomUUID();

        userCollection.insertOne(
            new Document("_id", id)
        );

        Main.User user = new Main.User(
            id,
            "Flow",
            21,
            Instant.now()
        );

        adapter.insert(user);

        long count = userCollection.countDocuments(
            new Document("_id", id)
        );

        assertEquals(1, count);
    }

    @Test
    void delete_existing_user_deletes_document() {
        UUID id = UUID.randomUUID();

        userCollection.insertOne(
            new Document("_id", id)
        );

        adapter.deleteById(id);

        Document found = userCollection
            .find(new Document("_id", id))
            .first();

        assertNull(found);
    }

    @Test
    void delete_non_existing_user_does_not_delete() {
        UUID id = UUID.randomUUID();

        adapter.deleteById(id);

        long count = userCollection.countDocuments(
            new Document("_id", id)
        );

        assertEquals(0, count);
    }

    @Test
    void find_by_id_queries_collection() {
        UUID id = UUID.randomUUID();

        userCollection.insertOne(
            new Document("_id", id)
                .append("username", "Flow")
                .append("age", 20)
                .append("password", Instant.now().toString())
        );

        Main.User user = adapter.findById(id);

        assertNotNull(user);
        assertEquals("Flow", user.getUsername());
    }

    @Test
    void json_select_option_builds_dotted_path_filter() {
        List<FilterOption> options = List.of(
            new JsonSelectOption("payload", "$.profile.name", "=", "Flow")
        );

        BsonDocument filter = adapter.createFilterBson(options);
        assertEquals(new BsonString("Flow"), filter.get("payload.profile.name"));
    }

    @Test
    void mixed_filters_build_and_clause() {
        List<FilterOption> options = List.of(
            new SelectOption("username", "=", "Flow"),
            new JsonSelectOption("payload", "$.age", ">=", 18)
        );

        BsonDocument filter = adapter.createFilterBson(options);
        assertTrue(filter.containsKey("$and"));
    }

    @Test
    void json_select_option_whole_object_uses_jsonCodec_and_bson_bridge() {
        List<FilterOption> options = List.of(
            new JsonSelectOption(
                "payload",
                "$.profile",
                "=",
                new MongoJsonEntity.Payload("Flow", 21)
            )
        );

        BsonDocument filter = jsonAdapter.createFilterBson(options);

        org.bson.BsonValue value = filter.get("payload.profile");
        assertNotNull(value);
        assertTrue(value.isDocument());

        BsonDocument doc = value.asDocument();
        assertEquals(new BsonString("Flow"), doc.get("n"));
        assertEquals(new BsonInt32(21), doc.get("a"));
    }

    @Test
    void one_to_many_relationship_is_populated_on_findById() {
        UUID teamId = UUID.randomUUID();
        Main.TeamRel team = new Main.TeamRel(teamId, "TeamA");
        teamAdapter.insert(team);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        playerAdapter.insert(new Main.PlayerRel(p1, "P1", team));
        playerAdapter.insert(new Main.PlayerRel(p2, "P2", team));

        Main.TeamRel loaded = teamAdapter.findById(teamId);
        assertNotNull(loaded);
        assertNotNull(loaded.getPlayers());
        assertEquals(2, loaded.getPlayers().size());
    }

    @Test
    void one_to_one_relationship_is_populated_on_findById() {
        UUID factionId = UUID.randomUUID();
        UUID warpId = UUID.randomUUID();

        Main.FactionRel faction = new Main.FactionRel(factionId, "FactionA");
        factionAdapter.insert(faction);

        warpAdapter.insert(new Main.WarpRel(warpId, "WarpA", faction));

        Main.FactionRel loaded = factionAdapter.findById(factionId);
        assertNotNull(loaded);
        assertNotNull(loaded.getWarp());
        assertEquals("WarpA", loaded.getWarp().getName());
    }
}