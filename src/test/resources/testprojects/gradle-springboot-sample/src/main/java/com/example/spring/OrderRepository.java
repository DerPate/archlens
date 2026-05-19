package com.example.spring;

import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    public String find(String id) {
        return id;
    }

    public String save(String body) {
        return body;
    }

    public void delete(String id) {}
}
