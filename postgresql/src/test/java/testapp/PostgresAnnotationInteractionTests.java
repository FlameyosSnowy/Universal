package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgresAnnotationInteractionTests {

    @Test
    void jsonfield_and_named_should_work_together() throws Exception {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(CombinedEntity.class, String.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS combined_entity CASCADE;");
        adapter.createRepository(true);

        String id = UUID.randomUUID().toString();
        CombinedEntity e = new CombinedEntity(id, new CombinedEntity.Payload("abc"));

        adapter.insert(e);

        String url = "jdbc:postgresql://" + credentials.getHost() + ":" +
            credentials.getPort() + "/" + credentials.getDatabase();

        try (Connection c = DriverManager.getConnection(url, credentials.getUsername(), credentials.getPassword());
             PreparedStatement ps = c.prepareStatement(
                 "SELECT custom_payload FROM combined_entity WHERE id = ?"
             )) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String json = rs.getString(1);

                assertNotNull(json);
                assertTrue(json.contains("abc"));
            }
        }
    }
}