package dev.villagerssavior;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Small bounded server-thread cache. Values expire in game ticks and never enter saved history. */
public final class TickCache<K, V> {
    private record Entry<V>(long tick, V value) {}
    private final long lifetime;
    private final int capacity;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    public TickCache(long lifetime, int capacity) {
        if (lifetime <= 0 || capacity <= 0) throw new IllegalArgumentException("positive cache bounds required");
        this.lifetime = lifetime; this.capacity = capacity;
    }
    public V get(K key, long now, Supplier<V> compute) {
        Entry<V> old = entries.get(key);
        if (old != null && now >= old.tick && now - old.tick < lifetime) return old.value;
        V value = Objects.requireNonNull(compute.get());
        put(key, now, value);
        return value;
    }
    public void put(K key, long now, V value) {
        entries.put(key, new Entry<>(now, Objects.requireNonNull(value)));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    public int size() { return entries.size(); }
}
