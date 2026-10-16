# 07 — Microservices 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Monolith vs Microservices 🔥

**Say this:** "A monolith is one app built and deployed as a single unit, usually with one shared database. Microservices split the app into small services, each owning one business capability and its own database, deployed independently and talking over REST or messaging. We get independent deploy and scaling, but pay with network calls, distributed data and harder debugging."

| Point | Monolith | Microservices |
|---|---|---|
| Deploy | Whole app for any change | Each service independently |
| Scale | Whole app | Only the busy service |
| Failure | One bug can bring down all | Isolated (if designed well) |
| Complexity | Simple to build/test/debug | High — network, tracing, distributed data |
| Data | One DB, easy ACID | DB per service, no simple ACID across services |

**Database per service:** each service owns its tables; others access its data only through its API or events.

**Most asked**
- **When NOT to use microservices?** Small team, new product with unclear domain, no DevOps maturity (CI/CD, containers, monitoring), low traffic, or heavy need for cross-data ACID → start with a **modular monolith**.
- **Why own DB per service?** Loose coupling — one service can change its schema without breaking others.

**Trap:** Services that call each other synchronously on every request = **distributed monolith** (worst of both worlds).

---

## 2. Typical Architecture

```
Client ──► API Gateway ──► [ Order Svc | Payment Svc | Inventory Svc ]
           (auth, routing,        │            │             │
            rate limit)       [Order DB]  [Payment DB]  [Inventory DB]
                                  └──── Kafka / RabbitMQ (events) ───┘
Supporting: Service Registry (Eureka) · Config Server · Tracing (Zipkin)
```

---

## 3. Service Discovery 🔥

**Say this:** "Instances keep starting, stopping and changing IPs, so services can't hardcode addresses. With Eureka, each service registers itself on startup and sends heartbeats; callers look up by service name like `payment-service`. In Kubernetes we usually skip Eureka — a K8s Service plus DNS (`http://payment-service`) does discovery and load balancing."

**Key points**
- Eureka Server: `@EnableEurekaServer`. Clients auto-register when the eureka-client starter is on the classpath.
- Client-side discovery (Eureka + Spring Cloud LoadBalancer picks an instance) vs server-side (K8s Service / AWS ELB routes for you).
- **Load balancing:** Spring Cloud LoadBalancer, round-robin by default; built into Feign and `lb://` routes.

**Most asked**
- **What if an instance dies?** Heartbeats stop → registry removes it after a timeout.
- **Do you need Eureka on Kubernetes?** Usually no — K8s DNS handles it.

---

## 4. API Gateway 🔥

**Say this:** "An API gateway is the single entry point for all clients. It routes each request to the right service and handles cross-cutting concerns in one place — auth, rate limiting, logging. In Spring we use Spring Cloud Gateway, which is reactive (WebFlux/Netty)."

**Responsibilities:** routing · JWT validation · rate limiting (429) · load balancing · logging/tracing · CORS · request/response transformation.

```yaml
spring.cloud.gateway.routes:
  - id: order-route
    uri: lb://order-service        # lb:// = discovery + load balance
    predicates: [ Path=/api/orders/** ]
```

**Most asked**
- **Why not let clients call services directly?** Clients would need every URL, and auth/rate limiting would be duplicated in every service.
- **How is rate limiting done?** Gateway `RequestRateLimiter` filter (Redis, token bucket) keyed by user/IP → 429 when exceeded.

**Trap:** The gateway can become a single point of failure, so run several instances behind a load balancer.

---

## 5. Sync vs Async Communication 🔥

**Say this:** "Sync means the caller sends a request and waits, like REST or gRPC. Async means the caller publishes an event to Kafka or RabbitMQ and moves on. I use sync when I need an answer now, and async for events, notifications and long work, because it decouples services."

| Point | Sync (REST) | Async (Kafka/RabbitMQ) |
|---|---|---|
| Tools | `RestClient`, `WebClient`, OpenFeign | Kafka, RabbitMQ |
| Coupling | Tight — both must be up | Loose — broker buffers |
| Response | Immediate | Eventual |
| Failure | Can cascade | Messages wait for the consumer |

```java
@FeignClient(name = "payment-service")   // resolved via discovery + LB
interface PaymentClient {
    @PostMapping("/payments")
    PaymentResponse pay(@RequestBody PaymentRequest req);
}
// needs @EnableFeignClients on the main class
```

**Most asked**
- **RestTemplate?** Maintenance mode → use `RestClient` (sync, Boot 3.2+) or `WebClient` (reactive).
- **Why Feign?** Declarative — just an interface, no HTTP boilerplate.

---

## 6. Circuit Breaker 🔥🔥

**Say this:** "A circuit breaker stops calling a failing service for some time and returns a fallback instead. This prevents cascading failures and stops threads from piling up waiting on a dead service. In Spring Boot 3 we use Resilience4j — Hystrix is deprecated."

```
        failure rate > threshold
 CLOSED ─────────────────────────► OPEN  (fail fast → fallback)
   ▲                                 │ wait duration over
   │ trial calls succeed             ▼
   └─────────────────────────── HALF_OPEN (few trial calls)
              trial calls fail ──► back to OPEN
```

```java
@CircuitBreaker(name = "paymentCB", fallbackMethod = "payFallback")
public String pay(Order o) { return paymentClient.pay(o); }

// same params + Throwable, same return type
public String payFallback(Order o, Throwable ex) { return "Payment pending"; }
```
Config: `failure-rate-threshold: 50`, `sliding-window-size: 10`, `wait-duration-in-open-state: 10s`.

