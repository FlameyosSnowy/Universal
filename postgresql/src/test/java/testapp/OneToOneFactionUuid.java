package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

import java.util.UUID;

@Repository(name = "one_to_one_factions")
public class OneToOneFactionUuid {
    @Id
    private UUID id;

    private String name;

    @OneToOne(mappedBy = "faction")
    private OneToOneWarpUuid warp;

    public OneToOneFactionUuid() {
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

    public OneToOneWarpUuid getWarp() {
        return warp;
    }

    public void setWarp(OneToOneWarpUuid warp) {
        this.warp = warp;
    }
}
