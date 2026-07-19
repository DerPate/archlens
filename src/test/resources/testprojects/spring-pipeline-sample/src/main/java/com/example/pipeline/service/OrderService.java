package com.example.pipeline.service;

import com.example.pipeline.messaging.OrderEventPublisher;
import com.example.pipeline.model.OrderEntity;
import com.example.pipeline.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
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

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void markReady(OrderEntity order) {
        order.setStatus("READY");
        OrderEntity saved = repository.save(order);
        publisher.publishReady(saved);
    }

    public List<OrderEntity> readyOrders() {
        return repository.findByStatus("READY");
    }

    public void prepare(OrderEntity order) {
        markReady(order);
    }
}
