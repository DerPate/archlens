package com.example.war.api;

import javax.ejb.EJB;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/orders")
public class OrderResource {

    @EJB
    private OrderService orderService;

    @GET
    public String list() {
        return orderService.findAll();
    }
}
