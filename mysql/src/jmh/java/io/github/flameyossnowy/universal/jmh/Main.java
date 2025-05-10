package io.github.flameyossnowy.universal.jmh;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@Fork(value = 1)
public class Main {

    private MySQLRepositoryAdapter<User, Integer> users;
    private MySQLRepositoryAdapter<Faction, Integer> factions;
    private MySQLRepositoryAdapter<Warp, Integer> warps;

    @Setup(Level.Trial)
    public void setup() {
        MySQLCredentials credentials = new MySQLCredentials("localhost", 3306, "newtestdb", "flameyosflow", "...");
        this.factions = MySQLRepositoryAdapter
                .builder(Faction.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        this.warps = MySQLRepositoryAdapter
                .builder(Warp.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        this.users = MySQLRepositoryAdapter
                .builder(User.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        users.executeRawQuery("DROP TABLE IF EXISTS factionUsers;");
        warps.executeRawQuery("DROP TABLE IF EXISTS warps;");
        factions.executeRawQuery("DROP TABLE IF EXISTS factions;");
        factions.createRepository(true);
        warps.createRepository(true);
        users.createRepository(true);
    }

    @Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void mysql_insertElements() {
        for (int i = 0; i < 10000; i++) {
            Faction faction = new Faction(i, "test" + i);
            factions.insert(faction);
            warps.insert(new Warp("test" + i, faction));
            users.insert(new User("test" + i, i, Instant.now(), faction));
        }
    }

    @Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void mysql_findElements() {
        factions.find();
    }

    @Repository(name = "factionUsers")
    public static class User {
        @Id
        @AutoIncrement
        public int id;

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
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", age=" + age +
                    ", createdAt=" + createdAt +
                    ", faction=" + (faction == null ? "None (error)" : String.valueOf(faction.id)) +
                    '}';
        }
    }

    @FetchPageSize(100)
    @Repository(name = "warps")
    public static class Warp {
        @Id
        @AutoIncrement
        public int id;

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
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", faction=" + (faction == null ? "None (error)" : String.valueOf(faction.id)) +
                    '}';
        }
    }

    @Repository(name = "factions")
    public static class Faction {
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
}

