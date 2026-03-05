package io.github.flameyossnowy.universal.postgresql.jmh;

import io.github.flameyossnowy.universal.api.options.Query;
import org.hibernate.Session;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Category: RELATIONSHIPS
 *
 * This is the most important category – it exposes the N+1 problem and
 * demonstrates how each framework mitigates it.
 *
 * Benchmarks:
 *   nPlusOne_*        – naive lazy load (deliberately bad, shows baseline cost)
 *   prefetch_*        – batch-load children in 2 queries
 *   joinFetch_*       – single SQL JOIN query
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Thread)
public class RelationshipBenchmark {

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

    // ════════════════════════════════════════════════════════════════════════
    //  N+1 LOAD  (deliberately bad baseline)
    // ════════════════════════════════════════════════════════════════════════

    /** Universal: load 100 parents, then call getChildren() on each (N+1). */
    @Benchmark
    public void universal_nPlusOne(Blackhole bh) {
        List<BenchmarkParent> parents = state.parentAdapter.find(
            Query.select().limit(100).build()
        );
        for (BenchmarkParent p : parents) {
            List<BenchmarkChild> children = state.childAdapter.find(
                Query.select().where("parent_id").eq(p.getId()).build()
            );
            bh.consume(children);
        }
        bh.consume(parents);
    }

    /** Hibernate: open-session N+1 with LAZY children. */
    @Benchmark
    public void hibernate_nPlusOne(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            List<BenchmarkParent> parents = s
                .createQuery("FROM BenchmarkParent", BenchmarkParent.class)
                .setMaxResults(100)
                .list();
            for (BenchmarkParent p : parents) {
                // Triggers a SELECT per parent
                int size = p.getChildren().size();
                bh.consume(size);
            }
            bh.consume(parents);
        }
    }

    /** jOOQ: two separate queries (manual N+1). */
    @Benchmark
    public void jooq_nPlusOne(Blackhole bh) {
        List<Map<String, Object>> parents = state.dsl
            .selectFrom(table("benchmark_parents"))
            .limit(100)
            .fetchMaps();
        for (Map<String, Object> p : parents) {
            List<Map<String, Object>> children = state.dsl
                .selectFrom(table("benchmark_children"))
                .where(field("parent_id").eq(p.get("id")))
                .fetchMaps();
            bh.consume(children);
        }
        bh.consume(parents);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  JOIN FETCH  (single-query solution)
    // ════════════════════════════════════════════════════════════════════════

    /** Universal: single JOIN via raw query executor. */
    @Benchmark
    public void universal_joinFetch(Blackhole bh) {
        // Universal exposes raw SQL for join scenarios not covered by the
        // high-level API (consistent with how the framework is used in practice)
        List<?> rows = state.parentAdapter.getQueryExecutor().executeQuery(
            "SELECT p.id, p.label, c.id AS cid, c.value " +
            "FROM benchmark_parents p " +
            "JOIN benchmark_children c ON c.parent_id = p.id " +
            "LIMIT 1000"
        );
        bh.consume(rows);
    }

    /** Hibernate: FETCH JOIN in a single HQL query. */
    @Benchmark
    public void hibernate_joinFetch(Blackhole bh) {
        try (Session s = state.sessionFactory.openSession()) {
            // JOIN FETCH prevents the N+1 and loads everything in one SQL
            List<BenchmarkParent> parents = s
                .createQuery(
                    "SELECT DISTINCT p FROM BenchmarkParent p LEFT JOIN FETCH p.children",
                    BenchmarkParent.class)
                .list();
            bh.consume(parents);
        }
    }

    /** jOOQ: single JOIN query, idiomatic jOOQ style. */
    @Benchmark
    public void jooq_joinFetch(Blackhole bh) {
        var result = state.dsl
            .select(
                field("p.id"),   field("p.label"),
                field("c.id"),   field("c.value")
            )
            .from(table("benchmark_parents").as("p"))
            .join(table("benchmark_children").as("c"))
              .on(field("c.parent_id").eq(field("p.id")))
            .limit(1000)
            .fetch();
        bh.consume(result);
    }
}
