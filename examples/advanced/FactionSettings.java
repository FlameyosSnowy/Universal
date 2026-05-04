package examples.advanced;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Settings class for Faction - stored as JSON.
 * Demonstrates complex nested JSON structures.
 */
public class FactionSettings {
    private boolean friendlyFire = false;
    private boolean explosionsAllowed = true;
    private boolean warpSharing = true;
    private String motd = "Welcome to our faction!";
    private String color = "GREEN";
    private int maxMembers = 20;
    private int maxWarps = 10;

    // Permission system
    private Map<String, Set<String>> permissions = new HashMap<>();

    // Warp access settings
    private WarpAccess warpAccess = WarpAccess.MEMBERS;
    private Set<String> warpWhitelist = new HashSet<>();

    public enum WarpAccess {
        LEADER_ONLY,
        OFFICERS,
        MEMBERS,
        ALLIES,
        PUBLIC
    }

    public FactionSettings() {
        // Default permissions
        permissions.put("leader", Set.of("*"));
        permissions.put("officer", Set.of("invite", "kick", "setwarp", "delwarp", "claim"));
        permissions.put("member", Set.of("warp", "chat", "deposit"));
    }

    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }

    public boolean isExplosionsAllowed() { return explosionsAllowed; }
    public void setExplosionsAllowed(boolean explosionsAllowed) { this.explosionsAllowed = explosionsAllowed; }

    public boolean isWarpSharing() { return warpSharing; }
    public void setWarpSharing(boolean warpSharing) { this.warpSharing = warpSharing; }

    public String getMotd() { return motd; }
    public void setMotd(String motd) { this.motd = motd; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }

    public int getMaxWarps() { return maxWarps; }
    public void setMaxWarps(int maxWarps) { this.maxWarps = maxWarps; }

    public Map<String, Set<String>> getPermissions() { return permissions; }
    public void setPermissions(Map<String, Set<String>> permissions) { this.permissions = permissions; }

    public boolean hasPermission(String rank, String action) {
        Set<String> rankPerms = permissions.get(rank);
        if (rankPerms == null) return false;
        return rankPerms.contains("*") || rankPerms.contains(action);
    }

    public WarpAccess getWarpAccess() { return warpAccess; }
    public void setWarpAccess(WarpAccess warpAccess) { this.warpAccess = warpAccess; }

    public Set<String> getWarpWhitelist() { return warpWhitelist; }
    public void setWarpWhitelist(Set<String> warpWhitelist) { this.warpWhitelist = warpWhitelist; }
}
