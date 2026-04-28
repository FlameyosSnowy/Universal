package testapp;

import io.github.flameyossnowy.universal.api.annotations.*;

@Repository(name = "combined_entity")
public class CombinedEntity {

    @Id
    private String id;

    @JsonField
    @Named("custom_payload")
    private Payload payload;

    public CombinedEntity() {}

    public CombinedEntity(String id, Payload payload) {
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
        private String value;

        public Payload() {}

        public Payload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}