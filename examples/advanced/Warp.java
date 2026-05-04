package examples.advanced;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.time.Instant;
import java.util.UUID;

/**
 * Warp entity representing a teleport location in a game world.
 * Demonstrates: OneToOne, ManyToOne relationships, JSON fields, caching, indexes.
 */
@Cacheable(maxCacheSize = 512, algorithm = io.github.flameyossnowy.universal.api.annotations.enums.CacheAlgorithmType.LEAST_RECENTLY_USED)
@FetchPageSize(50)
@Repository(name = "warps")
@Index(name = "idx_warp_world", fields = {"world"}, type = IndexType.NORMAL)
@Index(name = "idx_warp_public", fields = {"isPublic", "world"}, type = IndexType.NORMAL)
public class Warp {

    @Id
    private UUID id;

    @Unique
    @NonNull
    @Named("warp_name")
    private String name;

    @NonNull
    private String world;

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    @DefaultValue("true")
    private boolean isPublic;

    @DefaultValue("0")
    private int visitCount;

    /**
     * OneToOne relationship: Each warp can be the "home" of exactly one faction.
     * This is the owning side of the relationship.
     */
    @OneToOne(mappedBy = "homeWarp", lazy = true)
    @OnDelete(value = io.github.flameyossnowy.universal.api.annotations.enums.OnModify.NO_ACTION)
    private Faction owningFaction;

    /**
     * ManyToOne relationship: A warp can belong to one faction (as a regular warp, not home).
     * Multiple warps can belong to the same faction.
     */
    @ManyToOne(join = "factions")
    private Faction faction;

    /**
     * JSON field for flexible warp metadata (tags, permissions, custom data).
     */
    @JsonField(storage = JsonField.Storage.COLUMN, queryable = true)
    private WarpMetadata metadata;

    @Now
    private Instant createdAt;

    public Warp() {}

    public Warp(String name, String world, double x, double y, double z, float yaw, float pitch) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.isPublic = true;
        this.visitCount = 0;
        this.metadata = new WarpMetadata();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public int getVisitCount() { return visitCount; }
    public void setVisitCount(int visitCount) { this.visitCount = visitCount; }
    public void incrementVisits() { this.visitCount++; }

    public Faction getOwningFaction() { return owningFaction; }
    public void setOwningFaction(Faction owningFaction) { this.owningFaction = owningFaction; }

    public Faction getFaction() { return faction; }
    public void setFaction(Faction faction) { this.faction = faction; }

    public WarpMetadata getMetadata() { return metadata; }
    public void setMetadata(WarpMetadata metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Warp{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", world='" + world + '\'' +
                ", location=[" + x + ", " + y + ", " + z + "]" +
                ", isPublic=" + isPublic +
                ", visitCount=" + visitCount +
                ", faction=" + (faction != null ? faction.getName() : "none") +
                '}';
    }
}
