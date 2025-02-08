package io.github.flameyossnowy.universal.jmh;

import io.github.flameyossnowy.universal.api.annotations.Cast;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.HikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@Fork(value = 1)
public class Main {
    public static final Instant DEFAULT_CREATED_AT = Instant.now();

    private MySQLRepositoryAdapter<User> adapter;

    @Setup(Level.Trial)
    public void setup() {
        this.adapter = MySQLRepositoryAdapter
                .builder(User.class)
                .withCredentials(MySQLCredentials.builder()
                        .database("testdb")
                        .host("localhost")
                        .username("flameyosflow")
                        .port(3306)
                        .password("...")
                        .build())
                .withConnectionProvider(HikariConnectionProvider::new)
                .build();
        adapter.createRepository();
        adapter.clear();
    }

    @Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void mysql_insertElements() {
        for (int i = 0; i < 5_000; i++) {
            if (i % 11 == 0) {
                adapter.insert(new User(UUID.randomUUID(), "Flameyos" + i, 18, DEFAULT_CREATED_AT.minusSeconds(10)));
            } else {
                adapter.insert(new User(UUID.randomUUID(), "Flow" + i, 13, Instant.now()));
            }
        }
    }

    @Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Benchmark
    public void mysql_findElements() {
        adapter.find(Query.select()
                .where("createdAt", "<", DEFAULT_CREATED_AT)
                .build());
    }

    @Repository(name = "testUsers")
    public static class User {
        @Id
        private UUID id;

        private String username;
        private int age;

        private Instant createdAt;

        public User(UUID id, String username, int age, Instant createdAt) {
            this.id = id;
            this.username = username;
            this.age = age;
            this.createdAt = createdAt;
        }

        public User() {}

        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", age=" + age +
                    ", password=" + createdAt +
                    '}';
        }
    }
}

