import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository(name = "players")
public class Player {
    @Id
    public UUID id;

    public String name;

    @ManyToOne(join = "teams")
    public Team team;

    public Player(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public Player() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Team getTeam() {
        return team;
    }

    @Override
    public String toString() {
        return toString(new java.util.HashSet<>());
    }

    public String toString(java.util.Set<Object> visited) {
        if (visited.contains(this)) {
            return "Player{id=" + id + ", name='" + name + "' (circular reference)}";
        }

        visited.add(this);

        String teamStr = team == null ? "null" : team.toStringSummary();

        return "Player{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", team=" + teamStr +
                '}';
    }

    public String toStringSummary() {
        return "Player{id=" + id + ", name='" + name + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Player player = (Player) o;
        return Objects.equals(id, player.id) && Objects.equals(name, player.name) && Objects.equals(team, player.team);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(team);
        return result;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTeam(Team team) {
        this.team = team;
    }
}