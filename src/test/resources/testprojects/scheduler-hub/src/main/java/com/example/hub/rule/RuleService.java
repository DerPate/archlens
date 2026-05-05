package com.example.hub.rule;
import com.example.hub.rule.model.Rule;
import jakarta.enterprise.context.ApplicationScoped;
@ApplicationScoped
public class RuleService {
    public Rule getDefault(String tenantId) { return new Rule(); }
}
