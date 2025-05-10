import io.github.flameyossnowy.universal.api.annotations.AutoIncrement;
import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.OneToOne;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "Warps")
public record Warp(@Id @AutoIncrement long id, String name, @OneToOne Faction faction) {
}
