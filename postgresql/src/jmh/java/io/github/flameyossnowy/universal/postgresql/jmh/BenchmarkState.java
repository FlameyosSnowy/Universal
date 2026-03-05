package io.github.flameyossnowy.universal.postgresql.jmh;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.flameyossnowy.universal.api.ModelsBootstrap;
import io.github.flameyossnowy.universal.api.Optimizations;
import io.github.flameyossnowy.universal.postgresql.PostgreSQLRepositoryAdapter;
import io.github.flameyossnowy.universal.postgresql.credentials.PostgreSQLCredentials;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.openjdk.jmh.annotations.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Trial-scoped JMH state.
 *
 * All three frameworks share one HikariCP pool so the measurement captures only
 * framework overhead, not connection management. The schema is rebuilt once per
 * trial run and pre-seeded with realistic data.
 *
 * Pass DB coordinates at Gradle time:
 *   ./gradlew jmh -Pbenchmark.host=myhost -Pbenchmark.password=mypass
 */
@State(Scope.Benchmark)
public class BenchmarkState {

    public static final String HOST     = System.getProperty("benchmark.host",     "localhost");
    public static final int    PORT     = Integer.parseInt(System.getProperty("benchmark.port", "5432"));
    public static final String DATABASE = System.getProperty("benchmark.db",       "test");
    public static final String USER     = System.getProperty("benchmark.user",     "postgres");
    public static final String PASSWORD = System.getProperty("benchmark.password", "secret");

    // Shared pool
    public HikariDataSource pool;

    // Hibernate
    public SessionFactory sessionFactory;

    // jOOQ
    public DSLContext dsl;

    // Universal
    public PostgreSQLRepositoryAdapter<BenchmarkEntity, Long> entityAdapter;
    public PostgreSQLRepositoryAdapter<BenchmarkParent, Long> parentAdapter;
    public PostgreSQLRepositoryAdapter<BenchmarkChild,  Long> childAdapter;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        ModelsBootstrap.init();
        pool = buildPool();
        resetSchema(pool);

        // Universal
        PostgreSQLCredentials creds = new PostgreSQLCredentials(HOST, PORT, DATABASE, USER, PASSWORD);
        entityAdapter = PostgreSQLRepositoryAdapter.builder(BenchmarkEntity.class, Long.class)
            .withCredentials(creds).withOptimizations(Optimizations.RECOMMENDED_SETTINGS).build();
        parentAdapter = PostgreSQLRepositoryAdapter.builder(BenchmarkParent.class, Long.class)
            .withCredentials(creds).withOptimizations(Optimizations.RECOMMENDED_SETTINGS).build();
        childAdapter = PostgreSQLRepositoryAdapter.builder(BenchmarkChild.class, Long.class)
            .withCredentials(creds).withOptimizations(Optimizations.RECOMMENDED_SETTINGS).build();

        entityAdapter.createRepository(false);
        parentAdapter.createRepository(false);
        childAdapter.createRepository(false);

        // Hibernate
        Properties hp = new Properties();
        hp.put("hibernate.connection.datasource", pool);
        hp.put("hibernate.dialect",               "org.hibernate.dialect.PostgreSQLDialect");
        hp.put("hibernate.hbm2ddl.auto",          "none");
        hp.put("hibernate.show_sql",              "false");
        hp.put("hibernate.format_sql",            "false");
        hp.put("hibernate.jdbc.batch_size",       "50");
        hp.put("hibernate.order_inserts",         "true");
        hp.put("hibernate.generate_statistics",   "false");
        sessionFactory = new Configuration()
            .addProperties(hp)
            .addAnnotatedClass(BenchmarkEntity.class)
            .addAnnotatedClass(BenchmarkParent.class)
            .addAnnotatedClass(BenchmarkChild.class)
            .buildSessionFactory();

        // jOOQ
        dsl = DSL.using(pool, SQLDialect.POSTGRES);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        if (sessionFactory != null) sessionFactory.close();
        if (pool != null)           pool.close();
    }

    private static HikariDataSource buildPool() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:postgresql://" + HOST + ":" + PORT + "/" + DATABASE);
        cfg.setUsername(USER);
        cfg.setPassword(PASSWORD);
        cfg.setMaximumPoolSize(16);
        cfg.setMinimumIdle(4);
        cfg.setConnectionTimeout(5_000);
        cfg.setAutoCommit(true);
        return new HikariDataSource(cfg);
    }

    static void resetSchema(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS benchmark_children CASCADE");
            s.execute("DROP TABLE IF EXISTS benchmark_parents  CASCADE");
            s.execute("DROP TABLE IF EXISTS benchmark_entities CASCADE");

            s.execute("""
                CREATE TABLE benchmark_entities (
                    id     BIGSERIAL PRIMARY KEY,
                    name   TEXT      NOT NULL,
                    score  INT       NOT NULL DEFAULT 0,
                    active BOOLEAN   NOT NULL DEFAULT TRUE,
                    tags   TEXT[]
                )
            """);
            s.execute("""
                CREATE TABLE benchmark_parents (
                    id    BIGSERIAL PRIMARY KEY,
                    label TEXT      NOT NULL
                )
            """);
            s.execute("""
                CREATE TABLE benchmark_children (
                    id        BIGSERIAL PRIMARY KEY,
                    parent_id BIGINT    NOT NULL REFERENCES benchmark_parents(id),
                    value     TEXT      NOT NULL
                )
            """);

            // 100 parents · 10 children each · 1 000 entities
            s.execute("INSERT INTO benchmark_parents (label) SELECT 'parent-'||g FROM generate_series(1,100) g");
            s.execute("INSERT INTO benchmark_children (parent_id, value) SELECT (g%100)+1,'child-'||g FROM generate_series(1,1000) g");
            s.execute("""
                INSERT INTO benchmark_entities (name, score, active, tags)
                SELECT 'entity-'||g, g%500, (g%2=0), ARRAY['tag-'||(g%5),'tag-'||(g%7)]
                FROM generate_series(1,1000) g
            """);
        }
    }
}
