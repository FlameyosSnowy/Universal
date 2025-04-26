import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.List;
import java.util.UUID;

@Repository(name = "factions")
public class Faction {
    @Id
    public UUID id;

    public String name;

    @OneToOne
    public Warp warp;

    @OneToOne
    public transient String hi;

    public Faction() {}

    public Faction(String name, UUID id) {
        this.name = name;
        this.id = id;
    }

    public String toString() {
        return "Faction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", warp=" + (warp == null ? "None (Error)" : warp.id) +
                '}';
    }
}