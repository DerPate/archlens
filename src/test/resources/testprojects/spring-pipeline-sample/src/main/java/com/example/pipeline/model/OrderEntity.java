package com.example.pipeline.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class OrderEntity {
    @Id
    private String id;
    private String status;

    public OrderEntity() {}

    public OrderEntity(String id, String status) {
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
