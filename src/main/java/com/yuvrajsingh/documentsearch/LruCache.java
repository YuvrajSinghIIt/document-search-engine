package com.yuvrajsingh.documentsearch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A fixed-size, access-ordered least-recently-used cache. */
public final class LruCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> entries;

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(capacity, 0.75F, true);
    }

    public Optional<V> get(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    public void put(K key, V value) {
        entries.put(key, value);
        if (entries.size() > capacity) {
            K leastRecentlyUsedKey = entries.keySet().iterator().next();
            entries.remove(leastRecentlyUsedKey);
        }
    }

    public int size() {
        return entries.size();
    }
}
