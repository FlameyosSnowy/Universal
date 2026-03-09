package testapp;

import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Now;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.time.Instant;

@Repository(name = "annotated_now_entity")
public class AnnotatedNowEntity {
    @Id
    @AutoIncrement
    private Long id;

    @Now
    private Instant updatedAt;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
