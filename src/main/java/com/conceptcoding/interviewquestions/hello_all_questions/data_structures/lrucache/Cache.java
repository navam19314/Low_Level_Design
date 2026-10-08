package com.conceptcoding.interviewquestions.hello_all_questions.data_structures.lrucache;

// Generic key-value cache abstraction. Multiple impls (LRU, LFU, TTL, ARC) can
// substitute behind this interface — the application code never cares.
public interface Cache<K, V> {

    // Returns the cached value, or null if absent. Side-effect: marks the entry
    // "fresh" per the eviction policy.
    V get(K key);

    // Inserts or replaces; may evict the least-fresh entry if at capacity.
    void put(K key, V value);

    int size();     // current number of entries (<= capacity)
    void clear();   // wipes all entries
}
