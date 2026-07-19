package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionPlannerTest {

    private final QuestionPlanner planner = new QuestionPlanner();

    @Test
    void recognizesPersistenceDestinationQuestions() {
        Interpretation result = planner.interpret("Where is the order id ultimately persisted?");
        assertThat(result.intent()).isEqualTo("persistence_destination");
    }

    @Test
    void recognizesConsumerContextQuestions() {
        Interpretation result = planner.interpret("What consumes and processes orders.created?");
        assertThat(result.intent()).isEqualTo("consumer_context");
    }

    @Test
    void recognizesImpactQuestions() {
        Interpretation result = planner.interpret("What may break if OrderRepository is replaced?");
        assertThat(result.intent()).isEqualTo("impact");
    }

    @Test
    void recognizesTransactionContextQuestions() {
        Interpretation result = planner.interpret("Which transaction contains this call?");
        assertThat(result.intent()).isEqualTo("transaction_context");
    }

    @Test
    void recognizesEndpointContextQuestionsByHttpMethodAndPath() {
        Interpretation result = planner.interpret("What happens on POST /api/orders/{id}?");
        assertThat(result.intent()).isEqualTo("endpoint_context");
    }

    @Test
    void returnsUnsupportedForUnrecognizedWording() {
        Interpretation result = planner.interpret("What color is the sky?");
        assertThat(result.intent()).isEqualTo("unsupported");
        assertThat(result.subjectCandidates()).isEmpty();
    }

    @Test
    void extractsMethodAndPathAsHighestPrioritySubject() {
        Interpretation result = planner.interpret("What happens on POST /api/orders/{id}?");
        assertThat(result.subjectCandidates()).first().satisfies(candidate -> {
            assertThat(candidate.type()).isEqualTo("entrypoint");
            assertThat(candidate.ref()).isEqualTo("POST /api/orders/{id}");
        });
    }

    @Test
    void extractsQualifiedNameOverFuzzyCapitalizedWordWhenBothPresent() {
        Interpretation result =
                planner.interpret("What may break if com.example.pipeline.repository.OrderRepository is replaced?");
        assertThat(result.subjectCandidates()).first().satisfies(candidate -> {
            assertThat(candidate.type()).isEqualTo("component");
            assertThat(candidate.ref()).isEqualTo("com.example.pipeline.repository.OrderRepository");
        });
    }

    @Test
    void fallsBackToCapitalizedWordWhenNoQualifiedNamePresent() {
        Interpretation result = planner.interpret("What may break if OrderRepository is replaced?");
        assertThat(result.subjectCandidates()).first().satisfies(candidate -> {
            assertThat(candidate.type()).isEqualTo("component");
            assertThat(candidate.ref()).isEqualTo("OrderRepository");
        });
    }

    @Test
    void needsClarificationWhenTopIntentsAreTied() {
        Interpretation result = planner.interpret("Does persist break anything?");
        assertThat(result.intent()).isEqualTo("needs-clarification");
        assertThat(result.subjectCandidates())
                .extracting(Interpretation.SubjectCandidate::ref)
                .contains("persistence_destination", "impact");
    }

    @Test
    void recognizesMessagingFlowQuestions() {
        assertThat(planner.interpret("Who publishes the orders.created topic?").intent())
                .isEqualTo("messaging_flow");
    }

    @Test
    void recognizesScheduledWorkflowQuestions() {
        assertThat(planner.interpret("What does this scheduled job trigger?").intent())
                .isEqualTo("scheduled_workflow");
    }

    @Test
    void recognizesStateLifecycleQuestions() {
        assertThat(planner.interpret("Where is field orderStatus written and read?")
                        .intent())
                .isEqualTo("state_lifecycle");
    }

    @Test
    void recognizesConfigurationContextQuestions() {
        assertThat(planner.interpret("Where is the base url configured?").intent())
                .isEqualTo("configuration_context");
    }

    @Test
    void recognizesExternalIntegrationContextQuestions() {
        assertThat(planner.interpret("Which use cases call the billing integration client?")
                        .intent())
                .isEqualTo("external_integration_context");
    }

    @Test
    void recognizesRelationshipQuestions() {
        assertThat(planner.interpret("How is OrderService related to OrderRepository?")
                        .intent())
                .isEqualTo("relationship");
    }

    @Test
    void doesNotMatchConsumerContextInsideAnUnrelatedCompoundIdentifier() {
        Interpretation result = planner.interpret("What does OrderConsumerMetricsCollector track?");
        assertThat(result.intent()).isNotEqualTo("consumer_context");
    }

    @Test
    void doesNotMatchMessagingFlowInsideAnUnrelatedCompoundIdentifier() {
        Interpretation result = planner.interpret("What does OrderPublisherRegistry do?");
        assertThat(result.intent()).isNotEqualTo("messaging_flow");
    }

    @Test
    void doesNotMatchStateLifecycleInsideAnUnrelatedCompoundIdentifier() {
        Interpretation result = planner.interpret("What does CustomerFieldValidator check?");
        assertThat(result.intent()).isNotEqualTo("state_lifecycle");
    }

    @Test
    void doesNotMatchExternalIntegrationInsideAnUnrelatedCompoundIdentifier() {
        Interpretation result = planner.interpret("What does PaymentClientFactory build?");
        assertThat(result.intent()).isNotEqualTo("external_integration_context");
    }
}
