package com.example.messaging;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Fixture: @Incoming consumer that writes to a ConcurrentHashMap via
 * cache.put(key, tmp.get(key)) — the stored value is derived from the
 * parameter through a local variable, not a direct assignment.
 */
@ApplicationScoped
public class CachingConsumer {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Incoming("events")
    public void handle(String event) {
        HashMap<String, String> tmp = new HashMap<>();
        tmp.put("id", event.toUpperCase());
        cache.put("id", tmp.get("id"));
    }

    public ConcurrentHashMap<String, String> getCache() {
        return cache;
    }
}
