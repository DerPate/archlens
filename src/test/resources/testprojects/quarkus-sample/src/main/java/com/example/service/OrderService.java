package com.example.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import com.example.repository.OrderRepository;
import com.example.model.Order;

@ApplicationScoped
public class OrderService {

    @Inject
    OrderRepository orderRepository;

    public Order find(Long id) {
        return orderRepository.findById(id);
    }
}
