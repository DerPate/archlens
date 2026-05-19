package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class SpringExtractor {

    private static final Set<String> SPRING_BOOT_APP =
            Set.of("org.springframework.boot.autoconfigure.SpringBootApplication");
    private static final Set<String> REST_CONTROLLERS = Set.of(
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.stereotype.Controller");
    private static final Set<String> SERVICE = Set.of("org.springframework.stereotype.Service");
    private static final Set<String> REPOSITORY = Set.of("org.springframework.stereotype.Repository");
    private static final Set<String> COMPONENT = Set.of("org.springframework.stereotype.Component");
    private static final Set<String> CONFIGURATION = Set.of("org.springframework.context.annotation.Configuration");
    private static final Set<String> ENTITY = Set.of("javax.persistence.Entity", "jakarta.persistence.Entity");
    private static final Set<String> REQUEST_MAPPING =
            Set.of("org.springframework.web.bind.annotation.RequestMapping");
    private static final Set<String> GET_MAPPING = Set.of("org.springframework.web.bind.annotation.GetMapping");
    private static final Set<String> POST_MAPPING = Set.of("org.springframework.web.bind.annotation.PostMapping");
    private static final Set<String> PUT_MAPPING = Set.of("org.springframework.web.bind.annotation.PutMapping");
    private static final Set<String> DELETE_MAPPING = Set.of("org.springframework.web.bind.annotation.DeleteMapping");
    private static final Set<String> PATCH_MAPPING = Set.of("org.springframework.web.bind.annotation.PatchMapping");
    private static final Set<String> SCHEDULED = Set.of("org.springframework.scheduling.annotation.Scheduled");
    private static final Set<String> KAFKA_LISTENER = Set.of("org.springframework.kafka.annotation.KafkaListener");
    private static final Set<String> RABBIT_LISTENER = Set.of("org.springframework.amqp.rabbit.annotation.RabbitListener");
    private static final Set<String> JMS_LISTENER = Set.of("org.springframework.jms.annotation.JmsListener");

    private final SpringConfigResolver.Config config;

    public SpringExtractor() {
        this(new SpringConfigResolver().resolve(new java.io.File("/nonexistent")));
    }

    public SpringExtractor(SpringConfigResolver.Config config) {
        this.config = config;
    }

    public void extract(Collection<CtType<?>> types, ArchitectureModel model, String appId) {
        Set<String> existingIds = new HashSet<>();
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
    }

    private Component tryExtractComponent(CtType<?> type, String appId) {
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
        component.id = "comp:" + type.getQualifiedName();
        component.type = componentType;
        component.name = type.getSimpleName();
        component.qualifiedName = type.getQualifiedName();
        component.module = appId;
        component.technology = technology;
        component.stereotypes = stereotypes;
        component.source = new SourceInfo(getFile(type), getLine(type), "annotation", 0.95);
        return component;
    }

    private boolean hasScheduledMethod(CtType<?> type) {
        return type.getMethods().stream().anyMatch(method -> hasAnnotation(method, SCHEDULED));
    }

    private boolean hasListenerMethod(CtType<?> type) {
        return type.getMethods().stream().anyMatch(method ->
                hasAnnotation(method, KAFKA_LISTENER)
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
                ep.id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":" + mapping.method();
                ep.type = EntrypointType.REST_ENDPOINT;
                ep.name = method.getSimpleName();
                ep.httpMethod = mapping.method();
                ep.path = fullPath;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
                model.entrypoints.add(ep);
                addInterface(method, component, "rest_endpoint", mapping.method() + " " + fullPath, fullPath, model);
            }
            if (hasAnnotation(method, SCHEDULED)) {
                addSimpleEntrypoint(method, type, component, EntrypointType.SCHEDULER, "scheduled", model);
            }
            addListenerEntrypoint(method, type, component, KAFKA_LISTENER, EntrypointType.MESSAGING_CONSUMER,
                    MessagingBroker.KAFKA, firstNonEmptyAttribute(method, KAFKA_LISTENER, "topics", "topicPattern"), model);
            addListenerEntrypoint(method, type, component, RABBIT_LISTENER, EntrypointType.MESSAGING_CONSUMER,
                    MessagingBroker.RABBITMQ, firstNonEmptyAttribute(method, RABBIT_LISTENER, "queues", "bindings"), model);
            addListenerEntrypoint(method, type, component, JMS_LISTENER, EntrypointType.JMS_CONSUMER,
                    MessagingBroker.JMS, annotationAttribute(method, JMS_LISTENER, "destination"), model);
            if (isMainMethod(method) || isRunnerMethod(method, type)) {
                addSimpleEntrypoint(method, type, component, EntrypointType.MAIN_METHOD, "startup", model);
            }
        }
    }

    private void addSimpleEntrypoint(
            CtMethod<?> method,
            CtType<?> type,
            Component component,
            EntrypointType entrypointType,
            String suffix,
            ArchitectureModel model) {
        String id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":" + suffix;
        if (model.entrypoints.stream().anyMatch(e -> e.id.equals(id))) return;
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.type = entrypointType;
        ep.name = method.getSimpleName();
        ep.componentId = component.id;
        ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 0.95);
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
        if (resolved == null || resolved.isBlank()) resolved = "(unresolved)";
        String id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName() + ":spring-listener:" + broker + ":" + resolved;
        if (model.entrypoints.stream().anyMatch(e -> e.id.equals(id))) return;
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.type = entrypointType;
        ep.name = method.getSimpleName();
        ep.channelName = resolved;
        ep.broker = broker;
        ep.componentId = component.id;
        ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
        model.entrypoints.add(ep);
        addMessagingInterface(method, component, entrypointType == EntrypointType.JMS_CONSUMER ? "jms_consumer" : "messaging_consumer", resolved, broker, model);
    }

    private void addMessagingInterface(
            CtElement element, Component component, String type, String channel, MessagingBroker broker, ArchitectureModel model) {
        InterfaceEntry entry = addInterface(element, component, type, channel, channel, model);
        if (entry != null) entry.broker = broker;
    }

    private String firstNonEmptyAttribute(CtElement element, Set<String> annotation, String first, String second) {
        String value = annotationAttribute(element, annotation, first);
        return value.isEmpty() ? annotationAttribute(element, annotation, second) : value;
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
        if (hasAnnotation(method, DELETE_MAPPING)) return new Mapping("DELETE", firstMappingPath(method));
        if (hasAnnotation(method, PATCH_MAPPING)) return new Mapping("PATCH", firstMappingPath(method));
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
        if (method.contains("DELETE")) return "DELETE";
        if (method.contains("PATCH")) return "PATCH";
        return null;
    }

    private String firstMappingPath(CtElement element) {
        String path = annotationAttribute(element, REQUEST_MAPPING, "value");
        if (path.isEmpty()) path = annotationAttribute(element, REQUEST_MAPPING, "path");
        if (path.isEmpty()) path = annotationAttribute(element, GET_MAPPING, "value");
        if (path.isEmpty()) path = annotationAttribute(element, POST_MAPPING, "value");
        if (path.isEmpty()) path = annotationAttribute(element, PUT_MAPPING, "value");
        if (path.isEmpty()) path = annotationAttribute(element, DELETE_MAPPING, "value");
        if (path.isEmpty()) path = annotationAttribute(element, PATCH_MAPPING, "value");
        return config.resolve(path);
    }

    protected InterfaceEntry addInterface(
            CtElement element, Component component, String type, String name, String path, ArchitectureModel model) {
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

    protected boolean hasAnnotation(CtElement element, Set<String> names) {
        Set<String> simpleNames = simpleNames(names);
        return element.getAnnotations().stream()
                .anyMatch(annotation -> names.contains(annotation.getAnnotationType().getQualifiedName())
                        || simpleNames.contains(annotation.getAnnotationType().getSimpleName()));
    }

    protected String annotationAttribute(CtElement element, Set<String> names, String attribute) {
        for (CtAnnotation<?> annotation : element.getAnnotations()) {
            if (!annotationMatches(annotation, names)) continue;
            try {
                CtExpression<?> value = annotation.getValue(attribute);
                if (value == null && "value".equals(attribute)) value = annotation.getValue("path");
                if (value == null) return "";
                if (value instanceof CtLiteral<?> literal) {
                    Object raw = literal.getValue();
                    return raw == null ? "" : stripArray(raw.toString());
                }
                return stripArray(value.toString().replace("\"", ""));
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }

    private boolean annotationMatches(CtAnnotation<?> annotation, Set<String> names) {
        return names.contains(annotation.getAnnotationType().getQualifiedName())
                || simpleNames(names).contains(annotation.getAnnotationType().getSimpleName());
    }

    protected Set<String> simpleNames(Set<String> qualifiedNames) {
        return qualifiedNames.stream()
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .collect(java.util.stream.Collectors.toSet());
    }

    private String stripArray(String value) {
        String out = value == null ? "" : value.trim();
        if (out.startsWith("{") && out.endsWith("}")) out = out.substring(1, out.length() - 1).trim();
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

    protected String getFile(CtElement element) {
        var position = element.getPosition();
        return position.isValidPosition() ? position.getFile().getAbsolutePath() : "unknown";
    }

    protected int getLine(CtElement element) {
        var position = element.getPosition();
        return position.isValidPosition() ? position.getLine() : 0;
    }

    private record Mapping(String method, String path) {}
}
