import io.github.flameyossnowy.universal.api.annotations.*;

import java.util.UUID;

@Repository(name = "FactionsRecord")
public record FactionRecord(@Id @AutoIncrement UUID id, String name) {}
