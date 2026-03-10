import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.DefaultValue;
import io.github.flameyossnowy.universal.api.annotations.EnumAsOrdinal;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.Now;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.enums.OnModify;

import java.time.Instant;

@Repository(name = "mongo_annotated_entity")
public class MongoAnnotatedEntity {
    public enum Status {
        NEW,
        ACTIVE
    }

    @Id
    @AutoIncrement
    private Long id;

    @Now
    private Instant updatedAt;

    @DefaultValue("hello")
    private String greeting;

    @EnumAsOrdinal
    private Status status;

    @ManyToOne
    @OnDelete(OnModify.CASCADE)
    @OnUpdate(OnModify.RESTRICT)
    private MongoAnnotatedParent parent;

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

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public MongoAnnotatedParent getParent() {
        return parent;
    }

    public void setParent(MongoAnnotatedParent parent) {
        this.parent = parent;
    }
}
