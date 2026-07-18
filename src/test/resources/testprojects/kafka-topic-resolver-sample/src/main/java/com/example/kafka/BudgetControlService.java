package com.example.kafka;

import org.springframework.stereotype.Service;

@Service
public class BudgetControlService {
    private final KafkaJsonProducer kafkaJsonProducer;

    public BudgetControlService(KafkaJsonProducer kafkaJsonProducer) {
        this.kafkaJsonProducer = kafkaJsonProducer;
    }

    public void trigger() {
        // Caller passes a string literal — resolver should find "budgetControl"
        kafkaJsonProducer.sendEvent("budgetControl", "key", new Object());
    }
}
