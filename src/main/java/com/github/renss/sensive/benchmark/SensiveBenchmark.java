package com.github.renss.sensive.benchmark;

import com.github.renss.sensive.RuleType;
import com.github.renss.sensive.SensiveUtils;
import com.github.renss.sensive.engine.KeywordMatcher;
import com.github.renss.sensive.engine.KvStateMachine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Comprehensive JMH benchmarks for the Sensive log desensitization library.
 *
 * <p>Covering: throughput, SQL mode, no-sensitive-data overhead, micro benchmarks,
 * multi-thread scalability, large text, and sustained load.
 *
 * <p>Run with: {@code mvn -Pjmh clean package && java -jar target/benchmarks.jar}
 * or for a specific benchmark:
 * {@code java -jar target/benchmarks.jar -rf json -rff results.json}
 *
 * @author renss
 * @version V1.2.0
 * @since 1.2.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SensiveBenchmark {

    // === Benchmark Data ===

    private String[] typicalLogs;
    private String[] sqlParameterLogs;
    private String noSensitiveLog;
    private String largeLog;
    private String hugeLog;

    // maskValue test data
    private String phoneValue = "13812345678";
    private String nameValue = "张三丰";
    private String idcardValue = "310101199001011234";
    private String accountValue = "6222021234567890123";
    private String emailValue = "zhangsan@example.com";
    private String addressValue = "北京市朝阳区某某街道100号";

    @Setup
    public void setup() {
        // Typical log lines (~200 chars, 2-4 sensitive fields)
        typicalLogs = new String[] {
            "INFO 2024-06-01 10:00:00 [http-nio-8080-exec-1] " +
                "c.example.UserController - user login, phone=13812345678, email=test@example.com, name=张三",
            "DEBUG 2024-06-01 10:00:01 [http-nio-8080-exec-2] " +
                "c.example.OrderService - create order, accountNo=6222021234567890, name=李四, phone=13987654321",
            "INFO 2024-06-01 10:00:02 [http-nio-8080-exec-3] " +
                "c.example.AuthService - token validated, idcard=310101199001011234, real_name=王五",
            "WARN 2024-06-01 10:00:03 [http-nio-8080-exec-1] " +
                "c.example.PaymentService - payment retry, cardNo=6228481234567890, mobile=15011223344, address=上海市浦东新区100号",
            "INFO 2024-06-01 10:00:04 [http-nio-8080-exec-4] " +
                "c.example.ProfileService - update profile, address=北京市朝阳区某某街道100号, email=zhangsan@mail.com, phone=18612345678",
        };

        // MyBatis SQL parameter-style output
        sqlParameterLogs = new String[] {
            "==> Parameters: 13812345678(String), 张三(String), 310101199001011234(String)",
            "==> Parameters: 6222021234567890(Long), 13987654321(String), 李四(String)",
            "==> Parameters: 310101199001011234(String)",
        };

        // Log line with absolutely no sensitive data — measures pure scan overhead
        noSensitiveLog = "INFO 2024-06-01 10:00:00 [main] c.app.NormalService - " +
            "user action completed, status=success, duration=150ms, " +
            "requestId=abc-123-def, source=web, region=cn-north-1, " +
            "version=2.4.1, protocol=https, method=POST, code=200";

        // Large log (~100KB, ~500 KV pairs)
        StringBuilder sb = new StringBuilder(1024 * 100);
        sb.append("INFO 2024-06-01 10:00:00 [main] c.app.BigService - batch result: ");
        for (int i = 0; i < 500; i++) {
            sb.append("phone").append(i).append("=138").append(String.format("%08d", i % 10000000)).append(", ");
            sb.append("name").append(i).append("=用户").append(i % 10000).append(", ");
        }
        largeLog = sb.toString();

        // Huge log (~1MB, ~5000 records)
        sb = new StringBuilder(1024 * 1024);
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
        hugeLog = sb.toString();
    }

    // ============================================================
    // Benchmark 1: mask() throughput — typical log line
    // ============================================================

    @Benchmark
    public void mask_typicalLog_throughput(Blackhole bh) {
        bh.consume(SensiveUtils.mask(typicalLogs[0]));
    }

    // ============================================================
    // Benchmark 2: maskEnhanced() throughput — KV + text pattern scanning
    // ============================================================

    @Benchmark
    public void maskEnhanced_throughput(Blackhole bh) {
        bh.consume(SensiveUtils.maskEnhanced(sqlParameterLogs[0]));
    }

    // ============================================================
    // Benchmark 3: No sensitive data — scan overhead
    // ============================================================

    @Benchmark
    public void mask_noSensitiveData_overhead(Blackhole bh) {
        bh.consume(SensiveUtils.mask(noSensitiveLog));
    }

    // ============================================================
    // Benchmark 4: maskValue — micro benchmarks per RuleType
    // ============================================================

    @Benchmark
    public void maskValue_phone(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue(phoneValue, RuleType.PHONE_MASK));
    }

    @Benchmark
    public void maskValue_name(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue(nameValue, RuleType.NAME_MASK));
    }

    @Benchmark
    public void maskValue_idcard(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue(idcardValue, RuleType.IDCARD_MASK));
    }

    @Benchmark
    public void maskValue_account(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue(accountValue, RuleType.ACCOUNT_MASK));
    }

    @Benchmark
    public void maskValue_email(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue(emailValue, RuleType.EMAIL_MASK));
    }

    @Benchmark
    public void maskValue_address(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue(addressValue, RuleType.ADDRESS_MASK));
    }

    @Benchmark
    public void maskValue_fullMask(Blackhole bh) {
        bh.consume(SensiveUtils.maskValue("mySecretPassword", RuleType.FULL_MASK));
    }

    // ============================================================
    // Benchmark 5: KeywordMatcher scan overhead (isolated)
    // ============================================================

    @Benchmark
    public void keywordMatcher_scanOnly(Blackhole bh) {
        KeywordMatcher matcher = new KeywordMatcher();
        matcher.addKeyword("phone");
        matcher.addKeyword("name");
        matcher.addKeyword("idcard");
        matcher.addKeyword("email");
        matcher.addKeyword("password");
        matcher.addKeyword("mobile");
        matcher.addKeyword("address");
        matcher.addKeyword("token");
        matcher.addKeyword("secret");
        matcher.addKeyword("accountno");

        String text = "INFO 2024-06-01 10:00:00 [main] c.app.Service - " +
            "request completed, user=test, action=query, status=ok, duration=42ms";
        bh.consume(KvStateMachine.scan(text, matcher));
    }

    // ============================================================
    // Benchmark 6: Large text throughput
    // ============================================================

    @Benchmark
    public void mask_largeLog_throughput(Blackhole bh) {
        bh.consume(SensiveUtils.mask(largeLog));
    }

    // ============================================================
    // Benchmark 7: Huge text throughput (~1MB)
    // ============================================================

    @Benchmark
    @Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
    public void mask_hugeLog_throughput(Blackhole bh) {
        bh.consume(SensiveUtils.mask(hugeLog));
    }
}
