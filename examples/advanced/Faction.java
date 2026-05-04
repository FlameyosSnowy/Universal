package examples.advanced;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.annotations.enums.OnModify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Faction entity representing a player organization/group.
 * Demonstrates: OneToOne (bidirectional), OneToMany, ManyToOne, caching, constraints, indexes.
 */
@Cacheable(maxCacheSize = 256)
@FetchPageSize(25)
@Repository(name = "factions")
@Constraint(name = "chk_faction_power", fields = {"power", "maxPower"})
@Index(name = "idx_faction_name", fields = {"name"}, type = IndexType.UNIQUE)
@Index(name = "idx_faction_power", fields = {"power"}, type = IndexType.NORMAL)
@Index(name = "idx_faction_leader", fields = {"leaderId"}, type = IndexType.NORMAL)
public class Faction {

    @Id
    private UUID id;

    @Unique
    @NonNull
    @Named("faction_name")
    private String name;

    @Named("faction_tag")
    private String tag;

    @NonNull
    private UUID leaderId;

    @DefaultValue("0.0")
    private double power;

    @DefaultValue("100.0")
    private double maxPower;

    @DefaultValue("0")
    private int memberCount;

    @DefaultValue("true")
    private boolean isOpen;

    /**
     * OneToOne relationship: A faction has exactly one "home" warp.
     * This is the inverse side (mappedBy points to Warp.owningFaction).
     */
    @OneToOne(mappedBy = "owningFaction", lazy = false)
    @OnDelete(value = OnModify.NO_ACTION)
    private Warp homeWarp;

    /**
     * OneToMany relationship: A faction owns many warps.
     * This is the inverse side - Warp.faction holds the actual foreign key.
     */
    @OneToMany(mappedBy = FactionMember.class, lazy = true, consistency = Consistency.EVENTUAL)
    private List<FactionMember> members = new ArrayList<>();

    /**
     * OneToMany relationship: A faction has many regular warps (not home).
     * Uses lazy loading for performance with many warps.
     */
    @OneToMany(mappedBy = Warp.class, lazy = true, consistency = Consistency.EVENTUAL)
    private List<Warp> warps = new ArrayList<>();

    /**
     * ManyToOne relationship: Factions can have an alliance (another faction).
     * Multiple factions can ally with the same faction.
     */
    @ManyToOne(join = "factions")
    private Faction ally;

    /**
     * JSON field for faction settings and permissions.
     */
    @JsonField(storage = JsonField.Storage.COLUMN, queryable = true)
    private FactionSettings settings;

    @Now
    private Instant foundedAt;

    public Faction() {}

    public Faction(String name, String tag, UUID leaderId) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.tag = tag != null ? tag : name.substring(0, Math.min(4, name.length())).toUpperCase();
        this.leaderId = leaderId;
        this.power = 0.0;
        this.maxPower = 100.0;
        this.memberCount = 1;
        this.isOpen = true;
        this.settings = new FactionSettings();
    }

    // Business methods
    public void addMember(FactionMember member) {
        this.members.add(member);
        this.memberCount = this.members.size();
        member.setFaction(this);
    }

    public void removeMember(FactionMember member) {
        this.members.remove(member);
        this.memberCount = this.members.size();
    }

    public void addWarp(Warp warp) {
        this.warps.add(warp);
        warp.setFaction(this);
    }

    public void removeWarp(Warp warp) {
        this.warps.remove(warp);
        warp.setFaction(null);
    }

    public void setHomeWarp(Warp warp) {
        this.homeWarp = warp;
        warp.setOwningFaction(this);
    }

    public void gainPower(double amount) {
        this.power = Math.min(this.power + amount, this.maxPower);
    }

    public void losePower(double amount) {
        this.power = Math.max(this.power - amount, 0);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLeaderId() { return leaderId; }
    public void setLeaderId(UUID leaderId) { this.leaderId = leaderId; }

    public double getPower() { return power; }
    public void setPower(double power) { this.power = power; }

    public double getMaxPower() { return maxPower; }
    public void setMaxPower(double maxPower) { this.maxPower = maxPower; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean isOpen) { this.isOpen = isOpen; }

    public Warp getHomeWarp() { return homeWarp; }

    public List<FactionMember> getMembers() { return members; }
    public void setMembers(List<FactionMember> members) {
        this.members = members;
        this.memberCount = members != null ? members.size() : 0;
    }

    public List<Warp> getWarps() { return warps; }
    public void setWarps(List<Warp> warps) { this.warps = warps; }

    public Faction getAlly() { return ally; }
    public void setAlly(Faction ally) { this.ally = ally; }

    public FactionSettings getSettings() { return settings; }
    public void setSettings(FactionSettings settings) { this.settings = settings; }

    public Instant getFoundedAt() { return foundedAt; }
    public void setFoundedAt(Instant foundedAt) { this.foundedAt = foundedAt; }

    @Override
    public String toString() {
        return "Faction{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", power=" + power + "/" + maxPower +
                ", members=" + memberCount +
                ", warps=" + (warps != null ? warps.size() : 0) +
                ", open=" + isOpen +
                '}';
    }
}
