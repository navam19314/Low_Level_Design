# 06 — Spring Security & JWT 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Authentication vs Authorization 🔥

**Say this:** "Authentication checks who you are, using a password or a token. Authorization checks what you are allowed to do, using roles or authorities. Authentication always happens first. If authentication fails we return 401, and if the user is known but not allowed we return 403."

| | Authentication | Authorization |
|---|---|---|
| Question | Who are you? | What can you do? |
| Based on | Credentials (password, token) | Roles / authorities |
| Failure | **401 Unauthorized** | **403 Forbidden** |

**Default behavior:** adding `spring-boot-starter-security` secures all endpoints, creates user `user` with a random password printed in logs, and enables form login + HTTP Basic + CSRF. You override it with your own `SecurityFilterChain` bean and `UserDetailsService`.

---

## 2. Filter Chain & Authentication Flow 🔥🔥

**Say this:** "Spring Security is a chain of servlet filters that run before the request reaches the controller. `DelegatingFilterProxy` is a normal servlet filter that hands the request to Spring's `FilterChainProxy`, which picks the matching `SecurityFilterChain`. For login, the `AuthenticationManager` asks a provider to load the user through `UserDetailsService` and check the password with `PasswordEncoder`. On success the user is stored in `SecurityContextHolder`."

```
Request → DelegatingFilterProxy → FilterChainProxy → SecurityFilterChain
            ├─ CorsFilter, CsrfFilter
            ├─ JwtFilter / UsernamePasswordAuthenticationFilter   (authenticate)
            ├─ ExceptionTranslationFilter                        (→ 401 / 403)
            └─ AuthorizationFilter                               (check roles)
        → DispatcherServlet → Controller

AuthenticationManager (ProviderManager)
  → DaoAuthenticationProvider
      → UserDetailsService.loadUserByUsername()   (load from DB)
      → PasswordEncoder.matches(raw, hash)        (verify password)
  → authenticated Authentication → SecurityContextHolder (ThreadLocal)
```

**Most asked**
- **Where is the logged-in user stored?** `SecurityContextHolder.getContext().getAuthentication()`. It uses a ThreadLocal, so it is available anywhere in the same request thread.
- **Why `DelegatingFilterProxy`?** The servlet container doesn't know Spring beans; this proxy passes the request to Spring-managed filters.
- **Does filter order matter?** Yes. The JWT filter must run before the authorization check.

---

## 3. SecurityFilterChain Config 🔥

