package com.conceptcoding.interviewquestions.hello_all_questions.urlshortener;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UrlShortenerDriver {

    public static void main(String[] args) throws Exception {
        scenarioRoundTrip();
        scenarioIdempotency();
        scenarioCustomAlias();
        scenarioUnknownExpand();
        scenarioDelete();
        scenarioBase62EncodeDecodeRoundTrip();
        scenarioRandomStrategyCollisionRetry();
        scenarioConcurrentSameUrl();
    }

    // ---- 1. Basic round-trip ----
    private static void scenarioRoundTrip() {
        System.out.println("=== Scenario 1: basic round-trip ===");
        UrlShortener s = new UrlShortener();
        String code = s.shorten("https://example.com/very/long/url?with=params");
        String back = s.expand(code);
        System.out.println("  shortCode: " + code);
        System.out.println("  expand back: " + back);
        System.out.println();
    }

    // ---- 2. Idempotency — same URL → same code ----
    private static void scenarioIdempotency() {
        System.out.println("=== Scenario 2: same URL → same code (idempotent) ===");
        UrlShortener s = new UrlShortener();
        String url = "https://example.com/foo";
        String a = s.shorten(url);
        String b = s.shorten(url);
        String c = s.shorten(url);
        System.out.println("  all three return same code: " + (a.equals(b) && b.equals(c)));
        System.out.println("  size()=" + s.size() + "  (expect 1 — single mapping)");
        System.out.println();
    }

    // ---- 3. Custom alias / vanity code ----
    private static void scenarioCustomAlias() {
        System.out.println("=== Scenario 3: custom vanity alias ===");
        UrlShortener s = new UrlShortener();
        String url = "https://example.com/promo";
        String code = s.shortenWithAlias(url, "summer2026");
        System.out.println("  alias=" + code + "  →  " + s.expand("summer2026"));
        try {
            s.shortenWithAlias("https://example.com/different", "summer2026");
        } catch (IllegalStateException e) {
            System.out.println("  taking the same alias for a DIFFERENT url: rejected ✓");
        }
        // Same alias + SAME url is idempotent OK
        String same = s.shortenWithAlias(url, "summer2026");
        System.out.println("  same alias + same url: " + same + " (idempotent ✓)");
        System.out.println();
    }

    // ---- 4. Unknown code expand throws ----
    private static void scenarioUnknownExpand() {
        System.out.println("=== Scenario 4: unknown short code → exception ===");
        UrlShortener s = new UrlShortener();
        try { s.expand("doesnotexist"); }
        catch (NoSuchElementException e) { System.out.println("  rejected: " + e.getMessage()); }
        System.out.println();
    }

    // ---- 5. Delete frees the code AND removes idempotency mapping ----
    private static void scenarioDelete() {
        System.out.println("=== Scenario 5: delete frees the code ===");
        UrlShortener s = new UrlShortener();
        String url = "https://example.com/x";
        String code = s.shorten(url);
        System.out.println("  before delete: expand → " + s.expand(code));

        s.delete(code);
        try { s.expand(code); }
        catch (NoSuchElementException e) { System.out.println("  after delete:  expand → throws ✓"); }

        // Idempotency entry should also be cleared — same url now gets a NEW code.
        String newCode = s.shorten(url);
        System.out.println("  shorten(url) again → " + newCode + " (different code: " + !newCode.equals(code) + ")");
        System.out.println();
    }

    // ---- 6. Base62 encode/decode are inverses ----
    private static void scenarioBase62EncodeDecodeRoundTrip() {
        System.out.println("=== Scenario 6: base62 encode/decode round-trip ===");
        long[] samples = {0L, 1L, 61L, 62L, 1_000_000L, 218_340_105_584_896L /* 62^8 */};
        boolean ok = true;
        for (long id : samples) {
            String enc = Base62Encoder.encode(id);
            long dec = Base62Encoder.decode(enc);
            ok &= (dec == id);
            System.out.printf("  %20d → %-10s → %d  %s%n", id, enc, dec, dec == id ? "✓" : "✗");
        }
        System.out.println("  all round-trips OK: " + ok);
        System.out.println();
    }

    // ---- 7. RandomIdStrategy retries on collision ----
    private static void scenarioRandomStrategyCollisionRetry() {
        System.out.println("=== Scenario 7: RandomIdStrategy retries on collision ===");
        // Tiny range (10) so collisions are nearly certain after a few inserts.
        // Use a seeded Random for reproducibility.
        UrlShortener s = new UrlShortener(new RandomIdStrategy(10L, new Random(0), 100));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 9; i++) {       // load factor up to 9/10
            String code = s.shorten("https://x.com/" + i);
            codes.add(code);
        }
        System.out.println("  9 distinct codes generated (tiny 10-id range): " + (codes.size() == 9));
        System.out.println("  retries handled collisions without throwing ✓");
        System.out.println();
    }

    // ---- 8. Concurrent shorten() of SAME url → exactly one code ----
    private static void scenarioConcurrentSameUrl() throws Exception {
        System.out.println("=== Scenario 8: 50 threads shorten same URL → exactly 1 distinct code ===");
        UrlShortener s = new UrlShortener();
        String url = "https://example.com/contended";
        int threads = 50;
        Set<String> seen = new CopyOnWriteArraySet<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    seen.add(s.shorten(url));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  distinct codes returned: " + seen.size() + "  (expect 1)");
        System.out.println("  size() in shortener:     " + s.size() + "  (expect 1)");
        System.out.println(seen.size() == 1 && s.size() == 1
                ? "  ✓ idempotency holds under contention"
                : "  ✗ RACE — multiple codes minted for the same url");
    }
}
