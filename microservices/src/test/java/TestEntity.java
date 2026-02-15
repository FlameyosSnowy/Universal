import io.github.flameyossnowy.universal.api.annotations.FileRepository;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.NetworkRepository;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;
import io.github.flameyossnowy.universal.api.annotations.enums.NetworkProtocol;

@Repository(name = "test-entity")
@FileRepository(
        path = "ignored-in-tests",
        format = FileFormat.JSON
)
@NetworkRepository(
        baseUrl = "http://localhost",
        protocol = NetworkProtocol.REST
)
public class TestEntity {
    @Id
    private String id;
    private String name;

    public TestEntity() {}

    public TestEntity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
