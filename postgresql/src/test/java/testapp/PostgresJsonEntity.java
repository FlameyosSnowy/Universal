package testapp;

import io.github.flameyossnowy.universal.api.annotations.Id;
import io.github.flameyossnowy.universal.api.annotations.JsonField;
import io.github.flameyossnowy.universal.api.annotations.JsonIndex;
import io.github.flameyossnowy.universal.api.annotations.Repository;

@Repository(name = "postgres-json-entity")
public class PostgresJsonEntity {
    @Id
    private String id;

    @JsonField(codec = PostgresPayloadCodec.class, queryable = true)
    @JsonIndex(path = "$.n", unique = false)
    private Payload payload;

    @JsonField
    private PayloadWithNulls payloadWithNulls;

    public PostgresJsonEntity() {}

    public PostgresJsonEntity(String id, Payload payload) {
        this.id = id;
        this.payload = payload;
    }

    public PostgresJsonEntity(String id, Payload payload, PayloadWithNulls payloadWithNulls) {
        this.id = id;
        this.payload = payload;
        this.payloadWithNulls = payloadWithNulls;
    }

    public String getId() {
        return id;
    }

    public Payload getPayload() {
        return payload;
    }

    public PayloadWithNulls getPayloadWithNulls() {
        return payloadWithNulls;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    public void setPayloadWithNulls(PayloadWithNulls payloadWithNulls) {
        this.payloadWithNulls = payloadWithNulls;
    }
}
