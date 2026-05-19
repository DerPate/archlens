package com.example.pipeline.messaging;

import com.example.pipeline.model.OrderEntity;
import com.example.pipeline.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {
    private final OrderService service;

    public OrderCreatedListener(OrderService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${topics.orders.created}")
    public void onCreated(OrderEntity order) {
        service.markReady(order);
    }
}
