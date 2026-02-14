package io.github.flameyossnowy.universal.mongodb.codec;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A CodecProvider that provides codecs for types registered in TypeResolverRegistry.
 */
public record MongoTypeCodecProvider(TypeResolverRegistry typeResolverRegistry, CollectionHandler collectionHandler, RepositoryModel<?, ?> information) implements CodecProvider {
    @Override
    public <T> @Nullable Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (typeResolverRegistry.hasResolver(clazz)) {
            return new MongoTypeCodec<>(clazz, typeResolverRegistry, collectionHandler, information);
        }
        return null;
    }

    /**
     * Create a new instance with a custom TypeResolverRegistry.
     *
     * @param typeResolverRegistry the type resolver registry to use
     * @return a new instance
     */
    @Contract("_, _ -> new")
    public static @NotNull MongoTypeCodecProvider create(TypeResolverRegistry typeResolverRegistry, CollectionHandler collectionHandler, RepositoryModel<?, ?> information) {
        return new MongoTypeCodecProvider(typeResolverRegistry, collectionHandler, information);
    }
}
