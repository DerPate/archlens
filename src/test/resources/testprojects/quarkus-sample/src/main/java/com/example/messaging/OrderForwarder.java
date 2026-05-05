package com.example.messaging;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import io.quarkus.scheduler.Scheduled;

/**
 * Fixture: phase-2 of the two-phase pipeline. The scheduler reads from the
 * shared {@link OrderBuffer} and forwards via an injected {@code Emitter}
 * onto another channel — the producer-side MESSAGING outbound sink that
 * trace_data_flow must surface and link back to the consumer's STORE sink.
 */
@ApplicationScoped
public class OrderForwarder {

    @Inject
    OrderBuffer buffer;

    @Inject
    @Channel("orders-out")
    Emitter<String> outEmitter;

    @Scheduled(every = "1s")
    public void forward() {
        String v = buffer.peek();
        outEmitter.send(v);
    }
}
