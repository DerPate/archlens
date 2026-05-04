package com.example.repository;

import javax.enterprise.context.ApplicationScoped;
import com.example.model.Order;

import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class OrderRepository {

    public Order findById(Long id) { return null; }

    public void save(Order order) {}

    public void archive(Order order, Path destination) throws Exception {
        Files.writeString(destination, order.toString());
    }
}
