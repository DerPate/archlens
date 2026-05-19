package com.example.pipeline.messaging;

import com.example.pipeline.model.OrderEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {
    private final KafkaTemplate<String, OrderEntity> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderEntity> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(OrderEntity order) {
        kafkaTemplate.send("${topics.orders.created}", order);
    }

    public void publishReady(OrderEntity order) {
        kafkaTemplate.send("${topics.orders.ready}", order);
    }
}
