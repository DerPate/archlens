package com.example.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import com.example.repository.OrderRepository;
import com.example.model.Order;
import javax.transaction.Transactional;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class OrderService {

    @Inject
    OrderRepository orderRepository;

    public Order find(Long id) {
        return orderRepository.findById(id);
    }

    public Order findTernary(Long id) {
        return orderRepository.findById(id != null ? id : 0L);
    }

    public Order findWrapped(Long raw) {
        return orderRepository.findById(Long.valueOf(String.valueOf(raw)));
    }
}
