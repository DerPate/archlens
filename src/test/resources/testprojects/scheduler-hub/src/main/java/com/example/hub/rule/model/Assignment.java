package com.example.hub.rule.model;
public class Assignment {
    public String id;
    public String itemId;
    public String tenantId;
    public Rule rule;
    public Assignment() {}
    public Assignment(String itemId, Rule rule, String tenantId) {
        this.itemId = itemId; this.rule = rule; this.tenantId = tenantId;
    }
}
