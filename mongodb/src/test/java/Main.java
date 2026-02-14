import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.ManyToOne;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;
import org.bson.UuidRepresentation;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(
                        "mongodb+srv://flameyosflow:87654321@testingjava.vmol6.mongodb.net/?retryWrites=true&w=majority&appName=TestingJava"
                ))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();
        MongoRepositoryAdapter<User, UUID> adapter = MongoRepositoryAdapter
                .builder(User.class, UUID.class)
                .withCredentials(settings)
                .setDatabase("users")
                .build();

        adapter.createRepository();

        List<User> users = new ArrayList<>();

        Instant five = Instant.parse("2025-02-06T16:45:43.767Z");
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                users.add(new User(UUID.randomUUID(), "Flameyos" + i, 17, five));
            } else {
                users.add(new User(UUID.randomUUID(), "Flow" + i, 13, Instant.now()));
            }
            Thread.sleep(1000);
        }

        for (User user : users) {
            adapter.insert(user);
        }

        adapter.withSession((session) -> {
            UUID uuid = UUID.randomUUID();

            session.insert(new User(UUID.randomUUID(), "Flow", 13, Instant.now()));

            User user = session.findById(uuid);
            session.delete(user);

            TransactionResult<Boolean> result = session.commit();
            result.getError().ifPresent((error) -> session.rollback());
        });

        adapter.find(Query.select()
                        .where("password").gt(five)
                        .build())
                .forEach(System.out::println);
    }

    @SuppressWarnings("unused")
    @Repository(name = "users_old")
    public static class User {
        @Id
        private UUID id;

        private String username;

        @Condition(value = "age > 18")
        private int age;

        private Instant password;

        public User(UUID id, String username, int age, Instant password) {
            this.id = id;
            this.username = username;
            this.age = age;
            this.password = password;
        }

        public User() {}

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
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

        public Instant getPassword() {
            return password;
        }

        public void setPassword(Instant password) {
            this.password = password;
        }

        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", age=" + age +
                    ", password=" + password +
                    '}';
        }
    }

    @SuppressWarnings("unused")
    @Repository(name = "teams_rel")
    public static class TeamRel {
        @Id
        private UUID id;

        private String name;

        @OneToMany(mappedBy = PlayerRel.class)
        private List<PlayerRel> players;

        public TeamRel() {}

        public TeamRel(UUID id, String name) {
            this.id = id;
            this.name = name;
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

        public List<PlayerRel> getPlayers() {
            return players;
        }

        public void setPlayers(List<PlayerRel> players) {
            this.players = players;
        }
    }

    @SuppressWarnings("unused")
    @Repository(name = "players_rel")
    public static class PlayerRel {
        @Id
        private UUID id;

        private String name;

        @ManyToOne
        private TeamRel team;

        public PlayerRel() {}

        public PlayerRel(UUID id, String name, TeamRel team) {
            this.id = id;
            this.name = name;
            this.team = team;
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

        public TeamRel getTeam() {
            return team;
        }

        public void setTeam(TeamRel team) {
            this.team = team;
        }
    }

    @SuppressWarnings("unused")
    @Repository(name = "factions_rel")
    public static class FactionRel {
        @Id
        private UUID id;

        private String name;

        @OneToOne
        private WarpRel warp;

        public FactionRel() {}

        public FactionRel(UUID id, String name) {
            this.id = id;
            this.name = name;
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

        public WarpRel getWarp() {
            return warp;
        }

        public void setWarp(WarpRel warp) {
            this.warp = warp;
        }
    }

    @SuppressWarnings("unused")
    @Repository(name = "warps_rel")
    public static class WarpRel {
        @Id
        private UUID id;

        private String name;

        @OneToOne
        private FactionRel faction;

        public WarpRel() {}

        public WarpRel(UUID id, String name, FactionRel faction) {
            this.id = id;
            this.name = name;
            this.faction = faction;
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

        public FactionRel getFaction() {
            return faction;
        }

        public void setFaction(FactionRel faction) {
            this.faction = faction;
        }
    }
}
