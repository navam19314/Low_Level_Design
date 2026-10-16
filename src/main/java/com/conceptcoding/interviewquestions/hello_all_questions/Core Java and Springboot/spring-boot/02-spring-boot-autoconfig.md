# 02 — Spring Boot, Auto-Configuration & Production Basics 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Spring vs Spring Boot 🔥

**Say this:** "Spring Boot is built on top of Spring — it doesn't replace it. It removes the setup work with auto-configuration, starter dependencies and an embedded server, so the app runs with `java -jar`. It also gives production features like Actuator out of the box."

| Point | Spring | Spring Boot |
|---|---|---|
| Configuration | Manual (XML / Java config) | **Auto-configuration** + defaults |
| Server | Build WAR, deploy to external Tomcat | **Embedded** Tomcat, `java -jar` |
| Dependencies | Add each library, match versions yourself | **Starters** + managed versions |
| Production features | Add yourself | **Actuator** built in |

**Starters:** one dependency brings a tested bundle of libraries — e.g. `starter-web` = Spring MVC + Jackson + Tomcat, `starter-data-jpa` = Hibernate + Spring Data + HikariCP. `spring-boot-starter-parent` (BOM) manages versions, so no `<version>` needed.

**Most asked**
- **Is Spring Boot a replacement for Spring?** No. It uses Spring (IoC, MVC, Data) and only removes boilerplate.
- **Default server and port?** Tomcat on 8080. Change with `server.port=8081` (`0` = random). Can switch to Jetty/Undertow by excluding `starter-tomcat`.

---

## 2. `@SpringBootApplication` 🔥

**Say this:** "It's a shortcut on the main class that combines three annotations: `@SpringBootConfiguration` (a `@Configuration`), `@EnableAutoConfiguration` (turns on auto-config), and `@ComponentScan` (scans this package and sub-packages)."

```java
@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

**Most asked**
- **What 3 annotations?** `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- **Why is my bean not found?** It's outside the main class's package. Keep the main class in the root package (`com.shop`).

---

## 3. Auto-Configuration Internals 🔥🔥

**Say this:** "Auto-configuration creates beans for you based on three things: what's on the classpath, which beans already exist, and your properties. `@EnableAutoConfiguration` loads a list of auto-config classes from `META-INF/spring/...AutoConfiguration.imports` in Boot 3 — Boot 2 used `spring.factories`. Each class has `@Conditional` checks, and only the ones whose conditions are true create beans. If I define my own bean, Boot backs off because of `@ConditionalOnMissingBean`."

**Key points**
- Auto-config runs **after** your own config → your beans are registered first.
- Boot 3 file: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

| Condition | Creates bean only if… |
|---|---|
| `@ConditionalOnClass` | A class is on the classpath |
| `@ConditionalOnMissingBean` | You have **not** defined this bean (back off) |
| `@ConditionalOnProperty` | A property has a given value |
| `@ConditionalOnBean` | Some other bean exists |
| `@ConditionalOnWebApplication` | App is a web app |

**Most asked**
- **How does Boot know what to configure?** Reads `AutoConfiguration.imports`, checks `@Conditional`s (classpath, beans, properties), creates matching beans.
- **How to override an auto-configured bean?** Define your own bean of the same type — your bean wins. Or tweak via properties (`spring.datasource.*`).
- **How to disable one?** `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` or `spring.autoconfigure.exclude=...`.
- **How to see what got configured and why?** Run with `--debug` (or `debug=true`) → **Conditions Evaluation Report**. Or `/actuator/conditions`.
- **Boot 2 vs Boot 3 loading?** `spring.factories` vs `AutoConfiguration.imports`; Boot 3 uses `@AutoConfiguration` on the class.

**Trap:** Adding a starter changes behavior. `starter-security` locks all endpoints with zero code. `starter-data-jpa` with no DB URL → "Failed to configure a DataSource" at startup.

---

## 4. What `SpringApplication.run()` Does 🔥

**Say this:** "It creates and starts the Spring container. It decides the app type, loads config and profiles, scans my beans, applies auto-config, creates all singletons, starts the embedded server, then runs startup runners."

