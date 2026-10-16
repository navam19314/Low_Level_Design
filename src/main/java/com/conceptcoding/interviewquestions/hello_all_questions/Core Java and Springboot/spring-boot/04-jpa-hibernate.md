# 04 — Spring Data JPA & Hibernate 🔥🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. JPA vs Hibernate vs Spring Data JPA 🔥

**Say this:** "JPA is a specification — just interfaces and annotations like `@Entity` and `EntityManager`. Hibernate is the most popular implementation of JPA — it actually generates SQL, does caching and dirty checking. Spring Data JPA sits on top of JPA and auto-generates repositories, so I just write an interface and Spring creates the implementation."

| | JPA | Hibernate | Spring Data JPA |
|---|---|---|---|
| What | Specification | Implementation (ORM) | Abstraction over JPA |
| Gives | `@Entity`, `EntityManager`, JPQL | SQL generation, caching, dirty checking | `JpaRepository`, derived queries |
| Package | `jakarta.persistence` | `org.hibernate` | `org.springframework.data.jpa` |

**Most asked**
- **Can Spring Data JPA work without Hibernate?** Yes — it needs some JPA provider; Hibernate is Boot's default, EclipseLink also works.
- **What is ORM?** Mapping Java classes to DB tables (objects ↔ rows) so you work with objects, not raw SQL.

---

## 2. Entity Basics

**Say this:** "An entity is a Java class mapped to a table — each object is one row. It needs `@Entity`, an `@Id`, and a no-arg constructor."

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;
}
```

**Most asked**
- **IDENTITY vs SEQUENCE?** IDENTITY = DB auto-increment, id known only after INSERT (disables batch inserts). SEQUENCE = id taken from a DB sequence before INSERT (supports batching, best for Postgres/Oracle).

---

## 3. Repositories & Derived Queries

**Say this:** "I extend `JpaRepository<User, Long>` and Spring creates a proxy implementation (`SimpleJpaRepository`) at runtime. For simple queries, Spring parses the method name and builds the query for me."

```java
interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByAgeGreaterThanAndCity(int age, String city);
    boolean existsByEmail(String email);
}
```

**Most asked**
- **CrudRepository vs JpaRepository?** `CrudRepository` = basic CRUD. `JpaRepository` = CRUD + paging/sorting + JPA extras (`flush`, `saveAndFlush`, `deleteAllInBatch`, `getReferenceById`).
- **Wrong property in derived method name?** App fails at startup — errors caught early.
- **Page vs Slice?** `Page` runs an extra COUNT query for total; `Slice` only knows `hasNext()` — faster.
- **Projections?** Interface or record/DTO projection fetches only needed columns — read-only, faster.

---

## 4. @Query: JPQL vs Native + @Modifying 🔥

**Say this:** "When method names get long, I use `@Query`. JPQL uses entity and field names and is DB-independent. Native queries use real table and column names — I use them only for DB-specific features. For UPDATE or DELETE queries I must add `@Modifying` and run inside a transaction."

```java
@Query("SELECT u FROM User u WHERE u.email = :email")                 // JPQL
Optional<User> findUser(@Param("email") String email);

@Query(value = "SELECT * FROM users WHERE email = ?1", nativeQuery = true)  // native
Optional<User> findUserNative(String email);

@Modifying(clearAutomatically = true)
@Transactional
@Query("UPDATE User u SET u.active = false WHERE u.lastLogin < :date")
int deactivate(@Param("date") LocalDate date);
```

**Most asked**
- **JPQL vs native?** JPQL = entities, portable, validated at startup. Native = tables, DB-specific, not validated.
- **Why `@Modifying`?** Tells Spring the query changes data (returns row count, not entities).

**Trap:** Bulk update bypasses the persistence context → loaded entities become stale. Use `clearAutomatically = true`.

---

## 5. Relationships 🔥

**Say this:** "The owning side is the one that holds the foreign key — it has `@JoinColumn`, usually the `@ManyToOne` side. Only changes on the owning side are saved to the DB. The other side uses `mappedBy` to point to the owning field."

```java
@Entity
class Department {
    @Id @GeneratedValue Long id;
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Employee> employees = new ArrayList<>();      // inverse side
}