**Other Resilience4j tools**
- **Retry** — retry transient failures with exponential backoff (only idempotent ops).
- **TimeLimiter / timeout** — fail after X seconds, never wait forever.
- **Bulkhead** — cap concurrent calls per dependency so one slow service can't eat all threads.
- **RateLimiter** — cap calls per time window.

**Most asked**
- **Explain the states.** CLOSED = normal, failures counted. OPEN = no calls, fallback right away. HALF_OPEN = a few trial calls; success → CLOSED, failure → OPEN.
- **Retry vs circuit breaker?** Retry handles short glitches; circuit breaker handles long outages. Use both — retry inside, breaker outside.

**Trap:** `@CircuitBreaker` works through a Spring AOP proxy, so a call from the same class (self-invocation) skips it. The method must be public.

---

## 7. Config Server & Distributed Tracing

**Config Server:** central config for all services, stored in Git (`@EnableConfigServer`). Refresh without restart with `@RefreshScope` + `/actuator/refresh` (Spring Cloud Bus for all instances). On K8s, ConfigMaps/Secrets are common instead.

**Tracing:** one **traceId** for the whole request, one **spanId** per hop, passed in headers. Boot 3 uses **Micrometer Tracing** (replaced Sleuth) + Zipkin/Jaeger. Logs go to ELK, searchable by traceId.

---

## 8. Saga Pattern 🔥🔥

**Say this:** "Each service has its own DB, so one `@Transactional` can't cover Order and Payment. A Saga is a chain of local transactions, one per service. If a step fails, we run compensating transactions to undo the earlier steps. This gives eventual consistency, not ACID."

```
1. Order Svc:     create order (PENDING)   → compensate: cancel order
2. Payment Svc:   charge money             → compensate: refund
3. Inventory Svc: reserve stock ❌ out of stock
   → refund payment → cancel order (CANCELLED)
```

| Choreography | Orchestration |
|---|---|
| Services react to each other's events (no central boss) | A central orchestrator tells each service what to do |
| Loosely coupled, simple for few steps | Clear flow, easy to track and change |
| Hard to follow as steps grow | Extra component; logic is centralised |
| Kafka events | Orchestrator service / Temporal / Camunda |

**Most asked**
- **Why not 2PC?** Coordinator is a single point of failure, locks are held across services (slow, blocking), and Kafka/NoSQL don't support it well → poor availability and scale.
- **Which one would you pick?** Choreography for 2–3 steps; orchestration for long or complex flows.

**Trap:** Compensation is a **business undo** (refund), not a DB rollback. It must be idempotent and retryable.

---

## 9. Idempotency 🔥

**Say this:** "An idempotent operation gives the same result whether it runs once or many times. Retries and timeouts can make the same request arrive twice, so for payments the client sends a unique `Idempotency-Key` header. The server stores key → result, and on a repeat it returns the saved result instead of charging again."

**Key points**
- GET, PUT, DELETE are idempotent; **POST is not**.
- Kafka is at-least-once → consumers must also dedupe (e.g., store processed event IDs).

**Most asked**
- **How do you prevent double payment?** Idempotency key + unique constraint on that key in the DB.

---

## 10. Other Patterns (one line each)

| Pattern | One-liner |
|---|---|
| **Outbox** | Save data + event in the same local DB transaction (outbox table); a relay/CDC (Debezium) publishes to Kafka. Fixes the dual-write problem (DB saved, event lost). |
| **CQRS** | Separate models for writes (commands) and reads (queries). |
| **Strangler Fig** | Move a monolith to microservices gradually, one feature at a time. |
| **API Composition / Event Sourcing** | Aggregator joins results from many services / store every state change as an event. |

---

## 11. Handling Failures 🔥

**Most asked — "Service B is slow or down. How does A stay healthy?"**
**Timeouts → retry with exponential backoff + jitter → circuit breaker → fallback → idempotency**, plus bulkhead, and async messaging where an instant answer isn't needed.

**Trap:** Retries without backoff or a circuit breaker cause a **retry storm** that kills the struggling service faster.

**Deprecated Netflix tools:** Ribbon → Spring Cloud LoadBalancer, Hystrix → Resilience4j, Zuul → Spring Cloud Gateway.

---

## 📌 Quick Revision
- **Microservices** = small independent services, **own DB each**. Small team/unclear domain → modular monolith.
- **Discovery** = Eureka (register + heartbeat + lookup by name); on K8s, Service DNS.
- **Gateway** = single entry: routing, auth, rate limit → **Spring Cloud Gateway**.
- **Sync** = RestClient/WebClient/Feign; **Async** = Kafka/RabbitMQ (loose coupling).
- **Circuit breaker**: CLOSED → OPEN → HALF_OPEN; Resilience4j `@CircuitBreaker(fallbackMethod)`. Plus Retry, TimeLimiter, Bulkhead, RateLimiter.
- **Config Server** = central Git config + `@RefreshScope`. **Tracing** = traceId + spanId, Micrometer + Zipkin.
- **Saga** = local transactions + compensations; choreography vs orchestration. Avoid 2PC.
- **Idempotency key** stops double charges. **Outbox** = reliable events. **CQRS** = split read/write. **Strangler** = gradual migration.
- Failures: **timeout → retry + backoff → circuit breaker → fallback → idempotency**.
