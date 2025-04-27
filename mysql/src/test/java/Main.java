import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.ArrayList;
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
        factions.executeRawQuery("DROP TABLE IF EXISTS factions_string_map;");
        factions.executeRawQuery("DROP TABLE IF EXISTS factions;");
        factions.createRepository(true);
        warps.createRepository(true);
        //users.createRepository(true);

        FactionAdapter adapter = factions.createDynamicProxy(FactionAdapter.class);

        Faction faction = new Faction("TestFaction", UUID.randomUUID());
        Warp warp = new Warp("Test", UUID.randomUUID());

        faction.banned.put(Level.ERROR, List.of("Worldy", "World"));
        faction.banned.put(Level.INFO, List.of("Backy", "Back"));

        faction.warp = warp;
        warp.faction = faction;
        factions.insert(faction);
        warps.insert(warp);

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
