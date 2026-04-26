package com.example.ejb;

import javax.ejb.Stateless;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;
import com.example.model.Customer;

@Stateless
public class CustomerEjb {

    @PersistenceContext
    EntityManager em;

    public Customer findById(Long id) { return em.find(Customer.class, id); }

    public void save(Customer customer) { em.persist(customer); }
}
