package com.conceptcoding.interviewquestions.hello_all_questions.urlshortener;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator + facade. Three load-bearing concerns:
 *
 * <ol>
 *   <li><b>Bidirectional storage</b> — {@code codeToUrl} for lookup, {@code urlToCode}
 *       so the SAME long-url submitted twice returns the SAME short code (idempotency).</li>
 *   <li><b>Pluggable id generation</b> — {@link IdGenerationStrategy}: counter (predictable)
 *       or random+retry (unpredictable). Same Cache-style swap at construction.</li>
 *   <li><b>Base-62 encoding</b> — long ids → URL-safe short codes ({@link Base62Encoder}).</li>
 * </ol>
 *
 * <p>Thread-safety: {@code ConcurrentHashMap.putIfAbsent} on the {@code urlToCode}
 * map provides the atomic "if I'm first, I win" semantics for idempotency without
 * an outer service lock.
 */
public class UrlShortener {

    private final ConcurrentHashMap<String, String> codeToUrl = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> urlToCode = new ConcurrentHashMap<>();
    private final IdGenerationStrategy idStrategy;

    public UrlShortener()                                   { this(new CounterIdStrategy()); }
    public UrlShortener(IdGenerationStrategy idStrategy)    { this.idStrategy = idStrategy; }

    /**
     * Shorten a URL. IDEMPOTENT — same input URL returns the same short code on
     * subsequent calls. Built on {@code computeIfAbsent} so concurrent shorten()
     * calls for the same URL produce ONE entry (same pattern as PaymentGateway).
     */
    public String shorten(String longUrl) {
        if (longUrl == null || longUrl.isBlank())
            throw new IllegalArgumentException("longUrl required");

        return urlToCode.computeIfAbsent(longUrl, url -> {
            long id = idStrategy.nextId(code -> !codeToUrl.containsKey(code));
            String code = Base62Encoder.encode(id);
            codeToUrl.put(code, url);
            return code;
        });
    }

    /**
     * Shorten with a custom alias (vanity code). Rejects if the alias is already taken.
     * If the same {@code longUrl} was previously shortened with a different code, this
     * adds a SECOND alias — the URL is now reachable via both.
     */
    public String shortenWithAlias(String longUrl, String alias) {
        if (longUrl == null || longUrl.isBlank()) throw new IllegalArgumentException("longUrl required");
        if (alias == null || alias.isBlank())     throw new IllegalArgumentException("alias required");
        for (char c : alias.toCharArray()) {
            if ("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".indexOf(c) < 0) {
                throw new IllegalArgumentException("alias must be base-62: " + alias);
            }
        }
        String existing = codeToUrl.putIfAbsent(alias, longUrl);
        if (existing != null && !existing.equals(longUrl)) {
            throw new IllegalStateException("Alias already in use: " + alias);
        }
        // Note: we deliberately do NOT update urlToCode here — the "primary" code for
        // this URL stays whatever was set first (vanity codes don't override).
        return alias;
    }

    /** Expand a short code back to the original URL. */
    public String expand(String shortCode) {
        String url = codeToUrl.get(shortCode);
        if (url == null) throw new NoSuchElementException("Unknown short code: " + shortCode);
        return url;
    }

    /** Delete a short code. Useful for take-downs / expiration sweepers. */
    public boolean delete(String shortCode) {
        String url = codeToUrl.remove(shortCode);
        if (url == null) return false;
        // Only clear urlToCode if THIS code was the primary one (vanity codes don't appear in urlToCode)
        urlToCode.remove(url, shortCode);
        return true;
    }

    public int size() {
        return codeToUrl.size();
    }
}
