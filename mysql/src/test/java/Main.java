import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.HikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.time.Instant;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        MySQLRepositoryAdapter<User> adapter = MySQLRepositoryAdapter
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

        System.out.println(adapter.find().size());

        try {
            adapter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
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
                    ", createdAt=" + createdAt +
                    '}';
        }
    }
}
