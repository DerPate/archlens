package com.example.war.core;

import javax.ejb.Stateless;
import javax.ejb.EJB;

@Stateless
public class OrderService {

    @EJB
    private OrderRepository orderRepository;

    public String findAll() {
        return orderRepository.list();
    }
}
