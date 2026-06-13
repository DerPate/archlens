package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

/** Extracts Spring-specific architecture components, entrypoints, and interfaces from a Spoon model. */
public class SpringExtractor {

    private static final String ANNOTATION = "annotation";
    private static final String HTTP_DELETE = "DELETE";
    private static final String HTTP_PATCH = "PATCH";
    private static final String VALUE = "value";

    private static final Set<String> SPRING_BOOT_APP =
            Set.of("org.springframework.boot.autoconfigure.SpringBootApplication");
    private static final Set<String> REST_CONTROLLERS = Set.of(
            "org.springframework.web.bind.annotation.RestController", "org.springframework.stereotype.Controller");
    private static final Set<String> SERVICE = Set.of("org.springframework.stereotype.Service");
    private static final Set<String> REPOSITORY = Set.of("org.springframework.stereotype.Repository");
    private static final Set<String> COMPONENT = Set.of("org.springframework.stereotype.Component");
    private static final Set<String> CONFIGURATION = Set.of("org.springframework.context.annotation.Configuration");
    private static final Set<String> ENTITY = Set.of("javax.persistence.Entity", "jakarta.persistence.Entity");
    private static final Set<String> REQUEST_MAPPING = Set.of("org.springframework.web.bind.annotation.RequestMapping");
    private static final Set<String> GET_MAPPING = Set.of("org.springframework.web.bind.annotation.GetMapping");
    private static final Set<String> POST_MAPPING = Set.of("org.springframework.web.bind.annotation.PostMapping");
    private static final Set<String> PUT_MAPPING = Set.of("org.springframework.web.bind.annotation.PutMapping");
    private static final Set<String> DELETE_MAPPING = Set.of("org.springframework.web.bind.annotation.DeleteMapping");
    private static final Set<String> PATCH_MAPPING = Set.of("org.springframework.web.bind.annotation.PatchMapping");
    private static final Set<String> SCHEDULED = Set.of("org.springframework.scheduling.annotation.Scheduled");
    private static final Set<String> KAFKA_LISTENER = Set.of("org.springframework.kafka.annotation.KafkaListener");
    private static final Set<String> KAFKA_HANDLER = Set.of("org.springframework.kafka.annotation.KafkaHandler");
    private static final Set<String> RABBIT_LISTENER =
            Set.of("org.springframework.amqp.rabbit.annotation.RabbitListener");
    private static final Set<String> JMS_LISTENER = Set.of("org.springframework.jms.annotation.JmsListener");
    private static final Set<String> FEIGN_CLIENT = Set.of("org.springframework.cloud.openfeign.FeignClient");

    private final SpringConfigResolver.Config config;

    /** Creates an extractor with no config properties (no placeholder resolution). */
    public SpringExtractor() {
        this(new SpringConfigResolver().emptyConfig());
    }

