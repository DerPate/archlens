package com.example.hub.rule;
import com.example.hub.rule.model.Assignment;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
@ApplicationScoped
public class AssignmentService {
    public List<Assignment> getAllAssignments() { return List.of(); }
    public Assignment getAssignmentWithDefaultUncached(String itemId) { return null; }
}
