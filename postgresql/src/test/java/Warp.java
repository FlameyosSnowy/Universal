import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "Warps")
public class Warp {
    @Id
    @AutoIncrement
    private long id;

    private String name;

    @OneToOne
    private Faction faction;

    public Warp(long id, String name, Faction faction) {
        this.id = id;
        this.name = name;
        this.faction = faction;
    }

    public Warp() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Faction getFaction() {
        return faction;
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
    }
}
