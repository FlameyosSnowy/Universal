package io.github.flameyossnowy.universal.microservices;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.Serializers;
import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Jackson module that bridges TypeResolvers with ObjectMapper serialization.
 * This allows TypeResolvers to handle custom type conversions in JSON.
 */
public final class TypeResolverJacksonModule extends SimpleModule {

    private static final String TEMP_COLUMN = "temp";

    private final TypeResolverRegistry resolverRegistry;

    public TypeResolverJacksonModule(TypeResolverRegistry resolverRegistry) {
        super("TypeResolverModule");
        this.resolverRegistry = resolverRegistry;
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addSerializers(new ResolverSerializers(resolverRegistry));
        context.addDeserializers(new ResolverDeserializers(resolverRegistry));
    }

    /* ------------------------------------------------------------ */
    /* Serializers                                                   */
    /* ------------------------------------------------------------ */

    private static final class ResolverSerializers extends Serializers.Base {
        private final TypeResolverRegistry registry;

        private ResolverSerializers(TypeResolverRegistry registry) {
            this.registry = registry;
        }

        @Override
        public JsonSerializer<?> findSerializer(
            SerializationConfig config,
            JavaType type,
            BeanDescription beanDesc
        ) {
            Class<?> raw = type.getRawClass();
            TypeResolver<?> resolver = registry.resolve(raw);

            if (resolver == null) return null;
            if (resolver.getDatabaseType().equals(raw)) return null;

            return new ResolverSerializer<>(resolver);
        }
    }

    private static final class ResolverSerializer<T> extends JsonSerializer<T> {
        private final TypeResolver<T> resolver;

        private ResolverSerializer(TypeResolver<T> resolver) {
            this.resolver = resolver;
        }

        @Override
        public void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {

            if (value == null) {
                gen.writeNull();
                return;
            }

            MockDatabaseParameters params = new MockDatabaseParameters();
            resolver.insert(params, TEMP_COLUMN, value);

            Object dbValue = params.value;

            if (dbValue == null) {
                gen.writeNull();
            } else if (dbValue instanceof String s) {
                gen.writeString(s);
            } else if (dbValue instanceof Integer i) {
                gen.writeNumber(i);
            } else if (dbValue instanceof Long l) {
                gen.writeNumber(l);
            } else if (dbValue instanceof Double d) {
                gen.writeNumber(d);
            } else if (dbValue instanceof Boolean b) {
                gen.writeBoolean(b);
            } else if (dbValue instanceof byte[] bytes) {
                gen.writeBinary(bytes);
            } else {
                gen.writeObject(dbValue);
            }
        }
    }

    /* ------------------------------------------------------------ */
    /* Deserializers                                                 */
    /* ------------------------------------------------------------ */

    private static final class ResolverDeserializers extends Deserializers.Base {
        private final TypeResolverRegistry registry;

        private ResolverDeserializers(TypeResolverRegistry registry) {
            this.registry = registry;
        }

        @Override
        public JsonDeserializer<?> findBeanDeserializer(
            JavaType type,
            DeserializationConfig config,
            BeanDescription beanDesc
        ) {
            Class<?> raw = type.getRawClass();
            TypeResolver<?> resolver = registry.resolve(raw);

            if (resolver == null) return null;
            if (resolver.getDatabaseType().equals(raw)) return null;

            return new ResolverDeserializer<>(resolver);
        }
    }

    private static final class ResolverDeserializer<T> extends JsonDeserializer<T> {
        private final TypeResolver<T> resolver;

        private ResolverDeserializer(TypeResolver<T> resolver) {
            this.resolver = resolver;
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

            if (p.currentToken() == JsonToken.VALUE_NULL) {
                return null;
            }

            Class<?> dbType = resolver.getDatabaseType();
            Object dbValue;

            if (dbType == String.class) {
                dbValue = p.getValueAsString();
            } else if (dbType == Integer.class || dbType == int.class) {
                dbValue = p.getValueAsInt();
            } else if (dbType == Long.class || dbType == long.class) {
                dbValue = p.getValueAsLong();
            } else if (dbType == Double.class || dbType == double.class) {
                dbValue = p.getValueAsDouble();
            } else if (dbType == Boolean.class || dbType == boolean.class) {
                dbValue = p.getValueAsBoolean();
            } else if (dbType == byte[].class) {
                dbValue = p.getBinaryValue();
            } else {
                dbValue = p.readValueAs(dbType);
            }

            return resolver.resolve(new MockDatabaseResult(dbValue), TEMP_COLUMN);
        }
    }

    /* ------------------------------------------------------------ */
    /* Mock DatabaseParameters                                       */
    /* ------------------------------------------------------------ */

    private static final class MockDatabaseParameters implements DatabaseParameters {
        private Object value;
        private boolean set;

        @Override
        public CollectionHandler getCollectionHandler() {
            return null;
        }

        @Override
        public String getAdapterType() {
            return "microservices";
        }

        @Override
        public boolean supportsArraysNatively() {
            return true;
        }

        @Override
        public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
            this.value = value;
            this.set = true;
        }

        @Override
        public <T> void setRaw(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
            this.value = value;
            this.set = true;
        }

        @Override
        public void setNull(@NotNull String name, @NotNull Class<?> type) {
            this.value = null;
            this.set = true;
        }

        @Override
        public int size() {
            return set ? 1 : 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public @Nullable <T> T get(int index, @NotNull Class<T> type) {
            if (index != 1 || !set) return null;
            return (T) value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public @Nullable <T> T get(@NotNull String name, @NotNull Class<T> type) {
            if (!TEMP_COLUMN.equals(name) || !set) return null;
            return (T) value;
        }

        @Override
        public boolean contains(@NotNull String name) {
            return set && TEMP_COLUMN.equals(name);
        }
    }

    /* ------------------------------------------------------------ */
    /* Mock DatabaseResult                                           */
    /* ------------------------------------------------------------ */

    private static final class MockDatabaseResult implements DatabaseResult {
        private final Object value;

        private MockDatabaseResult(Object value) {
            this.value = value;
        }

        @Override
        public CollectionHandler getCollectionHandler() {
            return null;
        }

        @Override
        public boolean supportsArraysNatively() {
            return true;
        }

        @Override
        public RepositoryModel<?, ?> repositoryModel() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(String columnName, Class<T> type) {
            if (!TEMP_COLUMN.equals(columnName)) return null;
            return (T) value;
        }

        @Override
        public boolean hasColumn(String columnName) {
            return TEMP_COLUMN.equals(columnName);
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getColumnName(int columnIndex) {
            if (columnIndex != 1) {
                throw new IndexOutOfBoundsException("Only one column");
            }
            return TEMP_COLUMN;
        }
    }
}