package com.example.kafka;

import org.springframework.stereotype.Service;

@Service
public class SisService {
    private final KafkaProducer kafkaProducer;

    public SisService(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public void create() {
        // SisKafkaEvent.getType() returns "sisPDFCreation"
        kafkaProducer.sendEvent(new SisKafkaEvent("123"));
    }
}
