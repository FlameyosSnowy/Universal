package io.github.flameyossnowy.universal.mongodb.codec;

import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.mongodb.params.MongoDatabaseParameters;
import io.github.flameyossnowy.universal.mongodb.result.MongoDatabaseResult;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

/**
 * MongoDB Codec that uses TypeResolverRegistry for serialization/deserialization.
 * <p>
 * This codec uses RepositoryInformation to determine the actual field name for the type
 * being encoded/decoded, ensuring proper column name tracking instead of using generic "value".
 * <p>
 * Field context can be passed via {@link #setCurrentFieldName(String)} using ThreadLocal
 * to support accurate field-aware serialization even when multiple fields share the same type.
 *
 * @param <T>         the type to encode/decode
 * @param typeResolverRegistry the type resolver registry
 * @param information the repository information containing field metadata
 */
public record MongoTypeCodec<T>(Class<T> type, TypeResolverRegistry typeResolverRegistry,
                                RepositoryInformation information) implements Codec<T> {
    
    /**
     * ThreadLocal to pass field context from ObjectFactory to the codec.
     * This allows accurate thread-safe field name tracking even when multiple fields share the same type.
     */
    private static final ThreadLocal<String> CURRENT_FIELD_NAME = new ThreadLocal<>();
    
    /**
     * Sets the current field name being serialized/deserialized.
     * This should be called before encoding/decoding a field value.
     * 
     * @param fieldName the field name, or null to clear
     */
    public static void setCurrentFieldName(String fieldName) {
        if (fieldName == null) {
            CURRENT_FIELD_NAME.remove();
        } else {
            CURRENT_FIELD_NAME.set(fieldName);
        }
    }
    
    /**
     * Gets the current field name being serialized/deserialized.
     * 
     * @return the field name, or null if not set
     */
    public static String getCurrentFieldName() {
        return CURRENT_FIELD_NAME.get();
    }

    @Override
    public T decode(BsonReader reader, DecoderContext decoderContext) {
        Document document = Document.parse(reader.readString());
        MongoDatabaseResult result = new MongoDatabaseResult(document);
        
        // Find the field name for this type from repository information
        String columnName = getColumnNameForType();
        return typeResolverRegistry.getResolver(type).resolve(result, columnName);
    }

    @Override
    public void encode(BsonWriter writer, T value, EncoderContext encoderContext) {
        MongoDatabaseParameters parameters = new MongoDatabaseParameters(this.typeResolverRegistry);
        
        // Find the field name for this type from repository information
        String columnName = getColumnNameForType();
        typeResolverRegistry.getResolver(type).insert(parameters, columnName, value);
        
        Document document = parameters.toDocument();
        writer.writeString(document.toJson());
    }

    @Override
    public Class<T> getEncoderClass() {
        return type;
    }
    
    /**
     * Finds the column name for the type being encoded/decoded.
     * <p>
     * Priority order:
     * 1. ThreadLocal field name (set by ObjectFactory during serialization)
     * 2. Single field of this type in the repository
     * 3. Type's simple name in lowercase as fallback
     * 
     * @return the field name to use for serialization
     */
    private String getColumnNameForType() {
        // First priority: check if field name was explicitly set via ThreadLocal
        String currentFieldName = CURRENT_FIELD_NAME.get();
        if (currentFieldName != null) {
            return currentFieldName;
        }
        
        // Second priority: find field by type (only if unambiguous)
        FieldData<?> matchedField = null;
        int matchCount = 0;
        
        for (FieldData<?> field : information.getFields()) {
            if (field.type().equals(type)) {
                matchedField = field;
                matchCount++;
                if (matchCount > 1) {
                    // Multiple fields of the same type - ambiguous
                    break;
                }
            }
        }
        
        // If exactly one field matches, use its name
        if (matchCount == 1 && matchedField != null) {
            return matchedField.name();
        }
        
        // Fallback: use the type's simple name in lowercase
        return type.getSimpleName().toLowerCase();
    }
}
