package com.conceptcoding.interviewquestions.hello_all_questions.lrucache;

/**
 * Generic key-value cache abstraction. Multiple impls (LRU, LFU, TTL, ARC) can
 * substitute behind this interface — the application code never cares.
 */
public interface Cache<K, V> {
    /** Returns the cached value, or {@code null} if absent. Side-effect: marks the entry "fresh" per the eviction policy. */
    V get(K key);

    /** Inserts or replaces; may evict the least-fresh entry if at capacity. */
    void put(K key, V value);

    /** Current number of entries (≤ capacity). */
    int size();

    /** Wipes all entries. */
    void clear();
}
