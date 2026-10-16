# 09 — Spring Boot Tricky Questions + Night-Before Cheat Sheet 🔥

> Format: **Part 1** "what happens?" (cover the answer, guess first) · **Part 2** scenario questions · **Part 3** testing quick facts · **Part 4** night-before cheat sheet

---

# Part 1: What Happens?

### Transactions

```java
@Service
class OrderService {
    public void placeOrder() { saveOrder(); }      // same class
    @Transactional
    public void saveOrder() { repo.save(order); throw new RuntimeException(); }
}
```
**Answer: no transaction → order stays saved** — `this.saveOrder()` skips the Spring proxy. Fix: move it to another bean, or put `@Transactional` on `placeOrder`.

```java
@Transactional
public void pay() throws IOException {
    repo.save(payment);
    throw new IOException("gateway down");
}
```
**Answer: commits** — default rollback is only for `RuntimeException` and `Error`. Fix: `rollbackFor = Exception.class`.

```java
@Transactional
public void pay() {
    try { repo.save(payment); throw new RuntimeException(); }
    catch (RuntimeException e) { log.error("failed", e); }
}
```
**Answer: commits** — the proxy never sees the exception. If the exception came from another `@Transactional` (REQUIRED) bean → tx already marked rollback-only → `UnexpectedRollbackException` at commit.

```java
@Transactional
public void outer() {
    auditService.log("started");   // @Transactional(propagation = REQUIRES_NEW)
    throw new RuntimeException();
}
```
**Answer: audit row saved, outer work rolled back** — `REQUIRES_NEW` suspends the outer tx and commits on its own.

```java
@Transactional
private void update() { ... }
```
**Answer: ignored → no transaction** — proxies can't intercept private methods (also not `final`/`static`).

### Beans & DI

```java
@Component @Scope("prototype") class Cart { }
@Service class ShopService { @Autowired Cart cart; }   // singleton
```
**Answer: same `Cart` every time** — injected once when the singleton is created. Fix: `ObjectProvider<Cart>.getObject()` or `@Lookup`.

```java
@Component class EmailSender implements Sender { }
@Component class SmsSender   implements Sender { }
@Service class Notifier { Notifier(Sender sender) { } }
```
**Answer: startup fails — `NoUniqueBeanDefinitionException`** — two candidates. Fix: `@Primary`, `@Qualifier("emailSender")`, or inject `List<Sender>`.

```java
@Service
class ReportService {
    @Autowired private UserRepository repo;
    ReportService() { repo.count(); }
}
```
**Answer: `NullPointerException` at startup** — field injection happens after the constructor. Use constructor injection or `@PostConstruct`.

```java
@Service class A { A(B b) { } }
@Service class B { B(A a) { } }
```
**Answer: startup fails — `BeanCurrentlyInCreationException`** — neither can be built first. Fix: redesign / extract a third bean (`@Lazy` = workaround). Boot 2.6+ blocks all cycles by default.

```java
package com.app;        @SpringBootApplication class App { }
package com.utils;      @Service class MailUtil { }        // outside com.app
package com.app.order;  @Service class OrderService { OrderService(MailUtil m) { } }
```
**Answer: startup fails — `NoSuchBeanDefinitionException`** — scan covers only `com.app` and sub-packages. Fix: move it, `scanBasePackages`, or `@Import`.

### JPA / Hibernate

```java
Department d = deptRepo.findById(1L).get();   // no @Transactional, open-in-view = false
d.getEmployees().size();
```
**Answer: `LazyInitializationException`** — `@OneToMany` is LAZY and the session is closed. Fix: `JOIN FETCH`, `@EntityGraph`, DTO projection, or work inside `@Transactional`.

```java
@Transactional
public void print() {
    List<Department> depts = deptRepo.findAll();      // 10 departments
    depts.forEach(d -> d.getEmployees().size());
}
```
**Answer: 11 queries (N+1)** — 1 for departments + 1 per department. Fix: `JOIN FETCH` / `@EntityGraph` → 1 query; `@BatchSize` → few queries.

