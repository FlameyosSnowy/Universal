package testapp;

import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DbOrderCollectionTest {

    @Test
    void listOfMaps_shouldRoundTrip() {
        PostgreSQLRepositoryAdapter<DbOrder, UUID> adapter = PostgreSQLRepositoryAdapter
            .builder(DbOrder.class, UUID.class)
            .withCredentials(new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test"))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS orders;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        // List of Maps - each map is an order item
        List<Map<String, String>> orderItems = new ArrayList<>();

        Map<String, String> item1 = new HashMap<>();
        item1.put("productId", "prod-001");
        item1.put("name", "Widget");
        item1.put("quantity", "5");
        item1.put("price", "10.99");
        orderItems.add(item1);

        Map<String, String> item2 = new HashMap<>();
        item2.put("productId", "prod-002");
        item2.put("name", "Gadget");
        item2.put("quantity", "3");
        item2.put("price", "25.50");
        orderItems.add(item2);

        Map<String, String> item3 = new HashMap<>();
        item3.put("productId", "prod-003");
        item3.put("name", "Tool");
        item3.put("quantity", "1");
        item3.put("price", "99.99");
        orderItems.add(item3);

        DbOrder inserted = new DbOrder(id, "ORD-2024-001", orderItems, null);
        adapter.insert(inserted);

        List<DbOrder> orders = adapter.find();
        assertEquals(1, orders.size(), "Should have 1 order");

        DbOrder found = orders.get(0);
        assertNotNull(found.getOrderItems(), "orderItems should NOT be null");
        assertEquals(orderItems, found.getOrderItems(), "orderItems should match");

        System.out.println("SUCCESS: List of Maps = " + found.getOrderItems());
    }

    @Test
    void setOfMaps_shouldRoundTrip() {
        PostgreSQLRepositoryAdapter<DbOrder, UUID> adapter = PostgreSQLRepositoryAdapter
            .builder(DbOrder.class, UUID.class)
            .withCredentials(new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test"))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS orders;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        // Set of Maps - unique configurations
        Set<Map<String, Integer>> itemConfigs = new HashSet<>();

        Map<String, Integer> config1 = new HashMap<>();
        config1.put("width", 100);
        config1.put("height", 200);
        config1.put("depth", 50);
        itemConfigs.add(config1);

        Map<String, Integer> config2 = new HashMap<>();
        config2.put("width", 150);
        config2.put("height", 300);
        config2.put("depth", 75);
        itemConfigs.add(config2);

        DbOrder inserted = new DbOrder(id, "ORD-2024-002", null, itemConfigs);
        adapter.insert(inserted);

        List<DbOrder> orders = adapter.find();
        assertEquals(1, orders.size(), "Should have 1 order");

        DbOrder found = orders.get(0);
        assertNotNull(found.getItemConfigurations(), "itemConfigurations should NOT be null");
        assertEquals(itemConfigs, found.getItemConfigurations(), "itemConfigurations should match");

        System.out.println("SUCCESS: Set of Maps = " + found.getItemConfigurations());
    }

    @Test
    void bothNestedCollections_shouldRoundTrip() {
        PostgreSQLRepositoryAdapter<DbOrder, UUID> adapter = PostgreSQLRepositoryAdapter
            .builder(DbOrder.class, UUID.class)
            .withCredentials(new PostgreSQLCredentials("localhost", 5432, "test", "postgres", "test"))
            .build();

        adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS orders;");
        adapter.createRepository(true);
        adapter.clear();

        UUID id = UUID.randomUUID();

        List<Map<String, String>> orderItems = new ArrayList<>();
        Map<String, String> item = new HashMap<>();
        item.put("sku", "ABC-123");
        item.put("description", "Premium Package");
        orderItems.add(item);

        Set<Map<String, Integer>> configs = new HashSet<>();
        Map<String, Integer> config = new HashMap<>();
        config.put("priority", 1);
        config.put("shipping", 2);
        configs.add(config);

        DbOrder inserted = new DbOrder(id, "ORD-2024-003", orderItems, configs);
        adapter.insert(inserted);

        List<DbOrder> orders = adapter.find();
        assertEquals(1, orders.size(), "Should have 1 order");

        DbOrder found = orders.get(0);
        assertNotNull(found.getOrderItems(), "orderItems should NOT be null");
        assertNotNull(found.getItemConfigurations(), "itemConfigurations should NOT be null");

        assertEquals(orderItems, found.getOrderItems(), "orderItems should match");
        assertEquals(configs, found.getItemConfigurations(), "itemConfigurations should match");

        System.out.println("SUCCESS: both nested collections round-tripped");
        System.out.println("orderItems = " + found.getOrderItems());
        System.out.println("itemConfigurations = " + found.getItemConfigurations());
    }
}