    /**
     * Creates an extractor with the given resolved Spring config.
     *
     * @param config the resolved config for placeholder expansion
     */
    public SpringExtractor(SpringConfigResolver.Config config) {
        this.config = config;
    }

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    /**
     * Extracts Spring components, entrypoints, and interfaces from the given types into the model.
     *
     * @param types the Spoon types to analyse
     * @param model the architecture model to populate
     * @param appId the application id to assign to extracted components
     */
    public void extract(Collection<CtType<?>> types, ArchitectureModel model, AppId appId) {
        Span span = tracer().spanBuilder("spring.extract").startSpan();
        try (var _ = span.makeCurrent()) {
            Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds = new HashSet<>();
            for (Component component : model.components) existingIds.add(component.id);

            for (CtType<?> type : types) {
                Component component = tryExtractComponent(type, appId);
                if (component == null || !existingIds.add(component.id)) continue;

                model.components.add(component);
                model.applications.stream()
                        .filter(app -> app.id.equals(appId))
                        .findFirst()
                        .ifPresent(app -> app.componentIds.add(component.id));
                extractEntrypoints(type, component, model);
            }
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private Component tryExtractComponent(CtType<?> type, AppId appId) {
        ComponentType componentType = null;
        String technology = "spring";
        List<String> stereotypes = new ArrayList<>();

        if (hasAnnotation(type, ENTITY)) {
            componentType = ComponentType.ENTITY;
            technology = "jpa";
            stereotypes.add("entity");
        } else if (hasAnnotation(type, SPRING_BOOT_APP)) {
            componentType = ComponentType.SERVICE;
            technology = "spring-boot";
            stereotypes.add("spring-boot-application");
        } else if (hasAnnotation(type, FEIGN_CLIENT)) {
            componentType = ComponentType.HTTP_CLIENT;
            stereotypes.add("feign-client");
        } else if (hasAnnotation(type, REST_CONTROLLERS)) {
            componentType = ComponentType.REST_RESOURCE;
            stereotypes.add("controller");
        } else if (hasAnnotation(type, SERVICE)) {
            componentType = ComponentType.SERVICE;
            stereotypes.add("service");
        } else if (hasAnnotation(type, REPOSITORY)) {
            componentType = ComponentType.REPOSITORY;
            stereotypes.add("repository");
        } else if (hasAnnotation(type, CONFIGURATION)) {
            componentType = ComponentType.SERVICE;
            stereotypes.add("configuration");
        } else if (hasScheduledMethod(type)) {
            componentType = ComponentType.SCHEDULER;
            stereotypes.add("scheduled");
        } else if (hasListenerMethod(type)) {
            componentType = ComponentType.MESSAGE_DRIVEN_BEAN;
            stereotypes.add("messaging-listener");
        } else if (hasAnnotation(type, COMPONENT)) {
            componentType = ComponentType.SERVICE;
            stereotypes.add("component");
        }

        if (componentType == null) return null;
        Component component = new Component();
        component.id = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
        component.type = componentType;
        component.name = type.getSimpleName();
        component.qualifiedName = type.getQualifiedName();
        component.module = appId;
        component.technology = technology;
        component.stereotypes = stereotypes;
        component.source = new SourceInfo(getFile(type), getLine(type), ANNOTATION, 0.95);
        return component;
    }

    private boolean hasScheduledMethod(CtType<?> type) {
        return type.getMethods().stream().anyMatch(method -> hasAnnotation(method, SCHEDULED));
    }

    private boolean hasListenerMethod(CtType<?> type) {
        // Class-level @KafkaListener (multi-method listener with @KafkaHandler on methods)
        if (hasAnnotation(type, KAFKA_LISTENER)
                || hasAnnotation(type, RABBIT_LISTENER)
                || hasAnnotation(type, JMS_LISTENER)) {
            return true;
        }
        return type.getMethods().stream()
                .anyMatch(method -> hasAnnotation(method, KAFKA_LISTENER)
                        || hasAnnotation(method, RABBIT_LISTENER)
                        || hasAnnotation(method, JMS_LISTENER));
    }

    private void extractEntrypoints(CtType<?> type, Component component, ArchitectureModel model) {
        String classBase = firstMappingPath(type);
        String contextPath = config.value("server.servlet.context-path");
        for (CtMethod<?> method : type.getMethods()) {
            Mapping mapping = mapping(method);
            if (mapping != null && component.type != ComponentType.HTTP_CLIENT) {
                String fullPath = combinePaths(contextPath, combinePaths(classBase, mapping.path()));
                Entrypoint ep = new Entrypoint();
                ep.id = restEndpointId(type, method, mapping, fullPath);
                ep.type = EntrypointType.REST_ENDPOINT;
                ep.name = method.getSimpleName();
                ep.httpMethod = mapping.method();
                ep.path = fullPath;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), ANNOTATION, 1.0);
                model.entrypoints.add(ep);
                addInterface(method, component, "rest_endpoint", mapping.method() + " " + fullPath, fullPath, model);
            }
            if (hasAnnotation(method, SCHEDULED)) {
                addSimpleEntrypoint(method, type, component, EntrypointType.SCHEDULER, "scheduled", model);
            }
            addListenerEntrypoint(
                    method,
                    type,
                    component,
                    KAFKA_LISTENER,
                    EntrypointType.MESSAGING_CONSUMER,
                    MessagingBroker.KAFKA,
                    firstNonEmptyAttribute(method, KAFKA_LISTENER, "topics", "topicPattern"),
                    model);
            // Class-level @KafkaListener with @KafkaHandler on individual methods
            if (hasAnnotation(type, KAFKA_LISTENER) && hasAnnotation(method, KAFKA_HANDLER)) {
                String topics = firstNonEmptyAttribute(type, KAFKA_LISTENER, "topics", "topicPattern");
                addListenerEntrypoint(
                        method,
                        type,
                        component,
                        KAFKA_HANDLER,
                        EntrypointType.MESSAGING_CONSUMER,
                        MessagingBroker.KAFKA,
                        topics,
                        model);
            }
            addListenerEntrypoint(
                    method,
                    type,
                    component,
                    RABBIT_LISTENER,
                    EntrypointType.MESSAGING_CONSUMER,
                    MessagingBroker.RABBITMQ,
                    firstNonEmptyAttribute(method, RABBIT_LISTENER, "queues", "bindings"),
                    model);
            addListenerEntrypoint(
                    method,
                    type,
                    component,
                    JMS_LISTENER,
                    EntrypointType.JMS_CONSUMER,
                    MessagingBroker.JMS,
                    annotationAttribute(method, JMS_LISTENER, "destination"),
                    model);
            if (isMainMethod(method) || isRunnerMethod(method, type)) {
                addSimpleEntrypoint(method, type, component, EntrypointType.MAIN_METHOD, "startup", model);
            }
        }
        if (component.type == ComponentType.HTTP_CLIENT && hasAnnotation(type, FEIGN_CLIENT)) {
            extractFeignInterfaces(type, component, model);
        }
        extractOutboundCallSites(type, component, model);
    }

