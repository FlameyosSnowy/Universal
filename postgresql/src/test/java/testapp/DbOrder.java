package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.*;

@SuppressWarnings("unused")
@Repository(name = "db_orders")
public class DbOrder {
    @Id
    private UUID id;

    private String orderNumber;

    // List of Maps - each map represents an order item with attributes
    private List<Map<String, String>> orderItems;

    // Set of Maps - unique item configurations
    private Set<Map<String, Integer>> itemConfigurations;

    public DbOrder(UUID id, String orderNumber,
                   List<Map<String, String>> orderItems,
                   Set<Map<String, Integer>> itemConfigurations) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.orderItems = orderItems;
        this.itemConfigurations = itemConfigurations;
    }

    public DbOrder() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public List<Map<String, String>> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<Map<String, String>> orderItems) {
        this.orderItems = orderItems;
    }

    public Set<Map<String, Integer>> getItemConfigurations() {
        return itemConfigurations;
    }

    public void setItemConfigurations(Set<Map<String, Integer>> itemConfigurations) {
        this.itemConfigurations = itemConfigurations;
    }

    @Override
    public String toString() {
        return "DbOrder{" +
            "id=" + id +
            ", orderNumber='" + orderNumber + '\'' +
            ", orderItems=" + orderItems +
            ", itemConfigurations=" + itemConfigurations +
            '}';
    }
}
