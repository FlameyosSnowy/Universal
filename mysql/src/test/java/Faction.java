import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.*;

@Repository(name = "factions")
public class Faction {
    @Id
    public UUID id;

    public String name;

    @OneToOne
    public Warp warp;

    public Faction() {}

    public Faction(String name, UUID id) {
        this.name = name;
        this.id = id;
    }

    public String toString() {
        return "Faction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}