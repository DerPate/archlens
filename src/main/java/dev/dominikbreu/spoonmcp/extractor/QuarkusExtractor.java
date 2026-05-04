package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;

import java.util.*;

/**
 * Extracts Quarkus, CDI, JAX-RS, scheduler, REST client, and JPA architecture elements.
 */
public class QuarkusExtractor {

    private static final Set<String> CDI_SCOPE_ANNOTATIONS = Set.of(
        "javax.enterprise.context.ApplicationScoped",
        "jakarta.enterprise.context.ApplicationScoped",
        "javax.enterprise.context.RequestScoped",
        "jakarta.enterprise.context.RequestScoped",
        "javax.enterprise.context.SessionScoped",
        "jakarta.enterprise.context.SessionScoped",
        "javax.inject.Singleton",
        "jakarta.inject.Singleton"
    );

    private static final Set<String> JAX_RS_PATH = Set.of(
        "javax.ws.rs.Path", "jakarta.ws.rs.Path"
    );

    private static final Set<String> REST_CLIENT_ANNOTATIONS = Set.of(
        "org.eclipse.microprofile.rest.client.inject.RegisterRestClient",
        "org.eclipse.microprofile.rest.client.inject.RestClient",
        "io.quarkus.rest.client.reactive.RegisterRestClient"
    );

