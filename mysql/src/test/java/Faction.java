import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.*;

@Cacheable
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
                ", warp='" + warp + "'" +
                '}';
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

    public Warp getWarp() {
        return warp;
    }

    public void setWarp(Warp warp) {
        this.warp = warp;
    }
}