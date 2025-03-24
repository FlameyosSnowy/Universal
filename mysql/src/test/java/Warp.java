import io.github.flameyossnowy.universal.api.annotations.*;

@FetchPageSize(100)
@Repository(name = "warps")
public class Warp {/*
    @Id
    @AutoIncrement
    public int id;*/

    public String name;

    @ManyToOne(join = "faction")
    public Faction faction;

    public Warp() {}

    public Warp(String name, Faction faction) {
        this.name = name;
        this.faction = faction;
    }

    public String toString() {
        return "Warp{" +
                //"id=" + id +
                "name='" + name + '\'' +
                ", faction=" + (faction == null ? "None (error)" : String.valueOf(faction.id)) +
                '}';
    }
}