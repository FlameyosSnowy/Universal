package io.github.flameyossnowy.universal.microservices.relationship;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.RepositoryRegistry;
import io.github.flameyossnowy.universal.api.annotations.OneToMany;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.matcher.ElementMatchers;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

public class RelationshipResolver {

    public <T> void resolve(T entity, @NotNull RepositoryInformation repositoryInformation) {
        for (FieldData<?> field : repositoryInformation.getFields()) {
            if (field.oneToMany() != null) {
                resolveOneToMany(entity, field, repositoryInformation);
            } else if (field.manyToOne() != null) {
                resolveManyToOne(entity, field);
            } else if (field.oneToOne() != null) {
                resolveOneToOne(entity, field);
            }
        }
    }

    private <T> void resolveOneToMany(T entity, FieldData<?> reflectField, RepositoryInformation repositoryInformation) {
        OneToMany annotation = reflectField.oneToMany();
        if (annotation.lazy()) {
            // Use Byte Buddy to create a lazy-loaded proxy
            try {
                LazyLoader loader = new LazyLoader(() -> fetchOneToMany(entity, reflectField, annotation, repositoryInformation));
                Object proxy = new ByteBuddy()
                        .subclass(reflectField.type())
                        .method(ElementMatchers.any())
                        .intercept(MethodDelegation.to(loader))
                        .make()
                        .load(getClass().getClassLoader())
                        .getLoaded()
                        .getDeclaredConstructor().newInstance();
                reflectField.setValue(entity, proxy);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create lazy proxy for OneToMany relationship", e);
            }
        } else {
            // Eagerly fetch the collection
            reflectField.setValue(entity, fetchOneToMany(entity, reflectField, annotation, repositoryInformation));
        }
    }

    private <T> void resolveManyToOne(T entity, FieldData<?> reflectField) {
        // Similar logic for ManyToOne, potentially lazy loading the single related entity
    }

    private <T> void resolveOneToOne(T entity, FieldData<?> reflectField) {
        // Similar logic for OneToOne
    }

    private static <T> List<?> fetchOneToMany(T entity, FieldData<?> reflectField, OneToMany annotation, RepositoryInformation repositoryInformation) {
        RepositoryAdapter<?, ?, ?> targetAdapter = RepositoryRegistry.get(annotation.mappedBy());
        if (targetAdapter == null) {
            throw new IllegalStateException("No repository found for entity: " + annotation.mappedBy().getName());
        }

        // This is a simplified assumption. Real implementation needs to know the foreign key field.
        String foreignKey = entity.getClass().getSimpleName().toLowerCase() + "_" + RepositoryMetadata.getMetadata(entity.getClass()).getPrimaryKey().name();
        Object id = repositoryInformation.getPrimaryKey().getValue(entity);

        SelectQuery query = new SelectQuery.SelectQueryBuilder().where(foreignKey, id).build();
        return targetAdapter.find(query);
    }

    public static class LazyLoader {
        private final Supplier<Object> supplier;
        private Object value;

        public LazyLoader(Supplier<Object> supplier) {
            this.supplier = supplier;
        }

        public Object intercept(@Origin Method method) throws Exception {
            if (value == null) {
                value = supplier.get();
            }
            return method.invoke(value);
        }
    }
}
