import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flameyossnowy.universal.api.annotations.builder.EndpointConfig;
import io.github.flameyossnowy.universal.api.annotations.enums.AuthType;
import io.github.flameyossnowy.universal.api.annotations.enums.NetworkProtocol;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.microservices.network.NetworkRepositoryAdapter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NetworkRepositoryAdapterTest {

    static MockWebServer server;
    NetworkRepositoryAdapter<TestEntity, String> adapter;

    @BeforeAll
    static void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.shutdown();
    }

    @BeforeEach
    void setup() {
        String baseUrl = server.url("/api").toString();

        adapter = new NetworkRepositoryAdapter<>(
                TestEntity.class,
                String.class,
                baseUrl,
                NetworkProtocol.REST,
                AuthType.NONE,
                null,
                1000,
                1000,
                1,
                true,
                5,
                Map.of(),
                EndpointConfig.defaults(),
                new ObjectMapper(),
                null
        );
    }

    @Test
    void findByIdMakesHttpCall() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"id":"1","name":"Alice"}
                        """)
        );

        TestEntity entity = adapter.findById("1");

        assertEquals("Alice", entity.getName());
    }

    @Test
    void findUsesCacheForGetRequests() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"id":"1","name":"Cached"}
                        """)
        );

        TestEntity first = adapter.findById("1");
        TestEntity second = adapter.findById("1");

        assertEquals(first.getName(), second.getName());
        assertEquals(2, server.getRequestCount(), "Second call should hit cache");
    }

    @Test
    void insertClearsCache() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                {"id":"1","name":"A"}
            """));

        TestEntity first = adapter.findById("1");
        assertEquals("A", first.getName());

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                {"id":"1","name":"B"}
            """));

        adapter.insert(new TestEntity("1", "B"));

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                {"id":"1","name":"B"}
            """));

        TestEntity updated = adapter.findById("1");

        assertEquals("B", updated.getName());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void deleteByIdSendsDeleteRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        TransactionResult<Boolean> result = adapter.deleteById("1");

        assertTrue(result.isSuccess());
        assertEquals("DELETE", server.takeRequest().getMethod());
    }

    @Test
    void jsonField_is_encoded_on_write_and_decoded_on_read() throws Exception {
        NetworkRepositoryAdapter<JsonTestEntity, String> jsonAdapter = new NetworkRepositoryAdapter<>(
            JsonTestEntity.class,
            String.class,
            server.url("/api").toString(),
            NetworkProtocol.REST,
            AuthType.NONE,
            null,
            1000,
            1000,
            1,
            false,
            0,
            Map.of(),
            EndpointConfig.defaults(),
            new ObjectMapper(),
            null
        );

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("""
                {"id":"1","payload":{"n":"Alice","a":21}}
            """));

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("""
                {"id":"1","payload":{"n":"Alice","a":21}}
            """));

        jsonAdapter.insert(new JsonTestEntity("1", new JsonTestEntity.Payload("Alice", 21)));
        var recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(recorded);
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"payload\""));
        assertTrue(body.contains("\"n\""));
        assertTrue(body.contains("\"a\""));

        JsonTestEntity loaded = jsonAdapter.findById("1");
        assertNotNull(loaded);
        assertNotNull(loaded.getPayload());
        assertEquals("Alice", loaded.getPayload().getName());
        assertEquals(21, loaded.getPayload().getAge());
    }
}
