package examples.advanced;

import io.github.flameyossnowy.universal.api.annotations.*;
import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;

import java.time.Instant;
import java.util.UUID;

/**
 * FactionMember entity representing a player-faction relationship.
 * Demonstrates: ManyToOne relationship with rank tracking.
 */
@FetchPageSize(100)
@Repository(name = "faction_members")
@Index(name = "idx_member_faction", fields = {"faction"}, type = IndexType.NORMAL)
@Index(name = "idx_member_player", fields = {"playerId"}, type = IndexType.UNIQUE)
public class FactionMember {

    @Id
    private UUID id;

    @NonNull
    private UUID playerId;

    @NonNull
    @Named("player_name")
    private String playerName;

    /**
     * ManyToOne relationship: Each member belongs to exactly one faction.
     */
    @ManyToOne(join = "factions")
    private Faction faction;

    @DefaultValue("MEMBER")
    private String rank;

    @DefaultValue("0.0")
    private double contributedPower;

    @Now
    private Instant joinedAt;

    private Instant lastActiveAt;

    public FactionMember() {}

    public FactionMember(UUID playerId, String playerName, String rank) {
        this.id = UUID.randomUUID();
        this.playerId = playerId;
        this.playerName = playerName;
        this.rank = rank != null ? rank : "MEMBER";
        this.contributedPower = 0.0;
        this.lastActiveAt = Instant.now();
    }

    public boolean isOfficer() {
        return "OFFICER".equals(rank) || "LEADER".equals(rank);
    }

    public boolean isLeader() {
        return "LEADER".equals(rank);
    }

    public void contributePower(double amount) {
        this.contributedPower += amount;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Faction getFaction() { return faction; }
    public void setFaction(Faction faction) { this.faction = faction; }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    public double getContributedPower() { return contributedPower; }
    public void setContributedPower(double contributedPower) { this.contributedPower = contributedPower; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    @Override
    public String toString() {
        return "FactionMember{" +
                "id=" + id +
                ", playerId=" + playerId +
                ", playerName='" + playerName + '\'' +
                ", rank='" + rank + '\'' +
                ", contributedPower=" + contributedPower +
                '}';
    }
}
