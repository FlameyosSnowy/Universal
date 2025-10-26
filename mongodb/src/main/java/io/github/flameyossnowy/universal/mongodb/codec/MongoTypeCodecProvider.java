package io.github.flameyossnowy.universal.mongodb.codec;

import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * A CodecProvider that provides codecs for types registered in TypeResolverRegistry.
 */
public record MongoTypeCodecProvider(TypeResolverRegistry typeResolverRegistry, RepositoryInformation information) implements CodecProvider {
    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (typeResolverRegistry.hasResolver(clazz)) {
            return new MongoTypeCodec<>(clazz, typeResolverRegistry, information);
        }
        return null;
    }

    /**
     * Create a new instance with a custom TypeResolverRegistry.
     *
     * @param typeResolverRegistry the type resolver registry to use
     * @return a new instance
     */
    public static MongoTypeCodecProvider create(TypeResolverRegistry typeResolverRegistry, RepositoryInformation information) {
        return new MongoTypeCodecProvider(typeResolverRegistry, information);
    }
}