**Say this:** "In Spring Security 6 / Boot 3 we declare a `SecurityFilterChain` bean and use the lambda DSL. `WebSecurityConfigurerAdapter` was removed. For a JWT API we disable CSRF, make sessions stateless, and add our JWT filter before `UsernamePasswordAuthenticationFilter`."

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers("/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

**Most asked**
- **What changed in Spring Security 6?** `WebSecurityConfigurerAdapter` removed → `SecurityFilterChain` bean. `antMatchers` → `requestMatchers`. `authorizeRequests` → `authorizeHttpRequests`.

**Trap:** Rules are checked top to bottom. Put specific matchers first and `anyRequest()` last.

---

## 4. UserDetailsService & BCrypt

**Say this:** "`UserDetailsService` has one method, `loadUserByUsername()`, which tells Spring how to load a user, usually from the DB. Passwords are stored hashed with `BCryptPasswordEncoder`, never as plain text."

**Most asked**
- **Why BCrypt?** One-way hash, random salt per password (same password gives different hashes), and slow by design so brute force is expensive.

**Trap:** Don't use MD5/SHA-1. Always check with `encoder.matches(raw, hash)` — never compare hashes yourself.

---

## 5. JWT 🔥🔥

**Say this:** "A JWT is a signed, self-contained token that carries user info as claims. It has three Base64URL parts: `header.payload.signature`. The server verifies the signature and expiry on every request, so it doesn't need to store any session. It is signed, not encrypted — anyone can decode the payload, so we never put secrets in it."

**Key points**
- **Header:** algorithm + type (`HS256`). **Payload:** claims like `sub`, `roles`, `iat`, `exp`. **Signature:** `HMAC(header.payload, secret)` → proves it was not tampered with.
- **Flow:**
  1. Client calls `POST /auth/login` with username + password.
  2. Server checks them via `AuthenticationManager`, then creates a signed JWT with expiry.
  3. Client sends `Authorization: Bearer <token>` on every request.
  4. `JwtFilter` validates signature + expiry, loads the user, and sets `Authentication` in `SecurityContextHolder`.
  5. Request goes on to the controller.

```java
@Component
public class JwtFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String username = jwtService.extractUsername(token);
            UserDetails user = uds.loadUserByUsername(username);
            if (jwtService.isValid(token, user)) {               // signature + expiry
                var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);                                 // always continue
    }
}
```

**Most asked**
- **Is JWT encrypted?** No, only signed. The signature gives integrity, not confidentiality.
- **Why `OncePerRequestFilter`?** It makes sure the filter runs only once per request, even on internal forwards.
- **Where to keep the secret key?** In config/vault, at least 256 bits for HS256. Never hard-coded.

**Trap:** Forgetting `chain.doFilter()` blocks every request.

---

## 6. Session vs JWT 🔥

**Say this:** "With sessions, the server stores the login state and the client sends a `JSESSIONID` cookie. With JWT, the state is in the token itself, so any server can verify it — easy to scale. The downside of JWT is that it's hard to revoke before it expires."

| | Session | JWT |
|---|---|---|
| State stored | Server | Client (token) |
| Client sends | `JSESSIONID` cookie | `Authorization: Bearer` header |
| Scaling | Needs sticky sessions / Redis | Easy — any server verifies |
| Logout / revoke | Easy — delete session | Hard — valid until expiry |
| CSRF risk | Yes (cookies auto-sent) | No (if sent in header) |
| Best for | Server-rendered web apps | REST APIs, microservices, mobile |

---

## 7. Access vs Refresh Token 🔥

**Say this:** "The access token is short-lived (5–15 min) and sent on every call. The refresh token is long-lived, sent only to `/auth/refresh` to get a new access token, and stored in the DB so it can be revoked."

**Most asked — "How do you logout / revoke a JWT?"**
1. Keep access tokens short-lived.
2. Delete the refresh token from the DB on logout.
3. Blacklist the token ID (`jti`) in Redis with TTL = remaining expiry; the filter checks it.

---

## 8. OAuth2 & OIDC

**Say this:** "OAuth2 is a delegated authorization framework — it lets an app access your data on another service without your password, like 'Login with Google'. OIDC sits on top of OAuth2 and adds identity by returning an ID token (a JWT) with user info."

**Key points**
- Roles: **Resource Owner** (user) · **Client** (app) · **Authorization Server** (issues tokens) · **Resource Server** (API with the data).
- OAuth2 = authorization, OIDC = authentication. Spring: `spring-boot-starter-oauth2-client` / `oauth2-resource-server`.

---

## 9. CORS vs CSRF 🔥

**Say this:** "CORS is a browser rule that decides if JavaScript from one origin can call an API on another origin — we configure allowed origins to relax it. CSRF is an attack where an evil site makes your browser send a request with your cookies — Spring blocks it with a CSRF token."

| | CORS | CSRF |
|---|---|---|
| What | Browser rule for cross-origin calls | Attack using auto-sent cookies |
| Purpose | Allow trusted frontends | Protection via CSRF token |
| Config | `http.cors(...)` + `CorsConfigurationSource`, or `@CrossOrigin` | Enabled by default |

**Most asked**
- **When to disable CSRF?** Only for stateless APIs where the token is sent in a header (no cookies). Keep it enabled for session cookies or JWT stored in cookies.

**Trap:** CORS does not protect your server — it's only a browser restriction.

---

## 10. Method Security

**Say this:** "We secure single methods with `@EnableMethodSecurity` and `@PreAuthorize("hasRole('ADMIN')")`. It works through an AOP proxy, so a call from inside the same class skips the check."

**Most asked**
- **`hasRole` vs `hasAuthority`?** `hasRole('ADMIN')` checks `ROLE_ADMIN` (adds the prefix); `hasAuthority('ROLE_ADMIN')` needs the exact string.
- **401 vs 403?** 401 = not authenticated (no/invalid token). 403 = authenticated but no permission.

**Trap:** `@EnableGlobalMethodSecurity` is deprecated → use `@EnableMethodSecurity`.

---

## 📌 Quick Revision
- **Authentication** = who you are (401). **Authorization** = what you can do (403).
- Starter alone → all endpoints secured, user `user` + random password in logs.
- `DelegatingFilterProxy → FilterChainProxy → SecurityFilterChain → DispatcherServlet`.
- `AuthenticationManager → DaoAuthenticationProvider → UserDetailsService + PasswordEncoder → SecurityContextHolder`.
- Boot 3: `SecurityFilterChain` bean + lambda DSL; adapter removed; `requestMatchers`.
- **BCrypt** = salted, slow, one-way hash.
- JWT = `header.payload.signature`, Base64URL, **signed not encrypted**.
- `JwtFilter` (`OncePerRequestFilter`) → validate signature + expiry → set SecurityContext.
- JWT scales easily but is hard to revoke → short expiry + refresh token + Redis blacklist.
- OAuth2 = delegated authorization; OIDC = + identity (ID token).
- Disable **CSRF** only for stateless header-token APIs; **CORS** = allow cross-origin browser calls.
- `hasRole('X')` = `hasAuthority('ROLE_X')`.
