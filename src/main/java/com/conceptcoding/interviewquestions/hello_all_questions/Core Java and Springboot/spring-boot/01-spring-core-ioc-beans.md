# 01 — Spring Core: IoC, DI, Beans, Lifecycle & Scopes 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. IoC & Dependency Injection 🔥

**Say this:** "IoC means my code no longer creates its own dependencies — the Spring container creates objects, wires them together and manages their lifecycle. DI is how IoC is implemented: dependencies are passed into a class from outside, usually through the constructor. This gives loose coupling, easy testing with mocks, and easy swapping of implementations."

```java
// ❌ Tight coupling — class creates its own dependency
class OrderService { private PaymentService p = new PaymentService(); }

// ✅ DI — Spring passes it in
@Service
class OrderService {
    private final PaymentService p;
    OrderService(PaymentService p) { this.p = p; }   // single constructor → no @Autowired needed
}
```

**Most asked**
- **IoC vs DI?** IoC = the principle (framework controls object creation). DI = the technique to achieve it.
- **What is the IoC container?** `ApplicationContext` — creates beans, injects dependencies, manages lifecycle. Boot creates it for you.
- **Benefits of DI?** Loose coupling, testability (inject mocks), swap implementations without code changes.

---

## 2. Types of DI — Why Constructor Injection 🔥

**Say this:** "There are three types: constructor, setter and field injection. I always use constructor injection — fields can be `final`, the object is never created half-wired, and it's easy to unit test with `new Service(mock)`. Setter is only for optional dependencies, and field injection should be avoided."

| Type | Use |
|---|---|
| **Constructor** | ✅ Recommended — required dependencies |
| Setter | Optional dependencies |
| Field (`@Autowired` on field) | ❌ Avoid — no `final`, hidden deps, needs reflection to test |

**Most asked**
- **Why constructor injection?** `final`/immutable, no half-built objects, easy testing, circular dependencies fail fast at startup.
- **Is `@Autowired` needed on the constructor?** No, not if the class has only one constructor.
- **Too many constructor params?** A code smell — the class has too many responsibilities, split it.

---

## 3. Bean & Stereotype Annotations

**Say this:** "A bean is just an object created and managed by the Spring container. We register beans with stereotype annotations on our own classes, or with `@Bean` methods inside a `@Configuration` class."

| Annotation | Layer | Special behavior |
|---|---|---|
| `@Component` | Generic | — |
| `@Service` | Business logic | None — just readability |
| `@Repository` | Data access | **Translates DB exceptions** into Spring's `DataAccessException` |
| `@Controller` / `@RestController` | Web | Handles HTTP; `@RestController` = `@Controller` + `@ResponseBody` |

**Most asked**
- **`@Service` vs `@Component`?** Same behavior; `@Service` only shows intent.
- **What's special about `@Repository`?** Exception translation — vendor-specific SQL exceptions become `DataAccessException`.

---

## 4. @Component vs @Bean 🔥

**Say this:** "`@Component` goes on a class and is picked up automatically by component scanning — I use it for my own classes. `@Bean` goes on a method in a `@Configuration` class and I write the creation code myself — I use it for third-party classes I can't annotate, or when creation needs custom logic."

```java
@Configuration
class AppConfig {
    @Bean
    RestTemplate restTemplate() { return new RestTemplate(); }   // third-party class
}
```

| `@Component` | `@Bean` |
|---|---|
| On a class | On a method |
| Auto-detected by scan | Explicitly declared |
| Your own classes | Third-party classes / custom creation |

**Most asked**
- **Can I use `@Component` on `RestTemplate`?** No — you can't edit library code. Use `@Bean`.
- **Default bean name?** `@Component` → class name with lowercase first letter (`userService`). `@Bean` → method name.

---

## 5. BeanFactory vs ApplicationContext

`BeanFactory` is the basic container — lazy, no extras. `ApplicationContext` extends it and adds **eager singleton creation**, events, i18n, AOP and annotation support. We always use `ApplicationContext`; Spring Boot creates it.

