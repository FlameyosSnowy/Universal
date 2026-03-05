package io.github.flameyossnowy.universal.postgresql.jmh;

import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SortOrder;
import org.hibernate.Session;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.*;

/**
 * Category: AGGREGATION
 *
 * Benchmarks:
 *   groupByCount  – GROUP BY name, COUNT(*), ORDER BY count DESC LIMIT 10
 *   sumFiltered   – SUM(score) WHERE active = true
 *   havingFilter  – GROUP BY ... HAVING COUNT(*) > 1
 *
 * Universal uses its fluent Query.aggregate() API (as shown in PostgreSQLAggregationTest).
 * Hibernate uses raw HQL aggregation.
 * jOOQ uses its type-safe DSL.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Thread)
public class AggregationBenchmark {

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

    // ── GROUP BY + COUNT + ORDER + LIMIT ──────────────────────────────────────

    @Benchmark
    public void universal_groupByCount(Blackhole bh) {
        List<Map<String, Object>> rows = state.entityAdapter.aggregate(
            Query.aggregate()
                .select(
                    Query.field("name"),
                    Query.field("id").count().as("cnt")
                )
                .groupBy("name")
                .orderBy("cnt", SortOrder.DESCENDING)
                .limit(10)
                .build()
        );
        bh.consume(rows);
    }

    @Benchmark
    public void hibernate_groupByCount(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            List<Object[]> rows = s.createQuery(
                "SELECT e.name, COUNT(e.id) AS cnt " +
                "FROM BenchmarkEntity e " +
                "GROUP BY e.name " +
                "ORDER BY cnt DESC", Object[].class)
                .setMaxResults(10)
                .list();
            bh.consume(rows);
        }
    }

    @Benchmark
    public void jooq_groupByCount(Blackhole bh) {
        var result = state.dsl
            .select(field("name"), count(field("id")).as("cnt"))
            .from(table("benchmark_entities"))
            .groupBy(field("name"))
            .orderBy(field("cnt").desc())
            .limit(10)
            .fetch();
        bh.consume(result);
    }

    // ── SUM WHERE ─────────────────────────────────────────────────────────────

    @Benchmark
    public void universal_sumFiltered(Blackhole bh) {
        List<Map<String, Object>> rows = state.entityAdapter.aggregate(
            Query.aggregate()
                .select(Query.field("score").sum().as("total"))
                .where("active").eq(true)
                .build()
        );
        bh.consume(rows);
    }

    @Benchmark
    public void hibernate_sumFiltered(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            Long total = s.createQuery(
                "SELECT SUM(e.score) FROM BenchmarkEntity e WHERE e.active = true",
                Long.class)
                .uniqueResult();
            bh.consume(total);
        }
    }

    @Benchmark
    public void jooq_sumFiltered(Blackhole bh) {
        Long total = state.dsl
            .select(sum(field("score", Long.class)))
            .from(table("benchmark_entities"))
            .where(field("active").eq(true))
            .fetchOne(0, Long.class);
        bh.consume(total);
    }

    // ── HAVING ────────────────────────────────────────────────────────────────

    @Benchmark
    public void universal_having(Blackhole bh) {
        var builder = Query.aggregate()
            .select(
                Query.field("name"),
                Query.field("id").count().as("cnt")
            )
            .groupBy("name");

        builder.having().field("id").count().gt(2);

        List<Map<String, Object>> rows = state.entityAdapter.aggregate(
            builder.orderBy("cnt", SortOrder.DESCENDING).build()
        );
        bh.consume(rows);
    }

    @Benchmark
    public void hibernate_having(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            List<Object[]> rows = s.createQuery(
                "SELECT e.name, COUNT(e.id) AS cnt " +
                "FROM BenchmarkEntity e " +
                "GROUP BY e.name " +
                "HAVING COUNT(e.id) > 2 " +
                "ORDER BY cnt DESC", Object[].class)
                .list();
            bh.consume(rows);
        }
    }

    @Benchmark
    public void jooq_having(Blackhole bh) {
        var result = state.dsl
            .select(field("name"), count(field("id")).as("cnt"))
            .from(table("benchmark_entities"))
            .groupBy(field("name"))
            .having(count(field("id")).gt(2))
            .orderBy(field("cnt").desc())
            .fetch();
        bh.consume(result);
    }
}
