import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.sqlite.SQLiteRepositoryAdapter;
import io.github.flameyossnowy.universal.sqlite.credentials.SQLiteCredentials;
import testapp.Password;
import testapp.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

void main() {
    Logging.ENABLED = true;
    //Logging.DEEP = true;
    SQLiteRepositoryAdapter<User, UUID> adapter = SQLiteRepositoryAdapter
        .builder(User.class, UUID.class)
        .withCredentials(new SQLiteCredentials("/home/flameyosflow/newdb.db"))
        .build();

    adapter.getQueryExecutor().executeRawQuery("DROP TABLE IF EXISTS users;");
    adapter.createRepository(true);
    adapter.clear();

    adapter.insert(new User(UUID.randomUUID(), "Flameyos", 17, new Password("123456"), List.of("Coding", "Sleeping")));
    IO.println("Finding users");
    List<User> users = adapter.find();

    for (User user : users) {
        IO.println(user);
    }
    IO.println("Found users");
}