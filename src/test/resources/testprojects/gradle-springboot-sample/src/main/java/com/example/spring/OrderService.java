package com.example.spring;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public String get(String id) {
        return repository.find(id);
    }

    public String create(String body) {
        return repository.save(body);
    }

    public void delete(String id) {
        repository.delete(id);
    }
}
