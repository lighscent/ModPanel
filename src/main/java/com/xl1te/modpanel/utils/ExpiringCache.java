package com.xl1te.modpanel.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ExpiringCache<K, V> {

    private final ConcurrentMap<K, Entry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public ExpiringCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public V get(K key) {
        Entry<V> entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiry) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    public void put(K key, V value) {
        cache.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    private record Entry<V>(V value, long expiry) {}
}
