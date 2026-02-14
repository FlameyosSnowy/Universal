import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.Objects;

@Cacheable
@Repository(name = "Factions")
public class Faction {
    @Id
    @AutoIncrement
    private Long id;

    private String name;

    private Level level = Level.FINE;

    @ManyToOne
    private Something something;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "Faction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", level='" + level.name() + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Faction faction)) return false;
        return id.equals(faction.id) && name.equals(faction.name);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(name);
        return result;
    }

    public Something getSomething() {
        return something;
    }

    public void setSomething(Something something) {
        this.something = something;
    }
}