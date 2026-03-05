package io.github.flameyossnowy.universal.postgresql.jmh;

import io.github.flameyossnowy.universal.api.options.Query;
import org.hibernate.Session;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Category: SELECT
 *
 * Benchmarks:
 *   findAll      – hydrate all 1 000 seeded rows
 *   findById     – single-row PK lookup
 *   findFiltered – WHERE score > 250 AND active = true  LIMIT 100
 *   count        – COUNT(*) WHERE active = true
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Thread)
public class SelectBenchmark {

    private BenchmarkState state;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        state = new BenchmarkState();
        state.setup();
    }

    @TearDown(Level.Trial)
    public void teardown() {
        state.teardown();
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Benchmark
    public void universal_findAll(Blackhole bh) {
        List<BenchmarkEntity> list = state.entityAdapter.find();
        bh.consume(list);
    }

    @Benchmark
    public void hibernate_findAll(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            List<BenchmarkEntity> list =
                s.createQuery("FROM BenchmarkEntity", BenchmarkEntity.class).list();
            bh.consume(list);
        }
    }

    @Benchmark
    public void jooq_findAll(Blackhole bh) {
        List<Map<String, Object>> list =
            state.dsl.selectFrom(table("benchmark_entities")).fetchMaps();
        bh.consume(list);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Benchmark
    public void universal_findById(Blackhole bh) {
        BenchmarkEntity e = state.entityAdapter.findById(42L);
        bh.consume(e);
    }

    @Benchmark
    public void hibernate_findById(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            BenchmarkEntity e = s.get(BenchmarkEntity.class, 42L);
            bh.consume(e);
        }
    }

    @Benchmark
    public void jooq_findById(Blackhole bh) {
        var r = state.dsl
            .selectFrom(table("benchmark_entities"))
            .where(field("id").eq(42L))
            .fetchOne();
        bh.consume(r);
    }

    // ── findFiltered ──────────────────────────────────────────────────────────

    @Benchmark
    public void universal_findFiltered(Blackhole bh) {
        List<BenchmarkEntity> list = state.entityAdapter.find(
            Query.select()
                .where("score").gt(250)
                .where("active").eq(true)
                .limit(100)
                .build()
        );
        bh.consume(list);
    }

    @Benchmark
    public void hibernate_findFiltered(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            List<BenchmarkEntity> list = s
                .createQuery(
                    "FROM BenchmarkEntity WHERE score > 250 AND active = true",
                    BenchmarkEntity.class)
                .setMaxResults(100)
                .list();
            bh.consume(list);
        }
    }

    @Benchmark
    public void jooq_findFiltered(Blackhole bh) {
        List<Map<String, Object>> list = state.dsl
            .selectFrom(table("benchmark_entities"))
            .where(field("score").gt(250).and(field("active").eq(true)))
            .limit(100)
            .fetchMaps();
        bh.consume(list);
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Benchmark
    public void universal_count(Blackhole bh) {
        long n = state.entityAdapter.count(
            Query.select().where("active").eq(true).build()
        );
        bh.consume(n);
    }

    @Benchmark
    public void hibernate_count(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            long n = s.createQuery(
                "SELECT COUNT(e) FROM BenchmarkEntity e WHERE e.active = true",
                Long.class)
                .uniqueResult();
            bh.consume(n);
        }
    }

    @Benchmark
    public void jooq_count(Blackhole bh) {
        int n = state.dsl.selectCount()
            .from(table("benchmark_entities"))
            .where(field("active").eq(true))
            .fetchOne(0, int.class);
        bh.consume(n);
    }
}
