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

    // Pattern: PARAM_REF via overload — topic is the 3rd parameter.
    // Mirrors the phoenix KafkaProducer wrapper: same simple name as the
    // METHOD_CALL overload above, so overload collapse would lose this site.
    public void sendEvent(Object payload, String key, String topic) {
        kafkaTemplate.send(topic, key, payload.toString());
    }
}
