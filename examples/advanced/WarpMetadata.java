package examples.advanced;

import java.util.HashSet;
import java.util.Set;

/**
 * Metadata class for Warp - stored as JSON.
 * Demonstrates JSON field serialization.
 */
public class WarpMetadata {
    private Set<String> tags = new HashSet<>();
    private String description = "";
    private String icon = "DEFAULT";
    private Set<String> allowedPlayers = new HashSet<>();
    private CustomProperties customProperties = new CustomProperties();

    public static class CustomProperties {
        private boolean particlesEnabled = true;
        private String particleType = "PORTAL";
        private double particleRadius = 1.0;

        public boolean isParticlesEnabled() { return particlesEnabled; }
        public void setParticlesEnabled(boolean particlesEnabled) { this.particlesEnabled = particlesEnabled; }

        public String getParticleType() { return particleType; }
        public void setParticleType(String particleType) { this.particleType = particleType; }

        public double getParticleRadius() { return particleRadius; }
        public void setParticleRadius(double particleRadius) { this.particleRadius = particleRadius; }
    }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }
    public void addTag(String tag) { this.tags.add(tag); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Set<String> getAllowedPlayers() { return allowedPlayers; }
    public void setAllowedPlayers(Set<String> allowedPlayers) { this.allowedPlayers = allowedPlayers; }
    public void allowPlayer(String playerId) { this.allowedPlayers.add(playerId); }

    public CustomProperties getCustomProperties() { return customProperties; }
    public void setCustomProperties(CustomProperties customProperties) { this.customProperties = customProperties; }
}
