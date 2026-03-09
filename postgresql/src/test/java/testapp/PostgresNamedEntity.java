package testapp;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Named;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "postgres_named_entity")
public class PostgresNamedEntity {
    @Id
    @AutoIncrement
    private Long id;

    @Named("custom_name")
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
