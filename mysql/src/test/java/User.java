import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.time.Instant;

@Repository(name = "factionUsers")
public class User {
    public String username;

    public int age;

    public Instant createdAt;

    @ManyToOne(join = "faction")
    public Faction faction;

    public User() {}

    public User(String username, int age, Instant createdAt, Faction faction) {
        this.username = username;
        this.age = age;
        this.createdAt = createdAt;
        this.faction = faction;
    }

    @Override
    public String toString() {
        return "User{" +
                //"id=" + id +
                "username='" + username + '\'' +
                ", age=" + age +
                ", createdAt=" + createdAt +
                ", faction=" + (faction == null ? "None (error)" : String.valueOf(faction.id)) +
                '}';
    }
}
