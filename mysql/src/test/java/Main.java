import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        Logging.ENABLED = true;
        //Logging.DEEP = true;

        MySQLCredentials credentials = new MySQLCredentials("localhost", 3306, "test", "flameyosflow", "...");
        MySQLRepositoryAdapter<Faction, UUID> factions = MySQLRepositoryAdapter
                .builder(Faction.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        MySQLRepositoryAdapter<Warp, UUID> warps = MySQLRepositoryAdapter
                .builder(Warp.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        /*MySQLRepositoryAdapter<User, Integer> users = MySQLRepositoryAdapter
                .builder(User.class, Integer.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();*/

        //users.executeRawQuery("DROP TABLE IF EXISTS factionUsers;");
        warps.executeRawQuery("DROP TABLE IF EXISTS warps;");
        factions.executeRawQuery("DROP TABLE IF EXISTS factions;");
        factions.createRepository(true);
        warps.createRepository(true);
        //users.createRepository(true);

        FactionAdapter adapter = factions.createDynamicProxy(FactionAdapter.class);

        Faction faction = new Faction("TestFaction", UUID.randomUUID());
        Faction faction2 = new Faction("TestFaction2", UUID.randomUUID());
        Warp warp = new Warp("Test", UUID.randomUUID());
        Warp warp2 = new Warp("Test2", UUID.randomUUID());

        faction.warp = warp;
        warp.faction = faction;

        faction2.warp = warp2;
        warp2.faction = faction2;

        factions.insert(faction)
                .and(factions.insert(faction2))
                .and(warps.insert(warp))
                .and(warps.insert(warp2))
                .ifError(e -> System.out.println("error"));

        System.out.println("Finding factions");
        System.out.println(factions.find());
        System.out.println("Found factions");

        try {
            factions.close();
            warps.close();
            //users.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
