package com.example.pipeline.scheduler;

import com.example.pipeline.model.OrderEntity;
import com.example.pipeline.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderDispatchScheduler {
    private final OrderService service;

    public OrderDispatchScheduler(OrderService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatchReadyOrders() {
        for (OrderEntity order : service.readyOrders()) {
            order.getId();
        }
    }
}
