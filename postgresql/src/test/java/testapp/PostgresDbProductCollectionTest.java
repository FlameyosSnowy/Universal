package testapp;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PostgresDbProductCollectionTest {

    @Test
    void multimapWithListValues_shouldRoundTrip() {
        Logging.ENABLED = true;

        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(DbProduct.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS products;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        // Create multimap with List values
        Map<String, List<String>> categoryAttributes = new HashMap<>();
        categoryAttributes.put("colors", List.of("red", "blue", "green"));
        categoryAttributes.put("sizes", List.of("S", "M", "L", "XL"));
        categoryAttributes.put("materials", List.of("cotton", "polyester"));

        DbProduct inserted = new DbProduct(id, "T-Shirt", categoryAttributes, null);
        adapter.insert(inserted);

        List<DbProduct> products = adapter.find();
        assertEquals(1, products.size(), "Should have 1 product");

        DbProduct found = products.get(0);
        assertNotNull(found.getCategoryAttributes(), "categoryAttributes should NOT be null");
        assertEquals(categoryAttributes, found.getCategoryAttributes(), "categoryAttributes should match");

        System.out.println("SUCCESS: multimap with List values = " + found.getCategoryAttributes());
    }

    @Test
    void multimapWithSetValues_shouldRoundTrip() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(DbProduct.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS products;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        // Create multimap with Set values
        Map<String, Set<Integer>> tagScores = new HashMap<>();
        tagScores.put("quality", Set.of(5, 4, 3));
        tagScores.put("price", Set.of(3, 4, 5));
        tagScores.put("design", Set.of(5, 3, 4));

        DbProduct inserted = new DbProduct(id, "Laptop", null, tagScores);
        adapter.insert(inserted);

        List<DbProduct> products = adapter.find();
        assertEquals(1, products.size(), "Should have 1 product");

        DbProduct found = products.get(0);
        assertNotNull(found.getTagScores(), "tagScores should NOT be null");
        assertEquals(tagScores, found.getTagScores(), "tagScores should match");

        System.out.println("SUCCESS: multimap with Set values = " + found.getTagScores());
    }

    @Test
    void bothMultimapsCombined_shouldRoundTrip() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(DbProduct.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS products;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        Map<String, List<String>> categoryAttributes = new HashMap<>();
        categoryAttributes.put("features", List.of("waterproof", "durable", "lightweight"));
        categoryAttributes.put("warranty", List.of("2 years", "extended"));

        Map<String, Set<Integer>> tagScores = new HashMap<>();
        tagScores.put("performance", Set.of(9, 8, 6));
        tagScores.put("battery", Set.of(7, 8, 6));

        DbProduct inserted = new DbProduct(id, "Smartphone", categoryAttributes, tagScores);
        adapter.insert(inserted);

        List<DbProduct> products = adapter.find();
        assertEquals(1, products.size(), "Should have 1 product");

        DbProduct found = products.get(0);
        assertNotNull(found.getCategoryAttributes(), "categoryAttributes should NOT be null");
        assertNotNull(found.getTagScores(), "tagScores should NOT be null");

        assertEquals(categoryAttributes, found.getCategoryAttributes(), "categoryAttributes should match");
        assertEquals(tagScores, found.getTagScores(), "tagScores should match");

        System.out.println("SUCCESS: both multimaps round-tripped correctly");
        System.out.println("categoryAttributes = " + found.getCategoryAttributes());
        System.out.println("tagScores = " + found.getTagScores());
    }

    @Test
    void nestedCollectionInMap_shouldRoundTrip() {
        PostgreSQLCredentials credentials =
            new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test");

        var adapter = PostgreSQLRepositoryAdapter
            .builder(DbProduct.class, UUID.class)
            .withCredentials(credentials)
            .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS products;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        // Nested collections in map
        Map<String, List<String>> complexAttrs = new HashMap<>();
        complexAttrs.put("nested/list", List.of("a", "b", "c"));
        complexAttrs.put("another/key", List.of("value1", "value2"));

        DbProduct inserted = new DbProduct(id, "ComplexItem", complexAttrs, null);
        adapter.insert(inserted);

        List<DbProduct> products = adapter.find();
        assertEquals(1, products.size(), "Should have 1 product");

        DbProduct found = products.getFirst();
        assertNotNull(found.getCategoryAttributes(), "categoryAttributes should NOT be null");
        assertEquals(complexAttrs, found.getCategoryAttributes(), "nested collections should match");

        System.out.println("SUCCESS: nested collection in map = " + found.getCategoryAttributes());
    }
}