---

## 6. Multiple Beans of Same Type: @Primary & @Qualifier 🔥

**Say this:** "If two beans implement the same interface, Spring fails with `NoUniqueBeanDefinitionException`. I mark one as `@Primary` to make it the default, and use `@Qualifier` where I need a specific one. `@Qualifier` wins over `@Primary`. If I need all implementations, I inject a `List` or `Map` — great for the Strategy pattern."

```java
interface Payment { }
@Service @Primary class CardPayment implements Payment { }
@Service          class UpiPayment  implements Payment { }

OrderService(Payment p)                          // CardPayment (@Primary)
RefundService(@Qualifier("upiPayment") Payment p) // UpiPayment
PaymentRouter(Map<String, Payment> all)          // {"cardPayment"=..., "upiPayment"=...}
```

**Most asked**
- **`@Primary` vs `@Qualifier`?** `@Primary` = default choice; `@Qualifier` = pick by name, and it wins.
- **How to inject all implementations?** `List<Payment>` (all beans) or `Map<String, Payment>` (bean name → bean).
- **No bean found?** `NoSuchBeanDefinitionException`. Two found? `NoUniqueBeanDefinitionException`.

**Trap:** `@SpringBootApplication` scans only **its own package and sub-packages**. A class outside it is not a bean → `NoSuchBeanDefinitionException`.

---

## 7. Circular Dependency 🔥

**Say this:** "A circular dependency is when bean A needs B and B needs A. With constructor injection Spring can't create either one first, so startup fails — and since Boot 2.6, circular references are forbidden by default even for field injection. The best fix is to redesign: usually by moving the shared logic into a third bean."

```java
@Service class A { A(B b) {} }
@Service class B { B(A a) {} }   // ❌ BeanCurrentlyInCreationException at startup
```

**Fixes (best first):**
1. **Redesign** — extract shared logic into a third class, or use events.
2. **`@Lazy`** on one constructor param → Spring injects a proxy, real bean is resolved later.
3. Setter/field injection + `spring.main.allow-circular-references=true` (hides the design problem — avoid).

**Most asked**
- **Why does constructor injection catch it?** Neither object can be built without the other already existing — so it fails fast instead of hiding the problem.

---

## 8. Bean Lifecycle 🔥

**Say this:** "Spring first instantiates the bean by calling the constructor, then injects the remaining dependencies. Then BeanPostProcessors run before init, then the `@PostConstruct` method, then BeanPostProcessors run after init — that's where AOP proxies for `@Transactional`, `@Async` and `@Cacheable` are created. Now the bean is ready. On shutdown, `@PreDestroy` is called."

```
Instantiate (constructor) → Inject deps (setter/field)
  → BPP before-init → @PostConstruct → BPP after-init (🔥 AOP proxies created)
  → READY → (context close) → @PreDestroy
```

```java
@Service
class CountryCache {
    @PostConstruct void load()  { /* deps ready: warm up cache */ }   // jakarta.annotation.*
    @PreDestroy    void clear() { /* release resources */ }
}
```

**Most asked**
- **Why not do init work in the constructor?** Setter/field deps aren't injected yet, and the proxy doesn't exist yet — so `@Transactional` won't work. `@PostConstruct` runs when the bean is fully wired.
- **What is a BeanPostProcessor?** A hook that runs on **every** bean before/after init — Spring uses it for `@Autowired`, `@PostConstruct` and creating AOP proxies.
- **Where are `@Transactional` proxies made?** In `postProcessAfterInitialization`. Other beans get the proxy, not the raw object.

**Trap:** Boot 3 uses `jakarta.annotation.PostConstruct`. Old `javax.*` import → method silently not called. Also, `@PreDestroy` is **not** called for prototype beans or on `kill -9`.

---

## 9. Bean Scopes 🔥

**Say this:** "Scope decides how many instances Spring creates and how long they live. The default is singleton — one instance per container. Prototype gives a new instance every time it's requested. Web apps also have request, session and application scopes."

