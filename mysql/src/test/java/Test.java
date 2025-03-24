import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.Repository;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.util.List;

@Repository(name = "test")
public class Test {
    @Id
    @AutoIncrement
    public int id;

    public List<String> list;

    public Test() {

    }

    public Test(List<String> list) {
        this.list = list;
    }

    @Override
    public String toString() {
        return "Test{" +
                "list=" + list +
                '}';
    }

    public static void main(String[] args) {
        MySQLCredentials credentials = new MySQLCredentials("localhost", 3306, "newtestdb", "flameyosflow", "eyad4056");

        MySQLRepositoryAdapter<Test, Integer> users = MySQLRepositoryAdapter
                .builder(Test.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        users.createRepository(true);

        users.insert(new Test(List.of("1", "2", "3")));

        System.out.println(users.find());
    }
}
