package com.example.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Pattern: METHOD_CALL — topic is event.getType()
    public void sendEvent(IKafkaEvent event) {
        kafkaTemplate.send(event.getType(), event.getId(), "payload");
    }
}
