package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.UUID;

@Repository(name = "one_to_one_warps")
public class OneToOneWarpUuid {
    @Id
    private UUID id;

    private String name;

    @OneToOne
    private OneToOneFactionUuid faction;

    public OneToOneWarpUuid() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OneToOneFactionUuid getFaction() {
        return faction;
    }

    public void setFaction(OneToOneFactionUuid faction) {
        this.faction = faction;
    }
}
