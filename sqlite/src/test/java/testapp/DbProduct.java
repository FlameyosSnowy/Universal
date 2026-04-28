package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.*;

@SuppressWarnings("unused")
@Repository(name = "db_products")
public class DbProduct {
    @Id
    private UUID id;

    private String name;

    // Multimap: key -> List of values
    private Map<String, List<String>> categoryAttributes;

    // Multimap: key -> Set of values
    private Map<String, Set<Integer>> tagScores;

    public DbProduct(UUID id, String name,
                     Map<String, List<String>> categoryAttributes,
                     Map<String, Set<Integer>> tagScores) {
        this.id = id;
        this.name = name;
        this.categoryAttributes = categoryAttributes;
        this.tagScores = tagScores;
    }

    public DbProduct() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, List<String>> getCategoryAttributes() {
        return categoryAttributes;
    }

    public void setCategoryAttributes(Map<String, List<String>> categoryAttributes) {
        this.categoryAttributes = categoryAttributes;
    }

    public Map<String, Set<Integer>> getTagScores() {
        return tagScores;
    }

    public void setTagScores(Map<String, Set<Integer>> tagScores) {
        this.tagScores = tagScores;
    }

    @Override
    public String toString() {
        return "DbProduct{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", categoryAttributes=" + categoryAttributes +
            ", tagScores=" + tagScores +
            '}';
    }
}
