import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.sqlite.SQLiteRepositoryAdapter;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Main {
    public static final Instant DEFAULT_PASSWORD = Instant.now();

    public static void main(String[] args) {
        SQLiteRepositoryAdapter<User, UUID> adapter = SQLiteRepositoryAdapter
                .builder(User.class, UUID.class)
                .withCredentials(SQLiteCredentials.builder()
                        .directory("/home/flameyosflow/old.db")
                        .build())
                .build();

        adapter.createRepository();
        adapter.clear();

        adapter.insertAll(List.of(
                new User(UUID.randomUUID(), "Flameyos", 17,
                        Instant.now(), List.of("Coding", "Sleeping")),
                new User(UUID.randomUUID(), "Flow", 13,
                        Instant.now(), List.of("Coding", "Sleeping")),
                new User(UUID.randomUUID(), "FlameyosFlow", 15,
                        Instant.now(), List.of("Coding", "Sleeping"))
        ));

        System.out.println(adapter.find(Query.select()
                .where("password", "<", DEFAULT_PASSWORD)
                .build()));
    }

    @SuppressWarnings("unused")
    @Repository(name = "Use")
    public static class User {
        @Id
        private UUID id;

        private String username;
        private int age;

        private Instant password;

        private List<String> hobbies;

        public User(UUID id, String username, int age, Instant password, List<String> hobbies) {
            this.id = id;
            this.username = username;
            this.age = age;
            this.password = password;
            this.hobbies = hobbies;
        }

        public User() {}

        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", age=" + age +
                    ", password=" + password +
                    ", hobbies=" + hobbies +
                    '}';
        }
    }
}
