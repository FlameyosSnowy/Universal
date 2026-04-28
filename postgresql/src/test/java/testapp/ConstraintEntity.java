package testapp;

import io.github.flameyossnowy.universal.api.annotations.*;

@Repository(name = "constraint_entity")
public class ConstraintEntity {

    @Id
    @AutoIncrement
    private Long id;

    @NonNull
    @Unique
    private String name;

    public ConstraintEntity() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}