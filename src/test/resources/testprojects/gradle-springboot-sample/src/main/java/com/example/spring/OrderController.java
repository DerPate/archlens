package com.example.spring;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public String get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    public String create(String body) {
        return service.create(body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    // Array-style annotation where method path starts with a path variable (no leading /)
    @GetMapping(value = {"{id}/items/{itemId}"})
    public String getItem(@PathVariable String id, @PathVariable String itemId) {
        return service.get(id + "/" + itemId);
    }

    // Array-style annotation with leading / — should work the same as before
    @DeleteMapping(value = {"/{id}/items/{itemId}"})
    public void deleteItem(@PathVariable String id, @PathVariable String itemId) {
        service.delete(id + "/" + itemId);
    }
}
