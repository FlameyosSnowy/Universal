import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "mongo_annotated_parent")
public class MongoAnnotatedParent {
    @Id
    @AutoIncrement
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
