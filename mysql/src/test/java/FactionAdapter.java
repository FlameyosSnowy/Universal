import io.github.flameyossnowy.universal.api.annotations.proxy.Filter;
import io.github.flameyossnowy.universal.api.annotations.proxy.Insert;
import io.github.flameyossnowy.universal.api.annotations.proxy.Select;

import java.time.Instant;
import java.util.List;

// proxy test
public interface FactionAdapter {
    @Select
    List<Faction> findAll();

    @Select
    @Filter(value = "factions.createdAt", operator = "<")
    List<Faction> findOlderThan(Instant instant);

    @Insert
    void insert(Faction faction);
}
