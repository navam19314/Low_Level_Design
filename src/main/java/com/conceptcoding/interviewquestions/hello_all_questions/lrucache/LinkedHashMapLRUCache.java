package com.conceptcoding.interviewquestions.hello_all_questions.lrucache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Production-clean" alternative — same semantics in 10 lines using Java's
 * {@link LinkedHashMap} with {@code accessOrder=true}. Override
 * {@link #removeEldestEntry} to evict when over capacity.
 *
 * <p>Why we still implement the from-scratch version: the from-scratch design
 * is the interview signal — it proves you understand the HashMap + DLL composition
 * that underlies LinkedHashMap. Mention BOTH in the room: "in production I'd use
 * this 10-line LinkedHashMap version; in this interview I'm showing you the
 * underlying mechanics."
 */
public class LinkedHashMapLRUCache<K, V> implements Cache<K, V> {

    private final int capacity;
    // accessOrder=true → get() reorders to "most recent" automatically
    private final LinkedHashMap<K, V> map;

    public LinkedHashMapLRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, /* accessOrder */ true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LinkedHashMapLRUCache.this.capacity;
            }
        };
    }

    @Override public synchronized V    get(K key)            { return map.get(key); }
    @Override public synchronized void put(K key, V value)   { map.put(key, value); }
    @Override public synchronized int  size()                { return map.size(); }
    @Override public synchronized void clear()               { map.clear(); }
}