1. Decide app type (servlet / reactive / none) → create matching `ApplicationContext`.
2. Prepare `Environment` → properties, env vars, args, **active profiles**.
3. Print banner.
4. Component scan + auto-configuration → register bean definitions.
5. **Refresh context** → create singleton beans, inject dependencies.
6. Start embedded server (Tomcat, 8080).
7. Run `CommandLineRunner` / `ApplicationRunner` beans.
8. Publish `ApplicationReadyEvent` → app takes traffic.

**Most asked**
- **CommandLineRunner vs ApplicationRunner?** Both run once after startup; `CommandLineRunner` gets raw `String... args`, `ApplicationRunner` gets parsed `ApplicationArguments`. Order with `@Order`.

---

## 5. Profiles 🔥

**Say this:** "Profiles let me have different config and beans per environment — dev, test, prod. I keep common config in `application.yml` and overrides in `application-dev.yml`, `application-prod.yml`. I activate a profile with `spring.profiles.active`, usually through an env variable in prod."

```java
@Bean @Profile("dev")  FileStore localStore() { return new LocalFileStore(); }
@Bean @Profile("prod") FileStore s3Store()    { return new S3FileStore(); }
// activate: --spring.profiles.active=prod  or  SPRING_PROFILES_ACTIVE=prod
```

**Most asked**
- **No profile active?** `default` profile — only `application.yml` and beans without `@Profile`.
- **Profile file vs main file?** Profile file overrides the main file for the same key.

**Trap:** Never commit prod secrets in `application-prod.yml` — use env vars / Vault / K8s Secrets (`${DB_PASSWORD}`).

---

## 6. `@Value` vs `@ConfigurationProperties` 🔥

**Say this:** "`@Value` injects a single property. `@ConfigurationProperties` binds a group of properties with a common prefix into a type-safe class or record — with relaxed binding and validation. For anything more than one or two values I prefer `@ConfigurationProperties`."

| `@Value` | `@ConfigurationProperties` |
|---|---|
| One value | Whole group (`prefix`) |
| Not type-safe | **Type-safe** POJO / record |
| Supports SpEL `#{...}` | No SpEL |
| Exact key | **Relaxed binding** (`max-size` = `maxSize` = `MAX_SIZE`) |
| No easy validation | `@Validated` + `@NotBlank`, `@Min` |

**Most asked**
- **Default value with `@Value`?** `@Value("${app.timeout:30}")`.
- **How is a `@ConfigurationProperties` record registered?** `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties`.
- **Config priority?** Command-line args > env variables > `application-{profile}.yml` > `application.yml`. If `.properties` and `.yml` both have a key, `.properties` wins.

**Trap:** `@Value` with a missing key and no default → startup fails. It does not work on `static` fields or objects created with `new`.

---

## 7. Actuator 🔥

**Say this:** "Actuator adds ready-made endpoints to monitor and manage a running app — health, metrics, loggers, thread dumps. By default only `/health` is exposed over HTTP; others must be included explicitly, and sensitive ones should be secured."

| Endpoint | Shows |
|---|---|
| `/actuator/health` | UP/DOWN (+ DB, disk) |
| `/actuator/metrics`, `/prometheus` | JVM, CPU, HTTP stats (via Micrometer) |
| `/actuator/loggers` | View / **change log level at runtime** |
| `/actuator/threaddump` / `heapdump` | Stuck threads / memory leaks |
| `/actuator/env`, `/beans`, `/mappings` | Config, beans, URL mappings |

**Most asked**
- **How to expose more?** `management.endpoints.web.exposure.include=health,info,metrics,prometheus`.
- **Liveness vs readiness?** Liveness fails → K8s **restarts** the pod; readiness fails → K8s **stops sending traffic** (`/actuator/health/liveness`, `/readiness`).
- **Custom health check?** Implement `HealthIndicator` and return `Health.up()` / `Health.down()`.

