package examples.advanced;

import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.api.connection.TransactionContext;
import io.github.flameyossnowy.universal.api.utils.Logging;
import io.github.flameyossnowy.universal.mysql.MySQLRepositoryAdapter;
import io.github.flameyossnowy.universal.mysql.connections.MySQLHikariConnectionProvider;
import io.github.flameyossnowy.universal.mysql.credentials.MySQLCredentials;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Advanced example demonstrating the initialization and usage of
 * warps and factions with complex relationships.
 *
 * Features demonstrated:
 * - Repository initialization with connection pooling (HikariCP)
 * - Optimizations and caching
 * - OneToOne bidirectional relationships (Faction <-> home Warp)
 * - OneToMany relationships (Faction -> warps, Faction -> members)
 * - ManyToOne relationships (Warp -> faction, FactionMember -> faction)
 * - JSON field storage and querying
 * - Transaction support
 * - Lazy loading
 */
public class FactionSystemExample {

    private MySQLRepositoryAdapter<Faction, UUID> factionAdapter;
    private MySQLRepositoryAdapter<Warp, UUID> warpAdapter;
    private MySQLRepositoryAdapter<FactionMember, UUID> memberAdapter;

    public static void main(String[] args) {
        FactionSystemExample example = new FactionSystemExample();
        try {
            example.initialize();
            example.demonstrateBasicOperations();
            example.demonstrateRelationships();
            example.demonstrateJsonQueries();
            example.demonstrateTransactions();
        } finally {
            example.shutdown();
        }
    }

