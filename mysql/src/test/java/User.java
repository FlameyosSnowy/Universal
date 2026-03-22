import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository(name = "factionUsers")
public class User {
    @Id
    private UUID id;

    public String username;

    public int age;

    public Instant createdAt;

    @ManyToOne(join = "faction")
    public Faction faction;

    public User() {}

    public User(UUID id, String username, int age, Instant createdAt, Faction faction) {
        this.id = id;
        this.username = username;
        this.age = age;
        this.createdAt = createdAt;
        this.faction = faction;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                "username='" + username + '\'' +
                ", age=" + age +
                ", createdAt=" + createdAt +
                ", faction=" + (faction == null ? "None (error)" : String.valueOf(faction.id)) +
                '}';
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Faction getFaction() {
        return faction;
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
