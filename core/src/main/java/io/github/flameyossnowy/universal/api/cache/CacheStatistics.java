package io.github.flameyossnowy.universal.api.cache;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class CacheStatistics {

    // Core counters
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder puts = new LongAdder();
    private final LongAdder totalLoadTime = new LongAdder();
    private final LongAdder totalOps = new LongAdder();

    // Rolling window (60 seconds)
    private static final int WINDOW_SIZE = 60;
    private final AtomicIntegerArray buckets = new AtomicIntegerArray(WINDOW_SIZE);
    private final AtomicLong lastTick = new AtomicLong(currentSecond());

    // --- Recording operations ---

    public void recordHit() {
        hits.increment();
        recordOp();
    }

    public void recordMiss(long loadTimeMs) {
        misses.increment();
        totalLoadTime.add(loadTimeMs);
        recordOp();
    }

    public void recordEviction() {
        evictions.increment();
        recordOp();
    }

    public void recordPut() {
        puts.increment();
        recordOp();
    }

    public void recordPuts(int count) {
        if (count > 0) {
            puts.add(count);
            recordOps(count);
        }
    }

    private void recordOp() {
        recordOps(1);
    }

    private void recordOps(int count) {
        totalOps.add(count);
        rotateIfNeeded();

        int index = (int) (currentSecond() % WINDOW_SIZE);
        buckets.addAndGet(index, count);
    }

    // --- Time rotation logic ---

    private void rotateIfNeeded() {
        long now = currentSecond();
        long last = lastTick.get();

        if (now == last) return;

        if (!lastTick.compareAndSet(last, now)) return;

        long diff = Math.min(now - last, WINDOW_SIZE);

        for (int i = 1; i <= diff; i++) {
            int index = (int) ((last + i) % WINDOW_SIZE);
            buckets.set(index, 0);
        }
    }

    private static long currentSecond() {
        return System.currentTimeMillis() / 1000;
    }

    // --- Metrics ---

    public long getHits() {
        return hits.sum();
    }

    public long getMisses() {
        return misses.sum();
    }

    public long getEvictions() {
        return evictions.sum();
    }

    public long getPuts() {
        return puts.sum();
    }

    public double getHitRate() {
        long h = hits.sum();
        long m = misses.sum();
        long total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }

    public double getAverageLoadTime() {
        long m = misses.sum();
        return m == 0 ? 0.0 : (double) totalLoadTime.sum() / m;
    }

    /**
     * Returns operations per second over last 60 seconds.
     */
    public long getOpsPerSecond() {
        rotateIfNeeded();

        long sum = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            sum += buckets.get(i);
        }
        return sum / WINDOW_SIZE;
    }

    /**
     * Returns total operations in last 60 seconds.
     */
    public long getOpsLastMinute() {
        rotateIfNeeded();

        long sum = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            sum += buckets.get(i);
        }
        return sum;
    }

    public void reset() {
        hits.reset();
        misses.reset();
        evictions.reset();
        puts.reset();
        totalLoadTime.reset();
        totalOps.reset();

        for (int i = 0; i < WINDOW_SIZE; i++) {
            buckets.set(i, 0);
        }

        lastTick.set(currentSecond());
    }
}