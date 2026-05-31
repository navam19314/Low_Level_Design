package com.conceptcoding.interviewquestions.hello_all_questions.lrucache;

import java.util.HashMap;
import java.util.Map;

/**
 * LRU cache via HashMap + doubly-linked list — the canonical O(1) design.
 *
 * <pre>
 *   HashMap<K, Node>   — O(1) lookup of the node for a key
 *   Doubly-linked list — O(1) move-to-head and remove-tail (eviction)
 *
 *   head (most recently used)  ◀── ... ──▶  tail (least recently used)
 * </pre>
 *
 * <p>Sentinel head/tail nodes eliminate the head/tail null checks every time
 * a node moves — the cleanest way to write doubly-linked-list ops in Java.
 *
 * <p>Thread-safety: every public method is {@code synchronized}. For higher
 * throughput you'd reach for {@code java.util.concurrent} primitives (or just
 * use {@code ConcurrentHashMap} + per-bucket linked lists); for interview
 * scope coarse-grained sync is correct and trivially obvious.
 */
public class LRUCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> index = new HashMap<>();
    private final Node<K, V> head;     // sentinel — newest is head.next
    private final Node<K, V> tail;     // sentinel — oldest is tail.prev

    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        // Sentinels point at each other initially → empty list.
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public synchronized V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) return null;
        moveToHead(node);              // mark as most-recently-used
        return node.value;
    }

    @Override
    public synchronized void put(K key, V value) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            // Replace value + bump to head — no size change.
            existing.value = value;
            moveToHead(existing);
            return;
        }
        // New entry — evict LRU first if at capacity.
        if (index.size() == capacity) {
            Node<K, V> lru = tail.prev;     // oldest is tail.prev
            removeNode(lru);
            index.remove(lru.key);
        }
        Node<K, V> node = new Node<>(key, value);
        addToHead(node);
        index.put(key, node);
    }

    @Override
    public synchronized int size() { return index.size(); }

    @Override
    public synchronized void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
    }

    // ----- doubly-linked-list internals -----

    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    /** Doubly-linked-list node. Package-private so the cache touches fields directly. */
    static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev, next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
