package com.example.storehandoff;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Demonstrates issue #15: STORE sink must be emitted when store.put() value argument
 * is a method invocation rather than a direct variable read.
 *
 * <p>The call chain is:
 * onEvent(payload) → processAndStore(key, data) → deviceStore.put(key, localCache.get(key))
 *
 * At DFS depth 1 the tracked name is "data" (mapped from "payload") but the put() value
 * is localCache.get(key) — a CtInvocation. The extractor must not fall back to the key
 * argument as the source var, and the tracer must emit the STORE sink when no source var
 * could be extracted.
 */
@ApplicationScoped
public class DeviceStateIngestor {

    private Map<String, String> deviceStore = new ConcurrentHashMap<>();

    @Incoming("device-events")
    public void onEvent(String payload) {
        String key = extractKey(payload);
        processAndStore(key, payload);
    }

    private String extractKey(String payload) {
        int sep = payload.indexOf(':');
        return sep > 0 ? payload.substring(0, sep) : payload;
    }

    private void processAndStore(String key, String data) {
        Map<String, String> localCache = new HashMap<>();
        enrichCache(key, data, localCache);
        // Issue #15 pattern: value argument is a CtInvocation (localCache.get(key)),
        // not a CtVariableRead. The STORE sink must still be emitted.
        deviceStore.put(key, localCache.get(key));
    }

    private void enrichCache(String k, String v, Map<String, String> cache) {
        cache.put(k, v.toUpperCase());
    }

    public Map<String, String> getDeviceStore() {
        return deviceStore;
    }
}