    private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
        "javax.ws.rs.GET", "jakarta.ws.rs.GET",
        "javax.ws.rs.POST", "jakarta.ws.rs.POST",
        "javax.ws.rs.PUT", "jakarta.ws.rs.PUT",
        "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE",
        "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH",
        "javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD",
        "javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS"
    );

    private static final Set<String> SCHEDULED_ANNOTATIONS = Set.of(
        "io.quarkus.scheduler.Scheduled",
        "javax.ejb.Schedule", "jakarta.ejb.Schedule"
    );

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of(
        "javax.persistence.Entity", "jakarta.persistence.Entity"
    );

    private static final Set<String> INCOMING_ANNOTATIONS = Set.of(
        "org.eclipse.microprofile.reactive.messaging.Incoming"
    );

    private static final Set<String> OUTGOING_ANNOTATIONS = Set.of(
        "org.eclipse.microprofile.reactive.messaging.Outgoing"
    );

    private static final Set<String> CHANNEL_ANNOTATIONS = Set.of(
        "org.eclipse.microprofile.reactive.messaging.Channel",
        "io.smallrye.reactive.messaging.annotations.Channel"
    );

    private static final Set<String> WS_ENDPOINT_ANNOTATIONS = Set.of(
        "javax.websocket.server.ServerEndpoint",
        "jakarta.websocket.server.ServerEndpoint"
    );

    private static final Set<String> WS_ON_MESSAGE_ANNOTATIONS = Set.of(
        "javax.websocket.OnMessage",
        "jakarta.websocket.OnMessage"
    );

    private static final Set<String> JAX_RS_PRODUCES = Set.of(
        "javax.ws.rs.Produces", "jakarta.ws.rs.Produces"
    );

    private static final Set<String> SSE_EVENT_SINK_TYPES = Set.of(
        "SseEventSink", "Sse"
    );

    private static final Set<String> GRPC_SERVICE_ANNOTATIONS = Set.of(
        "io.quarkus.grpc.GrpcService"
    );

    private static final Set<String> GRPC_BINDABLE_SERVICE_TYPES = Set.of(
        "io.grpc.BindableService"
    );

    private static final Set<String> KAFKA_PRODUCER_TYPES = Set.of(
        "org.apache.kafka.clients.producer.KafkaProducer",
        "org.apache.kafka.clients.producer.Producer"
    );

    private static final Set<String> KAFKA_CONSUMER_TYPES = Set.of(
        "org.apache.kafka.clients.consumer.KafkaConsumer",
        "org.apache.kafka.clients.consumer.Consumer"
    );

    /** Matches Paho v3/v5 ({@code MqttClient}, {@code IMqttAsyncClient}) and HiveMQ ({@code Mqtt3Client}, {@code Mqtt5AsyncClient}, {@code Mqtt5BlockingClient}, {@code Mqtt5RxClient}). */
    private static final java.util.regex.Pattern MQTT_CLIENT_NAME = java.util.regex.Pattern.compile(
        "^I?Mqtt[35]?(Async|Blocking|Rx)?Client$");

    private static final String UNRESOLVED_TOPIC = "(unresolved)";

    private final MessagingCallSiteResolver callSiteResolver = new MessagingCallSiteResolver();

    /** Creates a Quarkus extractor using built-in annotation rules. */
    public QuarkusExtractor() {}

    /**
     * Adds Quarkus architecture elements to the model for one application.
     *
     * @param types Spoon types in the application or module
     * @param model architecture model to update
     * @param appId owning application identifier
     */
    public void extract(Collection<CtType<?>> types, ArchitectureModel model, String appId) {
        Set<String> existingIds = new HashSet<>();
        for (Component c : model.components) existingIds.add(c.id);

        for (CtType<?> type : types) {
            Component component = tryExtractComponent(type, appId);
            if (component == null || existingIds.contains(component.id)) continue;

            existingIds.add(component.id);
            model.components.add(component);
            model.applications.stream()
                .filter(a -> a.id.equals(appId))
                .findFirst()
                .ifPresent(a -> a.componentIds.add(component.id));

            extractEntrypoints(type, component, model);
        }
    }

    private Component tryExtractComponent(CtType<?> type, String appId) {
        boolean hasPath = hasAnnotation(type, JAX_RS_PATH);
        boolean hasCdiScope = hasAnnotation(type, CDI_SCOPE_ANNOTATIONS);
        boolean hasRestClient = hasAnnotation(type, REST_CLIENT_ANNOTATIONS);
        boolean isEntity = hasAnnotation(type, ENTITY_ANNOTATIONS);
        boolean hasWsEndpoint = hasAnnotation(type, WS_ENDPOINT_ANNOTATIONS);
        boolean isGrpcService = hasAnnotation(type, GRPC_SERVICE_ANNOTATIONS) || implementsGrpcBindable(type);
        boolean hasScheduled = type.getMethods().stream()
            .anyMatch(m -> hasAnnotation(m, SCHEDULED_ANNOTATIONS));
        boolean hasMessaging = type.getMethods().stream()
            .anyMatch(m -> hasAnnotation(m, INCOMING_ANNOTATIONS) || hasAnnotation(m, OUTGOING_ANNOTATIONS))
            || type.getFields().stream().anyMatch(f -> hasAnnotation(f, CHANNEL_ANNOTATIONS))
            || type.getFields().stream().anyMatch(f -> classifyRawClientField(f) != null);

        ComponentType compType;
        String technology;
        List<String> stereotypes = new ArrayList<>();

        if (isEntity) {
            compType = ComponentType.ENTITY;
            technology = "jpa";
            stereotypes.add("entity");
        } else if (hasRestClient) {
            compType = ComponentType.HTTP_CLIENT;
            technology = "microprofile-rest-client";
            stereotypes.add("rest-client");
            if (type.isInterface()) stereotypes.add("interface");
        } else if (hasPath) {
            compType = ComponentType.REST_RESOURCE;
            technology = "quarkus";
            stereotypes.add("jax-rs");
        } else if (hasWsEndpoint) {
            compType = ComponentType.REST_RESOURCE;
            technology = "websocket";
            stereotypes.add("websocket");
        } else if (isGrpcService) {
            compType = ComponentType.REST_RESOURCE;
            technology = "grpc";
            stereotypes.add("grpc");
        } else if (hasScheduled) {
            compType = ComponentType.SCHEDULER;
            technology = "quarkus";
            stereotypes.add("scheduled");
        } else if (hasCdiScope || hasMessaging) {
            technology = "quarkus";
            String lower = type.getSimpleName().toLowerCase();
            if (lower.endsWith("repository") || lower.endsWith("repo") || lower.endsWith("dao")) {
                compType = ComponentType.REPOSITORY;
                stereotypes.add("repository");
            } else if (lower.endsWith("service")) {
                compType = ComponentType.SERVICE;
                stereotypes.add("service");
            } else if (lower.endsWith("client") || lower.endsWith("proxy")) {
                compType = ComponentType.HTTP_CLIENT;
                stereotypes.add("client");
            } else {
                compType = ComponentType.SERVICE;
            }
            if (hasMessaging) stereotypes.add("messaging");
        } else {
            return null;
        }

        Component c = new Component();
        c.id = "comp:" + type.getQualifiedName();
        c.type = compType;
        c.name = type.getSimpleName();
        c.qualifiedName = type.getQualifiedName();
        c.module = appId;
        c.technology = technology;
        c.stereotypes = stereotypes;
        c.source = new SourceInfo(getFile(type), getLine(type), "annotation", 0.9);
        return c;
    }

    private void extractEntrypoints(CtType<?> type, Component component, ArchitectureModel model) {
        String classBasePath = getAnnotationStringValue(type, JAX_RS_PATH);
        String wsClassPath   = getAnnotationStringValue(type, WS_ENDPOINT_ANNOTATIONS);

        if ("grpc".equals(component.technology)) {
            emitGrpcEntrypoints(type, component, model);
            return;
        }

        for (CtMethod<?> method : type.getMethods()) {
            if (hasAnnotation(method, WS_ON_MESSAGE_ANNOTATIONS)) {
                Entrypoint ep = new Entrypoint();
                ep.id   = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":websocket";
                ep.type = EntrypointType.WEBSOCKET_ENDPOINT;
                ep.name = method.getSimpleName();
                ep.path = wsClassPath;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
                model.entrypoints.add(ep);
                continue;
            }
            String httpMethod = getHttpMethod(method);
            if (httpMethod != null && component.type != ComponentType.HTTP_CLIENT) {
                String methodPath = getAnnotationStringValue(method, JAX_RS_PATH);
                String fullPath = combinePaths(classBasePath, methodPath);
                boolean isSse = isSseEndpoint(method, type);
                Entrypoint ep = new Entrypoint();
                ep.id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName()
                      + (isSse ? ":sse" : "");
                ep.type = isSse ? EntrypointType.SSE_ENDPOINT : EntrypointType.REST_ENDPOINT;
                ep.name = method.getSimpleName();
                ep.httpMethod = httpMethod;
                ep.path = fullPath;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
                model.entrypoints.add(ep);
                addInterface(method, component, isSse ? "sse_endpoint" : "rest_endpoint",
                             httpMethod + " " + fullPath, fullPath, model);
                continue;
            }

            if (hasAnnotation(method, SCHEDULED_ANNOTATIONS)) {
                Entrypoint ep = new Entrypoint();
                ep.id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":scheduled";
                ep.type = EntrypointType.SCHEDULER;
                ep.name = method.getSimpleName();
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
                model.entrypoints.add(ep);
            }

            String incomingChannel = getAnnotationStringValue(method, INCOMING_ANNOTATIONS);
            if (!incomingChannel.isEmpty()) {
                addMessagingEntrypoint(method, type, component, EntrypointType.MESSAGING_CONSUMER,
                    incomingChannel, "in", model);
                addMessagingInterface(method, component, "messaging_consumer", incomingChannel, model);
            }
            String outgoingChannel = getAnnotationStringValue(method, OUTGOING_ANNOTATIONS);
            if (!outgoingChannel.isEmpty()) {
                addMessagingEntrypoint(method, type, component, EntrypointType.MESSAGING_PRODUCER,
                    outgoingChannel, "out", model);
                addMessagingInterface(method, component, "messaging_producer", outgoingChannel, model);
            }
        }

        for (CtField<?> field : type.getFields()) {
            String channel = getAnnotationStringValue(field, CHANNEL_ANNOTATIONS);
            if (channel.isEmpty()) continue;
            Entrypoint ep = new Entrypoint();
            ep.id = "ep:" + type.getQualifiedName() + "#" + field.getSimpleName() + ":emitter:" + channel;
            ep.type = EntrypointType.MESSAGING_PRODUCER;
            ep.name = field.getSimpleName();
            ep.channelName = channel;
            ep.broker = MessagingBroker.UNKNOWN;
            ep.componentId = component.id;
            ep.source = new SourceInfo(getFile(field), getLine(field), "annotation", 1.0);
            model.entrypoints.add(ep);
            addMessagingInterface(field, component, "messaging_producer", channel, model);
        }

        Map<String, MessagingCallSiteResolver.TrackedField> trackedFields = new LinkedHashMap<>();
        Map<String, RawClientKind> kinds = new LinkedHashMap<>();
        for (CtField<?> field : type.getFields()) {
            RawClientKind kind = classifyRawClientField(field);
            if (kind == null) continue;
            kinds.put(field.getSimpleName(), kind);
            MessagingCallSiteResolver.Role roleHint = switch (kind.role) {
                case PRODUCER -> MessagingCallSiteResolver.Role.PRODUCER;
                case CONSUMER -> MessagingCallSiteResolver.Role.CONSUMER;
                case BIDIRECTIONAL -> null;
            };
            trackedFields.put(field.getSimpleName(),
                new MessagingCallSiteResolver.TrackedField(kind.broker, roleHint));
        }

        Map<String, List<MessagingCallSiteResolver.Finding>> findingsByField = new LinkedHashMap<>();
        for (MessagingCallSiteResolver.Finding f : callSiteResolver.resolve(type, trackedFields)) {
            findingsByField.computeIfAbsent(f.fieldName(), k -> new ArrayList<>()).add(f);
        }

        for (CtField<?> field : type.getFields()) {
            RawClientKind kind = kinds.get(field.getSimpleName());
            if (kind == null) continue;
            List<MessagingCallSiteResolver.Finding> findings = findingsByField.get(field.getSimpleName());
            if (findings != null && !findings.isEmpty()) {
                for (MessagingCallSiteResolver.Finding f : findings) {
                    addRawClientInterface(component, model, field, kind.broker,
                        f.role() == MessagingCallSiteResolver.Role.PRODUCER ? "messaging_producer" : "messaging_consumer",
                        f.topic(), "call-site");
                }
            } else {
                String ifaceType = switch (kind.role) {
                    case PRODUCER -> "messaging_producer";
                    case CONSUMER -> "messaging_consumer";
                    case BIDIRECTIONAL -> "messaging_client";
                };
                addRawClientInterface(component, model, field, kind.broker, ifaceType,
                    UNRESOLVED_TOPIC, "field-type");
            }
        }

        if (component.type == ComponentType.HTTP_CLIENT) {
            String clientBasePath = normalizePath(classBasePath);
            String serviceName = restClientServiceName(type);
            InterfaceEntry clientIface = addInterface(type, component, "rest_client", component.name, clientBasePath, model);
            if (clientIface != null) clientIface.externalServiceName = serviceName;
            for (CtMethod<?> method : type.getMethods()) {
                String httpMethod = getHttpMethod(method);
                if (httpMethod == null) continue;
                String methodPath = getAnnotationStringValue(method, JAX_RS_PATH);
                String fullPath = combinePaths(clientBasePath, methodPath);
                InterfaceEntry opIface = addInterface(method, component, "rest_client_operation",
                    httpMethod + " " + fullPath, fullPath, model);
                if (opIface != null) opIface.externalServiceName = serviceName;
            }
        }
    }

    private void addMessagingEntrypoint(CtMethod<?> method, CtType<?> type, Component component,
                                        EntrypointType entryType, String channel, String suffix,
                                        ArchitectureModel model) {
        Entrypoint ep = new Entrypoint();
        ep.id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":msg-" + suffix + ":" + channel;
        ep.type = entryType;
        ep.name = method.getSimpleName();
        ep.channelName = channel;
        ep.broker = MessagingBroker.UNKNOWN;
        ep.componentId = component.id;
        ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
        model.entrypoints.add(ep);
    }

    private void addMessagingInterface(CtElement element, Component component, String type,
                                       String channel, ArchitectureModel model) {
        InterfaceEntry entry = addInterface(element, component, type, channel, channel, model);
        if (entry != null) {
            entry.broker = MessagingBroker.UNKNOWN;
        }
    }

    private void addRawClientInterface(Component component, ArchitectureModel model, CtField<?> field,
                                       MessagingBroker broker, String ifaceType, String topic, String evidence) {
        String id = "iface:" + component.id.substring("comp:".length()) + ":" + ifaceType
            + ":raw:" + field.getSimpleName() + ":" + topic;
        if (model.interfaces.stream().anyMatch(i -> i.id.equals(id))) return;
        InterfaceEntry entry = new InterfaceEntry();
        entry.id = id;
        entry.type = ifaceType;
        entry.name = field.getSimpleName();
        entry.path = topic;
        entry.componentId = component.id;
        entry.module = component.module;
        entry.technology = component.technology;
        entry.broker = broker;
        entry.source = new SourceInfo(getFile(field), getLine(field), evidence, "call-site".equals(evidence) ? 0.95 : 0.85);
        model.interfaces.add(entry);
    }

    private RawClientKind classifyRawClientField(CtField<?> field) {
        if (field.getType() == null) return null;
        String fqn = field.getType().getQualifiedName();
        String simple = field.getType().getSimpleName();
        if (fqn != null) {
            if (KAFKA_PRODUCER_TYPES.contains(fqn))
                return new RawClientKind(MessagingBroker.KAFKA, RawClientRole.PRODUCER);
            if (KAFKA_CONSUMER_TYPES.contains(fqn))
                return new RawClientKind(MessagingBroker.KAFKA, RawClientRole.CONSUMER);
        }
        if (simple != null && MQTT_CLIENT_NAME.matcher(simple).matches()) {
            return new RawClientKind(MessagingBroker.MQTT, RawClientRole.BIDIRECTIONAL);
        }
        return null;
    }

    private enum RawClientRole { PRODUCER, CONSUMER, BIDIRECTIONAL }

    private record RawClientKind(MessagingBroker broker, RawClientRole role) {}

    private String restClientServiceName(CtType<?> type) {
        String configKey = getAnnotationAttributeValue(type, REST_CLIENT_ANNOTATIONS, "configKey");
        if (!configKey.isEmpty()) return configKey;
        String baseUri = getAnnotationAttributeValue(type, REST_CLIENT_ANNOTATIONS, "baseUri");
        if (!baseUri.isEmpty()) return baseUri;
        return type.getSimpleName();
    }

    private InterfaceEntry addInterface(CtElement element, Component component, String type,
                                        String name, String path, ArchitectureModel model) {
        String id = "iface:" + component.id.substring("comp:".length()) + ":" + type + ":" + name;
        if (model.interfaces.stream().anyMatch(i -> i.id.equals(id))) return null;
        InterfaceEntry entry = new InterfaceEntry();
        entry.id = id;
        entry.type = type;
        entry.name = name;
        entry.path = path;
        entry.componentId = component.id;
        entry.module = component.module;
        entry.technology = component.technology;
        entry.source = new SourceInfo(getFile(element), getLine(element), "annotation", 0.95);
        model.interfaces.add(entry);
        return entry;
    }

    private boolean isSseEndpoint(CtMethod<?> method, CtType<?> type) {
        String produces = getAnnotationStringValue(method, JAX_RS_PRODUCES);
        if (produces.isEmpty()) produces = getAnnotationStringValue(type, JAX_RS_PRODUCES);
        if (produces.contains("text/event-stream") || produces.contains("SERVER_SENT_EVENTS")) {
            return true;
        }
        if (method.getType() != null
                && SSE_EVENT_SINK_TYPES.contains(method.getType().getSimpleName())) {
            return true;
        }
        for (CtParameter<?> param : method.getParameters()) {
            if (param.getType() != null
                    && SSE_EVENT_SINK_TYPES.contains(param.getType().getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private boolean implementsGrpcBindable(CtType<?> type) {
        for (var ref : type.getSuperInterfaces()) {
            String qn = ref.getQualifiedName();
            String sn = ref.getSimpleName();
            if (GRPC_BINDABLE_SERVICE_TYPES.contains(qn) || "BindableService".equals(sn)) return true;
        }
        var superRef = type.getSuperclass();
        if (superRef != null) {
            String qn = superRef.getQualifiedName();
            // Generated gRPC stubs typically extend "<Svc>Grpc.<Svc>ImplBase"
            if (qn != null && qn.contains(".grpc.") && qn.endsWith("ImplBase")) return true;
        }
        return false;
    }

    private void emitGrpcEntrypoints(CtType<?> type, Component component, ArchitectureModel model) {
        for (CtMethod<?> method : type.getMethods()) {
            if (!method.getModifiers().contains(spoon.reflect.declaration.ModifierKind.PUBLIC)) continue;
            if (method.getSimpleName().equals("bindService")) continue;
            String epId = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":grpc";
            if (model.entrypoints.stream().anyMatch(e -> e.id.equals(epId))) continue;
            Entrypoint ep = new Entrypoint();
            ep.id   = epId;
            ep.type = EntrypointType.GRPC_METHOD;
            ep.name = method.getSimpleName();
            ep.path = type.getSimpleName() + "/" + method.getSimpleName();
            ep.componentId = component.id;
            ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 0.95);
            model.entrypoints.add(ep);
        }
    }

    private String getHttpMethod(CtMethod<?> method) {
        for (var ann : method.getAnnotations()) {
            if (annMatches(ann, HTTP_METHOD_ANNOTATIONS)) {
                return ann.getAnnotationType().getSimpleName();
            }
        }
        return null;
    }

    private boolean hasAnnotation(CtElement element, Set<String> names) {
        Set<String> simpleNames = simpleNames(names);
        return element.getAnnotations().stream().anyMatch(a ->
            names.contains(a.getAnnotationType().getQualifiedName())
                || simpleNames.contains(a.getAnnotationType().getSimpleName()));
    }

    private boolean annMatches(spoon.reflect.declaration.CtAnnotation<?> ann, Set<String> names) {
        return names.contains(ann.getAnnotationType().getQualifiedName())
            || simpleNames(names).contains(ann.getAnnotationType().getSimpleName());
    }

    private Set<String> simpleNames(Set<String> qualifiedNames) {
        return qualifiedNames.stream()
            .map(n -> n.substring(n.lastIndexOf('.') + 1))
            .collect(java.util.stream.Collectors.toSet());
    }

    private String getAnnotationStringValue(CtElement element, Set<String> names) {
        return getAnnotationAttributeValue(element, names, "value");
    }

    private String getAnnotationAttributeValue(CtElement element, Set<String> names, String attribute) {
        for (var ann : element.getAnnotations()) {
            if (!annMatches(ann, names)) continue;
            try {
                CtExpression<?> val = ann.getValue(attribute);
                if (val == null) return "";
                if (val instanceof CtLiteral<?> lit) {
                    Object v = lit.getValue();
                    return v != null ? v.toString() : "";
                }
                String str = val.toString();
                if (str.startsWith("\"") && str.endsWith("\"")) {
                    return str.substring(1, str.length() - 1);
                }
                return str;
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        while (path.contains("//")) path = path.replace("//", "/");
        return path;
    }

    private String combinePaths(String base, String child) {
        if (base == null) base = "";
        if (child == null) child = "";
        if (base.endsWith("/") && child.startsWith("/")) return normalizePath(base + child.substring(1));
        if (!base.endsWith("/") && !child.startsWith("/") && !child.isEmpty()) return normalizePath(base + "/" + child);
        return normalizePath(base + child);
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
