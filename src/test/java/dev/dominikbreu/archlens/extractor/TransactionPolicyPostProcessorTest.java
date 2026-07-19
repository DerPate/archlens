package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.CallEdge;
import dev.dominikbreu.archlens.model.TransactionPolicy;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import org.junit.jupiter.api.Test;

class TransactionPolicyPostProcessorTest {

    @Test
    void doesNotDuplicateSelfInvocationLimitationWhenAppliedMultipleTimes() {
        ArchitectureModel model = new ArchitectureModel("test");
        TransactionPolicy policy = new TransactionPolicy();
        policy.framework = "spring";
        policy.componentId = ComponentId.of("OrderService");
        policy.methodName = "create";
        model.transactionPolicies.add(policy);

        CallEdge selfInvocation = new CallEdge();
        selfInvocation.fromComponentId = ComponentId.of("OrderService");
        selfInvocation.toComponentId = ComponentId.of("OrderService");
        selfInvocation.toMethod = "create";
        model.callEdges.add(selfInvocation);

        TransactionPolicyPostProcessor processor = new TransactionPolicyPostProcessor();
        processor.apply(model);
        processor.apply(model);

        assertThat(policy.limitations).containsExactly("spring-self-invocation-may-bypass-proxy");
    }
}
