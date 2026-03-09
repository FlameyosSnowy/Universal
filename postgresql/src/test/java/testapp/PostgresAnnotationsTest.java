package testapp;

import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.sql.internals.QueryParseEngine;
import io.github.flameyossnowy.universal.sql.internals.SQLConnectionProvider;
import io.github.flameyossnowy.universal.sql.internals.query.RepositoryDdlBuilder;
import io.github.flameyossnowy.universal.sql.internals.repository.SqlParameterBinder;
import io.github.flameyossnowy.universal.sql.params.SQLDatabaseParameters;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class PostgresAnnotationsTest {

    @Test
    void ddl_builder_includes_on_delete_and_on_update_actions_for_many_to_one() {
        ModelsBootstrap.init();

        RepositoryModel<AnnotatedChild, Long> childModel =
            io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(AnnotatedChild.class);
        assertNotNull(childModel);

        TypeResolverRegistry resolverRegistry = new TypeResolverRegistry();
        CapturingConnectionProvider provider  = new CapturingConnectionProvider();

        RepositoryDdlBuilder<AnnotatedChild, Long> ddlBuilder = new RepositoryDdlBuilder<>(
            QueryParseEngine.SQLType.POSTGRESQL,
            childModel,
            resolverRegistry,
            provider
        );

        String ddl = ddlBuilder.parseRepository(true);

        assertNotNull(provider.lastSql, "DDL builder must have executed a statement");
        assertEquals(ddl, provider.lastSql);

        assertTrue(ddl.contains("FOREIGN KEY (parent)"),    "DDL must declare the FK column");
        assertTrue(ddl.contains("ON DELETE RESTRICT"),      "DDL must include ON DELETE RESTRICT");
        assertTrue(ddl.contains("ON UPDATE NO_ACTION"),     "DDL must include ON UPDATE NO_ACTION");
    }

    @Test
    void sql_parameter_binder_sets_now_value_on_update_and_mutates_entity() {
        ModelsBootstrap.init();

        RepositoryModel<AnnotatedNowEntity, Long> model =
            io.github.flameyossnowy.universal.api.meta.GeneratedMetadata.getByEntityClass(AnnotatedNowEntity.class);
        assertNotNull(model, "testapp.AnnotatedNowEntity must be registered in GeneratedMetadata");

        TypeResolverRegistry registry = new TypeResolverRegistry();

        String sql = "UPDATE annotated_now_entity SET updatedAt = ?, name = ? WHERE id = ?";

        CapturingParameters params = new CapturingParameters(
            (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                }
            ),
            registry,
            sql,
            model
        );

        SqlParameterBinder<AnnotatedNowEntity, Long> binder = new SqlParameterBinder<>();

        AnnotatedNowEntity entity = new AnnotatedNowEntity();
        entity.setName("x");
        assertNull(entity.getUpdatedAt(), "updatedAt must be null before binder runs");

        binder.setUpdateParameters(params, entity, model, registry);

        // ── Entity mutation ───────────────────────────────────────────────────
        assertNotNull(entity.getUpdatedAt(),
            "@Now field must be set on the entity by the binder");
        assertInstanceOf(Instant.class, entity.getUpdatedAt(),
            "@Now field must be an Instant");

        // ── Parameter binding ─────────────────────────────────────────────────
        assertTrue(params.bound.containsKey("updatedAt"),
            "binder must have bound the 'updatedAt' parameter");
        System.out.println(params.bound.get("updatedAt"));

        assertTrue(params.bound.containsKey("name"),
            "binder must have bound the 'name' parameter");
        assertEquals("x", params.bound.get("name"),
            "bound value for 'name' must match the entity value");

        // id is @AutoIncrement — must NOT be bound
        assertFalse(params.bound.containsKey("id"),
            "@AutoIncrement field must not be bound as an update parameter");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final class CapturingConnectionProvider implements SQLConnectionProvider {
        String lastSql;

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                }
            );
        }

        @Override
        public void close() {}

        @Override
        public PreparedStatement prepareStatement(String sql, Connection connection) throws Exception {
            this.lastSql = sql;
            return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("executeUpdate")) return 0;
                    if (method.getName().equals("close")) return null;
                    return defaultValue(method.getReturnType());
                }
            );
        }
    }

    private static final class CapturingParameters extends SQLDatabaseParameters {
        /** All parameters bound during this session, in insertion order. */
        final Map<String, Object> bound = new LinkedHashMap<>();

        CapturingParameters(
            PreparedStatement statement,
            TypeResolverRegistry registry,
            String sql,
            RepositoryModel<?, ?> model
        ) {
            super(statement, registry, sql, model,
                (io.github.flameyossnowy.universal.api.handler.CollectionHandler) Proxy.newProxyInstance(
                    io.github.flameyossnowy.universal.api.handler.CollectionHandler.class.getClassLoader(),
                    new Class<?>[] { io.github.flameyossnowy.universal.api.handler.CollectionHandler.class },
                    (proxy, method, args) -> defaultValue(method.getReturnType())
                ),
                false
            );
        }

        @Override
        public <T> void set(String name, T value, Class<?> type) {
            setRaw(name, value, type);
        }

        @Override
        public <T> void setRaw(String name, T value, Class<?> type) {
            bound.put(name, value);
        }

        @Override
        public void setNull(String name, Class<?> type) {
            bound.put(name, null);
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) return null;
        if (returnType == boolean.class) return false;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == short.class) return (short) 0;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        if (returnType == char.class) return (char) 0;
        return null;
    }
}