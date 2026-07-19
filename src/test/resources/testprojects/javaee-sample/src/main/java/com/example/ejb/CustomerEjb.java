package com.example.ejb;

import javax.ejb.Stateless;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import com.example.model.Customer;

@Stateless
public class CustomerEjb {

    @PersistenceContext(unitName = "customer-unit")
    EntityManager em;

    public Customer findById(Long id) { return em.find(Customer.class, id); }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void save(Customer customer) { em.persist(customer); }

    public void audit(Customer customer) {}
}