```java
@Transactional
public void rename(Long id) {
    User u = userRepo.findById(id).get();
    u.setName("Navam");        // no save()
}
```
**Answer: saved** — `u` is managed; **dirty checking** runs `UPDATE` at flush/commit.

### Proxies & Filters

```java
@Service
class MailService {
    public void register() { sendMail(); }
    @Async public void sendMail() { ... }
}
```
**Answer: runs synchronously on the caller thread** — self-invocation skips the proxy. Same with `@Cacheable` → cache bypassed every time.

```java
@Component
class AuthFilter extends OncePerRequestFilter {
    protected void doFilterInternal(...) { throw new InvalidTokenException(); }
}
// @RestControllerAdvice has @ExceptionHandler(InvalidTokenException.class)
```
**Answer: handler NOT called** — filters run before `DispatcherServlet`; advice covers only controllers. Write the response in the filter, or use Security's `AuthenticationEntryPoint`.

---

# Part 2: Scenario Questions

**Q: An API is slow. How do you debug?**
- Metrics/traces first (Actuator + Micrometer, Grafana, Zipkin) → find the slow step: DB, external call, or code.
- DB: SQL logs / slow query log → **N+1**, missing index (`EXPLAIN`), fetching too much.
- Fix: pagination, DTO projections, caching (Redis), timeouts + parallel external calls, async for non-critical work, check pool waits.

**Q: Downstream service B is down.**
- **Timeouts** on every call. **Retry** with backoff only for transient errors + idempotent calls.
- **Circuit breaker** (Resilience4j) → fail fast + **fallback** (cached/default data). **Bulkhead** to protect your threads.
- Not needed instantly → publish to **Kafka**, process when B is back.

**Q: How do you secure APIs?**
- HTTPS. AuthN with **OAuth2/JWT** (short-lived access + refresh token). AuthZ with roles/scopes → `@PreAuthorize`; check ownership.
- `@Valid` input, parameterized queries (no SQL injection), rate limiting, strict CORS, secrets in Vault/env, no stack traces to client.

**Q: Client retries a payment → duplicate charge?**
- Client sends an **`Idempotency-Key`** (UUID). Server stores key + status + response (**unique constraint** in DB, or Redis `SETNX` + TTL).
- Same key again → return stored response (in progress → 409). Unique constraint handles parallel races.

**Q: `@Scheduled` runs on all 3 instances.**
- **ShedLock**: `@SchedulerLock(name = "report", lockAtMostFor = "10m")` → lock row in DB/Redis, one instance wins. Alternatives: Quartz cluster, K8s CronJob.

**Q: Transaction across microservices?**
- No 2PC (slow, blocking). **Saga**: local transactions + **compensating actions** (refund, release stock). Choreography (events) or orchestration (central coordinator).
- **Outbox pattern** for reliable DB update + event publish. **Idempotent consumers**.

