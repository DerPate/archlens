package com.example.api;

import javax.ws.rs.*;
import javax.ejb.EJB;
import com.example.ejb.CustomerEjb;
import com.example.model.Customer;
import java.util.List;

@Path("/customers")
public class CustomerResource {

    @EJB
    CustomerEjb customerEjb;

    @GET
    public List<Object> list() { return null; }

    @POST
    public Customer create(Customer customer) { customerEjb.save(customer); return customer; }

    @GET
    @Path("/{id}")
    public Object findById(@PathParam("id") Long id) { return customerEjb.findById(id); }
}
