package com.example.messaging;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Fixture: phase-1 of the two-phase pipeline. {@code @Incoming("orders-in")}
 * delegates the payload to {@link OrderBuffer#store(String)} which writes the
 * shared {@code cache} field — the consumer's STORE sink in trace_data_flow.
 */
@ApplicationScoped
public class OrderIngest {

    @Inject
    OrderBuffer buffer;

    @Incoming("orders-in")
    public void consume(String payload) {
        buffer.store(payload);
    }
}
