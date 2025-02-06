package io.github.flameyossnowy.universal.mongodb.resolvers;

public interface MongoValueTypeResolver<E, D> {
    E encode(D decoded);

    D decode(E encoded);

    Class<E> encodedType();
}
