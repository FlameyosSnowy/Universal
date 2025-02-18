import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.HikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.util.ArrayList;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        MySQLCredentials credentials = MySQLCredentials.builder()
                .database("testdb")
                .host("localhost")
                .username("flameyosflow")
                .port(3306)
                .password("eyad4056")
                .build();
        MySQLRepositoryAdapter<Faction, UUID> factions = MySQLRepositoryAdapter
                .builder(Faction.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(HikariConnectionProvider::new)
                .build();

        MySQLRepositoryAdapter<Warp, UUID> warps = MySQLRepositoryAdapter
                .builder(Warp.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(HikariConnectionProvider::new)
                .build();

        factions.createRepository();
        warps.createRepository();

        factions.clear();
        warps.clear();

        UUID id = UUID.randomUUID();
        Faction faction = new Faction();
        faction.id = id;

        Warp warp = new Warp();
        warp.id = UUID.randomUUID();
        warp.name = "Test";

        Warp warp2 = new Warp();
        warp2.id = UUID.randomUUID();
        warp2.name = "Test2";

        faction.warps = new ArrayList<>();
        faction.warps.add(warp);
        faction.warps.add(warp2);
        warp.faction = faction;
        warp2.faction = faction;

        factions.insert(faction);
        warps.insert(warp);

        /*factions.updateAll(Query.update()
                .set("warps", List.of(warp)
                ));*/

        System.out.println(factions.find(Query.select()
                .where("id", "=", id)
                .build()));

        try {
            factions.close();
            warps.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
