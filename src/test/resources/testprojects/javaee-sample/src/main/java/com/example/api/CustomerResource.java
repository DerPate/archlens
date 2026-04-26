package com.example.api;

import javax.ws.rs.*;
import javax.ejb.EJB;
import com.example.ejb.CustomerEjb;
import java.util.List;

@Path("/customers")
public class CustomerResource {

    @EJB
    CustomerEjb customerEjb;

    @GET
    public List<Object> list() { return null; }

    @POST
    public Object create(Object customer) { return null; }

    @GET
    @Path("/{id}")
    public Object findById(@PathParam("id") Long id) { return customerEjb.findById(id); }
}
