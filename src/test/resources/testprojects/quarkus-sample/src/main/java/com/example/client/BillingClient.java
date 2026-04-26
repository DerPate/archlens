package com.example.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

@RegisterRestClient(configKey = "billing")
@Path("/billing")
public interface BillingClient {

    @GET
    @Path("/{orderId}")
    String status(String orderId);
}
