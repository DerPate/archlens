package com.example.kafka;

import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.springframework.stereotype.Component;

// Pattern: Hibernate entity lifecycle listener — not a Spring entrypoint annotation,
// but a real Kafka producer root (mirrors the phoenix KafkaPost*EventListener beans).
@Component
public class EntityEventListener implements PostUpdateEventListener {
    private final KafkaProducer kafkaProducer;

    public EntityEventListener(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        kafkaProducer.sendEvent(new SisKafkaEvent("entity"));
    }
}
