package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import java.util.*;
import java.util.stream.Collectors;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

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

    private static final Set<String> OBSERVES_ANNOTATIONS =
            Set.of("javax.enterprise.event.Observes", "jakarta.enterprise.event.Observes", "Observes");

    private static final Set<String> CONSUME_EVENT_ANNOTATIONS =
            Set.of("io.quarkus.vertx.ConsumeEvent", "ConsumeEvent");

    private static final Set<String> CDI_EVENT_TYPES = Set.of(
            "Event", // javax.enterprise.event.Event / jakarta.enterprise.event.Event
            "EventBus" // io.quarkus.vertx.core.runtime.context.EventBus / io.vertx.mutiny.core.eventbus.EventBus
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
    public void extract(Collection<CtType<?>> types, ArchitectureModel model, AppId appId) {
        Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds =
                model.components.stream().map(c -> c.id).collect(Collectors.toSet());

        for (CtType<?> type : types) {
            detectProducer(type, model, appId, existingIds);
            detectConsumer(type, model, appId, existingIds);
            detectVertxEventBusConsumer(type, model, appId, existingIds);
        }
    }

    /**
     * Detects programmatic Vert.x EventBus consumers of the form
     * {@code eventBus.consumer(addr, lambda)} and
     * {@code eventBus.consumer(addr).handler(lambda)}.
     *
     * Each match becomes an {@link EntrypointType#EVENT_BUS_CONSUMER} entrypoint with
     * {@code channelName = addr} and the lambda's first parameter copied into the
     * entrypoint's parameters list so the data-flow tracer can follow it.
     */
    private void detectVertxEventBusConsumer(
            CtType<?> type,
            ArchitectureModel model,
            AppId appId,
            Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds) {
        for (CtMethod<?> method : type.getMethods()) {
            for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
                registerEventBusConsumer(type, method, inv, model, appId, existingIds);
            }
        }
    }

    private void registerEventBusConsumer(
            CtType<?> type,
            CtMethod<?> method,
            CtInvocation<?> inv,
            ArchitectureModel model,
            AppId appId,
            Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds) {
        if (!"consumer".equals(inv.getExecutable().getSimpleName())) return;
        CtTypeReference<?> targetType = inv.getTarget() != null ? inv.getTarget().getType() : null;
        if (targetType == null) return;
        if (!"EventBus".equals(targetType.getSimpleName())) return;
        if (inv.getArguments().isEmpty()) return;

        String address = literalString(inv.getArguments().get(0));
        CtLambda<?> lambda = findHandlerLambda(inv);
        if (address == null || lambda == null) return;
        String paramName = lambda.getParameters().isEmpty()
                ? "message"
                : lambda.getParameters().get(0).getSimpleName();

        dev.dominikbreu.spoonmcp.model.ids.ComponentId compId =
                dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
        Component comp = findOrCreateComponent(
                compId, type, appId, ComponentType.CDI_EVENT_CONSUMER, "event-bus-consumer", model, existingIds);

        dev.dominikbreu.spoonmcp.model.ids.EntrypointId epId =
                new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                        dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()),
                        method.getSimpleName(),
                        "eventbus:" + address);
        if (model.entrypoints.stream().anyMatch(e -> epId.equals(e.id))) return;

        Entrypoint ep = new Entrypoint();
        ep.id = epId;
        ep.type = EntrypointType.EVENT_BUS_CONSUMER;
        ep.name = method.getSimpleName();
        ep.channelName = address;
        ep.path = address;
        ep.componentId = comp.id;
        ep.parameters.add(paramName);
        ep.source = new SourceInfo(getFile(inv), getLine(inv), "invocation", 0.9);
        model.entrypoints.add(ep);
    }

    private static String literalString(CtExpression<?> expr) {
        if (expr instanceof CtLiteral<?> lit && lit.getValue() instanceof String s) return s;
        return null;
    }

    private static CtLambda<?> findHandlerLambda(CtInvocation<?> consumerInv) {
        // Form 1: eventBus.consumer(addr, lambda)
        for (CtExpression<?> arg : consumerInv.getArguments()) {
            if (arg instanceof CtLambda<?> l) return l;
        }
        // Form 2: eventBus.consumer(addr).handler(lambda)
        var parent = consumerInv.getParent();
        if (parent instanceof CtInvocation<?> chained
                && "handler".equals(chained.getExecutable().getSimpleName())) {
            for (CtExpression<?> arg : chained.getArguments()) {
                if (arg instanceof CtLambda<?> l) return l;
            }
        }
        return null;
    }

    /**
     * Cross-module pass: called once after all per-module extractions are done.
     * Matches producer event types to consumer event types and creates cdi-event dependencies.
     *
     * @param model architecture model to update
     */
    public void linkCrossModuleEvents(ArchitectureModel model) {
        Set<dev.dominikbreu.spoonmcp.model.ids.DependencyId> existingDepIds =
                model.dependencies.stream().map(d -> d.id).collect(Collectors.toSet());

        for (Map.Entry<String, List<String>> entry : producersByEventType.entrySet()) {
            String eventType = entry.getKey();
            List<String> producers = entry.getValue();
            List<String> consumers = consumersByEventType.getOrDefault(eventType, List.of());

            for (String producerId : producers) {
                for (String consumerId : consumers) {
                    if (producerId.equals(consumerId)) continue;
                    dev.dominikbreu.spoonmcp.model.ids.DependencyId depId =
                            new dev.dominikbreu.spoonmcp.model.ids.DependencyId(
                                    producerId + "->cdi-event:" + eventType + "->" + consumerId);
                    if (existingDepIds.add(depId)) {
                        Dependency dep = new Dependency();
                        dep.id = depId;
                        dep.fromId = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(producerId);
                        dep.toId = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(consumerId);
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

    private void detectProducer(
            CtType<?> type,
            ArchitectureModel model,
            AppId appId,
            Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds) {
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

        dev.dominikbreu.spoonmcp.model.ids.ComponentId compId =
                dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
        Component comp = findOrCreateComponent(
                compId, type, appId, ComponentType.CDI_EVENT_PRODUCER, "cdi-event-producer", model, existingIds);

        for (String eventType : firedEventTypes) {
            producersByEventType
                    .computeIfAbsent(eventType, k -> new ArrayList<>())
                    .add(comp.id.serialize());
        }
    }

    // ── consumer detection ────────────────────────────────────────────────────

    private void detectConsumer(
            CtType<?> type,
            ArchitectureModel model,
            AppId appId,
            Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds) {
        for (CtMethod<?> method : type.getMethods()) {
            String eventType = detectObservesParameter(method);
            if (eventType == null) eventType = detectConsumeEventAnnotation(method);
            if (eventType == null) continue;

            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId =
                    dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
            Component comp = findOrCreateComponent(
                    compId, type, appId, ComponentType.CDI_EVENT_CONSUMER, "cdi-event-consumer", model, existingIds);

            consumersByEventType
                    .computeIfAbsent(eventType, k -> new ArrayList<>())
                    .add(comp.id.serialize());

            // Create entrypoint for the observer method
            dev.dominikbreu.spoonmcp.model.ids.EntrypointId epId = new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                    dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()),
                    method.getSimpleName(),
                    "observer");
            if (model.entrypoints.stream().noneMatch(e -> epId.equals(e.id))) {
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
            boolean hasObserves = param.getAnnotations().stream()
                    .anyMatch(a ->
                            OBSERVES_ANNOTATIONS.contains(a.getAnnotationType().getSimpleName())
                                    || OBSERVES_ANNOTATIONS.contains(
                                            a.getAnnotationType().getQualifiedName()));
            if (hasObserves) return param.getType().getSimpleName();
        }
        return null;
    }

    /** Returns the event address/type from @ConsumeEvent if present, else null. */
    private String detectConsumeEventAnnotation(CtMethod<?> method) {
        for (var ann : method.getAnnotations()) {
            String sn = ann.getAnnotationType().getSimpleName();
            if (CONSUME_EVENT_ANNOTATIONS.contains(sn)
                    || CONSUME_EVENT_ANNOTATIONS.contains(
                            ann.getAnnotationType().getQualifiedName())) {
                // @ConsumeEvent("address") or @ConsumeEvent — use method name as event type fallback
                try {
                    var val = ann.getValue("value");
                    if (val != null) {
                        String addr = val.toString().replace("\"", "");
                        if (addr.isEmpty()) {
                            return method.getSimpleName();
                        } else {
                            return addr;
                        }
                    }
                } catch (Exception ignored) {
                }
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
    private Component findOrCreateComponent(
            dev.dominikbreu.spoonmcp.model.ids.ComponentId compId,
            CtType<?> type,
            AppId appId,
            ComponentType newType,
            String stereotype,
            ArchitectureModel model,
            Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds) {
        Optional<Component> existing =
                model.components.stream().filter(c -> c.id.equals(compId)).findFirst();

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
        if (pos.isValidPosition()) {
            return pos.getFile().getAbsolutePath();
        } else {
            return "unknown";
        }
    }

    private int getLine(CtElement el) {
        var pos = el.getPosition();
        if (pos.isValidPosition()) {
            return pos.getLine();
        } else {
            return 0;
        }
    }
}
