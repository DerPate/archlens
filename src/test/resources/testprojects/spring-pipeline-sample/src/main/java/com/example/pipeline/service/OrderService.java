package com.example.pipeline.service;

import com.example.pipeline.messaging.OrderEventPublisher;
import com.example.pipeline.model.OrderEntity;
import com.example.pipeline.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository repository;
    private final OrderEventPublisher publisher;

    public OrderService(OrderRepository repository, OrderEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public OrderEntity create(String id) {
        OrderEntity order = new OrderEntity(id, "CREATED");
        OrderEntity saved = repository.save(order);
        publisher.publishCreated(saved);
        return saved;
    }

    public void markReady(OrderEntity order) {
        order.setStatus("READY");
        OrderEntity saved = repository.save(order);
        publisher.publishReady(saved);
    }

    public List<OrderEntity> readyOrders() {
        return repository.findByStatus("READY");
    }
}
