package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.RuntimeFlow;
import dev.dominikbreu.archlens.model.RuntimeFlowStep;
import dev.dominikbreu.archlens.model.SourceInfo;
import dev.dominikbreu.archlens.model.TransactionPolicy;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import org.junit.jupiter.api.Test;

class TransactionScopeInferrerTest {

    @Test
    void appliesNormalizedTransitionsAndKeepsFlowsInSeparateScopes() {
        ArchitectureModel model = new ArchitectureModel("fixture");
        RuntimeFlow first = flow("flow:first", "required", "requiresNew", "notSupported", "unknown");
        RuntimeFlow asynchronous = flow("flow:async", "required");
        model.runtimeFlows.add(first);
        model.runtimeFlows.add(asynchronous);
        model.transactionPolicies.add(policy("required", "REQUIRED"));
        model.transactionPolicies.add(policy("requiresNew", "REQUIRES_NEW"));
        model.transactionPolicies.add(policy("notSupported", "NOT_SUPPORTED"));

        new TransactionScopeInferrer().infer(model);

        assertThat(first.steps.get(0).transactionTransition).isEqualTo("begin");
        assertThat(first.steps.get(1).transactionTransition).isEqualTo("suspend-and-begin");
        assertThat(first.steps.get(2).transactionTransition).isEqualTo("suspend");
        assertThat(first.steps.get(2).transactionScopeId).isNull();
        assertThat(first.steps.get(3).transactionTransition).isEqualTo("none");
        assertThat(first.steps.get(3).transactionLimitations).isEqualTo("no-effective-policy");
        assertThat(asynchronous.steps.getFirst().transactionTransition).isEqualTo("begin");
        assertThat(asynchronous.steps.getFirst().transactionScopeId)
                .startsWith("flow:async:")
                .isNotEqualTo(first.steps.getFirst().transactionScopeId);
    }

    @Test
    void coversSupportMandatoryNestedAndNeverSemantics() {
        ArchitectureModel model = new ArchitectureModel("fixture");
        RuntimeFlow flow = flow("flow:policies", "supports", "mandatory", "nested", "never");
        model.runtimeFlows.add(flow);
        model.transactionPolicies.add(policy("supports", "SUPPORTS"));
        model.transactionPolicies.add(policy("mandatory", "MANDATORY"));
        model.transactionPolicies.add(policy("nested", "NESTED"));
        model.transactionPolicies.add(policy("never", "NEVER"));

        new TransactionScopeInferrer().infer(model);

        assertThat(flow.steps.get(0).transactionTransition).isEqualTo("none");
        assertThat(flow.steps.get(1).transactionTransition).isEqualTo("missing-required-scope");
        assertThat(flow.steps.get(2).transactionTransition).isEqualTo("begin");
        assertThat(flow.steps.get(3).transactionTransition).isEqualTo("invalid-active-scope");
    }

    private static RuntimeFlow flow(String id, String... methods) {
        RuntimeFlow flow = new RuntimeFlow();
        flow.id = id;
        for (int index = 0; index < methods.length; index++) {
            flow.steps.add(new RuntimeFlowStep(
                    index, ComponentId.of("example.Service"), "Service", "service", "call", methods[index]));
        }
        return flow;
    }

    private static TransactionPolicy policy(String method, String policyValue) {
        TransactionPolicy policy = new TransactionPolicy();
        policy.id = "transaction-boundary:example.Service#" + method + "()";
        policy.componentId = ComponentId.of("example.Service");
        policy.methodName = method;
        policy.methodSignature = method + "()";
        policy.framework = "spring";
        policy.policy = policyValue;
        policy.nativePolicy = policyValue;
        policy.source = new SourceInfo("Service.java", 1, "annotation", 1.0);
        return policy;
    }
}
