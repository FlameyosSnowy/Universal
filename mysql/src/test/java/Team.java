import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository(name = "teams")
public class Team {

    @Id
    public UUID id;

    public String name;

    @OneToMany(mappedBy = Player.class)
    public List<Player> players = new ArrayList<>();

    public List<Player> oldPlayers = new ArrayList<>();

    public Team(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public Team() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public List<Player> getOldPlayers() {
        return oldPlayers;
    }

    @Override
    public String toString() {
        return toString(new java.util.HashSet<>());
    }

    public String toString(java.util.Set<Object> visited) {
        if (visited.contains(this)) {
            return "Team{id=" + id + ", name='" + name + "' (circular reference)}";
        }

        visited.add(this);

        String playersStr = players == null ? "null" :
                "[" + players.stream()
                        .map(p -> p.toStringSummary())
                        .collect(Collectors.joining(", ")) + "]";

        String oldPlayersStr = oldPlayers == null ? "null" :
                "[" + oldPlayers.stream()
                        .map(p -> p.toStringSummary())
                        .collect(Collectors.joining(", ")) + "]";

        return "Team{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", players=" + playersStr +
                ", oldPlayers=" + oldPlayersStr +
                '}';
    }

    public String toStringSummary() {
        return "Team{id=" + id + ", name='" + name + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Team team = (Team) o;
        return Objects.equals(id, team.id) && Objects.equals(name, team.name) && Objects.equals(players, team.players) && Objects.equals(oldPlayers, team.oldPlayers);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(players);
        result = 31 * result + Objects.hashCode(oldPlayers);
        return result;
    }
}