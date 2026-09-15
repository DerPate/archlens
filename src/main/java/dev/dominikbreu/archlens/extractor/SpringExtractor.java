package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.AppId;
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
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
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
    /** Marks a service-layer bean ({@code @Service}) as a {@link ComponentType#SERVICE}. */
    private static final Set<String> SERVICE = Set.of("org.springframework.stereotype.Service");
    /** Marks a persistence-layer bean ({@code @Repository}) as a {@link ComponentType#REPOSITORY}. */
    private static final Set<String> REPOSITORY = Set.of("org.springframework.stereotype.Repository");
    /** Marks a generic managed bean ({@code @Component}) as a {@link ComponentType#SERVICE}; checked last, after the more specific stereotypes. */
    private static final Set<String> COMPONENT = Set.of("org.springframework.stereotype.Component");
    /** Marks a bean-definition class ({@code @Configuration}) as a {@link ComponentType#SERVICE}. */
    private static final Set<String> CONFIGURATION = Set.of("org.springframework.context.annotation.Configuration");
    /** Marks a JPA entity class ({@code javax}/{@code jakarta} {@code @Entity}) as a {@link ComponentType#ENTITY}. */
    private static final Set<String> ENTITY = Set.of("javax.persistence.Entity", "jakarta.persistence.Entity");
    /** Generic Spring MVC route annotation ({@code @RequestMapping}); its {@code method} attribute picks the HTTP verb. */
    private static final Set<String> REQUEST_MAPPING = Set.of("org.springframework.web.bind.annotation.RequestMapping");
    /** Shorthand {@code @GetMapping} route annotation. */
    private static final Set<String> GET_MAPPING = Set.of("org.springframework.web.bind.annotation.GetMapping");
    /** Shorthand {@code @PostMapping} route annotation. */
    private static final Set<String> POST_MAPPING = Set.of("org.springframework.web.bind.annotation.PostMapping");
    /** Shorthand {@code @PutMapping} route annotation. */
    private static final Set<String> PUT_MAPPING = Set.of("org.springframework.web.bind.annotation.PutMapping");
    /** Shorthand {@code @DeleteMapping} route annotation. */
    private static final Set<String> DELETE_MAPPING = Set.of("org.springframework.web.bind.annotation.DeleteMapping");
    /** Shorthand {@code @PatchMapping} route annotation. */
    private static final Set<String> PATCH_MAPPING = Set.of("org.springframework.web.bind.annotation.PatchMapping");
    /** Marks a method as a {@link EntrypointType#SCHEDULER} entrypoint ({@code @Scheduled}). */
    private static final Set<String> SCHEDULED = Set.of("org.springframework.scheduling.annotation.Scheduled");
    /** Marks a Kafka consumer method, or a whole class hosting {@code @KafkaHandler} methods ({@code @KafkaListener}). */
    private static final Set<String> KAFKA_LISTENER = Set.of("org.springframework.kafka.annotation.KafkaListener");
    /** Marks one handler method inside a class-level {@code @KafkaListener} ({@code @KafkaHandler}). */
    private static final Set<String> KAFKA_HANDLER = Set.of("org.springframework.kafka.annotation.KafkaHandler");
    /** Marks a RabbitMQ consumer method ({@code @RabbitListener}). */
    private static final Set<String> RABBIT_LISTENER =
            Set.of("org.springframework.amqp.rabbit.annotation.RabbitListener");
    /** Marks a JMS consumer method ({@code @JmsListener}). */
    private static final Set<String> JMS_LISTENER = Set.of("org.springframework.jms.annotation.JmsListener");
    /** Marks a declarative HTTP client interface ({@code @FeignClient}) as a {@link ComponentType#HTTP_CLIENT}. */
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

    /**
     * Returns the shared OpenTelemetry tracer used to span this extractor's work.
     *
     * @return the {@code dev.dominikbreu.archlens} tracer
     */
    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.archlens");
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
            Set<dev.dominikbreu.archlens.model.ids.ComponentId> existingIds = new HashSet<>();
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

    /**
     * Classifies the given type as a Spring-managed component, if it matches any recognized
     * stereotype, entity, listener, or scheduler shape.
     *
     * @param type the Spoon type to classify
     * @param appId the application id to assign to the extracted component
     * @return the classified component, or {@code null} if the type matches no known Spring shape
     */
    private Component tryExtractComponent(CtType<?> type, AppId appId) {
        ComponentType componentType = null;
        String technology = "spring";
        List<String> stereotypes = new ArrayList<>();

        if (PersistenceEntityTypes.isEntity(type)) {
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
        component.id = dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName());
        component.type = componentType;
        component.name = type.getSimpleName();
        component.qualifiedName = type.getQualifiedName();
        component.module = appId;
        component.technology = technology;
        component.stereotypes = stereotypes;
        component.source = new SourceInfo(getFile(type), getLine(type), ANNOTATION, 0.95);
        return component;
    }

    /**
     * Returns true if any method on the given type is annotated {@code @Scheduled}.
     *
     * @param type the Spoon type to check
     * @return true if the type has a scheduled method
     */
    private boolean hasScheduledMethod(CtType<?> type) {
        return type.getMethods().stream().anyMatch(method -> hasAnnotation(method, SCHEDULED));
    }

    private static final Set<String> HIBERNATE_EVENT_LISTENER_INTERFACES =
            Set.of("PostInsertEventListener", "PostUpdateEventListener", "PostDeleteEventListener");
    private static final Set<String> HIBERNATE_EVENT_LISTENER_METHODS =
            Set.of("onPostInsert", "onPostUpdate", "onPostDelete");

    /**
     * Detects Hibernate entity lifecycle listener methods ({@code onPostInsert/Update/Delete} on a
     * type implementing the matching {@code org.hibernate.event.spi} interface). These beans have
     * no Spring entrypoint annotation but are real producer roots — the ORM invokes them on every
     * entity write, so their outbound sends would otherwise be unreachable from any entrypoint.
     */
    private boolean isEntityEventListenerMethod(CtMethod<?> method, CtType<?> type) {
        if (!HIBERNATE_EVENT_LISTENER_METHODS.contains(method.getSimpleName())) return false;
        return type.getSuperInterfaces().stream()
                .anyMatch(i -> HIBERNATE_EVENT_LISTENER_INTERFACES.contains(i.getSimpleName()));
    }

    /**
     * Returns true if the type or any of its methods carries a Kafka, RabbitMQ, or JMS listener
     * annotation.
     *
     * @param type the Spoon type to check
     * @return true if the type is a messaging listener
     */
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

    /**
     * Scans every method of the given type for REST routes, scheduled triggers, messaging
     * listeners, main/runner methods, and Hibernate entity-event listener methods, adding a
     * matching {@link Entrypoint} for each one found. Also extracts Feign client interfaces and
     * outbound call sites for the type as a whole.
     *
     * @param type the Spoon type to scan
     * @param component the component the entrypoints belong to
     * @param model the architecture model to populate
     */
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
                addScheduledEntrypoint(method, type, component, model);
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
            if (isEntityEventListenerMethod(method, type)) {
                addSimpleEntrypoint(
                        method, type, component, EntrypointType.ENTITY_EVENT_LISTENER, "entity-event", model);
            }
        }
        if (component.type == ComponentType.HTTP_CLIENT && hasAnnotation(type, FEIGN_CLIENT)) {
            extractFeignInterfaces(type, component, model);
        }
        extractOutboundCallSites(type, component, model);
    }

    private dev.dominikbreu.archlens.model.ids.EntrypointId restEndpointId(
            CtType<?> type, CtMethod<?> method, Mapping mapping, String fullPath) {
        return new dev.dominikbreu.archlens.model.ids.EntrypointId(
                dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName()),
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
        dev.dominikbreu.archlens.model.ids.EntrypointId id = new dev.dominikbreu.archlens.model.ids.EntrypointId(
                dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName()),
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

    private void addScheduledEntrypoint(
            CtMethod<?> method, CtType<?> type, Component component, ArchitectureModel model) {
        dev.dominikbreu.archlens.model.ids.EntrypointId id = new dev.dominikbreu.archlens.model.ids.EntrypointId(
                dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName()),
                method.getSimpleName(),
                "scheduled");
        if (model.entrypoints.stream().anyMatch(e -> id.equals(e.id))) return;
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.type = EntrypointType.SCHEDULER;
        ep.name = method.getSimpleName();
        ep.componentId = component.id;
        ep.source = new SourceInfo(getFile(method), getLine(method), ANNOTATION, 0.95);
        String cron = annotationAttribute(method, SCHEDULED, "cron");
        String fixedRate = firstNonEmptyAttribute(method, SCHEDULED, "fixedRate", "fixedRateString");
        String fixedDelay = firstNonEmptyAttribute(method, SCHEDULED, "fixedDelay", "fixedDelayString");
        if (!cron.isEmpty()) {
            ep.triggerKind = "CRON";
            ep.triggerExpression = cron;
        } else if (!fixedRate.isEmpty()) {
            ep.triggerKind = "FIXED_RATE";
            ep.triggerExpression = fixedRate;
        } else if (!fixedDelay.isEmpty()) {
            ep.triggerKind = "FIXED_DELAY";
            ep.triggerExpression = fixedDelay;
        }
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
        dev.dominikbreu.archlens.model.ids.EntrypointId id = new dev.dominikbreu.archlens.model.ids.EntrypointId(
                dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName()),
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

    /**
     * Reads the first attribute, falling back to the second if the first is absent or empty.
     *
     * @param element the AST element bearing the annotation
     * @param annotation the fully-qualified annotation type names to match
     * @param first the preferred attribute name
     * @param second the fallback attribute name
     * @return the first non-empty attribute value, or {@code ""} if both are absent
     */
    private String firstNonEmptyAttribute(CtElement element, Set<String> annotation, String first, String second) {
        String value = annotationAttribute(element, annotation, first);
        if (value.isEmpty()) {
            return annotationAttribute(element, annotation, second);
        } else {
            return value;
        }
    }

    /**
     * Returns true if the method is a plain Java {@code static void main(String[])} entry point.
     *
     * @param method the method to check
     * @return true if the method is a {@code main} method
     */
    private boolean isMainMethod(CtMethod<?> method) {
        return "main".equals(method.getSimpleName()) && method.isStatic();
    }

    /**
     * Returns true if the method is the {@code run} method of a Spring Boot
     * {@code ApplicationRunner} or {@code CommandLineRunner} implementation.
     *
     * @param method the method to check
     * @param type the declaring type
     * @return true if the method is a Spring Boot runner entry point
     */
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

    /**
     * Resolves the HTTP method and path for a REST-mapped method, checking the shorthand
     * mapping annotations before the generic {@code @RequestMapping}.
     *
     * @param method the method to inspect
     * @return the resolved mapping, or {@code null} if the method carries no mapping annotation
     */
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

    /**
     * Reads the {@code method} attribute of a {@code @RequestMapping} and maps it to a single
     * HTTP verb string.
     *
     * @param element the AST element bearing the annotation
     * @return the HTTP verb, or {@code null} if none of GET/POST/PUT/DELETE/PATCH is present
     */
    private String requestMappingMethod(CtElement element) {
        String method = annotationAttribute(element, REQUEST_MAPPING, "method");
        if (method.contains("GET")) return "GET";
        if (method.contains("POST")) return "POST";
        if (method.contains("PUT")) return "PUT";
        if (method.contains(HTTP_DELETE)) return HTTP_DELETE;
        if (method.contains(HTTP_PATCH)) return HTTP_PATCH;
        return null;
    }

    /**
     * Resolves the route path from whichever mapping annotation is present on the element,
     * expanding any Spring property placeholder via the extractor's config.
     *
     * @param element the AST element bearing the annotation
     * @return the resolved route path, or {@code ""} if no mapping annotation carries a path
     */
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
            CtExpression<?> value = safeAnnotationValue(annotation, attribute);
            if (value == null && VALUE.equals(attribute)) value = safeAnnotationValue(annotation, "path");
            if (value == null) return "";
            return stripArray(resolveAnnotationValue(value));
        }
        return "";
    }

    /**
     * Reads one annotation attribute, treating a failed reflective default-value lookup the same as
     * "attribute not set" rather than aborting the whole {@link #annotationAttribute} call. Spoon
     * resolves an attribute that isn't explicitly present in source by reflectively loading the
     * annotation's own class to read its declared default; that load fails whenever the annotation's
     * library (e.g. Spring) isn't on ArchLens's own classpath, which is unrelated to whether a sibling
     * attribute (e.g. {@code path} next to an unset {@code value}) was actually written.
     */
    private CtExpression<?> safeAnnotationValue(CtAnnotation<?> annotation, String attribute) {
        try {
            return annotation.getValue(attribute);
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Resolves an annotation attribute expression to its string form, handling literals, arrays
     * (using the first non-blank element), and reads of another class's static final field.
     *
     * @param value the attribute value expression
     * @return the resolved string value
     */
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

    /**
     * Returns the literal's value as a string, or {@code ""} if the literal's value is null.
     *
     * @param literal the literal expression
     * @return the string form of the literal's value
     */
    private static String literalString(CtLiteral<?> literal) {
        Object raw = literal.getValue();
        return raw == null ? "" : raw.toString();
    }

    /**
     * Returns the first non-blank resolved element of an array-valued annotation attribute
     * (e.g. the first {@code topics} entry of a {@code @KafkaListener}).
     *
     * @param array the array expression
     * @return the first non-blank resolved element, or {@code ""} if all elements are blank
     */
    private String firstNonBlankElement(CtNewArray<?> array) {
        for (CtExpression<?> element : array.getElements()) {
            String resolved = resolveAnnotationValue(element);
            if (!resolved.isBlank()) return resolved;
        }
        return "";
    }

    /**
     * Reads the literal default value of a referenced static final field (e.g.
     * {@code SomeConstants.TOPIC} used as an annotation attribute value).
     *
     * @param fieldRef the field reference
     * @return the field's literal default value, or {@code null} if it isn't a resolvable literal
     */
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

    /**
     * Returns true if the given annotation instance's type matches one of the given qualified or
     * simple names.
     *
     * @param annotation the annotation instance to check
     * @param names the fully-qualified annotation type names to match
     * @return true if the annotation matches
     */
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

    /**
     * Strips Java array braces from a resolved annotation value, but leaves a leading
     * {@code {pathVariable}} untouched since that's meaningful path syntax, not array syntax.
     *
     * @param value the resolved attribute value
     * @return the value with array braces removed, if applicable
     */
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

    /**
     * Normalizes a route path: ensures a leading slash and collapses repeated slashes.
     *
     * @param path the raw path
     * @return the normalized path, defaulting to {@code "/"} for a null or empty input
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        while (path.contains("//")) path = path.replace("//", "/");
        return path;
    }

    /**
     * Joins a class-level base path and a method-level child path into one normalized path.
     *
     * @param base the base (class-level or context) path
     * @param child the child (method-level) path
     * @return the combined, normalized path
     */
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
        if (position.isValidPosition() && position.getFile() != null) {
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

    /**
     * Adds a {@code rest_client} interface for the Feign client type itself, plus one
     * {@code rest_client_operation} interface per mapped method.
     *
     * @param type the {@code @FeignClient}-annotated interface
     * @param component the HTTP client component the interfaces belong to
     * @param model the architecture model to populate
     */
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

    /**
     * Scans every method invocation in the type for outbound HTTP and messaging call sites.
     *
     * @param type the Spoon type to scan
     * @param component the component the call sites belong to
     * @param model the architecture model to populate
     */
    private void extractOutboundCallSites(CtType<?> type, Component component, ArchitectureModel model) {
        type.getElements(element -> element instanceof CtInvocation<?>)
                .forEach(element -> processOutboundInvocation((CtInvocation<?>) element, component, model));
    }

    /**
     * Recognizes one invocation as an outbound REST call (RestTemplate/WebClient-style methods
     * on a URL-shaped first argument) or messaging send ({@code send}/{@code convertAndSend}),
     * adding the matching interface entry. Also checks the invocation for a Kafka outbound sink
     * site independently of this classification.
     *
     * @param invocation the invocation to classify
     * @param component the component the invocation belongs to
     * @param model the architecture model to populate
     */
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

    /**
     * Recognizes a {@code KafkaTemplate.send(...)} call and records it as an outbound sink site,
     * classifying the topic argument and payload along the way.
     *
     * @param invocation the invocation to check
     * @param component the component the call site belongs to
     * @param model the architecture model to populate
     */
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
        site.broker = MessagingBroker.KAFKA;
        classifyTopicArg(invocation, site);
        site.payloadVarName = payloadVarName(invocation);
        site.payloadType = payloadType(invocation);
        site.linkEvidence = "spring-kafka-template-send";
        site.source = new SourceInfo(getFile(invocation), getLine(invocation), "spring-kafka-template-send", 0.95);
        model.outboundSinkSites.add(site);
    }

    /**
     * Classifies the topic argument of a {@code KafkaTemplate.send(...)} call and, where
     * possible, resolves it to a concrete topic name: a string/property-placeholder literal, a
     * referenced static final field's literal default, a method parameter or local variable, or
     * an opaque method-call expression.
     *
     * @param sendInv the {@code send} invocation
     * @param site the sink site to populate with the classification
     */
    private void classifyTopicArg(CtInvocation<?> sendInv, OutboundSinkSite site) {
        if (sendInv.getArguments().isEmpty()) {
            site.topicArgKind = TopicArgKind.UNKNOWN;
            return;
        }
        CtExpression<?> arg = sendInv.getArguments().getFirst();

        // String literal or Spring property placeholder (e.g. "${topics.orders.created}")
        if (arg instanceof CtLiteral<?> lit && lit.getValue() instanceof String s) {
            SpringConfigResolver.ResolvedValue resolved = config.resolveWithKey(s);
            site.topicArgKind = TopicArgKind.LITERAL;
            site.topic = resolved.value();
            site.channel = resolved.value();
            site.topicPropertyKey = resolved.wasResolved() ? resolved.propertyKey() : null;
            return;
        }

        // Static final field read (e.g. KafkaConfig.ADDRESS_TOPIC = "address")
        if (arg instanceof CtVariableRead<?> vr && vr.getVariable() instanceof CtFieldReference<?> fr) {
            try {
                CtField<?> decl = fr.getDeclaration();
                if (decl != null
                        && decl.getDefaultExpression() instanceof CtLiteral<?> lit
                        && lit.getValue() instanceof String s) {
                    SpringConfigResolver.ResolvedValue resolved = config.resolveWithKey(s);
                    site.topicArgKind = TopicArgKind.LITERAL;
                    site.topic = resolved.value();
                    site.channel = resolved.value();
                    site.topicPropertyKey = resolved.wasResolved() ? resolved.propertyKey() : null;
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // Variable read: method parameter or local variable
        if (arg instanceof CtVariableRead<?> vr) {
            try {
                CtVariable<?> varDecl = vr.getVariable().getDeclaration();
                if (varDecl instanceof CtParameter<?> param) {
                    CtTypeReference<?> paramType = param.getType();
                    String typeName = paramType != null ? paramType.getSimpleName() : "";
                    if (typeName.contains("Message")) {
                        site.topicArgKind = TopicArgKind.MESSAGE_OBJECT;
                    } else {
                        site.topicArgKind = TopicArgKind.PARAM_REF;
                        CtMethod<?> enclosing = sendInv.getParent(CtMethod.class);
                        if (enclosing != null) {
                            List<CtParameter<?>> params = enclosing.getParameters();
                            for (int i = 0; i < params.size(); i++) {
                                if (params.get(i).getSimpleName().equals(param.getSimpleName())) {
                                    site.topicArgParamIndex = i;
                                    break;
                                }
                            }
                        }
                    }
                    return;
                }
                if (varDecl instanceof CtLocalVariable<?> local
                        && local.getDefaultExpression() instanceof CtLiteral<?> lit
                        && lit.getValue() instanceof String s) {
                    SpringConfigResolver.ResolvedValue resolved = config.resolveWithKey(s);
                    site.topicArgKind = TopicArgKind.LITERAL;
                    site.topic = resolved.value();
                    site.channel = resolved.value();
                    site.topicPropertyKey = resolved.wasResolved() ? resolved.propertyKey() : null;
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // Method invocation (e.g. event.getType(), event.getTopic())
        if (arg instanceof CtInvocation<?>) {
            site.topicArgKind = TopicArgKind.METHOD_CALL;
            return;
        }

        site.topicArgKind = TopicArgKind.UNKNOWN;
    }

    /**
     * Returns the variable name of the second argument to a {@code send(topic, payload)} call,
     * if that argument is a variable read.
     *
     * @param invocation the {@code send} invocation
     * @return the payload variable's name, or {@code null} if not applicable
     */
    private String payloadVarName(CtInvocation<?> invocation) {
        if (invocation.getArguments().size() < 2) return null;
        if (invocation.getArguments().get(1) instanceof CtVariableRead<?> variableRead
                && variableRead.getVariable() != null) {
            return variableRead.getVariable().getSimpleName();
        }
        return null;
    }

    /**
     * Returns the qualified type name of the second argument to a {@code send(topic, payload)}
     * call.
     *
     * @param invocation the {@code send} invocation
     * @return the payload's qualified type name, or {@code null} if not applicable
     */
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

    /**
     * Returns true if the value looks like an absolute HTTP(S) URL.
     *
     * @param value the value to check
     * @return true if the value starts with {@code http://} or {@code https://}
     */
    private boolean looksLikeUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    /**
     * Strips a surrounding pair of double quotes from an argument's {@code toString()} rendering.
     *
     * @param value the raw value
     * @return the value with surrounding quotes removed, or {@code ""} for a null input
     */
    private String stripQuotes(String value) {
        if (value == null) return "";
        String out = value.trim();
        if (out.startsWith("\"") && out.endsWith("\"")) return out.substring(1, out.length() - 1);
        return out;
    }

    /**
     * A resolved REST mapping: HTTP method plus route path.
     *
     * @param method the HTTP verb (e.g. {@code "GET"})
     * @param path the route path
     */
    private record Mapping(String method, String path) {}
}
