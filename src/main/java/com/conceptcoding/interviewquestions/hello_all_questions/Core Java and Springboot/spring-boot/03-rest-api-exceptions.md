# 03 — REST APIs & Exception Handling 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. REST Basics

**Say this:** "REST is an API style where everything is a resource with a URI, and we act on it with standard HTTP methods. It is stateless — every request carries everything the server needs, like the token. URIs are nouns, not verbs: `/users/42`, not `/getUser?id=42`. Data is usually sent as JSON."

---

## 2. DispatcherServlet Request Flow 🔥🔥

**Say this:** "DispatcherServlet is the front controller of Spring MVC — one servlet that receives every request and routes it. First, servlet filters run (security, CORS). Then DispatcherServlet asks HandlerMapping which controller method matches, runs interceptors' `preHandle`, and the HandlerAdapter calls the method and binds path variables and the body. The return object is turned into JSON by an HttpMessageConverter (Jackson) and sent back."

```
Request → Filters → DispatcherServlet
            → HandlerMapping (find handler + interceptors)
            → Interceptor.preHandle()
            → HandlerAdapter → Controller → Service → Repository
            → HttpMessageConverter (Jackson: Object → JSON)
            → Interceptor.postHandle() / afterCompletion()
         → Response
```

**Trap:** With `@RestController`, the JSON body is already written before `postHandle()` runs — you can't change the body there.

---

## 3. @Controller vs @RestController 🔥

**Say this:** "`@RestController` is `@Controller` plus `@ResponseBody`. With `@Controller`, the returned string is treated as a view name, like an HTML template. With `@RestController`, the returned object is written straight into the response body as JSON."

**Trap:** `@Controller` returning `"user"` makes Spring look for a view named `user` → error if none exists.

---

## 4. @PathVariable vs @RequestParam vs @RequestBody 🔥

**Say this:** "Path variable identifies the resource, request param filters or sorts it, and request body carries the data to create or update."

| Annotation | Reads from | Example | Use for |
|---|---|---|---|
| `@PathVariable` | URI path | `/users/42` | Identify a resource |
| `@RequestParam` | Query string | `/users?city=Pune` | Filter / sort / optional input |
| `@RequestBody` | JSON body | `{"name":"Ram"}` | Create / update data |

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));                       // 200
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest req) {
        UserResponse saved = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/users/" + saved.id()))
                             .body(saved);                                    // 201 + Location
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();                            // 204
    }
}
```

**Most asked**
- **What is `ResponseEntity`?** The full HTTP response — status + headers + body.
- **How many `@RequestBody` per method?** Only one — the body can be read once.

**Trap:** `@RequestParam` is required by default → missing param = 400. Use `required = false` or `defaultValue`.

---

## 5. HTTP Status Codes 🔥

| Code | Meaning | When |
|---|---|---|
| **200** OK | Success | GET / PUT / PATCH |
| **201** Created | Created | POST (+ `Location` header) |
| **204** No Content | Success, no body | DELETE |
| **400** Bad Request | Invalid input | Validation failed, bad JSON |
| **401** Unauthorized | Not authenticated | Missing / invalid token |
| **403** Forbidden | No permission | USER calling ADMIN API |
| **404** Not Found | Resource missing | `/users/999` |
| **409** Conflict | State conflict | Duplicate email, version clash |
| **500** Internal Error | Server bug | Unhandled exception |

**Most asked**
- **401 vs 403?** 401 = "who are you?" (not logged in / bad token). 403 = "I know you, but you can't do this" (logged in, missing role).

---

## 6. HTTP Methods & Idempotency 🔥

**Say this:** "Safe means it doesn't change server state. Idempotent means calling it many times gives the same server state as calling it once. GET, PUT and DELETE are idempotent; POST is not, and PATCH is not guaranteed to be."

| Method | Purpose | Safe | Idempotent |
|---|---|---|---|
| GET | Read | ✅ | ✅ |
| POST | Create | ❌ | ❌ |
| PUT | Replace whole resource | ❌ | ✅ |
| PATCH | Partial update | ❌ | ❌ (not guaranteed) |
| DELETE | Remove | ❌ | ✅ |

**Most asked**
- **PUT vs PATCH vs POST?** POST = create new, server assigns ID, twice = 2 records. PUT = replace the full resource, missing fields become null. PATCH = update only the sent fields.
- **Why can PATCH be non-idempotent?** An operation like "increment count by 1" changes state on every call.

**Trap:** DELETE is idempotent even if the second call returns 404 — idempotency is about server state, not the response code.

---

## 7. DTO vs Entity 🔥

**Say this:** "An entity maps to a DB table. A DTO is a simple object for the API's input and output. I never expose entities directly — I map them to DTOs, manually or with MapStruct."

**Key points — why not expose entities?**
1. **Security** — leaks fields like `password` or internal flags.
2. **Over-posting** — client sends `"role":"ADMIN"` and it gets saved.
3. **Lazy loading** — `LazyInitializationException` or extra queries during JSON conversion.
4. **Infinite recursion** — bidirectional relations (`User ↔ Order`) loop in JSON.
5. **Tight coupling** — a DB schema change breaks the API contract.

---

## 8. Validation (@Valid) 🔥

**Say this:** "I put constraint annotations like `@NotBlank` and `@Email` on DTO fields, and add `@Valid` on the controller parameter. If validation fails, Spring throws `MethodArgumentNotValidException`, which becomes a 400. It needs `spring-boot-starter-validation`."

```java
public record UserRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @Min(18) int age) { }
```

**Most asked**
- **`@NotNull` vs `@NotEmpty` vs `@NotBlank`?** Not null · not null and size > 0 · not null and has non-space text (strings only).
- **Validate `@PathVariable` / `@RequestParam`?** Put constraints on the parameter (e.g. `@Min(1) Long id`). Boot 3.2+ validates them by built-in method validation (`HandlerMethodValidationException`); on older versions add `@Validated` on the class.
- **Nested objects?** Add `@Valid` on the nested field too.

**Trap:** Forget `@Valid` → constraints are silently ignored.

---

## 9. Pagination & Versioning

- **Pagination:** add a `Pageable` parameter and return `Page<T>` — `GET /users?page=0&size=10&sort=name,asc` (page is 0-based, default size 20).
- **Versioning:** URI versioning (`/api/v1/users`) is the most common and simplest; alternatives are a header (`X-API-Version`) or media type (`Accept: application/vnd.app.v1+json`).

---

## 10. Filter vs Interceptor 🔥

**Say this:** "A filter is a servlet-level component that runs before the request reaches DispatcherServlet — used for security, CORS and logging. An interceptor is a Spring MVC component that runs around the controller method and knows which handler is called — used for timing, auditing or per-endpoint checks."

| | Filter | Interceptor |
|---|---|---|
| Level | Servlet (`jakarta.servlet.Filter`) | Spring MVC (`HandlerInterceptor`) |
| Runs | Before DispatcherServlet | After DispatcherServlet, around controller |
| Knows handler? | ❌ | ✅ |
| Methods | `doFilter()` | `preHandle`, `postHandle`, `afterCompletion` |
| Use cases | Auth, CORS, logging, compression | Timing, audit, locale |
| Register | `@Component` / `FilterRegistrationBean` | `WebMvcConfigurer.addInterceptors()` |

**Most asked**
- **Is Spring Security a filter or an interceptor?** A chain of filters — that's why it runs before controllers.
- **`preHandle` returns false?** Request stops; the controller is not called.

---

## 11. RestTemplate vs WebClient vs RestClient

- **RestTemplate** = old synchronous client, in maintenance mode. **WebClient** = reactive, non-blocking (WebFlux).
- **RestClient** (Spring 6.1 / Boot 3.2+) = modern fluent synchronous client — preferred for blocking calls.

---

## 12. @ExceptionHandler vs @RestControllerAdvice 🔥

**Say this:** "`@ExceptionHandler` marks a method that handles a given exception type. Inside a controller, it works only for that controller. Put it in a `@RestControllerAdvice` class and it applies globally to all controllers — one place, one consistent error format. `@RestControllerAdvice` is `@ControllerAdvice` plus `@ResponseBody`, so the return value is written as JSON."

**Key points**
- No handler at all → Boot forwards to `/error` (`BasicErrorController`) → Whitelabel page or default JSON.
- `@ResponseStatus(HttpStatus.NOT_FOUND)` on a custom exception sets a fixed status but gives no custom body.

**Most asked**
- **Why not try-catch in every controller?** Duplicate code and inconsistent responses. Global advice handles this cross-cutting concern in one place.

---

## 13. Global Exception Handler 🔥

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String msg) { super(msg); }
}
public record ErrorResponse(Instant timestamp, int status, String message) {}

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)                 // 404
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(Instant.now(), 404, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)           // 400
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(Exception.class)                                 // 500 fallback
    ResponseEntity<ErrorResponse> generic(Exception ex) {
        log.error("Unexpected error", ex);                             // full trace in logs only
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse(Instant.now(), 500, "Something went wrong"));
    }
}
```

