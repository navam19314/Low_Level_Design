package com.conceptcoding.interviewquestions.hello_all_questions.lrucache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LRUCacheDriver {

    public static void main(String[] args) throws Exception {
        scenarioBasic();
        scenarioEviction();
        scenarioGetRefreshesRecency();
        scenarioPutUpdateDoesNotGrow();
        scenarioBothImplsAgree();
        scenarioConcurrentBurst();
    }

    // ---- 1. Basic put/get ----
    private static void scenarioBasic() {
        System.out.println("=== Scenario 1: basic put/get ===");
        Cache<String, Integer> c = new LRUCache<>(3);
        c.put("a", 1);
        c.put("b", 2);
        c.put("c", 3);
        System.out.println("  get(a)=" + c.get("a") + " (expect 1)");
        System.out.println("  get(b)=" + c.get("b") + " (expect 2)");
        System.out.println("  size()=" + c.size() + " (expect 3)");
        System.out.println();
    }

    // ---- 2. Eviction when over capacity ----
    private static void scenarioEviction() {
        System.out.println("=== Scenario 2: eviction when over capacity ===");
        Cache<String, Integer> c = new LRUCache<>(3);
        c.put("a", 1);  // [a]
        c.put("b", 2);  // [b, a]
        c.put("c", 3);  // [c, b, a]
        c.put("d", 4);  // [d, c, b]  — "a" was LRU, evicted
        System.out.println("  get(a)=" + c.get("a") + " (expect null — evicted)");
        System.out.println("  get(b)=" + c.get("b") + " (expect 2)");
        System.out.println("  get(d)=" + c.get("d") + " (expect 4)");
        System.out.println("  size()=" + c.size() + " (expect 3)");
        System.out.println();
    }

    // ---- 3. get() refreshes recency — should protect from eviction ----
    private static void scenarioGetRefreshesRecency() {
        System.out.println("=== Scenario 3: get() refreshes recency ===");
        Cache<String, Integer> c = new LRUCache<>(3);
        c.put("a", 1);  // [a]
        c.put("b", 2);  // [b, a]
        c.put("c", 3);  // [c, b, a]
        c.get("a");     // [a, c, b]  — a is now MRU
        c.put("d", 4);  // [d, a, c]  — "b" was LRU (not "a"), evicted
        System.out.println("  get(a)=" + c.get("a") + " (expect 1 — was refreshed)");
        System.out.println("  get(b)=" + c.get("b") + " (expect null — evicted)");
        System.out.println("  get(c)=" + c.get("c") + " (expect 3)");
        System.out.println("  get(d)=" + c.get("d") + " (expect 4)");
        System.out.println();
    }

    // ---- 4. put() that updates an existing key must NOT grow size ----
    private static void scenarioPutUpdateDoesNotGrow() {
        System.out.println("=== Scenario 4: put on existing key doesn't grow size ===");
        Cache<String, Integer> c = new LRUCache<>(3);
        c.put("a", 1);
        c.put("a", 11);   // update value, not insert
        c.put("a", 111);
        System.out.println("  size()=" + c.size()  + " (expect 1)");
        System.out.println("  get(a)=" + c.get("a") + " (expect 111)");
        System.out.println();
    }

    // ---- 5. Both implementations should agree ----
    private static void scenarioBothImplsAgree() {
        System.out.println("=== Scenario 5: from-scratch and LinkedHashMap impls agree ===");
        Cache<String, Integer> a = new LRUCache<>(3);
        Cache<String, Integer> b = new LinkedHashMapLRUCache<>(3);
        String[] ops = {"put A 1", "put B 2", "put C 3", "get A", "put D 4", "get B"};
        for (String op : ops) applyOp(a, op);
        for (String op : ops) applyOp(b, op);
        boolean agree = true;
        for (String k : new String[]{"A", "B", "C", "D"}) {
            Integer va = a.get(k), vb = b.get(k);
            // .get reorders, so we just check string equality of values
            agree &= ((va == null) == (vb == null)) && (va == null || va.equals(vb));
        }
        // Note: after the .get calls above we've reordered both caches; just compare final get values
        System.out.println("  both impls agree on final state: " + agree);
        System.out.println();
    }

    // ---- 6. Concurrent burst — synchronization holds ----
    private static void scenarioConcurrentBurst() throws Exception {
        System.out.println("=== Scenario 6: 50 threads × 100 ops — no corruption ===");
        Cache<Integer, Integer> c = new LRUCache<>(10);
        int threads = 50, opsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger nullsBefore = new AtomicInteger();    // gets returning null

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < opsPerThread; i++) {
                    if ((i & 1) == 0) c.put((tid * 1000) + i, i);
                    else if (c.get((tid * 1000) + (i - 1)) == null) nullsBefore.incrementAndGet();
                }
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  size()=" + c.size() + " (expect ≤ 10 — capacity)");
        System.out.println("  null gets:  " + nullsBefore.get() + " (expected — many entries evicted in a 10-slot cache)");
        System.out.println(c.size() <= 10 ? "  ✓ capacity invariant held under contention" : "  ✗ capacity exceeded — race!");
    }

    private static void applyOp(Cache<String, Integer> c, String op) {
        String[] p = op.split(" ");
        if (p[0].equals("put")) c.put(p[1], Integer.parseInt(p[2]));
        else                     c.get(p[1]);
    }
}
