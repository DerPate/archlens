package com.example.messaging;

import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;

/**
 * Fixture: shared in-memory buffer used as the meeting point of the two-phase
 * pipeline (consumer writes via {@link #store(String)}, scheduler reads via
 * {@link #peek()}). The {@code cache} field is the shared-state hop the
 * data-flow tracer must stitch together across two entrypoints.
 */
@ApplicationScoped
public class OrderBuffer {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public void store(String value) {
        cache.put("k", value);
    }

    public String peek() {
        return cache.get("k");
    }
}
