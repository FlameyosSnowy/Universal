import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.List;

@Repository(name = "factions")
public class Faction {
    @Id
    @AutoIncrement
    public int id;

    public String name;

    @OneToMany(mappedBy = Warp.class)
    public List<Warp> warps;

    @OneToMany(mappedBy = User.class)
    public List<User> users;

    public Faction() {
    }

    public Faction(String name) {
        this.name = name;
    }

    public Faction(int id, String name) {
        this.name = name;
        this.id = id;
    }

    public String toString() {
        return "Faction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", warps=" + warps +
                ", users=" + users +
                '}';
    }
}