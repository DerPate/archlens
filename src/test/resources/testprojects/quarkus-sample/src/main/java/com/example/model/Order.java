package com.example.model;

import javax.persistence.Entity;

@Entity
public class Order {
    public Long id;
    public String description;
    public String status;
}
