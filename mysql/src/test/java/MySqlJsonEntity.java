import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "mysql-json-entity")
public class MySqlJsonEntity {
    @Id
    private String id;

    @JsonField(codec = MySqlPayloadCodec.class)
    private Payload payload;

    public MySqlJsonEntity() {}

    public MySqlJsonEntity(String id, Payload payload) {
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
