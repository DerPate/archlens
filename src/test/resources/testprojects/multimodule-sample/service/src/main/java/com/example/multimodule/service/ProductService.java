package com.example.multimodule.service;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductService {
    public String findAll() {
        return "[]";
    }
}
