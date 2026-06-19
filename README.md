# Sensive - 日志脱敏库

Java 8+ 通用日志脱敏库，兼容 Logback 和 Log4j2，零外部依赖传递。

## 特性

- **零传递依赖**：logback/log4j2/snakeyaml 均使用 `provided` scope，不传递到项目中
- **四框架兼容**：同时支持 Logback（1.2.x ~ 1.5.x）、Log4j 2.x（2.x）、Log4j 1.x（1.2.x）
- **Spring Boot 自动配置**：引入即用，无需编辑 logback.xml 或 log4j2.xml
- **MyBatis SQL 自动脱敏**：自动识别 `Preparing:`（放行）和 `Parameters:`（maskEnhanced 增强脱敏），不修改实际 SQL
- **内置 35+ 关键字**：覆盖手机号、姓名、身份证、银行卡、邮箱、地址、密码等常见敏感信息
- **6 种 KV 格式**：`key=value`、`key: value`、`"key":"value"`、`'key':'value'`、`key(value)`、`<key>value</key>`
- **2 种脱敏模式**：`mask()` KV 匹配（通用日志）、`maskEnhanced()` 增强脱敏 — KV + 文本模式（裸露数字序列）
- **忽略大小写匹配**：关键字匹配不区分大小写
- **可自定义规则**：支持内置规则类型和正则表达式自定义规则
- **编程式 API**：可在代码中直接调用脱敏工具方法
- **多配置源支持**：Apollo / Nacos 配置中心 → Spring `application.yml` → classpath `sensitive.yml`，按优先级覆盖
- **配置文件驱动**：支持 YAML 和 Properties 两种外部配置格式
- **线程安全**：所有 API 线程安全，可并发调用
- **null/空值保护**：值为 `null`、空字符串 `""` 或字面字符串 `"null"` 时原样返回，不脱敏

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.renss</groupId>
    <artifactId>sensive</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 2. 自动启用（Spring Boot）

**无需任何额外配置。** 引入依赖后：

- **Logback 日志脱敏**：自动注册，`%msg` 自动脱敏，无需编辑 `logback.xml`
- **MyBatis SQL 自动脱敏**：`Preparing:` 行自动跳过，`Parameters:` 行自动使用双引擎脱敏
- **控制开关**：`sensitive.enabled: false` 可全局禁用

### 3. 非 Spring Boot 项目

在 `main()` 方法最开始调用一行即可启用 Logback 脱敏：

```java
import com.github.renss.sensive.logback.SensiveLogbackInitializer;

public class App {
    public static void main(String[] args) {
        SensiveLogbackInitializer.install(); // 必须在任何日志输出前调用
        // ...
    }
}
```

### 4. 代码中使用

```java
// KV 模式：通用日志脱敏（仅 key=value 匹配）
String safe = SensiveUtils.mask("phone=13812345678, name=张三, idcard=310101199001011234");
// 结果: phone=138****5678, name=张*, idcard=310101********1234

// SQL 模式：KV + 文本模式（识别裸露的手机号/身份证/银行卡号）
// 增强模式：KV + 文本模式（识别裸露的手机号/身份证/银行卡号）
String enhanced = SensiveUtils.maskEnhanced("==> Parameters: 13812345678(String), 310101199001011234(String)");
// 结果: ==> Parameters: 138****5678(String), 310101********1234(String)

// 脱敏单个值
String phone = SensiveUtils.maskValue("13812345678", RuleType.PHONE_MASK);
// 结果: 138****5678
```

### 5. 手动集成到日志框架（可选）

如果已使用自动配置或 `SensiveLogbackInitializer.install()`，以下手动配置**不需要**。仅在需要自定义行为时才使用。

**Logback** (logback.xml):