    private dev.dominikbreu.spoonmcp.model.ids.EntrypointId restEndpointId(
            CtType<?> type, CtMethod<?> method, Mapping mapping, String fullPath) {
        return new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()),
                method.getSimpleName(),
                mapping.method() + ":" + fullPath);
    }

    private void addSimpleEntrypoint(
            CtMethod<?> method,
            CtType<?> type,
            Component component,
            EntrypointType entrypointType,
            String suffix,
            ArchitectureModel model) {
        dev.dominikbreu.spoonmcp.model.ids.EntrypointId id = new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()),
                method.getSimpleName(),
                suffix);
        if (model.entrypoints.stream().anyMatch(e -> id.equals(e.id))) return;
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.type = entrypointType;
        ep.name = method.getSimpleName();
        ep.componentId = component.id;
        ep.source = new SourceInfo(getFile(method), getLine(method), ANNOTATION, 0.95);
        model.entrypoints.add(ep);
    }

    private void addListenerEntrypoint(
            CtMethod<?> method,
            CtType<?> type,
            Component component,
            Set<String> annotation,
            EntrypointType entrypointType,
            MessagingBroker broker,
            String channel,
            ArchitectureModel model) {
        if (!hasAnnotation(method, annotation)) return;
        String resolved = config.resolve(channel);
        if (StringUtils.isBlank(resolved)) resolved = "(unresolved)";
        dev.dominikbreu.spoonmcp.model.ids.EntrypointId id = new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()),
                method.getSimpleName(),
                "spring-listener:" + broker + ":" + resolved);
        if (model.entrypoints.stream().anyMatch(e -> id.equals(e.id))) return;
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.type = entrypointType;
        ep.name = method.getSimpleName();
        ep.channelName = resolved;
        ep.broker = broker;
        ep.componentId = component.id;
        ep.source = new SourceInfo(getFile(method), getLine(method), ANNOTATION, 1.0);
        model.entrypoints.add(ep);
        addMessagingInterface(
                method,
                component,
                entrypointType == EntrypointType.JMS_CONSUMER ? "jms_consumer" : "messaging_consumer",
                resolved,
                broker,
                model);
    }

    private void addMessagingInterface(
            CtElement element,
            Component component,
            String type,
            String channel,
            MessagingBroker broker,
            ArchitectureModel model) {
        InterfaceEntry entry = addInterface(element, component, type, channel, channel, model);
        if (entry != null) {
            entry.broker = broker;
            entry.topic = channel;
        }
    }

    private String firstNonEmptyAttribute(CtElement element, Set<String> annotation, String first, String second) {
        String value = annotationAttribute(element, annotation, first);
        if (value.isEmpty()) {
            return annotationAttribute(element, annotation, second);
        } else {
            return value;
        }
    }

    private boolean isMainMethod(CtMethod<?> method) {
        return "main".equals(method.getSimpleName()) && method.isStatic();
    }

    private boolean isRunnerMethod(CtMethod<?> method, CtType<?> type) {
        if (!"run".equals(method.getSimpleName())) return false;
        for (CtTypeReference<?> ref : type.getSuperInterfaces()) {
            String name = ref.getQualifiedName();
            if ("org.springframework.boot.ApplicationRunner".equals(name)
                    || "org.springframework.boot.CommandLineRunner".equals(name)
                    || "ApplicationRunner".equals(ref.getSimpleName())
                    || "CommandLineRunner".equals(ref.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private Mapping mapping(CtMethod<?> method) {
        if (hasAnnotation(method, GET_MAPPING)) return new Mapping("GET", firstMappingPath(method));
        if (hasAnnotation(method, POST_MAPPING)) return new Mapping("POST", firstMappingPath(method));
        if (hasAnnotation(method, PUT_MAPPING)) return new Mapping("PUT", firstMappingPath(method));
        if (hasAnnotation(method, DELETE_MAPPING)) return new Mapping(HTTP_DELETE, firstMappingPath(method));
        if (hasAnnotation(method, PATCH_MAPPING)) return new Mapping(HTTP_PATCH, firstMappingPath(method));
        if (hasAnnotation(method, REQUEST_MAPPING)) {
            String httpMethod = requestMappingMethod(method);
            return new Mapping(httpMethod == null ? "REQUEST" : httpMethod, firstMappingPath(method));
        }
        return null;
    }

    private String requestMappingMethod(CtElement element) {
        String method = annotationAttribute(element, REQUEST_MAPPING, "method");
        if (method.contains("GET")) return "GET";
        if (method.contains("POST")) return "POST";
        if (method.contains("PUT")) return "PUT";
        if (method.contains(HTTP_DELETE)) return HTTP_DELETE;
        if (method.contains(HTTP_PATCH)) return HTTP_PATCH;
        return null;
    }

    private String firstMappingPath(CtElement element) {
        String path = annotationAttribute(element, REQUEST_MAPPING, VALUE);
        if (path.isEmpty()) path = annotationAttribute(element, REQUEST_MAPPING, "path");
        if (path.isEmpty()) path = annotationAttribute(element, GET_MAPPING, VALUE);
        if (path.isEmpty()) path = annotationAttribute(element, POST_MAPPING, VALUE);
        if (path.isEmpty()) path = annotationAttribute(element, PUT_MAPPING, VALUE);
        if (path.isEmpty()) path = annotationAttribute(element, DELETE_MAPPING, VALUE);
        if (path.isEmpty()) path = annotationAttribute(element, PATCH_MAPPING, VALUE);
        return config.resolve(path);
    }

    /**
     * Adds an interface entry for the given component, deduplicating by id.
     *
     * @param element the AST element providing source location
     * @param component the owning component
     * @param type the interface type string (e.g. {@code "rest_endpoint"})
     * @param name the interface name (route or channel)
     * @param path the URL path or channel topic
     * @param model the architecture model to add the interface to
     * @return the created interface entry, or {@code null} if it already exists
     */
    protected InterfaceEntry addInterface(
            CtElement element, Component component, String type, String name, String path, ArchitectureModel model) {
        String id = "iface:" + component.id.qualifiedName() + ":" + type + ":" + name;
        if (model.interfaces.stream().anyMatch(i -> i.id.equals(id))) return null;
        InterfaceEntry entry = new InterfaceEntry();
        entry.id = id;
        entry.type = type;
        entry.name = name;
        entry.path = path;
        entry.componentId = component.id;
        entry.module = component.module;
        entry.technology = component.technology;
        entry.source = new SourceInfo(getFile(element), getLine(element), ANNOTATION, 0.95);
        model.interfaces.add(entry);
        return entry;
    }

    /**
     * Returns true if the given element carries any annotation matching one of the given qualified names.
     *
     * @param element the AST element to check
     * @param names the fully-qualified annotation type names to match
     * @return true if a matching annotation is present
     */
    protected boolean hasAnnotation(CtElement element, Set<String> names) {
        Set<String> simpleNames = simpleNames(names);
        return element.getAnnotations().stream()
                .anyMatch(annotation -> names.contains(
                                annotation.getAnnotationType().getQualifiedName())
                        || simpleNames.contains(annotation.getAnnotationType().getSimpleName()));
    }

    /**
     * Returns the string value of an annotation attribute, or an empty string if not found.
     *
     * @param element the AST element bearing the annotation
     * @param names the fully-qualified annotation type names to match
     * @param attribute the attribute name to read
     * @return the attribute value, or {@code ""} if absent
     */
    protected String annotationAttribute(CtElement element, Set<String> names, String attribute) {
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            if (!annotationMatches(annotation, names)) continue;
            try {
                CtExpression<?> value = annotation.getValue(attribute);
                if (value == null && VALUE.equals(attribute)) value = annotation.getValue("path");
                if (value == null) return "";
                return stripArray(resolveAnnotationValue(value));
            } catch (Exception _) {
                return "";
            }
        }
        return "";
    }

    private String resolveAnnotationValue(CtExpression<?> value) {
        if (value instanceof CtLiteral<?> literal) {
            return literalString(literal);
        }
        if (value instanceof CtNewArray<?> array) {
            return firstNonBlankElement(array);
        }
        if (value instanceof CtVariableRead<?> read && read.getVariable() instanceof CtFieldReference<?> fieldRef) {
            String fromField = fieldDefaultLiteral(fieldRef);
            if (fromField != null) return fromField;
        }
        return value.toString().replace("\"", "");
    }

    private static String literalString(CtLiteral<?> literal) {
        Object raw = literal.getValue();
        return raw == null ? "" : raw.toString();
    }

    private String firstNonBlankElement(CtNewArray<?> array) {
        for (CtExpression<?> element : array.getElements()) {
            String resolved = resolveAnnotationValue(element);
            if (!resolved.isBlank()) return resolved;
        }
        return "";
    }

    private static String fieldDefaultLiteral(CtFieldReference<?> fieldRef) {
        try {
            CtField<?> field = fieldRef.getDeclaration();
            if (field != null && field.getDefaultExpression() instanceof CtLiteral<?> lit) {
                return literalString(lit);
            }
        } catch (Exception _) {
        }
        return null;
    }

    private boolean annotationMatches(CtAnnotation<?> annotation, Set<String> names) {
        return names.contains(annotation.getAnnotationType().getQualifiedName())
                || simpleNames(names).contains(annotation.getAnnotationType().getSimpleName());
    }

    /**
     * Returns the simple (unqualified) name for each name in the given set.
     *
     * @param qualifiedNames the fully-qualified names to convert
     * @return a set of simple names
     */
    protected Set<String> simpleNames(Set<String> qualifiedNames) {
        return qualifiedNames.stream()
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .collect(java.util.stream.Collectors.toSet());
    }

    private String stripArray(String value) {
        String out;
        if (value == null) {
            out = "";
        } else {
            out = value.trim();
        }
        // Only strip Java array braces when the inner content is a string literal ("...").
        // Path values can start with { (a path variable like {id}) — those must not be stripped.
        if (out.startsWith("{") && out.endsWith("}")) {
            String inner = out.substring(1, out.length() - 1).trim();
            if (inner.isEmpty() || inner.startsWith("\"")) out = inner;
        }
        return out;
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

    /**
     * Returns the absolute source file path for the given element, or {@code "unknown"} if unavailable.
     *
     * @param element the AST element
     * @return the absolute file path, or {@code "unknown"}
     */
    protected String getFile(CtElement element) {
        var position = element.getPosition();
        if (position.isValidPosition()) {
            return position.getFile().getAbsolutePath();
        } else {
            return "unknown";
        }
    }

    /**
     * Returns the source line number for the given element, or {@code -1} if unavailable.
     *
     * @param element the AST element
     * @return the line number, or {@code -1}
     */
    protected int getLine(CtElement element) {
        var position = element.getPosition();
        if (position.isValidPosition()) {
            return position.getLine();
        } else {
            return 0;
        }
    }

    private void extractFeignInterfaces(CtType<?> type, Component component, ArchitectureModel model) {
        String name = annotationAttribute(type, FEIGN_CLIENT, "name");
        if (name.isEmpty()) name = annotationAttribute(type, FEIGN_CLIENT, VALUE);
        if (name.isEmpty()) name = type.getSimpleName();
        String url = config.resolve(annotationAttribute(type, FEIGN_CLIENT, "url"));
        InterfaceEntry client = addInterface(type, component, "rest_client", component.name, url, model);
        if (client != null) client.externalServiceName = name;
        for (CtMethod<?> method : type.getMethods()) {
            Mapping mapping = mapping(method);
            if (mapping == null) continue;
            addInterface(
                    method,
                    component,
                    "rest_client_operation",
                    mapping.method() + " " + mapping.path(),
                    mapping.path(),
                    model);
        }
    }

    private void extractOutboundCallSites(CtType<?> type, Component component, ArchitectureModel model) {
        type.getElements(element -> element instanceof CtInvocation<?>)
                .forEach(element -> processOutboundInvocation((CtInvocation<?>) element, component, model));
    }

    private void processOutboundInvocation(CtInvocation<?> invocation, Component component, ArchitectureModel model) {
        addKafkaOutboundSinkSite(invocation, component, model);
        String executable = invocation.getExecutable() == null
                ? ""
                : invocation.getExecutable().getSimpleName();
        List<String> args = invocation.getArguments().stream()
                .map(arg -> config.resolve(stripQuotes(arg.toString())))
                .toList();
        if (args.isEmpty()) return;
        if (Set.of("getForObject", "postForObject", "exchange", "uri").contains(executable)
                && looksLikeUrl(args.getFirst())) {
            addInterface(
                    invocation,
                    component,
                    "rest_client_operation",
                    executable + " " + args.getFirst(),
                    args.getFirst(),
                    model);
        }
        if ("send".equals(executable)) {
            addProducerInterface(invocation, component, MessagingBroker.KAFKA, args.getFirst(), model);
        }
        if ("convertAndSend".equals(executable)) {
            MessagingBroker broker = args.getFirst().contains("jms") ? MessagingBroker.JMS : MessagingBroker.RABBITMQ;
            addProducerInterface(invocation, component, broker, args.getFirst(), model);
        }
    }

    private void addKafkaOutboundSinkSite(CtInvocation<?> invocation, Component component, ArchitectureModel model) {
        if (invocation.getArguments().isEmpty()) return;
        String executable;
        if (invocation.getExecutable() == null) {
            executable = "";
        } else {
            executable = invocation.getExecutable().getSimpleName();
        }
        if (!"send".equals(executable)) return;
        String declaringType;
        if (invocation.getExecutable().getDeclaringType() == null) {
            declaringType = "";
        } else {
            declaringType = invocation.getExecutable().getDeclaringType().getQualifiedName();
        }
        String targetType;
        if (invocation.getTarget() == null || invocation.getTarget().getType() == null) {
            targetType = "";
        } else {
            targetType = invocation.getTarget().getType().getQualifiedName();
        }
        if (!declaringType.contains("KafkaTemplate") && !targetType.contains("KafkaTemplate")) return;

        SpringConfigResolver.ResolvedValue topic = config.resolveWithKey(
                stripQuotes(invocation.getArguments().getFirst().toString()));
        CtMethod<?> enclosingMethod = invocation.getParent(CtMethod.class);
        if (enclosingMethod == null) return;
        OutboundSinkSite site = new OutboundSinkSite();
        site.id = "outbound:" + component.id.serialize() + "#" + enclosingMethod.getSimpleName() + ":spring-kafka:"
                + model.outboundSinkSites.size();
        site.kind = DataFlowSink.Kind.MESSAGING;
        site.componentId = component.id;
        site.method = enclosingMethod.getSimpleName();
        site.calleeQualifiedName = declaringType.isBlank() ? targetType : declaringType;
        site.calleeMethod = executable;
        site.channel = topic.value();
        site.broker = MessagingBroker.KAFKA;
        site.topic = topic.value();
        site.topicPropertyKey = topic.propertyKey();
        site.payloadVarName = payloadVarName(invocation);
        site.payloadType = payloadType(invocation);
        site.linkEvidence = "spring-kafka-template-send";
        site.source = new SourceInfo(getFile(invocation), getLine(invocation), "spring-kafka-template-send", 0.95);
        model.outboundSinkSites.add(site);
    }

    private String payloadVarName(CtInvocation<?> invocation) {
        if (invocation.getArguments().size() < 2) return null;
        if (invocation.getArguments().get(1) instanceof CtVariableRead<?> variableRead
                && variableRead.getVariable() != null) {
            return variableRead.getVariable().getSimpleName();
        }
        return null;
    }

    private String payloadType(CtInvocation<?> invocation) {
        if (invocation.getArguments().size() < 2) return null;
        spoon.reflect.reference.CtTypeReference<?> type =
                invocation.getArguments().get(1).getType();
        if (type == null) {
            return null;
        } else {
            return type.getQualifiedName();
        }
    }

    private void addProducerInterface(
            CtElement element,
            Component component,
            MessagingBroker broker,
            String destination,
            ArchitectureModel model) {
        InterfaceEntry entry = addInterface(element, component, "messaging_producer", destination, destination, model);
        if (entry != null) {
            entry.broker = broker;
            entry.topic = destination;
        }
    }

    private boolean looksLikeUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String stripQuotes(String value) {
        if (value == null) return "";
        String out = value.trim();
        if (out.startsWith("\"") && out.endsWith("\"")) return out.substring(1, out.length() - 1);
        return out;
    }

    private record Mapping(String method, String path) {}
}
