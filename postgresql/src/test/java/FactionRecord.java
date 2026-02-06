import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.Objects;
import java.util.UUID;

@Repository(name = "FactionsRecord")
public class FactionRecord {
    @Id
    private UUID id;

    private String name;

    public FactionRecord() {
    }

    public FactionRecord(UUID id, String name) {
        this.id = id;
        this.name = name;
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

    @Override
    public String toString() {
        return "FactionRecord{" +
            "id=" + id +
            ", name='" + name + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FactionRecord that = (FactionRecord) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(name);
        return result;
    }
}