**Most asked**
- **How do you return field-level validation errors?** Catch `MethodArgumentNotValidException`, read `getBindingResult().getFieldErrors()`, return a `field → message` map with 400.
- **What is `ProblemDetail`?** Spring 6's built-in standard error format (RFC 9457: `type`, `title`, `status`, `detail`, `instance`) — return it from a handler; `spring.mvc.problemdetails.enabled=true` makes Spring's own MVC exceptions use it.
- **Which handler wins?** Local (in controller) beats global; the most specific exception type wins; between advices, `@Order` decides.

**Trap:** If your advice extends `ResponseEntityExceptionHandler`, don't also add `@ExceptionHandler(MethodArgumentNotValidException.class)` → "Ambiguous handler" at startup. Override `handleMethodArgumentNotValid(...)` instead.

**Trap:** Exceptions thrown in a **filter** are NOT handled by `@ControllerAdvice` — filters run before DispatcherServlet. Fix: write the error response in the filter, delegate to `HandlerExceptionResolver`, or (Spring Security) use `AuthenticationEntryPoint` (401) / `AccessDeniedHandler` (403).

---

## 📌 Quick Revision
- **REST** = resources (URIs) + HTTP methods + stateless + JSON.
- **Flow:** Filter → DispatcherServlet → HandlerMapping → preHandle → HandlerAdapter → Controller → Jackson → Response.
- `@RestController` = `@Controller` + `@ResponseBody`.
- **Path** = identify, **Param** = filter, **Body** = data. Only one `@RequestBody`.
- **201** create (+ Location), **204** delete, **401** = who are you, **403** = not allowed, **409** = conflict.
- Idempotent: **GET, PUT, DELETE**. Not: POST, PATCH. PUT = replace all, PATCH = partial.
- Never expose entities → record DTOs (security, over-posting, lazy loading, recursion, coupling).
- `@Valid` → `MethodArgumentNotValidException` → 400. Forget `@Valid` = no validation.
- Filter = servlet, before DispatcherServlet. Interceptor = Spring MVC, around controller.
- New sync client → **RestClient**; reactive → **WebClient**.
- `@RestControllerAdvice` = global JSON error handling. Local beats global, specific beats generic.
- `ProblemDetail` = standard error format in Boot 3.
- Filter exceptions **bypass** `@ControllerAdvice`.
