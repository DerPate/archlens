package com.example.storehandoff;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class RuleEngineB {

    @Incoming("processed-b")
    public void evaluate(String snapshot) {
        // rule evaluation for B-class snapshots
    }
}
