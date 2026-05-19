package com.example.pipeline.api;

import com.example.pipeline.model.OrderEntity;
import com.example.pipeline.service.OrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/{id}")
    public OrderEntity create(@PathVariable String id) {
        return service.create(id);
    }
}