**Trap:** `include=*` in prod leaks secrets via `/env` and `/heapdump`. Don't put DB checks in liveness — DB down would restart every pod.

---

## 8. Logging 🔥

**Say this:** "Spring Boot uses SLF4J as the logging API and Logback as the default implementation. Levels are TRACE < DEBUG < INFO < WARN < ERROR, default INFO. I use parameterized logging and MDC to put a traceId on every log line."

```java
log.info("Order created id={}", id);        // ✅ parameterized, no string built if level off
log.info("Order created id=" + id);         // ❌ always builds the string
log.error("Payment failed id={}", id, ex);  // ✅ exception last → full stack trace
// logging.level.com.shop.order=DEBUG
```

**Key points**
- **MDC** = per-thread key-value map (`traceId`) added to every log line. Set in a filter, **clear in `finally`** (thread pools reuse threads).
- Never log passwords, tokens, card numbers or PII.

---

## 9. HikariCP & Graceful Shutdown

**Say this:** "HikariCP is the default connection pool in Spring Boot. It keeps ready, reusable DB connections because opening one is slow. Default max pool size is 10."

**Most asked**
- **How to size the pool?** Start ~10, watch Hikari metrics (active, pending), tune. Total = pool size × instances must stay below DB `max_connections`.
- **Pool too small vs too big?** Small → requests wait and time out (`SQLTransientConnectionException`). Big → DB overloaded.
- **Graceful shutdown?** `server.shutdown=graceful` (default since Boot 3.4) — stop new requests, finish in-flight ones, wait up to `spring.lifecycle.timeout-per-shutdown-phase`.

**Trap:** Slow queries or long transactions hold connections → pool exhaustion even with a big pool. Fix the query, not just the pool size.

---

## 10. Spring Boot 3 Changes 🔥

**Say this:** "Boot 3 needs Java 17, moves from `javax.*` to `jakarta.*`, and is built on Spring 6. It brings Micrometer Tracing instead of Sleuth, GraalVM native images, `ProblemDetail` errors, virtual threads from 3.2, and the new `RestClient`."

| Change | Meaning |
|---|---|
| **Java 17** baseline | Records, sealed classes, etc. |
| `javax.*` → **`jakarta.*`** | Biggest migration pain (`jakarta.persistence`, `jakarta.servlet`) |
| **Spring Framework 6** | Underlying framework |
| **Micrometer Tracing** | Replaces Spring Cloud Sleuth |
| **GraalVM native image** | AOT → fast startup, low memory |
| **`ProblemDetail`** | Standard RFC 7807 error body |
| **Virtual threads** (3.2+, Java 21) | `spring.threads.virtual.enabled=true` |
| **`RestClient`** (3.2) | Fluent synchronous HTTP client, modern `RestTemplate` alternative |

**Most asked**
- **Biggest migration issue Boot 2 → 3?** `javax` → `jakarta` imports, plus Java 17 and `spring.factories` → `AutoConfiguration.imports` for custom auto-configs.

---

## 📌 Quick Revision
- **Spring Boot** = Spring + auto-config + starters + embedded server + Actuator. Not a replacement.
- `@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- **Auto-config**: `AutoConfiguration.imports` (Boot 3) / `spring.factories` (Boot 2) + `@Conditional*`.
- `@ConditionalOnMissingBean` → **your bean wins**. Exclude via `exclude = ...`. Debug with `--debug`.
- `run()` → context → environment → scan + auto-config → singletons → server → runners → ready.
- **Profiles**: `application-{profile}.yml`, `spring.profiles.active`, `@Profile`; default = `default`.
- `@ConfigurationProperties` > `@Value` for groups (type-safe, relaxed binding, validation).
- Priority: **CLI > env vars > profile file > main file**.
- **Actuator**: only `/health` exposed by default; liveness → restart, readiness → no traffic.
- **SLF4J + Logback**, use `{}`, MDC for traceId. **HikariCP** default, max **10**.
- Boot 3: **Java 17, jakarta.*, Spring 6**, Micrometer Tracing, native, ProblemDetail, virtual threads, RestClient.
