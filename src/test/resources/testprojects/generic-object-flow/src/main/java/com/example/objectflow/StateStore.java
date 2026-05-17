package com.example.objectflow;

import java.util.LinkedHashMap;
import java.util.Map;

public class StateStore {
    private final Map<String, String> cache = new LinkedHashMap<>();

    public Map<String, String> cache() {
        return cache;
    }
}