    /**
     * Initialize all repositories with advanced configuration.
     */
    public void initialize() {
        // Enable logging for debugging
        Logging.ENABLED = true;
        Logging.DEEP = false; // Set to true for verbose SQL logging

        // Configure database credentials
        MySQLCredentials credentials = new MySQLCredentials(
                "localhost",
                3306,
                "faction_db",
                "root",
                "secret"
        );

        // Initialize Faction repository with caching and connection pooling
        this.factionAdapter = MySQLRepositoryAdapter
                .<Faction, UUID>builder(Faction.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        // Initialize Warp repository with larger page size for bulk operations
        this.warpAdapter = MySQLRepositoryAdapter
                .<Warp, UUID>builder(Warp.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        // Initialize FactionMember repository
        this.memberAdapter = MySQLRepositoryAdapter
                .<FactionMember, UUID>builder(FactionMember.class, UUID.class)
                .withCredentials(credentials)
                .withConnectionProvider(MySQLHikariConnectionProvider::new)
                .withOptimizations(Optimizations.RECOMMENDED_SETTINGS)
                .build();

        System.out.println("✓ All repositories initialized successfully");
    }

    /**
     * Demonstrate basic CRUD operations.
     */
    public void demonstrateBasicOperations() {
        System.out.println("\n=== Basic Operations ===");

        // Create a faction
        UUID leaderId = UUID.randomUUID();
        Faction dragons = new Faction("DragonSlayers", "DS", leaderId);
        dragons.getSettings().setColor("RED");
        dragons.getSettings().setMaxMembers(50);

        // Insert faction
        factionAdapter.insert(dragons);
        System.out.println("Created faction: " + dragons.getName());

        // Create warps for the faction
        Warp spawn = new Warp("spawn", "world", 100.5, 64.0, 200.5, 90.0f, 0.0f);
        spawn.setFaction(dragons);
        spawn.getMetadata().addTag("public");
        spawn.getMetadata().addTag("safe-zone");
        spawn.getMetadata().setDescription("Main spawn area");

        Warp shop = new Warp("shop", "world", 150.0, 65.0, 250.0, 180.0f, 0.0f);
        shop.setFaction(dragons);
        shop.setPublic(true);
        shop.getMetadata().addTag("trading");

        // Insert warps
        warpAdapter.insert(spawn);
        warpAdapter.insert(shop);
        System.out.println("Created warps: " + spawn.getName() + ", " + shop.getName());

        // Set home warp (OneToOne relationship)
        Warp home = new Warp("home", "world", 0.0, 70.0, 0.0, 0.0f, 0.0f);
        home.setFaction(dragons);
        home.getMetadata().setIcon("HOME");
        warpAdapter.insert(home);

        dragons.setHomeWarp(home);
        factionAdapter.update(dragons);
        System.out.println("Set home warp for faction: " + dragons.getName());

        // Retrieve and display
        Optional<Faction> retrieved = factionAdapter.findById(dragons.getId());
        retrieved.ifPresent(f -> System.out.println("Retrieved: " + f));
    }

    /**
     * Demonstrate relationship handling.
     */
    public void demonstrateRelationships() {
        System.out.println("\n=== Relationship Operations ===");

        // Create faction with members (OneToMany)
        UUID leaderId = UUID.randomUUID();
        Faction knights = new Faction("KnightsOfTheRound", "KOTR", leaderId);
        factionAdapter.insert(knights);

        // Create members
        FactionMember leader = new FactionMember(leaderId, "KingArthur", "LEADER");
        leader.setFaction(knights);

        FactionMember officer = new FactionMember(UUID.randomUUID(), "SirLancelot", "OFFICER");
        officer.setFaction(knights);
        officer.contributePower(25.0);

        FactionMember member = new FactionMember(UUID.randomUUID(), "SquireTim", "MEMBER");
        member.setFaction(knights);

        // Insert members
        memberAdapter.insert(leader);
        memberAdapter.insert(officer);
        memberAdapter.insert(member);

        System.out.println("Added members to faction: " + knights.getName());

        // Refresh faction to load members (lazy loading)
        Faction refreshedFaction = factionAdapter.findById(knights.getId());
        if (refreshedFaction != null && refreshedFaction.getMembers() != null) {
            System.out.println("Faction has " + refreshedFaction.getMembers().size() + " members");
            for (FactionMember m : refreshedFaction.getMembers()) {
                System.out.println("  - " + m.getPlayerName() + " (" + m.getRank() + ")");
            }
        }

        // Demonstrate bidirectional OneToOne
        Warp castle = new Warp("castle", "world", 500.0, 70.0, 500.0, 0.0f, 0.0f);
        castle.setFaction(knights);
        castle.setOwningFaction(knights); // This makes it the home warp
        warpAdapter.insert(castle);

        knights.setHomeWarp(castle);
        factionAdapter.update(knights);

        // Verify bidirectional relationship
        Warp retrievedWarp = warpAdapter.findById(castle.getId());
        if (retrievedWarp != null && retrievedWarp.getOwningFaction() != null) {
            System.out.println("Home warp '" + retrievedWarp.getName() + "' belongs to faction: " +
                    retrievedWarp.getOwningFaction().getName());
        }
    }

    /**
     * Demonstrate JSON field querying capabilities.
     */
    public void demonstrateJsonQueries() {
        System.out.println("\n=== JSON Field Queries ===");

        // Create warps with different metadata
        Warp mining = new Warp("mining", "world_nether", 100.0, 30.0, 100.0, 0.0f, 0.0f);
        mining.setPublic(true);
        mining.getMetadata().addTag("resource");
        mining.getMetadata().addTag("dangerous");
        mining.getMetadata().getCustomProperties().setParticlesEnabled(true);
        mining.getMetadata().getCustomProperties().setParticleType("FLAME");

        warpAdapter.insert(mining);

        // Query by JSON content (adapter-dependent, typically works on PostgreSQL)
        // This would filter warps with specific tags in their metadata
        System.out.println("Created warp with JSON metadata: " + mining.getName());
        System.out.println("Tags: " + mining.getMetadata().getTags());
        System.out.println("Particles: " + mining.getMetadata().getCustomProperties().getParticleType());

        // List all public warps
        List<Warp> publicWarps = warpAdapter.find()
                .where(warp -> warp.isPublic())
                .list();
        System.out.println("Public warps count: " + publicWarps.size());
    }

    /**
     * Demonstrate transaction support for atomic operations.
     */
    public void demonstrateTransactions() {
        System.out.println("\n=== Transaction Operations ===");

        TransactionContext ctx = factionAdapter.beginTransaction();
        try {
            // Create faction with initial setup in a transaction
            UUID leaderId = UUID.randomUUID();
            Faction merchants = new Faction("MerchantGuild", "MG", leaderId);

            factionAdapter.insert(merchants);

            // Create home warp
            Warp hq = new Warp("hq", "world", 1000.0, 70.0, 1000.0, 45.0f, 0.0f);
            hq.setFaction(merchants);
            warpAdapter.insert(hq);

            // Create leader member
            FactionMember leader = new FactionMember(leaderId, "MerchantKing", "LEADER");
            leader.setFaction(merchants);
            memberAdapter.insert(leader);

            // Update faction with relationships
            merchants.setHomeWarp(hq);
            factionAdapter.update(merchants);

            // Commit transaction
            ctx.commit();
            System.out.println("✓ Transaction committed: Created faction with all relationships");

        } catch (Exception e) {
            ctx.rollback();
            System.err.println("✗ Transaction rolled back: " + e.getMessage());
        } finally {
            ctx.close();
        }
    }

    /**
     * Shutdown all repositories and clean up resources.
     */
    public void shutdown() {
        System.out.println("\n=== Shutdown ===");
        try {
            factionAdapter.close();
            warpAdapter.close();
            memberAdapter.close();
            System.out.println("✓ All repositories closed successfully");
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }
}
