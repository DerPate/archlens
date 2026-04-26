package com.example.multimodule.api;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

@Path("/products")
public class ProductResource {

    @Inject
    private ProductService productService;

    @GET
    public String list() {
        return productService.findAll();
    }
}
