package com.example.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
public class KafkaJsonProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaJsonProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Pattern: PARAM_REF — topic is a String parameter
    public void sendEvent(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload);
    }

    // Pattern: PARAM_REF overload with NO callers — mirrors the phoenix
    // "currently unused" wrapper overload. Its site must stay unresolved
    // (topic=null) instead of falling back to the cross-overload caller
    // walk, which would re-union all topics onto every caller.
    public void sendEvent(String topic, Object payload) {
        kafkaTemplate.send(topic, payload);
    }

    // Pattern: MESSAGE_OBJECT — topic is in Message headers
    public void sendMessage(Message<String> message) {
        kafkaTemplate.send(message);
    }
}