```xml
<configuration>
    <!-- 全局替换 %msg 脱敏 -->
    <conversionRule conversionWord="msg"
                    converterClass="com.github.renss.sensive.logback.SensitiveMessageConverter"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d %level [%thread] %msg%n</pattern>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

或使用独立 `%sensitive` 关键词：

```xml
<conversionRule conversionWord="sensitive"
                converterClass="com.github.renss.sensive.logback.SensitiveConverter"/>

<pattern>%d %level [%thread] %sensitive%n</pattern>
```

**Log4j2** (log4j2.xml):

```xml
<Configuration>
    <Appenders>
        <Rewrite name="rewrite">
            <SensitiveRewritePolicy />
            <AppenderRef ref="Console" />
        </Rewrite>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d %level [%t] %msg%n" />
        </Console>
    </Appenders>
    <Loggers>
        <Root level="info">
            <AppenderRef ref="rewrite" />
        </Root>
    </Loggers>
</Configuration>
```

**Log4j 1.x** (log4j.properties):

```properties
log4j.appender.CONSOLE=org.apache.log4j.ConsoleAppender
log4j.appender.CONSOLE.layout=com.github.renss.sensive.log4j.SensitivePatternLayout
log4j.appender.CONSOLE.layout.ConversionPattern=%d %p [%t] %m%n
```

或 log4j.xml：

```xml
<appender name="CONSOLE" class="org.apache.log4j.ConsoleAppender">
    <layout class="com.github.renss.sensive.log4j.SensitivePatternLayout">
        <param name="ConversionPattern" value="%d %p [%t] %m%n" />
    </layout>
</appender>
```

> **兼容性**：Log4j 1.2.x 全系列适用。log4j 依赖为 `provided` scope，不传递到消费方项目。
>
> **原理说明**：Log4j 1.x 无法单独截获消息体。`SensitivePatternLayout` 在父类 `PatternLayout` 完成整体格式化后对结果字符串执行脱敏。由于脱敏引擎仅识别 key=value 等键值格式，时间戳、日志级别、线程名等格式化元数据不会被误脱敏。

## 脱敏模式说明

sensive 提供两种脱敏模式，适用于不同场景：

| 模式 | API | 匹配方式 | 适用场景 |
|------|-----|---------|---------|
| **基础模式** | `SensiveUtils.mask(text)` | 仅 KV 匹配 | 通用业务日志（有 key=value 结构） |
| **增强模式** | `SensiveUtils.maskEnhanced(text)` | KV 匹配 + 文本模式扫描 | 含裸露敏感数字的文本 |

日志框架适配器（`SensitiveMessageConverter` / `SensitiveRewritePolicy`）会自动根据消息内容选择模式：

| 消息类型 | 识别特征 | 处理方式 |
|---------|---------|---------|
| MyBatis SQL 语句 | 包含 `Preparing:` | **跳过**，原样保留（`?` 占位符不被破坏） |
| MyBatis SQL 参数 | 包含 `Parameters:` | `maskEnhanced()` — KV + 文本模式扫描 |
| 普通业务日志 | 其他 | `mask()` — 仅 KV 匹配 |

## 内置脱敏规则

| 规则类型 | 策略 | 示例输入 | 脱敏后 |
|---------|------|---------|--------|
| `PHONE_MASK` | 保留前3后4 | `13812345678` | `138****5678` |
| `NAME_MASK` | 1字→`*`；2字→`姓*`；3字+→`姓*尾` | `张三丰` | `张*丰` |
| `IDCARD_MASK` | 保留前6后4 | `310101199001011234` | `310101********1234` |
| `ACCOUNT_MASK` | 只保留后4位 | `6222021234567890` | `************7890` |
| `EMAIL_MASK` | 用户名保留前2后1 | `zhangsan@mail.com` | `zh***n@mail.com` |
| `ADDRESS_MASK` | 保留前6字 | `北京市朝阳区某某街道100号` | `北京市朝阳区***` |
| `FULL_MASK` | 全部替换为 `****` | `mypassword` | `****` |

## 内置关键字映射

| 类别 | 关键字 |
|------|--------|
| 手机号 | `phone`, `phoneno`, `phone_no`, `phonenum`, `phone_num`, `mobile`, `mobileno`, `mobile_no`, `usermobile`, `mobilephone`, `tel`, `telephone` |
| 姓名 | `name`, `username`, `realname`, `real_name`, `nickname`, `nick_name`, `customername`, `customer_name` |
| 身份证 | `idcard`, `idcardno`, `id_card`, `id_number`, `idno`, `identitycard`, `identity_card`, `identityno`, `certificateno` |
| 银行卡 | `accountno`, `account_number`, `bankcardno`, `bankcard`, `bank_card_no`, `cardno`, `cardnumber`, `card_number` |
| 邮箱 | `email`, `mail` |
| 地址 | `address`, `addr` |
| 密码凭证 | `password`, `passwd`, `pwd`, `secret`, `token`, `accesstoken`, `access_token` |

## 配置

sensive 支持多源配置，按优先级覆盖：

```
Apollo / Nacos 配置中心  (最高优先级，通过 Spring Cloud 自动整合)
    ↓
