package com.example.hub.client;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class TopicResolver {
    public String buildDispatchTopic(String tenantId, String itemId, String workflowId) { return ""; }
}
