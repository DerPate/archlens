package com.example.storehandoff;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class RuleEngineA {

    @Incoming("processed-a")
    public void evaluate(String snapshot) {
        // rule evaluation for A-class snapshots
    }
}
