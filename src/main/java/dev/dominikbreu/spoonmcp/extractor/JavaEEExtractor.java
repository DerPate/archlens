package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import java.util.*;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;

/**
 * Extracts Java EE and Jakarta EE components, entrypoints, and JPA entities from Spoon types.
 */
public class JavaEEExtractor {

    private static final String ANNOTATION = "annotation";
    private static final String ON_MESSAGE = "onMessage";

    private static final Set<String> EJB_STATELESS = Set.of("javax.ejb.Stateless", "jakarta.ejb.Stateless");
    private static final Set<String> EJB_STATEFUL = Set.of("javax.ejb.Stateful", "jakarta.ejb.Stateful");
    private static final Set<String> EJB_SINGLETON = Set.of("javax.ejb.Singleton", "jakarta.ejb.Singleton");
    private static final Set<String> MESSAGE_DRIVEN = Set.of("javax.ejb.MessageDriven", "jakarta.ejb.MessageDriven");
    private static final Set<String> JAX_RS_PATH = Set.of("javax.ws.rs.Path", "jakarta.ws.rs.Path");
    private static final Set<String> HTTP_METHODS = Set.of(
            "javax.ws.rs.GET", "jakarta.ws.rs.GET",
            "javax.ws.rs.POST", "jakarta.ws.rs.POST",
            "javax.ws.rs.PUT", "jakarta.ws.rs.PUT",
            "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE",
            "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH");
    private static final Set<String> ENTITY_ANNOTATIONS =
            Set.of("javax.persistence.Entity", "jakarta.persistence.Entity");

    /** Creates a Java EE extractor using built-in annotation rules. */
    public JavaEEExtractor() {}

    /**
     * Adds Java EE architecture elements to the model for one application.
     *
     * @param types Spoon types in the application or module
     * @param model architecture model to update
     * @param appId owning application identifier
     */
    public void extract(Collection<CtType<?>> types, ArchitectureModel model, AppId appId) {
        Set<dev.dominikbreu.spoonmcp.model.ids.ComponentId> existingIds = new HashSet<>();
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

    private Component tryExtractComponent(CtType<?> type, AppId appId) {
        ComponentType compType = null;
        String tech = "javaee";
        List<String> stereos = new ArrayList<>();

        if (hasAnn(type, EJB_STATELESS)) {
            compType = ComponentType.EJB_STATELESS;
            stereos.add("ejb-stateless");
        } else if (hasAnn(type, EJB_STATEFUL)) {
            compType = ComponentType.EJB_STATEFUL;
            stereos.add("ejb-stateful");
        } else if (hasAnn(type, EJB_SINGLETON)) {
            compType = ComponentType.EJB_SINGLETON;
            stereos.add("ejb-singleton");
        } else if (hasAnn(type, MESSAGE_DRIVEN)) {
            compType = ComponentType.MESSAGE_DRIVEN_BEAN;
            stereos.add("mdb");
        } else if (hasAnn(type, JAX_RS_PATH)) {
            compType = ComponentType.REST_RESOURCE;
            stereos.add("jax-rs");
        } else if (hasAnn(type, ENTITY_ANNOTATIONS)) {
            compType = ComponentType.ENTITY;
            tech = "jpa";
            stereos.add("entity");
        }

        if (compType == null) return null;

        Component c = new Component();
        c.id = dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
        c.type = compType;
        c.name = type.getSimpleName();
        c.qualifiedName = type.getQualifiedName();
        c.module = appId;
        c.technology = tech;
        c.stereotypes = stereos;
        c.source = new SourceInfo(getFile(type), getLine(type), ANNOTATION, 0.95);
        return c;
    }

    private void extractEntrypoints(CtType<?> type, Component component, ArchitectureModel model) {
        String classBasePath = getAnnotationStringValue(type, JAX_RS_PATH);

        for (CtMethod<?> method : type.getMethods()) {
            String httpMethod = getHttpMethod(method);
            if (httpMethod != null) {
                String methodPath = getAnnotationStringValue(method, JAX_RS_PATH);
                String fullPath = combinePaths(classBasePath, methodPath);
                Entrypoint ep = new Entrypoint();
                ep.id = new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                        dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()),
                        method.getSimpleName(),
                        "");
                ep.type = EntrypointType.REST_ENDPOINT;
                ep.name = method.getSimpleName();
                ep.httpMethod = httpMethod;
                ep.path = fullPath;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), ANNOTATION, 1.0);
                model.entrypoints.add(ep);
                addInterface(method, component, "rest_endpoint", httpMethod + " " + fullPath, fullPath, model);
                continue;
            }

            // MDB onMessage is always an entrypoint
            if (component.type == ComponentType.MESSAGE_DRIVEN_BEAN && ON_MESSAGE.equals(method.getSimpleName())) {
                Entrypoint ep = new Entrypoint();
                ep.id = new dev.dominikbreu.spoonmcp.model.ids.EntrypointId(
                        dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName()), ON_MESSAGE, "");
                ep.type = EntrypointType.JMS_CONSUMER;
                ep.name = ON_MESSAGE;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "type-relation", 0.95);
                model.entrypoints.add(ep);
            }
        }
    }

    private void addInterface(
            CtElement element, Component component, String type, String name, String path, ArchitectureModel model) {
        String id = "iface:" + component.id.qualifiedName() + ":" + type + ":" + name;
        if (model.interfaces.stream().anyMatch(i -> i.id.equals(id))) return;
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
    }

    private boolean hasAnn(CtElement element, Set<String> names) {
        Set<String> sn = simpleNames(names);
        return element.getAnnotations().stream()
                .anyMatch(a -> names.contains(a.getAnnotationType().getQualifiedName())
                        || sn.contains(a.getAnnotationType().getSimpleName()));
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

    private String getHttpMethod(CtMethod<?> method) {
        for (var ann : method.getAnnotations()) {
            if (annMatches(ann, HTTP_METHODS)) {
                return ann.getAnnotationType().getSimpleName();
            }
        }
        return null;
    }

    private String getAnnotationStringValue(CtElement element, Set<String> names) {
        for (var ann : element.getAnnotations()) {
            if (!annMatches(ann, names)) continue;
            return annotationValueString(ann);
        }
        return "";
    }

    private String annotationValueString(spoon.reflect.declaration.CtAnnotation<?> ann) {
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
