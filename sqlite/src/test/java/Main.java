import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.annotations.Resolver;
import io.github.flameyossnowy.universal.sqlite.SQLiteRepositoryAdapter;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        SQLiteRepositoryAdapter<User> adapter = SQLiteRepositoryAdapter
                .builder(User.class)
                .withCredentials(SQLiteCredentials.builder()
                        .directory("/home/flameyosflow/helloworld.db")
                        .build())

                .build();

        adapter.createRepository();
        adapter.clear();

        List.of(
                new User(UUID.randomUUID(), "Flameyos", 17, new Password("Flameyos"), List.of("Coding", "Sleeping")),
                new User(UUID.randomUUID(), "Flow", 13, new Password("Flow"), List.of("Coding", "Sleeping")),
                new User(UUID.randomUUID(), "FlameyosFlow", 15, new Password("FLAm"), List.of("Coding", "Sleeping"))
        ).forEach(adapter::insert);

        System.out.println(adapter.find());
    }

    @SuppressWarnings("unused")
    @Repository(name = "Userss")
    public static class User {
        @Id
        private UUID id;

        private String username;
        private int age;

        @Resolver(PasswordConverter.class)
        private Password password;

        private List<String> hobbies;

        public User(UUID id, String username, int age, Password password, List<String> hobbies) {
            this.id = id;
            this.username = username;
            this.age = age;
            this.password = password;
        }

        public User() {}

        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", age=" + age +
                    ", password=" + password.password() +
                    ", hobbies=" + hobbies +
                    '}';
        }
    }
}
