package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects CDI event bus patterns across JAR modules.
 *
 * Producers: a component field of type {@code Event<T>} (javax/jakarta) or
 *            {@code EventBus} (Quarkus Vert.x) marks the class as a CDI_EVENT_PRODUCER
 *            and records which event types it fires.
 *
 * Consumers: a method parameter annotated with {@code @Observes} or a method annotated
 *            with {@code @ConsumeEvent} (Quarkus) marks the class as a CDI_EVENT_CONSUMER
 *            and creates a CDI_EVENT_OBSERVER entrypoint.
 *
 * Cross-module linking: after all modules are extracted, {@link #linkCrossModuleEvents}
 * matches producer event types to consumer event types by simple name and creates
 * {@code Dependency} edges with kind="{@code cdi-event}".
 */
public class EventBusExtractor {

    private static final Set<String> OBSERVES_ANNOTATIONS = Set.of(
        "javax.enterprise.event.Observes",
        "jakarta.enterprise.event.Observes",
        "Observes"
    );

    private static final Set<String> CONSUME_EVENT_ANNOTATIONS = Set.of(
        "io.quarkus.vertx.ConsumeEvent",
        "ConsumeEvent"
    );

    private static final Set<String> CDI_EVENT_TYPES = Set.of(
        "Event",      // javax.enterprise.event.Event / jakarta.enterprise.event.Event
        "EventBus"    // io.quarkus.vertx.core.runtime.context.EventBus / io.vertx.mutiny.core.eventbus.EventBus
    );

    // event simple name → list of component IDs that fire it
    private final Map<String, List<String>> producersByEventType = new LinkedHashMap<>();

    // event simple name → list of component IDs that observe it
    private final Map<String, List<String>> consumersByEventType = new LinkedHashMap<>();

    /** Creates an event bus extractor with empty producer and consumer indexes. */
    public EventBusExtractor() {}

    /**
     * Per-module pass: detect producers and consumers in the given types,
     * register new components if not already present, create entrypoints.
     *
     * @param types Spoon types in the application or module
     * @param model architecture model to update
     * @param appId owning application identifier
     */
    public void extract(Collection<CtType<?>> types, ArchitectureModel model, String appId) {
        Set<String> existingIds = model.components.stream()
            .map(c -> c.id).collect(Collectors.toSet());

        for (CtType<?> type : types) {
            detectProducer(type, model, appId, existingIds);
            detectConsumer(type, model, appId, existingIds);
        }
    }

    /**
     * Cross-module pass: called once after all per-module extractions are done.
     * Matches producer event types to consumer event types and creates cdi-event dependencies.
     *
     * @param model architecture model to update
     */
    public void linkCrossModuleEvents(ArchitectureModel model) {
        int depCounter = model.dependencies.size();
        Set<String> existingDepIds = model.dependencies.stream()
            .map(d -> d.id).collect(Collectors.toSet());

        for (Map.Entry<String, List<String>> entry : producersByEventType.entrySet()) {
            String eventType = entry.getKey();
            List<String> producers = entry.getValue();
            List<String> consumers = consumersByEventType.getOrDefault(eventType, List.of());

            for (String producerId : producers) {
                for (String consumerId : consumers) {
                    if (producerId.equals(consumerId)) continue;
                    String depId = "dep:" + producerId + "->cdi-event:" + eventType + "->" + consumerId;
                    if (existingDepIds.add(depId)) {
                        Dependency dep = new Dependency();
                        dep.id = depId;
                        dep.fromId = producerId;
                        dep.toId = consumerId;
                        dep.kind = "cdi-event";
                        dep.derivedFrom = "event-type-match:" + eventType;
                        dep.confidence = 0.8;
                        model.dependencies.add(dep);
                    }
                }
            }
        }
    }

    // ── producer detection ────────────────────────────────────────────────────

    private void detectProducer(CtType<?> type, ArchitectureModel model, String appId,
                                Set<String> existingIds) {
        List<String> firedEventTypes = new ArrayList<>();

        for (CtField<?> field : type.getFields()) {
            String fieldTypeName = field.getType().getSimpleName();
            if (CDI_EVENT_TYPES.contains(fieldTypeName)) {
                // Extract the generic type parameter (the event payload type)
                List<CtTypeReference<?>> args = field.getType().getActualTypeArguments();
                if (!args.isEmpty()) {
                    firedEventTypes.add(args.get(0).getSimpleName());
                } else {
                    firedEventTypes.add("UnknownEvent");
                }
            }
        }

        if (firedEventTypes.isEmpty()) return;

        String compId = "comp:" + type.getQualifiedName();
        Component comp = findOrCreateComponent(compId, type, appId, ComponentType.CDI_EVENT_PRODUCER,
            "cdi-event-producer", model, existingIds);

        for (String eventType : firedEventTypes) {
            producersByEventType.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(comp.id);
        }
    }

    // ── consumer detection ────────────────────────────────────────────────────

    private void detectConsumer(CtType<?> type, ArchitectureModel model, String appId,
                                Set<String> existingIds) {
        for (CtMethod<?> method : type.getMethods()) {
            String eventType = detectObservesParameter(method);
            if (eventType == null) eventType = detectConsumeEventAnnotation(method);
            if (eventType == null) continue;

            String compId = "comp:" + type.getQualifiedName();
            Component comp = findOrCreateComponent(compId, type, appId, ComponentType.CDI_EVENT_CONSUMER,
                "cdi-event-consumer", model, existingIds);

            consumersByEventType.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(comp.id);

            // Create entrypoint for the observer method
            String epId = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":observer";
            if (model.entrypoints.stream().noneMatch(e -> e.id.equals(epId))) {
                Entrypoint ep = new Entrypoint();
                ep.id = epId;
                ep.type = EntrypointType.CDI_EVENT_OBSERVER;
                ep.name = method.getSimpleName();
                ep.path = eventType;
                ep.componentId = comp.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 0.95);
                model.entrypoints.add(ep);
            }
        }
    }

    /** Returns the event type simple name if a parameter is annotated with @Observes, else null. */
    private String detectObservesParameter(CtMethod<?> method) {
        for (CtParameter<?> param : method.getParameters()) {
            boolean hasObserves = param.getAnnotations().stream().anyMatch(a ->
                OBSERVES_ANNOTATIONS.contains(a.getAnnotationType().getSimpleName())
                    || OBSERVES_ANNOTATIONS.contains(a.getAnnotationType().getQualifiedName()));
            if (hasObserves) return param.getType().getSimpleName();
        }
        return null;
    }

    /** Returns the event address/type from @ConsumeEvent if present, else null. */
    private String detectConsumeEventAnnotation(CtMethod<?> method) {
        for (var ann : method.getAnnotations()) {
            String sn = ann.getAnnotationType().getSimpleName();
            if (CONSUME_EVENT_ANNOTATIONS.contains(sn)
                    || CONSUME_EVENT_ANNOTATIONS.contains(ann.getAnnotationType().getQualifiedName())) {
                // @ConsumeEvent("address") or @ConsumeEvent — use method name as event type fallback
                try {
                    var val = ann.getValue("value");
                    if (val != null) {
                        String addr = val.toString().replace("\"", "");
                        return addr.isEmpty() ? method.getSimpleName() : addr;
                    }
                } catch (Exception ignored) {}
                return method.getSimpleName();
            }
        }
        return null;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the existing component if already registered, or creates and registers a new one.
     * If the component already exists with a different type, adds the stereotype without changing the type.
     */
    private Component findOrCreateComponent(String compId, CtType<?> type, String appId,
                                            ComponentType newType, String stereotype,
                                            ArchitectureModel model, Set<String> existingIds) {
        Optional<Component> existing = model.components.stream()
            .filter(c -> c.id.equals(compId))
            .findFirst();

        if (existing.isPresent()) {
            Component c = existing.get();
            if (!c.stereotypes.contains(stereotype)) c.stereotypes.add(stereotype);
            return c;
        }

        Component c = new Component();
        c.id = compId;
        c.type = newType;
        c.name = type.getSimpleName();
        c.qualifiedName = type.getQualifiedName();
        c.module = appId;
        c.technology = "cdi";
        c.stereotypes = new ArrayList<>(List.of(stereotype));
        c.source = new SourceInfo(getFile(type), getLine(type), "annotation", 0.9);

        existingIds.add(compId);
        model.components.add(c);
        model.applications.stream()
            .filter(a -> a.id.equals(appId))
            .findFirst()
            .ifPresent(a -> a.componentIds.add(compId));

        return c;
    }

    private String getFile(CtElement el) {
        var pos = el.getPosition();
        return pos.isValidPosition() ? pos.getFile().getAbsolutePath() : "unknown";
    }

    private int getLine(CtElement el) {
        var pos = el.getPosition();
        return pos.isValidPosition() ? pos.getLine() : 0;
    }
}