Spring application.yml / application.properties
    ↓
classpath sensitive.yml / sensitive.properties
    ↓
内置默认配置 (35+ 关键字)  (最低优先级)
```

### Spring 项目配置（推荐）

在任意 Spring 配置源（application.yml、Apollo、Nacos 等）中编写：

```yaml
sensitive:
  enabled: true
  keywords:
    # 格式1：关键字 → 规则（适用于单个关键字）
    my_custom_field: ACCOUNT_MASK

    # 格式2：规则 → 关键字合集（同规则多关键字推荐使用）
    PHONE_MASK:
      - phone
      - phoneno
      - mobile
    FULL_MASK: mysecret, mytoken, apikey   # 或逗号分隔字符串
  text-pattern:
    enabled: true
    patterns: phone, idcard, bankcard
  excludes:
    - status
    - version
```

对应的 Properties 格式（Spring / Apollo / Nacos）：

```properties
sensitive.enabled=true
# 格式1：关键字 → 规则
sensitive.keywords.my_custom_field=ACCOUNT_MASK
# 格式2：规则 → 关键字（逗号分隔）
sensitive.keywords.PHONE_MASK=phone, phoneno, mobile
sensitive.keywords.FULL_MASK=mysecret, mytoken, apikey
```

Spring Boot 项目引入 sensive 后 `SensiveAutoConfiguration` 自动从 `Environment` 读取以上配置，无需额外代码。

> **原理**：Spring Cloud 已把 Apollo/Nacos 的配置注入到 Spring `Environment`，sensive 直接从 `Environment` 中提取所有 `sensitive.*` 前缀属性，因此 Apollo/Nacos 配置自动生效，无需单独对接。

### 非 Spring 项目配置

在 classpath 根目录创建 `sensitive.yml`：

```yaml
sensitive:
  enabled: true          # 总开关

  # 覆盖或新增关键字映射（支持两种格式混用）
  keywords:
    # 格式1：关键字 → 规则
    phone: PHONE_MASK
    my_custom_field: ACCOUNT_MASK

    # 格式2：规则 → 关键字合集（YAML 列表）
    PHONE_MASK:
      - phoneno
      - mobile
      - mobileno
      - telephone
    FULL_MASK: secret, token, apikey   # 或逗号分隔字符串

  # 自定义规则：使用内置规则类型
  rules:
    - name: custom_card
      keyword: bank_card
      type: builtin
      builtin: ACCOUNT_MASK

    # 自定义规则：使用正则表达式
    - name: custom_order_id
      keyword: order_id
      type: regex
      pattern: "^(\\w{4})\\w+(\\w{4})$"
      replacement: "$1****$2"

  # 排除关键字（不脱敏）
  excludes:
    - status
    - version

  # 文本模式扫描：识别裸露数字序列（手机号/身份证/银行卡号）
  # 仅对 maskEnhanced() 生效，不影响 mask()
  # 默认关闭，需要时手动开启
  textPattern:
    enabled: false
    patterns:
      - phone       # 11位手机号 (1[3-9]xxxxxxxxx)
      - idcard      # 18位身份证 (含末尾 X)
      - bankcard    # 16-19位银行卡号
