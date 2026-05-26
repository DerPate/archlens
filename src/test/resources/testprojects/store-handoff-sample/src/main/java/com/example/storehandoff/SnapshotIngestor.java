package com.example.storehandoff;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Receives raw snapshots from a messaging channel and stores them in an
 * in-memory map through a private helper method. The write is intentionally
 * NOT in the @Incoming method body so that the DataFlowTracer must follow
 * through the intra-component call to detect the STORE sink.
 */
@ApplicationScoped
public class SnapshotIngestor {

    private Map<String, String> snapshots = new ConcurrentHashMap<>();

    @Incoming("raw-snapshots")
    public void ingest(String payload) {
        String key = extractKey(payload);
        storeSnapshot(key, payload);
    }

    private String extractKey(String payload) {
        int sep = payload.indexOf(':');
        return sep > 0 ? payload.substring(0, sep) : payload;
    }

    private void storeSnapshot(String key, String data) {
        snapshots.put(key, data);
    }

    public Map<String, String> getSnapshots() {
        return snapshots;
    }
}
