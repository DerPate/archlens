package com.example.messaging;

import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Fixture: phase-3 of the two-phase pipeline. Consumes the channel that
 * {@link OrderForwarder} emits onto, closing the chain:
 *   @Incoming("orders-in") → buffer → @Scheduled → Emitter.send("orders-out") → @Incoming("orders-out")
 */
@ApplicationScoped
public class OrderNextStage {

    @Incoming("orders-out")
    public void process(String order) {
    }
}