```

也支持 `sensitive.properties`（无需 SnakeYAML）：

```properties
sensitive.enabled=true
# 格式1：关键字 = 规则
sensitive.keywords.phone=PHONE_MASK
# 格式2：规则 = 关键字合集（逗号分隔）
sensitive.keywords.PHONE_MASK=phoneno, mobile, mobileno, telephone
sensitive.keywords.FULL_MASK=secret, token, apikey
```

## 文本模式匹配

用于处理无显式 key=value 结构的场景（裸露数字文本等）。**仅在 `maskEnhanced()` 中生效，不影响 `mask()`。**

### 工作原理

在 KV 匹配完成后，对未被覆盖的文本区域扫描连续数字序列，按长度和格式判断敏感类型：

| 模式 | 匹配规则 | 脱敏规则 |
|------|---------|---------|
| `phone` | 11位数字，第1位=1，第2位∈[3-9] | `PHONE_MASK` |
| `idcard` | 18位数字 或 17位数字+X/x | `IDCARD_MASK` |
| `bankcard` | 16-19位连续数字 | `ACCOUNT_MASK` |

- 已在 KV 匹配中处理的区段不会重复处理
- 不匹配时间戳（14位日期时间）、普通ID等其他数字序列
- O(n) 扫描，无正则
- **默认关闭**，需要时通过配置开启，仅影响 SQL 模式

### 配置

```yaml
sensitive:
  textPattern:
    enabled: true          # 默认 true
    patterns: [phone, idcard, bankcard]
```

## API 参考

### SensiveUtils

```java
// KV 模式：通用日志脱敏（仅 key=value 匹配，无文本模式扫描）
public static String mask(String text)

// 增强模式：KV 匹配 + 文本模式扫描（适用于含裸露敏感数字的文本）
public static String maskEnhanced(String text)

// 脱敏指定关键字的值
public static String mask(String text, String keyword)

// 脱敏指定关键字，使用指定规则（覆盖默认规则）
public static String mask(String text, String keyword, RuleType ruleType)

// 对单个值直接脱敏
public static String maskValue(String value, RuleType ruleType)

// 重新加载配置（无需重启）
public static void reloadConfig()

