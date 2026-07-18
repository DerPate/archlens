package com.example.kafka;

import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final KafkaJsonProducer kafkaJsonProducer;

    public AccountService(KafkaJsonProducer kafkaJsonProducer) {
        this.kafkaJsonProducer = kafkaJsonProducer;
    }

    public void register() {
        // Second caller of the same PARAM_REF wrapper as BudgetControlService —
        // "account" must be attributed to this call site only, never unioned
        // with "budgetControl" onto both callers.
        kafkaJsonProducer.sendEvent("account", "key", new Object());
    }
}
