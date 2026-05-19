package com.example.spring;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "billing", url = "${billing.base-url}")
public interface BillingClient {
    @GetMapping("/billing/{id}")
    String getBill(@PathVariable String id);
}