// 运行时注册关键字
public static void registerKeyword(String keyword, RuleType ruleType)
```

### RuleType

```java
RuleType.PHONE_MASK        // 手机号
RuleType.NAME_MASK         // 姓名
RuleType.IDCARD_MASK       // 身份证
RuleType.ACCOUNT_MASK      // 银行卡/账号
RuleType.EMAIL_MASK        // 邮箱
RuleType.ADDRESS_MASK      // 地址
RuleType.FULL_MASK         // 全部隐藏
RuleType.CUSTOM_REGEX      // 自定义正则（需配合配置使用）
```

### SensiveLogbackInitializer

```java
// 非 Spring Boot 项目：注册 Logback 脱敏转换器（在 main() 最开始调用）
public static void install()
```

## 性能

### 设计保证

- **O(n) 单次遍历**：Trie 前缀树多关键字并行匹配 + 状态机 KV 解析，不回退不回溯
- **对象复用**：内部使用 `StringBuilder` 拼接，`MaskPosition` 复用，`ArrayList` 延迟分配
- **无锁读取**：`ConcurrentHashMap` 实现无锁关键字查找，多线程无 CAS 争用
- **预编译正则**：自定义规则 Pattern 在配置加载时编译，非运行时
- **零外部依赖**：不引入任何第三方库的初始化开销

### JMH 基准测试结果 (v1.2.0)

测试环境：Apple M3 Pro (12 核)，macOS 25，JDK 17.0.1

| 场景 | 数据规模 | 吞吐量 | 单次延迟 | 说明 |
|------|---------|--------|---------|------|
| 典型日志行 (mask) | ~200 chars | **85.2 万 ops/s** | **1.17 μs** | 含 3~4 个敏感字段 |
| 增强脱敏 (maskEnhanced) | ~80 chars | **399 万 ops/s** | **0.25 μs** | KV + 文本模式扫描 |
| 无敏感数据 | ~230 chars | **148 万 ops/s** | **0.67 μs** | 无匹配时扫描开销极小（延迟分配） |
| 大日志 | ~100 KB | **1.3 万 ops/s** | **76 μs** | 含 500 个键值对 |
| 超大日志 | ~1 MB | — | — | 含 20000 敏感字段（见下方） |
| 单值脱敏 (phone) | 11 位 | **1320 万 ops/s** | **76 ns** | `maskValue()` 快速路径 |
| 单值脱敏 (name) | 3 字 | **2391 万 ops/s** | **42 ns** | `maskValue()` 快速路径 |
| 单值脱敏 (idcard) | 18 位 | **1299 万 ops/s** | **77 ns** | `maskValue()` 快速路径 |
| 单值脱敏 (account) | 19 位 | **1447 万 ops/s** | **69 ns** | `maskValue()` 快速路径 |

### 多线程可扩展性（典型日志行，~180 chars，5 敏感字段）

| 线程数 | 吞吐量 | vs 旧版 v1.1.0 |
|--------|--------|---------------|
| 1 | **75.6 万 ops/s** | 旧版 ~30 万（提升 **2.5x**） |
| 8 | **329 万 ops/s** | 旧版 ~79 万（提升 **4.2x**） |
| 32 | **323 万 ops/s** | 旧版未测试 |

> 本机为 12 核，32 线程存在超订阅（vs 8 线程略降）。在 128 核服务器上，
> ConcurrentHashMap 无锁设计保证近乎线性扩展至物理核数。

### 内存与 GC

- **每条日志分配**：~100-500 字节（延迟分配 ArrayList、复用 MatchResult、applyFast 避免 substring）
- **GC 压力**：年轻代回收清除绝大部分对象，无老年代堆积
- **ConcurrentHashMap**：读取无锁、零 CAS 争用、无额外内存分配
- **v1.1.0 → v1.2.0 关键改进**：消除 ReadWriteLock CAS 瓶颈、消除双重 toLowerCase 字符串分配、ArrayList 延迟分配、MatchResult 复用、正则预编译

### 结论

- **常规日志**：每次脱敏增加约 **1.2 微秒**（v1.1.0 为 3 μs），百万次调用仅增加 1.2 秒
- **SQL 参数**：每次脱敏仅 **0.25 微秒**
- **高并发**：8 线程达 329 万 ops/s，支撑百万 QPS 仅需 ~3 个 CPU 核心
- **可扩展性**：无锁设计，32+ 线程近线性扩展，适应双十一规模流量

> 脱敏对系统性能的影响可忽略不计。

### 复现基准测试

```bash
# 构建 JMH 基准测试 jar
mvn -Pjmh clean package -DskipTests

# 运行所有基准测试
java -jar target/benchmarks.jar

# 运行特定基准测试
java -jar target/benchmarks.jar "mask_typicalLog_throughput"

