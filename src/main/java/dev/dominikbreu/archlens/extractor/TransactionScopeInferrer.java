package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.RuntimeFlow;
import dev.dominikbreu.archlens.model.RuntimeFlowStep;
import dev.dominikbreu.archlens.model.TransactionPolicy;
import java.util.List;
import java.util.Objects;

/** Applies effective method policies to runtime-flow steps and infers bounded transaction scopes. */
public class TransactionScopeInferrer {

    /** Creates an inferrer using normalized transaction-policy semantics. */
    public TransactionScopeInferrer() {}

    /**
     * Annotates every runtime-flow step with a policy, transition, scope id, and caveats.
     *
     * @param model architecture model containing policies and runtime flows
     */
    public void infer(ArchitectureModel model) {
        for (RuntimeFlow flow : model.runtimeFlows) infer(flow, model.transactionPolicies);
    }

    private void infer(RuntimeFlow flow, List<TransactionPolicy> policies) {
        String currentScope = null;
        int scopeCounter = 0;
        for (RuntimeFlowStep step : flow.steps) {
            TransactionPolicy policy = policy(step, policies);
            if (policy == null) {
                step.transactionTransition = currentScope == null ? "none" : "join";
                step.transactionScopeId = currentScope;
                step.transactionConfidence = currentScope == null ? 0.0 : 0.6;
                step.transactionLimitations = "no-effective-policy";
                continue;
            }
            step.transactionPolicy = policy.policy;
            step.transactionLimitations = String.join(",", policy.limitations);
            step.transactionConfidence = policy.source != null ? policy.source.confidence : 0.6;
            switch (Objects.toString(policy.policy, "UNKNOWN")) {
                case "REQUIRED" -> {
                    if (currentScope == null) {
                        currentScope = scopeId(flow, ++scopeCounter);
                        step.transactionTransition = "begin";
                    } else {
                        step.transactionTransition = "join";
                    }
                }
                case "REQUIRES_NEW" -> {
                    step.transactionTransition = currentScope == null ? "begin" : "suspend-and-begin";
                    currentScope = scopeId(flow, ++scopeCounter);
                }
                case "NESTED" -> {
                    step.transactionTransition = currentScope == null ? "begin" : "nested";
                    if (currentScope == null) currentScope = scopeId(flow, ++scopeCounter);
                }
                case "SUPPORTS" -> step.transactionTransition = currentScope == null ? "none" : "join";
                case "MANDATORY" ->
                    step.transactionTransition = currentScope == null ? "missing-required-scope" : "join";
                case "NOT_SUPPORTED" -> {
                    step.transactionTransition = currentScope == null ? "none" : "suspend";
                    currentScope = null;
                }
                case "NEVER" -> step.transactionTransition = currentScope == null ? "none" : "invalid-active-scope";
                default -> {
                    step.transactionTransition = "unknown";
                    currentScope = null;
                    step.transactionConfidence = Math.min(step.transactionConfidence, 0.5);
                }
            }
            step.transactionScopeId = currentScope;
        }
    }

    private static TransactionPolicy policy(RuntimeFlowStep step, List<TransactionPolicy> policies) {
        if (step.componentId == null || step.method == null) return null;
        List<TransactionPolicy> matches = policies.stream()
                .filter(policy -> step.componentId.equals(policy.componentId) && step.method.equals(policy.methodName))
                .toList();
        if (matches.size() != 1) return null;
        return matches.getFirst();
    }

    private static String scopeId(RuntimeFlow flow, int index) {
        return flow.id + ":tx:" + index;
    }
}
