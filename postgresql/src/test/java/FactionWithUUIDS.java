import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.UUID;

@Repository(name = "factionswithuuids")
public class FactionWithUUIDS {
    @Id
    private UUID id;

    private String name;

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

    @Override
    public String toString() {
        return "FactionWithUUIDS{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
