package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.JsonVersioned;
import io.github.flameyossnowy.universal.api.annotations.Named;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "postgres-json-versioned-entity")
public class PostgresJsonVersionedEntity {
    @Id
    private String id;

    @JsonField
    @JsonVersioned
    private Payload payload;

    @Named("payload_version")
    private Integer payloadVersion;

    public PostgresJsonVersionedEntity() {}

    public PostgresJsonVersionedEntity(String id, Payload payload, Integer payloadVersion) {
        this.id = id;
        this.payload = payload;
        this.payloadVersion = payloadVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    public Integer getPayloadVersion() {
        return payloadVersion;
    }

    public void setPayloadVersion(Integer payloadVersion) {
        this.payloadVersion = payloadVersion;
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
