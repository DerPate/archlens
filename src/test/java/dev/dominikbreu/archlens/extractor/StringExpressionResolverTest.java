package dev.dominikbreu.archlens.extractor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class StringExpressionResolverTest extends ExtractorTestBase {

    private static CtModel model;

    @BeforeAll
    static void scanModel() {
        model = scan("kafka-topic-resolver-sample");
    }

    private static CtType<?> type(String qn) {
        return model.getAllTypes().stream()
                .filter(t -> qn.equals(t.getQualifiedName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Type not found: " + qn));
    }

    private static CtMethod<?> method(CtType<?> type, String name) {
        return type.getMethods().stream()
                .filter(m -> name.equals(m.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + name));
    }

    // ── Task 4 tests ──────────────────────────────────────────────────────

    @Test
    void resolvesStringLiteralDirectly() {
        // BudgetControlService.trigger() passes literal "budgetControl" to sendEvent
        CtType<?> callerType = type("com.example.kafka.BudgetControlService");
        CtMethod<?> callerMethod = method(callerType, "trigger");
        // The literal "budgetControl" is arg[0] of the sendEvent call
        CtInvocation<?> call = callerMethod.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(inv -> "sendEvent".equals(inv.getExecutable().getSimpleName()))
                .findFirst().orElseThrow();

        Set<String> result = StringExpressionResolver.resolve(
                call.getArguments().get(0), callerType, callerMethod, model, 5, new HashSet<>());

        assertThat(result).containsExactly("budgetControl");
    }

    @Test
    void resolvesStaticFinalFieldRead() {
        // PushNotificationService.send() calls setHeader(KafkaHeaders.TOPIC, KafkaConfig.PUSH_NOTIFICATION_TOPIC)
        CtType<?> callerType = type("com.example.kafka.PushNotificationService");
        CtMethod<?> callerMethod = method(callerType, "send");
        CtInvocation<?> setHeaderCall = callerMethod.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(inv -> "setHeader".equals(inv.getExecutable().getSimpleName()))
                .findFirst().orElseThrow();
        // arg[1] is KafkaConfig.PUSH_NOTIFICATION_TOPIC (a static field read)

        Set<String> result = StringExpressionResolver.resolve(
                setHeaderCall.getArguments().get(1), callerType, callerMethod, model, 5, new HashSet<>());

        assertThat(result).containsExactly("pushNotification");
    }

    @Test
    void returnsEmptyWhenDepthIsZero() {
        CtType<?> callerType = type("com.example.kafka.BudgetControlService");
        CtMethod<?> callerMethod = method(callerType, "trigger");
        CtInvocation<?> call = callerMethod.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(inv -> "sendEvent".equals(inv.getExecutable().getSimpleName()))
                .findFirst().orElseThrow();

        Set<String> result = StringExpressionResolver.resolve(
                call.getArguments().get(0), callerType, callerMethod, model, 0, new HashSet<>());

        assertThat(result).isEmpty();
    }

    @Test
    void resolvesParamRefByWalkingCallers() {
        // KafkaJsonProducer.sendEvent(String topic, ...) — topic is a PARAM_REF
        // BudgetControlService.trigger() passes literal "budgetControl"
        CtType<?> producerType = type("com.example.kafka.KafkaJsonProducer");
        CtMethod<?> sendEvent = method(producerType, "sendEvent");
        // arg[0] of the kafkaTemplate.send(topic,...) call inside sendEvent is a CtVariableRead of `topic`
        CtInvocation<?> sendCall = sendEvent.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(inv -> "send".equals(inv.getExecutable().getSimpleName()))
                .findFirst().orElseThrow();

        Set<String> result = StringExpressionResolver.resolve(
                sendCall.getArguments().get(0), producerType, sendEvent, model, 10, new HashSet<>());

        assertThat(result).containsExactly("budgetControl");
    }

    @Test
    void resolvesMethodCallByWalkingImplementations() {
        // KafkaProducer.sendEvent(IKafkaEvent event) — topic is event.getType()
        // SisKafkaEvent.getType() returns "sisPDFCreation"
        CtType<?> producerType = type("com.example.kafka.KafkaProducer");
        CtMethod<?> sendEvent = method(producerType, "sendEvent");
        CtInvocation<?> sendCall = sendEvent.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(inv -> "send".equals(inv.getExecutable().getSimpleName()))
                .findFirst().orElseThrow();
        // arg[0] is event.getType() — a CtInvocation

        Set<String> result = StringExpressionResolver.resolve(
                sendCall.getArguments().get(0), producerType, sendEvent, model, 10, new HashSet<>());

        assertThat(result).containsExactly("sisPDFCreation");
    }

    @Test
    void cycleGuardPreventsInfiniteLoop() {
        // Just verifying resolve() terminates — use a deep depth cap
        CtType<?> producerType = type("com.example.kafka.KafkaJsonProducer");
        CtMethod<?> sendEvent = method(producerType, "sendEvent");
        CtInvocation<?> sendCall = sendEvent.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(inv -> "send".equals(inv.getExecutable().getSimpleName()))
                .findFirst().orElseThrow();

        // Should not hang regardless of cycle guard trigger
        Set<String> result = StringExpressionResolver.resolve(
                sendCall.getArguments().get(0), producerType, sendEvent, model, 15, new HashSet<>());

        // No assertion on value — just that it terminates
        assertThat(result).isNotNull();
    }
}
