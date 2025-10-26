package io.github.flameyossnowy.universal.mongodb.codec;

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
 *
 * @param <T> the type to encode/decode
 */
public record MongoTypeCodec<T>(Class<T> type, TypeResolverRegistry typeResolverRegistry) implements Codec<T> {
    @Override
    public T decode(BsonReader reader, DecoderContext decoderContext) {
        Document document = Document.parse(reader.readString());
        MongoDatabaseResult result = new MongoDatabaseResult(document);
        return typeResolverRegistry.getResolver(type).resolve(result, "value");
    }

    @Override
    public void encode(BsonWriter writer, T value, EncoderContext encoderContext) {
        MongoDatabaseParameters parameters = new MongoDatabaseParameters(this.typeResolverRegistry);
        typeResolverRegistry.getResolver(type).insert(parameters, 0, value);
        Document document = parameters.toDocument();
        writer.writeString(document.toJson());
    }

    @Override
    public Class<T> getEncoderClass() {
        return type;
    }
}