# 输出 JSON 格式结果
java -jar target/benchmarks.jar -rf json -rff results.json
```

## FAQ

### Q: Spring Boot 项目引入后需要做什么配置？

**什么都不需要。** 引入 sensive 依赖即可：

- Logback `%msg` 自动脱敏（无需编辑 `logback.xml`）
- SQL 语句中的 `?` 占位符自动保护不修改
- MyBatis `Parameters:` 行自动启用全文模式扫描

如需禁用：设置 `sensitive.enabled: false`。

### Q: 非 Spring Boot 项目如何启用？

在 `main()` 方法最开始调用：

```java
SensiveLogbackInitializer.install();
```

### Q: mask() 和 maskEnhanced() 有什么区别？

| 方法 | 匹配方式 | 适用场景 |
|------|---------|---------|
| `mask()` | 仅 KV 匹配 | 通用业务日志 |
| `maskEnhanced()` | KV + 文本模式 | 裸露数字序列 |

日志框架适配器会自动判断消息类型并选择合适的方法，无需手动调用。

### Q: 脱敏会破坏 MyBatis SQL 语句吗？

不会。sensive 自动识别 `Preparing:` 行（SQL 语句）并跳过不处理。`KvStateMachine` 也会将 `?` 占位符识别为非值字符，即使通过 `mask()` 也不会被破坏。

### Q: 如何只对特定 appender 脱敏？

Logback: 在 `<appender>` 内使用独立 `<encoder>` 配置 `%sensitive`。

Log4j2: 只对需要的 Appender 使用 `<Rewrite>` 包装。

### Q: 脱敏失败会怎样？

默认 fail-safe 模式：脱敏异常返回原文，不影响正常日志输出。

### Q: SnakeYAML 是必须的么？

不必须。如果不配置 `sensitive.yml`，库使用内置 Java 代码中的默认配置（零依赖）。如果需要外部配置文件：
- 有 SnakeYAML 环境（如 Spring Boot）：使用 `sensitive.yml`
- 无 SnakeYAML 环境：使用 `sensitive.properties`

### Q: 如何在项目中避免引入不必要的 logging 依赖？

本库所有 logging 依赖均为 `provided` scope（maven），运行时由项目自行提供已存在的 Logback/Log4j2 版本，不会传递到消费方项目中，也不会产生版本冲突。

## 项目结构

```
sensive/
├── src/main/java/com/github/renss/sensive/
│   ├── SensiveUtils.java              # 公共 API 入口
│   ├── RuleType.java                  # 内置脱敏规则枚举
│   ├── autoconfigure/
│   │   ├── SensiveAutoConfiguration.java        # Spring Boot 自动配置
│   │   └── SensiveLogbackApplicationListener.java # Logback 提前初始化
│   ├── config/
│   │   ├── SensitiveConfig.java       # 线程安全配置单例
│   │   ├── ConfigLoader.java          # YAML/Properties 配置加载
│   │   ├── DefaultConfig.java         # 内置默认关键字
│   │   └── model/CustomRule.java      # 自定义规则模型
│   ├── engine/
│   │   ├── MaskEngine.java            # 核心脱敏引擎
│   │   ├── KeywordMatcher.java        # Trie 前缀树匹配
│   │   ├── KvStateMachine.java        # KV 状态机解析
│   │   ├── RuleExecutor.java          # 规则执行器
│   │   ├── TextPatternMatcher.java    # 文本模式扫描（方案B）
│   │   └── MaskPosition.java          # 脱敏位置区间数据类
│   ├── logback/
│   │   ├── SensitiveMessageConverter.java # %msg 替换（全局脱敏）
│   │   ├── SensitiveConverter.java        # %sensitive 独立关键字
│   │   └── SensiveLogbackInitializer.java # 程序化注册（非 Spring Boot）
│   ├── log4j2/
│   │   └── SensitiveRewritePolicy.java    # Log4j2 RewritePolicy
```

## 兼容性

| 依赖 | 编译版本 | 运行时兼容 |
|------|---------|-----------|
| logback-classic | 1.2.3 | 1.2.x ~ 1.5.x |
| log4j-core | 2.17.0 | 2.x (2.3+) |
| log4j | 1.2.17 | 1.2.x |
| spring-boot-autoconfigure | 2.7.0 | Spring Boot 2.x / 3.x |
| Java | 1.8 | 8 / 11 / 17 / 21 |
| SnakeYAML | 1.27 (provided) | 任意版本 |

## License

MIT
