package com.example.war.core;

import javax.ejb.Stateless;
import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;

@Stateless
public class OrderRepository {

    @PersistenceContext
    private EntityManager em;

    public String list() {
        return "[]";
    }
}