@Entity
class Employee {
    @Id @GeneratedValue Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")                // owning side (FK)
    Department department;
}
```

**Key points**
- **Cascade** → operations on parent (persist, merge, remove) pass to children.
- **orphanRemoval = true** → child removed from the parent's collection is deleted from DB.
- `@ManyToMany` → uses a join table (`@JoinTable`); use `Set`, never `CascadeType.REMOVE`.

**Most asked**
- **What is `mappedBy`?** Marks the inverse side; its value is the field name on the owning side.
- **Cascade REMOVE vs orphanRemoval?** REMOVE deletes children when the parent is deleted; orphanRemoval also deletes a child just unlinked from the parent.

**Trap:** In bidirectional mappings set both sides (helper `addEmployee()`), and don't put relations in `toString/equals` (infinite loop / lazy loading).

---

## 6. Fetch Types: Lazy vs Eager 🔥

**Say this:** "Lazy means related data is loaded only when accessed — Hibernate puts a proxy until then. Eager means it's loaded immediately with the parent. I make everything LAZY and fetch what I need per query with JOIN FETCH or an entity graph."

| Annotation | Default |
|---|---|
| `@ManyToOne`, `@OneToOne` | **EAGER** |
| `@OneToMany`, `@ManyToMany` | **LAZY** |

**Most asked**
- **Why avoid EAGER?** Loads data you don't need, can't be turned off per query, and still causes N+1 on `findAll()`.

---

## 7. LazyInitializationException & Open-in-View 🔥

**Say this:** "This happens when I access a lazy field after the persistence context (session) is closed — Hibernate can't go to the DB anymore. The fix is to load the data while the session is open: JOIN FETCH, `@EntityGraph`, DTO projection, or do the work inside a `@Transactional` service method."

```java
Department d = deptRepo.findById(1L).orElseThrow();  // session closes here
d.getEmployees().size();                              // LazyInitializationException
```

**Most asked**
- **Fixes?** JOIN FETCH · `@EntityGraph` · DTO projection · access inside `@Transactional`. Avoid EAGER and `enable_lazy_load_no_trans` (hacks).
- **What is Open-Session-In-View?** Keeps the session open until the HTTP response is written, so lazy loading works in controllers. `spring.jpa.open-in-view=true` by default.
- **Should OSIV be on?** No — set it `false` in production. It holds a DB connection for the whole request and hides N+1.

---

## 8. N+1 Problem 🔥🔥

**Say this:** "N+1 is when one query loads N parents, and then Hibernate fires one extra query per parent to load its children — so 1 + N queries instead of 1. I detect it with SQL logs or Hibernate statistics, and fix it with JOIN FETCH, `@EntityGraph`, batch fetching, or a DTO projection."

```java
List<Department> depts = deptRepo.findAll();       // 1 query
for (Department d : depts) d.getEmployees().size(); // +N queries

// Fix 1: JOIN FETCH → 1 query
@Query("SELECT d FROM Department d JOIN FETCH d.employees")
List<Department> findAllWithEmployees();

// Fix 2: @EntityGraph → same, no JPQL
@EntityGraph(attributePaths = "employees")
List<Department> findAll();

// Fix 3: batch fetching → 1 + N/50 queries (IN clause)
@BatchSize(size = 50)            // or hibernate.default_batch_fetch_size=50
@OneToMany(mappedBy = "department") List<Employee> employees;

