package io.github.flameyossnowy.universal.postgresql.jmh;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Category: INSERT
 *
 * Benchmarks:
 *   insertSingle – batchSize=1   (one round-trip per call)
 *   insertBulk   – batchSize=50  (batched inserts per call)
 *
 * Run with: ./gradlew jmh --tests "*InsertBenchmark*"
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Thread)
public class InsertBenchmark {

    @Param({"1", "50"})
    public int batchSize;

    private BenchmarkState state;
    private final AtomicLong counter = new AtomicLong(1_000_000);

    @Setup(Level.Trial)
    public void setup() throws Exception {
        state = new BenchmarkState();
        state.setup();
    }

    @TearDown(Level.Trial)
    public void teardown() {
        state.teardown();
    }

    // ── Universal ─────────────────────────────────────────────────────────────

    @Benchmark
    public void universal_insert(Blackhole bh) throws Throwable {
        List<BenchmarkEntity> batch = buildBatch(batchSize);
        if (batchSize == 1) {
            state.entityAdapter.insert(batch.get(0)).ifError(Throwable::printStackTrace);
        } else {
            state.entityAdapter.insertAll(batch).get();
        }
        bh.consume(batch);
    }

    // ── Hibernate ─────────────────────────────────────────────────────────────

    @Benchmark
    public void hibernate_insert(Blackhole bh) {
        List<BenchmarkEntity> batch = buildBatch(batchSize);
        try (Session s = state.sessionFactory.openSession()) {
            Transaction tx = s.beginTransaction();
            int i = 0;
            for (BenchmarkEntity e : batch) {
                s.persist(e);
                // flush + clear every JDBC batch size to avoid memory buildup
                if (++i % 50 == 0) { s.flush(); s.clear(); }
            }
            tx.commit();
        }
        bh.consume(batch);
    }

    // ── jOOQ ──────────────────────────────────────────────────────────────────

    @Benchmark
    public void jooq_insert(Blackhole bh) {
        List<BenchmarkEntity> batch = buildBatch(batchSize);
        if (batchSize == 1) {
            BenchmarkEntity e = batch.get(0);
            state.dsl.insertInto(table("benchmark_entities"))
                .columns(field("name"), field("score"), field("active"))
                .values(e.getName(), e.getScore(), e.getActive())
                .execute();
        } else {
            var step = state.dsl.batch(
                state.dsl.insertInto(table("benchmark_entities"))
                    .columns(field("name"), field("score"), field("active"))
                    .values((Object) null, null, null)
            );
            for (BenchmarkEntity e : batch) {
                step.bind(e.getName(), e.getScore(), e.getActive());
            }
            step.execute();
        }
        bh.consume(batch);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private List<BenchmarkEntity> buildBatch(int n) {
        List<BenchmarkEntity> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BenchmarkEntity e = new BenchmarkEntity();
            e.setName("bench-" + counter.incrementAndGet());
            e.setScore(i % 500);
            e.setActive(i % 2 == 0);
            list.add(e);
        }
        return list;
    }
}
