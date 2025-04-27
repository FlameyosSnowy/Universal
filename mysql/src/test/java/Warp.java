import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.List;
import java.util.UUID;

@FetchPageSize(100)
@Repository(name = "warps")
public class Warp {
    @Id
    public UUID id;

    public String name;

    @OneToOne
    public Faction faction;

    public Warp() {}

    public Warp(String test, UUID id) {
        this.name = test;
        this.id = id;
    }

    public String toString() {
        return "Warp{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", faction=" + (faction == null ? "None (error)" : String.valueOf(faction.id)) +
                '}';
    }

    public String factionToString() {
        return faction.toString();
    }
}