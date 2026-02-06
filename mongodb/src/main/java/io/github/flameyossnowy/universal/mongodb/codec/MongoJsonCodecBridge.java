package io.github.flameyossnowy.universal.mongodb.codec;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class MongoJsonCodecBridge {

    private MongoJsonCodecBridge() {
        throw new AssertionError("No instances");
    }

    /**
     * Converts a JSON string (which may represent an object, array, string, number, boolean, or null)
     * into a MongoDB-driver-friendly Java value.
     */
    public static @Nullable Object jsonToBsonFriendly(@Nullable String json) {
        if (json == null) return null;

        // Wrap to support non-object JSON values.
        BsonDocument wrapper = BsonDocument.parse("{\"v\":" + json + "}");
        BsonValue value = wrapper.get("v");
        return bsonValueToJava(value);
    }

    private static @Nullable Object bsonValueToJava(@Nullable BsonValue value) {
        if (value == null || value instanceof BsonNull) return null;

        return switch (value.getBsonType()) {
            case DOCUMENT -> Document.parse(value.asDocument().toJson());
            case ARRAY -> bsonArrayToList(value.asArray());
            case STRING -> ((BsonString) value).getValue();
            case BOOLEAN -> ((BsonBoolean) value).getValue();
            case INT32 -> ((BsonInt32) value).getValue();
            case INT64 -> ((BsonInt64) value).getValue();
            case DOUBLE -> ((BsonDouble) value).getValue();
            case NULL -> null;
            default -> value;
        };
    }

    private static @NotNull List<Object> bsonArrayToList(@NotNull BsonArray array) {
        List<BsonValue> values = array.getValues();
        List<Object> out = new ArrayList<>(values.size());
        for (BsonValue v : values) {
            out.add(bsonValueToJava(v));
        }
        return out;
    }
}
