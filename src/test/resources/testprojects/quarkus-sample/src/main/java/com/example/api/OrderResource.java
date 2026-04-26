package com.example.api;

import javax.ws.rs.*;
import javax.inject.Inject;
import com.example.service.OrderService;
import java.util.List;

@Path("/orders")
public class OrderResource {

    @Inject
    OrderService orderService;

    @GET
    public List<Object> list() { return null; }

    @GET
    @Path("/{id}")
    public Object get(@PathParam("id") Long id) { return orderService.find(id); }

    @POST
    public Object create(Object order) { return null; }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") Long id) {}
}
