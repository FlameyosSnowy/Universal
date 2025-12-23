
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RelationshipTest {

    private MySQLRepositoryAdapter<Faction, UUID> factionsAdapter;
    private MySQLRepositoryAdapter<Warp, UUID> warpsAdapter;
    private MySQLRepositoryAdapter<Team, UUID> teamsAdapter;
    private MySQLRepositoryAdapter<Player, UUID> playersAdapter;


    @BeforeEach
    void setUp() {
        Logging.ENABLED = true;
        Logging.DEEP = true;
        MySQLCredentials credentials = new MySQLCredentials("localhost", 3306, "main", "root", "secret");

        factionsAdapter = MySQLRepositoryAdapter
                .builder(Faction.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        warpsAdapter = MySQLRepositoryAdapter
                .builder(Warp.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        teamsAdapter = MySQLRepositoryAdapter
                .builder(Team.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        playersAdapter = MySQLRepositoryAdapter
                .builder(Player.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        factionsAdapter.createRepository(true);
        warpsAdapter.createRepository(true);
        teamsAdapter.createRepository(true);
        playersAdapter.createRepository(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        factionsAdapter.close();
        warpsAdapter.close();
        teamsAdapter.close();
        playersAdapter.close();
    }

    @Test
    void testOneToOneRelationship() {
        // Arrange
        Faction faction = new Faction("TestFaction", UUID.randomUUID());
        Warp warp = new Warp("TestWarp", UUID.randomUUID());

        faction.warp = warp;
        warp.faction = faction;

        // Act
        factionsAdapter.insert(faction);
        warpsAdapter.insert(warp);

        // Assert
        Faction retrievedFaction = factionsAdapter.findById(faction.id);
        System.out.println(retrievedFaction);
        assertNotNull(retrievedFaction, "Retrieved faction should not be null");
        assertNotNull(retrievedFaction.warp, "Faction's warp should not be null");

        Warp retrievedWarp = retrievedFaction.warp;
        System.out.println(retrievedWarp);
        assertEquals(warp.id, retrievedWarp.id, "Warp ID should match");
        assertEquals(warp.name, retrievedWarp.name, "Warp name should match");

        assertNotNull(retrievedWarp.faction, "Warp's faction should not be null");
        assertEquals(faction.id, retrievedWarp.faction.id, "Faction ID should match on the back-reference");
    }

    @Test
    void testOneToManyRelationship() {
        // Arrange
        Team team = new Team("TestTeam");
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");

        player1.team = team;
        player2.team = team;

        // Act
        teamsAdapter.insert(team);
        playersAdapter.insert(player1);
        playersAdapter.insert(player2);

        // Assert
        Team retrievedTeam = teamsAdapter.findById(team.id);
        System.out.println(retrievedTeam);
        assertNotNull(retrievedTeam, "Retrieved team should not be null");
        assertNotNull(retrievedTeam.players, "Team's players should not be null");
        assertEquals(2, retrievedTeam.players.size(), "Should have two players in the team");
    }
}
