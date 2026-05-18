package com.example.multi.api;

import com.example.multi.service.MultiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MultiController {
    private final MultiService service;

    public MultiController(MultiService service) {
        this.service = service;
    }

    @GetMapping("/multi")
    public String multi() {
        return service.message();
    }
}
