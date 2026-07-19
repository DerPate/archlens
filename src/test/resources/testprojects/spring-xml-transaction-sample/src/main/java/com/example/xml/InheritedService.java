package com.example.xml;

import org.springframework.stereotype.Service;

@Service
public class InheritedService implements InheritedOperations {
    @Override
    public String inspect() {
        return "ok";
    }
}
