package io.github.flameyossnowy.universal.mongodb.resolvers;

import org.bson.types.Binary;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.time.Instant;
import java.util.*;

@SuppressWarnings("unused")
public class ValueTypeResolverRegistry {
    private final Map<Class<?>, MongoValueTypeResolver<?, ?>> resolvers = new HashMap<>();

    public ValueTypeResolverRegistry() {
        register(String.class, String.class, value -> value, value -> value);

        register(Integer.class, Integer.class, value -> value, value -> value);

        register(Long.class, Long.class, value -> value, value -> value);

        register(Float.class, Double.class, Float::doubleValue, Double::floatValue);

        register(Double.class, Double.class, value -> value, value -> value);

        register(int.class, int.class, value -> value, value -> value);

        register(long.class, long.class, value -> value, value -> value);

        register(float.class, double.class, Float::doubleValue, Double::floatValue);

        register(double.class, double.class, value -> value, value -> value);

        register(UUID.class, String.class, UUID::toString, UUID::fromString);

        register(Instant.class, Long.class, Instant::toEpochMilli, Instant::ofEpochMilli);

        register(Object.class, Binary.class,
                ValueTypeResolverRegistry::serializeObject,
                ValueTypeResolverRegistry::deserializeObject);
    }

    public <E, D> void register(Class<D> type, Class<E> encodedType, Encoder<E, D> resolver, Decoder<E, D> insert) {
        resolvers.put(type, new DefaultMongoValueTypeResolver<>(encodedType, resolver, insert));
    }

    public <E, D> void register(Class<D> type, MongoValueTypeResolver<E, D> resolver) {
        resolvers.put(type, resolver);
    }

    public <E, D> MongoValueTypeResolver<?, ?> getResolver(Class<D> type) {
        Objects.requireNonNull(type);
        if (type == Object.class) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }

        MongoValueTypeResolver<?, ?> resolver = resolvers.get(type);
        if (resolver != null) {
            return resolver;
        }

        if (Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Serializable.class.isAssignableFrom(type)) {
            return resolvers.get(Object.class);
        }

        return null;
    }

    @FunctionalInterface
    public interface Encoder<E, D> {
        E encode(D value);
    }

    @FunctionalInterface
    public interface Decoder<E, D> {
        D decode(E value);
    }

    private record DefaultMongoValueTypeResolver<E, D>(Class<E> encodedType, Encoder<E, D> resolver, Decoder<E, D> insertInt) implements MongoValueTypeResolver<E, D> {
        @Override
        public E encode(D value) {
            return resolver.encode(value);
        }

        @Override
        public D decode(E value) {
            return insertInt.decode( value);
        }
    }

    @Contract("_ -> new")
    private static @NotNull Binary serializeObject(Object value) {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ObjectOutputStream objectOut = new ObjectOutputStream(byteOut)) {
            objectOut.writeObject(value);
            return new Binary(byteOut.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    private static Object deserializeObject(@NotNull Binary binary) {
        byte[] array = binary.getData();
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(array);
             ObjectInputStream objectIn = new ObjectInputStream(byteIn)) {
            return objectIn.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }
}