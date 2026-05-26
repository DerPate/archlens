package com.example.storehandoff;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Issue #15 Case 1: consumer whose ONLY parameter is a device identifier (the store KEY).
 * The store value is computed inside a helper, so the tracker must recognise `device`
 * as a key-match to emit the STORE sink — not just as a value-match.
 *
 * <p>Pipeline:
 * onDevice(device) → registerDevice(device) → registry.put(device, computeState(device))
 *
 * A parameter-less scheduler that iterates registry.keySet() must be stitched to this
 * consumer via the STORE sink on `registry`.
 */
@ApplicationScoped
public class DeviceRegistryConsumer {

    private Map<String, String> registry = new ConcurrentHashMap<>();

    @Incoming("device-registrations")
    public void onDevice(String device) {
        registerDevice(device);
    }

    private void registerDevice(String id) {
        registry.put(id, computeState(id));
    }

    private String computeState(String id) {
        return "ACTIVE:" + id;
    }

    public Map<String, String> getRegistry() {
        return registry;
    }
}