| Scope | Instances | `@PreDestroy` called? |
|---|---|---|
| **singleton** (default) | One per container | ✅ |
| **prototype** | New on every injection / `getBean()` | ❌ |
| request | One per HTTP request | ✅ |
| session | One per HTTP session | ✅ |
| application | One per `ServletContext` | ✅ |

**Most asked**
- **Default scope?** Singleton.
- **Does Spring manage the full prototype lifecycle?** No — it creates and initializes it, then hands it off. Destroy is never called; the caller cleans up.
- **Spring singleton vs GoF singleton?** Spring = one per **container** (per bean name); GoF = one per **JVM/ClassLoader** with a private constructor.

---

## 10. Are Singleton Beans Thread-Safe? 🔥

**Say this:** "No. Spring doesn't make beans thread-safe — one singleton instance is shared by all request threads. It's safe in practice because services and controllers are stateless: they only hold `final` references to other beans, and request data lives in method parameters and local variables."

```java
@Service
class CounterService {
    private int count;                                   // ❌ race condition
    private final AtomicInteger safe = new AtomicInteger(); // ✅
}
```

**Key points — how to keep singletons safe:**
- Keep beans **stateless** (only `final` injected deps).
- Use local variables, or concurrent types (`AtomicInteger`, `ConcurrentHashMap`).
- Per-request state → request-scoped bean; per-thread → `ThreadLocal` (always `remove()`).

**Most asked**
- **Why are controllers safe then?** They're stateless — no mutable instance fields.

---

## 11. Prototype Bean inside a Singleton 🔥

**Say this:** "A singleton is created once, so its dependencies are injected once. If I inject a prototype into it, I get the same prototype instance forever — it behaves like a singleton. To get a fresh one each time, I inject `ObjectProvider` and call `getObject()`, or use `@Lookup`, or a scoped proxy."

```java
@Service
class OrderService {
    private final ObjectProvider<ReportBuilder> provider;
    OrderService(ObjectProvider<ReportBuilder> provider) { this.provider = provider; }
    void run() { ReportBuilder b = provider.getObject(); }   // new instance each call ✅
}
```

**Fixes:**
1. **`ObjectProvider<T>`** — recommended, simple, no magic.
2. **`@Lookup`** method — Spring overrides it to return a new bean each call.
3. **Scoped proxy** — `@Scope(value="prototype", proxyMode=TARGET_CLASS)`.

**Trap:** Scoped proxy on prototype creates a new instance on **every method call** — state is lost between calls. Same problem for request/session beans in a singleton — `@RequestScope` uses a scoped proxy by default, so it just works.

---

## 12. @Lazy

Creates the bean on first use instead of at startup. Faster startup, but wiring errors show up later at runtime. On an injection point, `@Lazy` injects a proxy (used to break circular deps). Global: `spring.main.lazy-initialization=true`.

---

## 📌 Quick Revision
- **IoC** = container controls object creation; **DI** = how (deps passed in). Container = `ApplicationContext`.
- **Constructor injection** = best: `final`, testable, fails fast. Avoid field injection.
- `@Repository` translates DB exceptions; `@Service` is just a label.
- `@Component` = class, auto-scan, own code. `@Bean` = method, third-party code.
- `ApplicationContext` > `BeanFactory` (eager, events, AOP).
- Many beans of one type → `@Primary` (default) / `@Qualifier` (wins). Inject `List`/`Map` for all.
- Scan = main class package + sub-packages.
- Circular dependency → redesign; `@Lazy` as a quick fix. Forbidden by default since Boot 2.6.
- Lifecycle: **constructor → inject → BPP before → `@PostConstruct` → BPP after (proxies) → ready → `@PreDestroy`**.
- Init in `@PostConstruct`, not constructor. Use `jakarta.annotation` in Boot 3.
- Scopes: **singleton (default)**, prototype (no destroy), request, session, application.
- Singletons are **not thread-safe** → keep them stateless.
- Prototype in singleton = injected once → **`ObjectProvider`**, `@Lookup`, scoped proxy.
