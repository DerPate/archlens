package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.TransactionPolicy;
import java.util.Objects;

/** Adds call-graph-dependent limitations to effective transaction policies. */
final class TransactionPolicyPostProcessor {

    void apply(ArchitectureModel model) {
        for (TransactionPolicy policy : model.transactionPolicies) {
            if (!"spring".equals(policy.framework) && !"spring-xml".equals(policy.framework)) continue;
            boolean selfInvocation = model.callEdges.stream()
                    .anyMatch(edge -> Objects.equals(edge.fromComponentId, policy.componentId)
                            && Objects.equals(edge.toComponentId, policy.componentId)
                            && Objects.equals(edge.toMethod, policy.methodName));
            if (selfInvocation) {
                policy.limitations.add("spring-self-invocation-may-bypass-proxy");
                if (policy.source != null) policy.source.confidence = Math.min(policy.source.confidence, 0.6);
            }
        }
    }
}
