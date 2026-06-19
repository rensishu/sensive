package com.github.renss.sensive;

import com.github.renss.sensive.engine.KeywordMatcher;
import com.github.renss.sensive.engine.KvStateMachine;
import com.github.renss.sensive.engine.MaskPosition;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

/**
 * Performance benchmarks for the desensitization engine.
 *
 * Key scenarios:
 *   1. High-frequency small logs — many calls with typical log lines
 *   2. Large single logs — very long log messages
 *   3. Many keywords — large keyword dictionary impact
 */
public class SensivePerformanceTest {

    private static final int WARMUP_ITERATIONS = 5000;
    private static final int BENCH_ITERATIONS = 50000;
    private static final int LARGE_LOG_ITERATIONS = 500;

    @BeforeClass
    public static void warmUp() {
        // Trigger JIT compilation
        String warmup = "phone=13812345678, name=张三, idcard=310101199001011234";
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            SensiveUtils.mask(warmup);
        }
    }

    // ============================================================
    // Scenario 1: High-frequency typical log lines
    // ============================================================

    @Test
    public void highFrequencyTypicalLog() {
        String[] samples = {
                "INFO 2024-06-01 10:00:00 [http-nio-8080-exec-1] " +
                        "c.example.UserController - user login, phone=13812345678, email=test@example.com",
                "DEBUG 2024-06-01 10:00:01 [http-nio-8080-exec-2] " +
                        "c.example.OrderService - create order, accountNo=6222021234567890, name=张三",
                "INFO 2024-06-01 10:00:02 [http-nio-8080-exec-3] " +
                        "c.example.AuthService - token validated, idcard=310101199001011234, realName=李四",
                "WARN 2024-06-01 10:00:03 [http-nio-8080-exec-1] " +
                        "c.example.PaymentService - payment retry, cardNo=6228481234567890, mobile=13912345678",
                "INFO 2024-06-01 10:00:04 [http-nio-8080-exec-4] " +
                        "c.example.ProfileService - update profile, address=北京市朝阳区某某街道100号, phone=18612345678",
        };

        long start = System.nanoTime();
        String result = null;
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            result = SensiveUtils.mask(samples[i % samples.length]);
        }
        long elapsed = System.nanoTime() - start;

        double avgNs = (double) elapsed / BENCH_ITERATIONS;
        double opsPerSec = 1_000_000_000.0 / avgNs;

        System.out.println("=== High-Frequency Typical Log ===");
        System.out.printf("  Iterations   : %d%n", BENCH_ITERATIONS);
        System.out.printf("  Total time   : %.2f ms%n", elapsed / 1_000_000.0);
        System.out.printf("  Avg latency  : %.0f ns (%.2f us)%n", avgNs, avgNs / 1000);
        System.out.printf("  Throughput   : %.0f ops/s%n", opsPerSec);
        System.out.printf("  Sample result: %s%n", result);
        System.out.println();

        // Assertion: average latency under 50us per call (acceptable for logging)
        Assert.assertTrue("Avg latency too high: " + (avgNs / 1000) + " us", avgNs < 50_000);
    }

    // ============================================================
    // Scenario 2: Large single log messages
    // ============================================================

    @Test
    public void largeLogMessage() {
        // Build a large log: many key-value pairs concatenated
        StringBuilder sb = new StringBuilder(1024 * 100);
        sb.append("INFO 2024-06-01 10:00:00 [main] c.app.BigService - batch result: ");
        for (int i = 0; i < 500; i++) {
            sb.append("phone").append(i).append("=138").append(String.format("%08d", i)).append(", ");
            sb.append("name").append(i).append("=用户").append(i).append(", ");
        }
        String largeText = sb.toString();
        int textLen = largeText.length();

        // Warmup
        for (int i = 0; i < 100; i++) {
            SensiveUtils.mask(largeText);
        }

        long start = System.nanoTime();
        String result = null;
        for (int i = 0; i < LARGE_LOG_ITERATIONS; i++) {
            result = SensiveUtils.mask(largeText);
        }
        long elapsed = System.nanoTime() - start;

        double avgMs = (double) elapsed / LARGE_LOG_ITERATIONS / 1_000_000.0;
        double throughputKBps = (textLen / 1024.0 * LARGE_LOG_ITERATIONS) / (elapsed / 1_000_000_000.0);

        System.out.println("=== Large Log Message ===");
        System.out.printf("  Text size    : %d chars (%.1f KB)%n", textLen, textLen / 1024.0);
        System.out.printf("  Iterations   : %d%n", LARGE_LOG_ITERATIONS);
        System.out.printf("  Total time   : %.2f ms%n", elapsed / 1_000_000.0);
        System.out.printf("  Avg latency  : %.2f ms%n", avgMs);
        System.out.printf("  Throughput   : %.0f KB/s%n", throughputKBps);
        System.out.printf("  Result len   : %d chars%n", result != null ? result.length() : 0);
        System.out.println();

        // Assertion: average latency under 20ms for ~100KB text
        Assert.assertTrue("Large text latency too high: " + avgMs + " ms", avgMs < 20);
    }

    // ============================================================
    // Scenario 3: No keywords to mask (worst case — scans everything)
    // ============================================================

    @Test
    public void noSensitiveDataScan() {
        // Text with no sensitive keywords — engine still scans the full text
        String text = "INFO 2024-06-01 10:00:00 [main] c.app.NormalService - " +
                "user action completed, status=success, duration=150ms, " +
                "requestId=abc-123-def, source=web, region=cn-north-1, " +
                "version=2.4.1, protocol=https, method=POST, code=200";

        long start = System.nanoTime();
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            SensiveUtils.mask(text);
        }
        long elapsed = System.nanoTime() - start;

        double avgNs = (double) elapsed / BENCH_ITERATIONS;

        System.out.println("=== No Sensitive Data (full scan overhead) ===");
        System.out.printf("  Iterations   : %d%n", BENCH_ITERATIONS);
        System.out.printf("  Avg latency  : %.0f ns (%.2f us)%n", avgNs, avgNs / 1000);
        System.out.println();

        // No sensitive data should be extremely fast (< 5us)
        Assert.assertTrue("No-match scan overhead too high: " + (avgNs / 1000) + " us", avgNs < 5_000);
    }

    // ============================================================
    // Scenario 4: MaskValue single value performance
    // ============================================================

    @Test
    public void maskValueBulk() {
        String[] values = {
                "13812345678", "13987654321", "15011223344", "18600001111",
                "张三", "李四", "王五", "赵六",
                "310101199001011234", "110101198512123456",
                "6222021234567890", "6228480012345678",
        };
        RuleType[] rules = {
                RuleType.PHONE_MASK, RuleType.PHONE_MASK, RuleType.PHONE_MASK, RuleType.PHONE_MASK,
                RuleType.NAME_MASK, RuleType.NAME_MASK, RuleType.NAME_MASK, RuleType.NAME_MASK,
                RuleType.IDCARD_MASK, RuleType.IDCARD_MASK,
                RuleType.ACCOUNT_MASK, RuleType.ACCOUNT_MASK,
        };

        long start = System.nanoTime();
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            int idx = i % values.length;
            SensiveUtils.maskValue(values[idx], rules[idx]);
        }
        long elapsed = System.nanoTime() - start;

        double avgNs = (double) elapsed / BENCH_ITERATIONS;

        System.out.println("=== maskValue Single Value ===");
        System.out.printf("  Iterations   : %d%n", BENCH_ITERATIONS);
        System.out.printf("  Avg latency  : %.0f ns%n", avgNs);
        System.out.println();

        Assert.assertTrue("maskValue too slow: " + avgNs + " ns", avgNs < 300);
    }

    // ============================================================
    // Scenario 5: KeywordMatcher scan overhead (isolated)
    // ============================================================

    @Test
    public void keywordMatcherOverhead() {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");
        matcher.addKeyword("name");
        matcher.addKeyword("idcard");
        matcher.addKeyword("accountno");
        matcher.addKeyword("email");
        matcher.addKeyword("password");
        matcher.addKeyword("mobile");
        matcher.addKeyword("address");
        matcher.addKeyword("token");
        matcher.addKeyword("secret");

        String text = "INFO 2024-06-01 10:00:00 [main] c.app.Service - " +
                "request completed, user=test, action=query, status=ok, duration=42ms";

        long start = System.nanoTime();
        for (int i = 0; i < BENCH_ITERATIONS; i++) {
            KvStateMachine.scan(text, matcher);
        }
        long elapsed = System.nanoTime() - start;

        double avgNs = (double) elapsed / BENCH_ITERATIONS;

        System.out.println("=== KeywordMatcher + KvStateMachine Scan Only ===");
        System.out.printf("  Iterations   : %d%n", BENCH_ITERATIONS);
        System.out.printf("  Keywords     : 10%n");
        System.out.printf("  Text length  : %d chars%n", text.length());
        System.out.printf("  Avg latency  : %.0f ns (%.2f us)%n", avgNs, avgNs / 1000);
        System.out.println();

        Assert.assertTrue("Scan overhead too high: " + (avgNs / 1000) + " us", avgNs < 5_000);
    }

    // ============================================================
    // Scenario 6: Very large text with many sensitive fields
    // ============================================================

    @Test
    public void veryLargeTextManyFields() {
        // ~1MB log with 5000 sensitive fields
        StringBuilder sb = new StringBuilder(1024 * 1024);
        sb.append("{\"batch\":[");
        for (int i = 0; i < 5000; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"phone\":\"138").append(String.format("%08d", i % 10000000))
                    .append("\",\"name\":\"用户").append(i % 10000)
                    .append("\",\"idcard\":\"31010119900101").append(String.format("%04d", i % 10000))
                    .append("\",\"accountNo\":\"622202123456").append(String.format("%04d", i % 10000))
                    .append("\"}");
        }
        sb.append("]}");
        String hugeText = sb.toString();

        // Warmup
        for (int i = 0; i < 20; i++) {
            SensiveUtils.mask(hugeText);
        }

        int iterations = 50;
        long start = System.nanoTime();
        String result = null;
        for (int i = 0; i < iterations; i++) {
            result = SensiveUtils.mask(hugeText);
        }
        long elapsed = System.nanoTime() - start;

        double avgMs = (double) elapsed / iterations / 1_000_000.0;
        double throughputMBps = (hugeText.length() / (1024.0 * 1024.0) * iterations) / (elapsed / 1_000_000_000.0);

        System.out.println("=== Very Large Text (~1MB, 5000 fields) ===");
        System.out.printf("  Text size    : %.2f MB%n", hugeText.length() / (1024.0 * 1024.0));
        System.out.printf("  Sensitive fld: 20000 (phone+name+idcard+account per record)%n");
        System.out.printf("  Iterations   : %d%n", iterations);
        System.out.printf("  Total time   : %.2f ms%n", elapsed / 1_000_000.0);
        System.out.printf("  Avg latency  : %.2f ms%n", avgMs);
        System.out.printf("  Throughput   : %.2f MB/s%n", throughputMBps);
        System.out.printf("  Result len   : %d chars%n", result != null ? result.length() : 0);
        System.out.println();

        // 1MB text with 20000 sensitive fields should complete within 500ms per call
        Assert.assertTrue("Huge text latency too high: " + avgMs + " ms", avgMs < 500);
    }

    // ============================================================
    // Scenario 7: Concurrent threads stress (light)
    // ============================================================

    @Test
    public void concurrentMasking() throws Exception {
        final int threadCount = 8;
        final int perThreadIterations = 10000;
        final String text = "phone=13812345678, name=张三, idcard=310101199001011234, email=test@example.com";

        Thread[] threads = new Thread[threadCount];
        final long[] threadTimes = new long[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int idx = t;
            threads[t] = new Thread(new Runnable() {
                @Override
                public void run() {
                    long start = System.nanoTime();
                    for (int i = 0; i < perThreadIterations; i++) {
                        SensiveUtils.mask(text);
                    }
                    threadTimes[idx] = System.nanoTime() - start;
                }
            });
        }

        long totalStart = System.nanoTime();
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        long totalElapsed = System.nanoTime() - totalStart;

        long totalOps = threadCount * perThreadIterations;
        double totalMs = totalElapsed / 1_000_000.0;
        double avgPerOpNs = (double) totalElapsed / totalOps;
        double opsPerSec = 1_000_000_000.0 / avgPerOpNs;

        System.out.println("=== Concurrent Masking (" + threadCount + " threads) ===");
        System.out.printf("  Total ops     : %d%n", totalOps);
        System.out.printf("  Wall time     : %.2f ms%n", totalMs);
        System.out.printf("  Avg per op    : %.0f ns%n", avgPerOpNs);
        System.out.printf("  Throughput    : %.0f ops/s%n", opsPerSec);
        System.out.println();

        // Each op should average well under 10us even with contention
        Assert.assertTrue("Concurrent overhead too high: " + (avgPerOpNs / 1000) + " us", avgPerOpNs < 10_000);
    }
}
