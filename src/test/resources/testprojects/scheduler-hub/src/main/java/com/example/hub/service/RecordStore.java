package com.example.hub.service;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;
@ApplicationScoped
public class RecordStore {

    private Map<String, JsonNode> records = Map.of();

    public Map<String, JsonNode> getLatestRecords() { return records; }
    public Set<String> activeItems() { return records.keySet(); }
}