**Q: "Connection is not available" — DB pool exhausted.**
- Cause: connections held too long → slow queries, HTTP calls inside `@Transactional`, leaks, OSIV on.
- Check HikariCP metrics (active/pending), `leak-detection-threshold`. Fix: short transactions, faster queries, `open-in-view=false`, tune `maximum-pool-size` (default 10; bigger isn't always better).

**Q: Zero-downtime deployment?**
- **Rolling update** + **readiness probe** (`/actuator/health/readiness`) → traffic only to ready pods; **liveness** restarts stuck pods.
- `server.shutdown=graceful` → finish in-flight requests. DB changes **backward compatible** (Flyway: add column first, drop later). Blue-green / canary for safety.

---

# Part 3: Testing Quick Facts

- **Unit** = one class, dependencies mocked, no Spring, fast. **Integration** = several layers together, usually Spring context + real DB.
- `@Mock` creates a fake; `@InjectMocks` creates the **real** class under test and injects the mocks. Needs `@ExtendWith(MockitoExtension.class)`.
- Mock = all methods fake; **Spy** = real object, stub only some methods. `verify(mock).save(any())` checks calls.

| Annotation | Loads | Use for |
|---|---|---|
| `@SpringBootTest` | Full context | Integration / end-to-end flow |
| `@WebMvcTest(X.class)` | Web layer only (controllers, advice, filters) | Controller + validation + status codes |
| `@DataJpaTest` | JPA only (repos, embedded DB, rollback per test) | Custom queries / mappings |

- **`@MockitoBean`** (Boot 3.4+) replaces a bean in the Spring context with a mock; `@MockBean` is deprecated.
- **MockMvc** = call controllers without a real server: `mockMvc.perform(get("/api/users/1")).andExpect(status().isOk())`.
- **Testcontainers** = real Postgres/Kafka in Docker for integration tests (`@ServiceConnection` in Boot 3.1+).

---

# Part 4: Night-Before Cheat Sheet

### Core / IoC / Beans → [01](01-spring-core-ioc-beans.md)
- IoC = container controls object creation; DI = how it's done. Container = `ApplicationContext` (eager) vs `BeanFactory` (lazy, basic).
- **Constructor injection** best: final fields, easy tests, cycles caught at startup. Single constructor → no `@Autowired` needed.
- `@Component` (own classes, scanned) vs `@Bean` (method in `@Configuration`, third-party classes).
- `@Repository` translates DB exceptions to `DataAccessException`.
- Two beans same type → `@Primary` (default) / `@Qualifier` (explicit, wins).
- Lifecycle: instantiate → inject → BPP before → `@PostConstruct` → BPP after (**proxy created here**) → ready → `@PreDestroy`.
- Scopes: singleton (default), prototype, request, session. Spring doesn't call destroy on prototype.
- Singleton beans are **not thread-safe** → keep them stateless.

### Boot & Auto-config → [02](02-spring-boot-autoconfig.md)
- `@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`.
- Auto-config list in `META-INF/spring/...AutoConfiguration.imports`; applied via `@ConditionalOnClass / OnMissingBean / OnProperty`.
- Your bean wins — auto-config backs off (`@ConditionalOnMissingBean`). See report: `--debug` or `/actuator/conditions`.
- Starters = curated dependency bundles. Default server = Tomcat.
- Config priority: command-line > env vars > profile file > `application.yml`. Prefer `@ConfigurationProperties` over `@Value`.
- Boot 3: Java 17+, `jakarta.*`, Spring 6, `ProblemDetail`, Micrometer Tracing, virtual threads (`spring.threads.virtual.enabled=true`, 3.2+).

### REST & Exceptions → [03](03-rest-api-exceptions.md)
- Flow: filters → `DispatcherServlet` → HandlerMapping → controller → `HttpMessageConverter` (Jackson) → response.
- `@RestController` = `@Controller` + `@ResponseBody`. `@PathVariable` / `@RequestParam` / `@RequestBody`.
- PUT = full replace, idempotent; PATCH = partial; POST = create, not idempotent. 201 Created, 204 No Content.
- **401** = not authenticated; **403** = authenticated but not allowed.
- `@Valid` fails → `MethodArgumentNotValidException` → 400. Return DTOs, never entities.
- `@RestControllerAdvice` + `@ExceptionHandler` = global; local handler and most specific type win. `ProblemDetail` = RFC 9457.
- Filter = servlet level (before DispatcherServlet); Interceptor = Spring MVC level. `RestClient` (6.1+) = modern sync client.

### JPA → [04](04-jpa-hibernate.md)
- JPA = spec, Hibernate = implementation, Spring Data JPA = repositories on top.
- Defaults: `@ManyToOne`/`@OneToOne` = **EAGER**, `@OneToMany`/`@ManyToMany` = **LAZY**. Make everything LAZY.
- Owning side = has the FK (`@ManyToOne`); other side uses `mappedBy`.
- N+1 fix: `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, DTO projection. JOIN FETCH collection + paging → paginated in memory (warning).
- Entity states: transient, managed, detached, removed. Managed + changed → auto `UPDATE` (dirty checking).
- 1st-level cache = per persistence context, always on. 2nd-level = shared, optional.
- OSIV is on by default → set `spring.jpa.open-in-view=false`.
- `@Version` = optimistic lock (`OptimisticLockException`); `PESSIMISTIC_WRITE` = row lock. `@Modifying` for update/delete `@Query`.

### Transactions → [05](05-transactions.md)
- `@Transactional` = AOP proxy: begin → call → commit / rollback.
- Rollback only on **unchecked + Error**. Checked → `rollbackFor`. Caught exception → commit.
- Doesn't work: self-invocation, private/final methods, new thread, non-Spring objects.
- **REQUIRED** (default, join) vs **REQUIRES_NEW** (suspend outer, own commit — audit logs) vs NESTED (savepoint).
- Isolation default = DB default (Postgres READ_COMMITTED, MySQL REPEATABLE_READ). Dirty / non-repeatable / phantom reads.
- `readOnly = true` → skips dirty checking/flush. Keep transactions short — no HTTP calls inside.

### Security → [06](06-security-jwt.md)
- Boot 3: define a `SecurityFilterChain` bean (`WebSecurityConfigurerAdapter` removed).
- Filters run via `FilterChainProxy` before `DispatcherServlet`. Current user in `SecurityContextHolder` (ThreadLocal).
- Flow: `AuthenticationManager` → `AuthenticationProvider` → `UserDetailsService` → `PasswordEncoder` (**BCrypt**).
- JWT = header.payload.signature — **signed, not encrypted**. Stateless; revoke with short expiry + refresh + Redis blacklist.
- Stateless JWT API → disable CSRF, `SessionCreationPolicy.STATELESS`. CORS ≠ CSRF.
- `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")`; `hasRole` adds `ROLE_` prefix.
- OAuth2 = delegated authorization; OIDC = identity on top (ID token). Prefer `oauth2ResourceServer().jwt()`.

### Microservices → [07](07-microservices.md)
- DB per service. Discovery (Eureka / K8s DNS). Gateway (Spring Cloud Gateway) = routing, auth, rate limit.
- Sync (REST/Feign/RestClient) vs async (Kafka). Prefer async for decoupling.
- Circuit breaker: **CLOSED → OPEN → HALF_OPEN**. Plus retry, timeout, bulkhead, rate limiter (Resilience4j).
- Saga (choreography / orchestration) + Outbox + idempotency key. Avoid 2PC.
- Tracing: traceId/spanId via Micrometer Tracing + Zipkin; MDC puts traceId in logs.
- Hystrix → Resilience4j, Zuul → Gateway, Ribbon → Spring Cloud LoadBalancer.

### AOP / Cache / Async / Kafka → [08](08-aop-caching-async-kafka.md)
- AOP: aspect, advice, pointcut, join point (method). `@Around` must call `proceed()`. Boot defaults to **CGLIB** proxies.
- Proxy limits (all of these): self-invocation, private/final, only Spring beans.
- `@Cacheable` (read-through) / `@CachePut` (always update) / `@CacheEvict` (remove). Needs `@EnableCaching`; set TTL.
- `@Async` needs `@EnableAsync`; define your own executor; void method exceptions are lost; no tx/security context propagation.
- `fixedRate` (from start) vs `fixedDelay` (after finish) vs `cron`. Cluster → ShedLock.
- Kafka: ordering only within a partition (use a key). One partition → one consumer per group; extra consumers idle.
- At-least-once is common → idempotent consumers. Failures → retries + **Dead Letter Topic**.
- Kafka (log, retention, replay, high throughput) vs RabbitMQ (queue, routing, deleted after ack).
