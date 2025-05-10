import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.api.cache.TransactionResult;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.mongodb.MongoRepositoryAdapter;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(
                        "mongodb+srv://...:...@testingjava.vmol6.mongodb.net/?retryWrites=true&w=majority&appName=TestingJava"
                ))
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
                        .where("password", ">", five)
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

        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", username='" + username + '\'' +
                    ", age=" + age +
                    ", password=" + password +
                    '}';
        }
    }
}
