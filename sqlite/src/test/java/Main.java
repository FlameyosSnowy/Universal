import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.resolver.ResolveWith;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sqlite.SQLiteRepositoryAdapter;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        Logging.ENABLED = true;
        //Logging.DEEP = true;
        SQLiteRepositoryAdapter<User, UUID> adapter = SQLiteRepositoryAdapter
                .builder(User.class, UUID.class)
                .withCredentials(new SQLiteCredentials("/home/flameyosflow/newdb.db"))
                .build();

        adapter.executeRawQuery("DROP TABLE IF EXISTS users;");
        adapter.createRepository(true);
        adapter.clear();

        adapter.insert(new User(UUID.randomUUID(), "Flameyos", 17, new Password("123456"), List.of("Coding", "Sleeping")));
        System.out.println("Finding users");
        List<User> users = adapter.find();

        for (User user : users) {
            System.out.println(user);
        }
        System.out.println("Found users");
    }

    @SuppressWarnings("unused")
    @Repository(name = "users")
    public static class User {
        @Id
        private UUID id;

        private String username;
        private int age;

        @ResolveWith(PasswordConverter.class)
        private Password password;

        private List<String> hobbies;

        public User(UUID id, String username, int age, Password password, List<String> hobbies) {
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
