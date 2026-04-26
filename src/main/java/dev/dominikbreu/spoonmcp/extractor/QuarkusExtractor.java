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
        boolean hasScheduled = type.getMethods().stream()
            .anyMatch(m -> hasAnnotation(m, SCHEDULED_ANNOTATIONS));

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
        } else if (hasScheduled) {
            compType = ComponentType.SCHEDULER;
            technology = "quarkus";
            stereotypes.add("scheduled");
        } else if (hasCdiScope) {
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

        for (CtMethod<?> method : type.getMethods()) {
            String httpMethod = getHttpMethod(method);
            if (httpMethod != null && component.type != ComponentType.HTTP_CLIENT) {
                String methodPath = getAnnotationStringValue(method, JAX_RS_PATH);
                String fullPath = combinePaths(classBasePath, methodPath);
                Entrypoint ep = new Entrypoint();
                ep.id = "ep:" + type.getQualifiedName() + "#" + method.getSimpleName();
                ep.type = EntrypointType.REST_ENDPOINT;
                ep.name = method.getSimpleName();
                ep.httpMethod = httpMethod;
                ep.path = fullPath;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "annotation", 1.0);
                model.entrypoints.add(ep);
                addInterface(method, component, "rest_endpoint", httpMethod + " " + fullPath, fullPath, model);
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
        }

        if (component.type == ComponentType.HTTP_CLIENT) {
            String clientBasePath = normalizePath(classBasePath);
            addInterface(type, component, "rest_client", component.name, clientBasePath, model);
            for (CtMethod<?> method : type.getMethods()) {
                String httpMethod = getHttpMethod(method);
                if (httpMethod == null) continue;
                String methodPath = getAnnotationStringValue(method, JAX_RS_PATH);
                String fullPath = combinePaths(clientBasePath, methodPath);
                addInterface(method, component, "rest_client_operation",
                    httpMethod + " " + fullPath, fullPath, model);
            }
        }
    }

    private void addInterface(CtElement element, Component component, String type,
                              String name, String path, ArchitectureModel model) {
        String id = "iface:" + component.id.substring("comp:".length()) + ":" + type + ":" + name;
        if (model.interfaces.stream().anyMatch(i -> i.id.equals(id))) return;
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
        for (var ann : element.getAnnotations()) {
            if (!annMatches(ann, names)) continue;
            try {
                CtExpression<?> val = ann.getValue("value");
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
