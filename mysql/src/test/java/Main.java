import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.time.Instant;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Logging.ENABLED = true;
        //Logging.DEEP = true;
        Logging.simplify();

        MySQLCredentials credentials = new MySQLCredentials("...", 3306, "newtestdb", "flameyosflow", "...");
        MySQLRepositoryAdapter<Faction, Integer> factions = MySQLRepositoryAdapter
                .builder(Faction.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        MySQLRepositoryAdapter<Warp, Integer> warps = MySQLRepositoryAdapter
                .builder(Warp.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        MySQLRepositoryAdapter<User, Integer> users = MySQLRepositoryAdapter
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

        FactionAdapter adapter = factions.createDynamicProxy(FactionAdapter.class);

        Faction faction = new Faction(1, "TestFaction");
        Faction faction2 = new Faction(2, "TestFaction2");
        adapter.insert(faction);
        adapter.insert(faction2);

        System.out.println("Finding factions");
        System.out.println(adapter.findAll());
        System.out.println("Found factions");

        Warp warp = new Warp("Test", faction);
        Warp warp2 = new Warp("Test2", faction);
        List.of(warp, warp2).forEach(warps::insert);

        User user = new User("James", 17, Instant.now(), faction);
        User user2 = new User("John", 17, Instant.now(), faction);
        List.of(user, user2).forEach(users::insert);

        List<Warp> factions1 = warps.find().list();
        System.out.println(factions1);


        try {
            factions.close();
            warps.close();
            users.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
