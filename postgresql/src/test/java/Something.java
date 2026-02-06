import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.List;

@Repository(name = "Something")
public class Something {
    @Id
    @AutoIncrement
    private long id;

    private String name;

    @OneToMany(mappedBy = Faction.class, lazy = true)
    private List<Faction> faction;

    public Something(long id, String name, List<Faction> faction) {
        this.id = id;
        this.name = name;
        this.faction = faction;
    }

    public Something() {
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

    public List<Faction> getFaction() {
        return faction;
    }

    public void setFaction(List<Faction> faction) {
        this.faction = faction;
    }
}
