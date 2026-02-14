import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "postgres-json-entity")
public class PostgresJsonEntity {
    @Id
    private String id;

    @JsonField(codec = PostgresPayloadCodec.class)
    private Payload payload;

    public PostgresJsonEntity() {}

    public PostgresJsonEntity(String id, Payload payload) {
        this.id = id;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    public static class Payload {
        private String name;
        private int age;

        public Payload() {}

        public Payload(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }
}
