import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoRepositoryAdapterTest {

    @Mock
    MongoClient mongoClient;

    @Mock
    MongoDatabase mongoDatabase;

    @Mock
    MongoCollection<Document> collection;

    MongoRepositoryAdapter<Main.User, UUID> adapter;

    @BeforeEach
    void setup() {
        when(mongoClient.getDatabase("users")).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("users_old")).thenReturn(collection);

        adapter = MongoRepositoryAdapter
                .builder(Main.User.class, UUID.class)
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

        when(collection.find(any(Document.class)).first())
            .thenReturn(null);

        adapter.insert(user);

        verify(collection).find(any(Document.class));
        verify(collection).insertOne(any(Document.class));
    }

    @Test
    void insert_existing_user_does_not_insert() {
        UUID id = UUID.randomUUID();

        Main.User user = new Main.User(
            id,
            "Flow",
            21,
            Instant.now()
        );

        Document existing = new Document("_id", id.toString());

        when(collection.find(any(Document.class)).first())
            .thenReturn(existing);

        adapter.insert(user);

        verify(collection).find(any(Document.class));
        verify(collection, never()).insertOne(any(Document.class));
    }

    @Test
    void delete_existing_user_deletes_document() {
        UUID id = UUID.randomUUID();

        Document existing = new Document("_id", id.toString());

        when(collection.find(any(Document.class)).first())
            .thenReturn(existing);

        adapter.deleteById(id);

        verify(collection).find(any(Document.class));
        verify(collection).deleteOne(any(Document.class));
    }

    @Test
    void delete_non_existing_user_does_not_delete() {
        UUID id = UUID.randomUUID();

        when(collection.find(any(Document.class)).first())
            .thenReturn(null);

        adapter.deleteById(id);

        verify(collection).find(any(Document.class));
        verify(collection, never()).deleteOne(any(Document.class));
    }

    @Test
    void find_by_id_queries_collection() {
        UUID id = UUID.randomUUID();

        Document doc = new Document()
                .append("_id", id.toString())
                .append("username", "Flow")
                .append("age", 20)
                .append("password", Instant.now().toString());

        when(collection.find(any(Document.class)).first())
                .thenReturn(doc);

        Main.User user = adapter.findById(id);

        verify(collection).find(any(Document.class));
    }
}
