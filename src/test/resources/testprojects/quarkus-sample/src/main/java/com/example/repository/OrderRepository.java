package com.example.repository;

import javax.enterprise.context.ApplicationScoped;
import com.example.model.Order;

@ApplicationScoped
public class OrderRepository {

    public Order findById(Long id) { return null; }

    public void save(Order order) {}
}
