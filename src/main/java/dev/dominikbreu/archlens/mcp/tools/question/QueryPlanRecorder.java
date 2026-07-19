package dev.dominikbreu.archlens.mcp.tools.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records the ordered sequence of {@code GraphQuery} operations an answerer performs, for the {@code queryPlan} output field. */
public final class QueryPlanRecorder {

    private final List<Map<String, Object>> operations = new ArrayList<>();

    /**
     * Records one graph operation.
     *
     * @param op the operation name (e.g. {@code resolveEntrypoint}, {@code flowSteps})
     * @param args the operation's key arguments, rendered as strings
     */
    public void record(String op, Map<String, Object> args) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("op", op);
        entry.putAll(args);
        operations.add(entry);
    }

    /**
     * Records one graph operation with a single argument.
     *
     * @param op the operation name
     * @param argKey the argument key
     * @param argValue the argument value
     */
    public void record(String op, String argKey, String argValue) {
        record(op, Map.of(argKey, argValue));
    }

    /**
     * Returns the recorded operations in execution order.
     *
     * @return an immutable copy of the recorded operations
     */
    public List<Map<String, Object>> operations() {
        return List.copyOf(operations);
    }
}