// Fix 4: DTO projection → only needed columns
@Query("SELECT new com.app.DeptDto(d.name, e.name) FROM Department d JOIN d.employees e")
List<DeptDto> findDeptEmployees();
```

**Key points**
- **Detect:** `logging.level.org.hibernate.SQL=DEBUG`, `hibernate.generate_statistics=true`, or tests that assert query count.
- Hibernate 6 removes duplicate parents from JOIN FETCH automatically (no `DISTINCT` needed).

**Most asked**
- **Does EAGER fix N+1?** No — `findAll()` with EAGER `@ManyToOne` still fires N extra queries.
- **JOIN FETCH vs @BatchSize?** JOIN FETCH = one query (best for single collection). BatchSize = few queries, works with pagination.

**Trap:** JOIN FETCH a collection + pagination → Hibernate paginates in memory (warning HHH90003004). JOIN FETCH two `List`s → `MultipleBagFetchException` — use `Set` or separate queries.

---

## 9. Entity States & Dirty Checking 🔥

**Say this:** "An entity has four states: Transient (just `new`), Managed (tracked by the persistence context), Detached (context closed), and Removed (marked for delete). For managed entities Hibernate does dirty checking — it keeps a snapshot and at flush/commit auto-generates UPDATE for changed fields, so I don't need to call `save()`."

| State | Meaning |
|---|---|
| Transient | `new` object, not tracked, not in DB |
| Managed | Tracked; changes auto-synced at flush |
| Detached | Was managed, context closed / `detach()` |
| Removed | Deleted at flush/commit |

```java
@Transactional
public void rename(Long id, String name) {
    User u = repo.findById(id).orElseThrow();  // managed
    u.setName(name);                           // UPDATE at commit, no save()
}
```

**Most asked**
- **persist vs merge?** `persist` makes a new entity managed; `merge` copies a detached entity into a managed copy and returns it. Spring's `save()` = persist if new, else merge.

**Trap:** Without `@Transactional` the entity is detached right after `findById` → the change is not saved.

---

## 10. Persistence Context & Caching 🔥

**Say this:** "The persistence context is the set of managed entities for the current transaction — `EntityManager` is the API to it (Hibernate calls it `Session`). It also acts as the first-level cache: the same `find(id)` twice in one transaction hits the DB once. The second-level cache is optional and shared across sessions."

| First-Level Cache | Second-Level Cache |
|---|---|
| Per transaction/session | Shared across sessions (SessionFactory) |
| On by default, can't disable | Optional, must configure |
| Built-in | Needs provider: Ehcache, Hazelcast, Infinispan |

**Most asked**
- **`save()` vs `saveAndFlush()`?** `save` may delay SQL until flush/commit; `saveAndFlush` sends SQL immediately (still rollback-able) — useful to catch constraint errors early.
- **`findById()` vs `getReferenceById()`?** `findById` hits DB now, returns `Optional`. `getReferenceById` returns a proxy, no DB hit until accessed (`EntityNotFoundException` if missing) — good for setting FKs.
- **Batch inserts?** Set `hibernate.jdbc.batch_size=50` and use SEQUENCE (IDENTITY disables batching).

---

## 11. Optimistic vs Pessimistic Locking 🔥

**Say this:** "Optimistic locking assumes conflicts are rare — no DB lock, just a `@Version` column checked on update; if someone else changed the row, the update fails and I retry. Pessimistic locking assumes conflicts are common — it locks the row with `SELECT ... FOR UPDATE` so others wait."

| Optimistic | Pessimistic |
|---|---|
| `@Version` field | `@Lock(LockModeType.PESSIMISTIC_WRITE)` |
| No DB lock, version check at update | Row lock (`FOR UPDATE`) |
| Read-heavy, low conflict | High conflict (stock, balance) |
| Fails → `OptimisticLockException` → retry | Waits / deadlocks → set timeout |

```java
@Version Long version;   // UPDATE ... SET version=6 WHERE id=? AND version=5

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findForUpdate(@Param("id") Long id);   // needs @Transactional
```

**Most asked**
- **Which for inventory/payments?** Low contention → optimistic + retry; very hot rows → pessimistic.
- **What does Spring throw?** `ObjectOptimisticLockingFailureException` — handle with retry (`@Retryable`) or return 409 Conflict.

---

## 📌 Quick Revision
- **JPA** = spec, **Hibernate** = implementation, **Spring Data JPA** = auto repositories.
- IDENTITY = id after insert, no batching; SEQUENCE = id before insert, batching.
- `JpaRepository` = CRUD + paging + `flush`/`saveAndFlush`. Derived queries from method names.
- `@Query` JPQL = entities, native = tables. `@Modifying` for UPDATE/DELETE (+ `clearAutomatically`).
- **Owning side** = FK (`@ManyToOne` + `@JoinColumn`); `mappedBy` on inverse. orphanRemoval deletes unlinked children.
- Defaults: `*ToOne` EAGER, `*ToMany` LAZY → make **all LAZY**.
- **LazyInitializationException** = lazy access after session closed → JOIN FETCH / EntityGraph / DTO / `@Transactional`. `open-in-view=false`.
- **N+1** = 1 + N queries → JOIN FETCH, `@EntityGraph`, `@BatchSize`, DTO. Detect via SQL logs/statistics.
- States: Transient → Managed → Detached / Removed. **Dirty checking** = auto UPDATE for managed entities.
- L1 cache = per transaction, default. L2 = shared, optional.
- `findById` = DB hit; `getReferenceById` = proxy.
- Optimistic = `@Version` + retry. Pessimistic = `PESSIMISTIC_WRITE` row lock.
