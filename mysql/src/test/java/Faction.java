import io.github.flameyossnowy.universal.api.annotations.*;
import org.slf4j.event.Level;

import java.util.*;

@Repository(name = "factions")
public class Faction {
    @Id
    public UUID id;

    public String name;

    @OneToOne
    public Warp warp;

    public Map<Level, List<String>> banned = new HashMap<>(5);

    public Faction() {}

    public Faction(String name, UUID id) {
        this.name = name;
        this.id = id;
    }

    public String toString() {
        return "Faction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", warp=" + (warp == null ? "None (Error)" : warp) +
                ", banned=" + banned +
                '}';
    }
}