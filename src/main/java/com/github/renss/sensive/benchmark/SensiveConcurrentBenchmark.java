package com.github.renss.sensive.benchmark;

import com.github.renss.sensive.SensiveUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Multi-thread scalability and latency distribution benchmarks.
 *
 * <p>Tests mask() throughput under varying thread counts (1, 2, 4, 8, 16, 32).
 * Uses @State(Scope.Thread) to avoid contention on shared data.
 *
 * @author renss
 * @version V1.2.0
 * @since 1.2.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class SensiveConcurrentBenchmark {

    private String logLine;

    @Setup
    public void setup() {
        // Typical log line with 4 sensitive fields
        logLine = "INFO 2024-06-01 10:00:00 [http-nio-8080-exec-1] " +
            "c.example.UserController - user login, phone=13812345678, email=test@example.com, " +
            "name=张三, idcard=310101199001011234, address=北京市朝阳区某某街道100号";
    }

    @Benchmark
    @Threads(1)
    public void mask_1thread(Blackhole bh) {
        bh.consume(SensiveUtils.mask(logLine));
    }

    @Benchmark
    @Threads(2)
    public void mask_2threads(Blackhole bh) {
        bh.consume(SensiveUtils.mask(logLine));
    }

    @Benchmark
    @Threads(4)
    public void mask_4threads(Blackhole bh) {
        bh.consume(SensiveUtils.mask(logLine));
    }

    @Benchmark
    @Threads(8)
    public void mask_8threads(Blackhole bh) {
        bh.consume(SensiveUtils.mask(logLine));
    }

    @Benchmark
    @Threads(16)
    public void mask_16threads(Blackhole bh) {
        bh.consume(SensiveUtils.mask(logLine));
    }

    @Benchmark
    @Threads(32)
    public void mask_32threads(Blackhole bh) {
        bh.consume(SensiveUtils.mask(logLine));
    }
}
