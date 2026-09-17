package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.AppId;
import java.util.*;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.*;

/**
 * Extracts Java EE and Jakarta EE components, entrypoints, and JPA entities from Spoon types.
 */
public class JavaEEExtractor {

    /**
     * Evidence kind recorded on a {@link SourceInfo} for a component, entrypoint, or interface
     * that was derived directly from an annotation on its declaration (as opposed to, e.g., a
     * type relationship).
     */
    private static final String ANNOTATION = "annotation";

    /**
     * Method name identifying a message-driven bean's JMS listener callback; matched to derive
     * that method's {@link EntrypointType#JMS_CONSUMER} entrypoint.
     */
    private static final String ON_MESSAGE = "onMessage";

    /**
     * Legacy {@code javax.ejb.Stateless} and Jakarta EE 9+ {@code jakarta.ejb.Stateless}
     * annotation names, matched via {@link #hasAnn} to classify a type as a stateless session
     * bean.
     */
    private static final Set<String> EJB_STATELESS = Set.of("javax.ejb.Stateless", "jakarta.ejb.Stateless");

    /**
     * Legacy {@code javax.ejb.Stateful} and Jakarta EE 9+ {@code jakarta.ejb.Stateful} annotation
     * names, matched via {@link #hasAnn} to classify a type as a stateful session bean.
     */
    private static final Set<String> EJB_STATEFUL = Set.of("javax.ejb.Stateful", "jakarta.ejb.Stateful");

    /**
     * Legacy {@code javax.ejb.Singleton} and Jakarta EE 9+ {@code jakarta.ejb.Singleton}
     * annotation names, matched via {@link #hasAnn} to classify a type as a singleton session
     * bean.
     */
    private static final Set<String> EJB_SINGLETON = Set.of("javax.ejb.Singleton", "jakarta.ejb.Singleton");

    /**
     * Legacy {@code javax.ejb.MessageDriven} and Jakarta EE 9+ {@code jakarta.ejb.MessageDriven}
     * annotation names, matched via {@link #hasAnn} to classify a type as a message-driven bean.
     */
    private static final Set<String> MESSAGE_DRIVEN = Set.of("javax.ejb.MessageDriven", "jakarta.ejb.MessageDriven");

    /**
     * Legacy {@code javax.ws.rs.Path} and Jakarta EE 9+ {@code jakarta.ws.rs.Path} annotation
     * names, matched via {@link #hasAnn} to classify a type as a JAX-RS resource and, via {@link
     * #getAnnotationStringValue}, to read the class- and method-level base paths used when
     * building REST endpoint paths.
     */
    private static final Set<String> JAX_RS_PATH = Set.of("javax.ws.rs.Path", "jakarta.ws.rs.Path");

    /**
     * Legacy {@code javax.ws.rs.*} and Jakarta EE 9+ {@code jakarta.ws.rs.*} HTTP-method
     * annotation names ({@code @GET}, {@code @POST}, {@code @PUT}, {@code @DELETE}, {@code @PATCH}),
     * matched via {@link #getHttpMethod} to identify a JAX-RS endpoint method and its HTTP verb.
     */
    private static final Set<String> HTTP_METHODS = Set.of(
            "javax.ws.rs.GET", "jakarta.ws.rs.GET",
            "javax.ws.rs.POST", "jakarta.ws.rs.POST",
            "javax.ws.rs.PUT", "jakarta.ws.rs.PUT",
            "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE",
            "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH");

    /**
     * Legacy {@code javax.persistence.Entity} and Jakarta EE 9+ {@code jakarta.persistence.Entity}
     * annotation names. Part of the shared annotation-name catalog for this extractor; JPA entity
     * classification itself is currently delegated to {@link PersistenceEntityTypes#isEntity},
     * which does not consult this set.
     */
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
        Set<dev.dominikbreu.archlens.model.ids.ComponentId> existingIds = new HashSet<>();
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

    /**
     * Classifies a type as one Java EE/Jakarta EE component, if any, and builds its {@link
     * Component} record.
     *
     * <p>Checks, in order, EJB {@code @Stateless}, {@code @Stateful}, {@code @Singleton},
     * {@code @MessageDriven}, then JAX-RS {@code @Path}, then {@link PersistenceEntityTypes#isEntity},
     * returning on the first match. For the three session-bean kinds and MDBs, the EJB name is read
     * from the annotation's {@code name} attribute via {@link #getAnnotationNameValue}. JPA entities
     * are tagged with technology {@code "jpa"} instead of the default {@code "javaee"}. Returns
     * {@code null} when none of the rules match. The component id is the type's qualified name, and
     * its {@link SourceInfo} always cites evidence kind {@link #ANNOTATION} at confidence 0.95.
     *
     * @param type Spoon type under inspection
     * @param appId owning application identifier
     * @return the classified component, or {@code null} if the type matches no Java EE rule
     */
    private Component tryExtractComponent(CtType<?> type, AppId appId) {
        ComponentType compType = null;
        String tech = "javaee";
        List<String> stereos = new ArrayList<>();
        String ejbName = null;

        if (hasAnn(type, EJB_STATELESS)) {
            compType = ComponentType.EJB_STATELESS;
            stereos.add("ejb-stateless");
            ejbName = getAnnotationNameValue(type, EJB_STATELESS);
        } else if (hasAnn(type, EJB_STATEFUL)) {
            compType = ComponentType.EJB_STATEFUL;
            stereos.add("ejb-stateful");
            ejbName = getAnnotationNameValue(type, EJB_STATEFUL);
        } else if (hasAnn(type, EJB_SINGLETON)) {
            compType = ComponentType.EJB_SINGLETON;
            stereos.add("ejb-singleton");
            ejbName = getAnnotationNameValue(type, EJB_SINGLETON);
        } else if (hasAnn(type, MESSAGE_DRIVEN)) {
            compType = ComponentType.MESSAGE_DRIVEN_BEAN;
            stereos.add("mdb");
            ejbName = getAnnotationNameValue(type, MESSAGE_DRIVEN);
        } else if (hasAnn(type, JAX_RS_PATH)) {
            compType = ComponentType.REST_RESOURCE;
            stereos.add("jax-rs");
        } else if (PersistenceEntityTypes.isEntity(type)) {
            compType = ComponentType.ENTITY;
            tech = "jpa";
            stereos.add("entity");
        }

        if (compType == null) return null;

        Component c = new Component();
        c.id = dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName());
        c.type = compType;
        c.name = type.getSimpleName();
        c.qualifiedName = type.getQualifiedName();
        c.module = appId;
        c.technology = tech;
        c.stereotypes = stereos;
        c.ejbName = ejbName;
        c.source = new SourceInfo(getFile(type), getLine(type), ANNOTATION, 0.95);
        return c;
    }

    /**
     * Derives entrypoints for one already-classified component: one REST endpoint per JAX-RS
     * HTTP-method-annotated method, plus the single JMS-consumer entrypoint for a message-driven
     * bean's {@code onMessage} method.
     *
     * <p>For each method, {@link #getHttpMethod} decides whether it is a JAX-RS endpoint; if so, the
     * method's {@code @Path} (if any) is combined with the type-level base path from {@code
     * classBasePath} via {@link #combinePaths}, a {@link EntrypointType#REST_ENDPOINT} entrypoint is
     * recorded at confidence 1.0, and a matching {@code rest_endpoint} interface entry is added via
     * {@link #addInterface}. Otherwise, if the component is a {@link
     * ComponentType#MESSAGE_DRIVEN_BEAN} and the method is named {@link #ON_MESSAGE}, a {@link
     * EntrypointType#JMS_CONSUMER} entrypoint is recorded at confidence 0.95 with evidence kind
     * {@code "type-relation"} (the entrypoint follows from the bean's type, not from an annotation
     * on the method itself).
     *
     * @param type Spoon type owning the methods to scan
     * @param component already-classified component the entrypoints belong to
     * @param model architecture model to append entrypoints and interfaces to
     */
    private void extractEntrypoints(CtType<?> type, Component component, ArchitectureModel model) {
        String classBasePath = getAnnotationStringValue(type, JAX_RS_PATH);

        for (CtMethod<?> method : type.getMethods()) {
            String httpMethod = getHttpMethod(method);
            if (httpMethod != null) {
                String methodPath = getAnnotationStringValue(method, JAX_RS_PATH);
                String fullPath = combinePaths(classBasePath, methodPath);
                Entrypoint ep = new Entrypoint();
                ep.id = new dev.dominikbreu.archlens.model.ids.EntrypointId(
                        dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName()),
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
                ep.id = new dev.dominikbreu.archlens.model.ids.EntrypointId(
                        dev.dominikbreu.archlens.model.ids.ComponentId.of(type.getQualifiedName()), ON_MESSAGE, "");
                ep.type = EntrypointType.JMS_CONSUMER;
                ep.name = ON_MESSAGE;
                ep.componentId = component.id;
                ep.source = new SourceInfo(getFile(method), getLine(method), "type-relation", 0.95);
                model.entrypoints.add(ep);
            }
        }
    }

    /**
     * Appends a deduplicated {@link InterfaceEntry} for one exposed interface (e.g. a REST
     * endpoint) of a component.
     *
     * <p>The entry id is {@code "iface:" + componentId + ":" + type + ":" + name}; if an entry with
     * that id already exists in {@code model.interfaces} this is a no-op. The new entry inherits the
     * component's module and technology, and its {@link SourceInfo} cites evidence kind {@link
     * #ANNOTATION} at confidence 0.95.
     *
     * @param element Spoon element (typically the endpoint method) the interface is derived from
     * @param component owning component
     * @param type interface kind label, e.g. {@code "rest_endpoint"}
     * @param name interface name, e.g. {@code "GET /path"}
     * @param path normalized request path of the interface
     * @param model architecture model to append the interface entry to
     */
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

    /**
     * Checks whether any annotation on the element matches one of the given fully-qualified
     * annotation names.
     *
     * <p>Matches both by exact qualified name and, via {@link #simpleNames}, by simple name — so an
     * annotation resolves as a match even when Spoon could not resolve its type to a qualified name
     * (e.g. missing classpath entries), as long as its simple name (e.g. {@code "Stateless"})
     * appears among {@code names}.
     *
     * @param element Spoon element whose annotations are checked
     * @param names fully-qualified annotation names to match against (e.g. {@link #EJB_STATELESS})
     * @return {@code true} if at least one annotation on {@code element} matches
     */
    private boolean hasAnn(CtElement element, Set<String> names) {
        Set<String> sn = simpleNames(names);
        return element.getAnnotations().stream()
                .anyMatch(a -> names.contains(a.getAnnotationType().getQualifiedName())
                        || sn.contains(a.getAnnotationType().getSimpleName()));
    }

    /**
     * Checks whether a single annotation matches one of the given fully-qualified annotation
     * names, by qualified name or, as a fallback, by simple name.
     *
     * @param ann annotation to test
     * @param names fully-qualified annotation names to match against
     * @return {@code true} if {@code ann} matches one of {@code names}
     */
    private boolean annMatches(spoon.reflect.declaration.CtAnnotation<?> ann, Set<String> names) {
        return names.contains(ann.getAnnotationType().getQualifiedName())
                || simpleNames(names).contains(ann.getAnnotationType().getSimpleName());
    }

    /**
     * Derives the set of simple (unqualified) names for a set of fully-qualified annotation
     * names, used to match annotations whose type Spoon could not fully resolve.
     *
     * @param qualifiedNames fully-qualified names, e.g. {@code "jakarta.ejb.Stateless"}
     * @return the corresponding simple names, e.g. {@code "Stateless"}
     */
    private Set<String> simpleNames(Set<String> qualifiedNames) {
        return qualifiedNames.stream()
                .map(n -> n.substring(n.lastIndexOf('.') + 1))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Returns the JAX-RS HTTP method name (e.g. {@code "GET"}) if the method carries one of the
     * {@code HTTP_METHODS} annotations, identified by the matched annotation's simple name.
     *
     * @param method Spoon method to inspect
     * @return the simple name of the matched HTTP-method annotation, or {@code null} if none match
     */
    private String getHttpMethod(CtMethod<?> method) {
        for (var ann : method.getAnnotations()) {
            if (annMatches(ann, HTTP_METHODS)) {
                return ann.getAnnotationType().getSimpleName();
            }
        }
        return null;
    }

    /**
     * Returns the {@code value} attribute of the first annotation on the element that matches one
     * of the given names, e.g. the {@code @Path} string.
     *
     * @param element Spoon element whose annotations are searched
     * @param names fully-qualified annotation names to match, e.g. {@link #JAX_RS_PATH}
     * @return the annotation's {@code value} as a string, or {@code ""} if no annotation matches or
     *     it has no readable {@code value}
     */
    private String getAnnotationStringValue(CtElement element, Set<String> names) {
        for (var ann : element.getAnnotations()) {
            if (!annMatches(ann, names)) continue;
            return annotationValueString(ann);
        }
        return "";
    }

    /**
     * Reads an annotation's {@code value} attribute as a plain string, unwrapping a literal's
     * value or stripping surrounding quotes from a non-literal expression's textual form.
     *
     * @param ann annotation to read
     * @return the {@code value} attribute as a string, or {@code ""} if absent, {@code null}, or
     *     unreadable (any exception is swallowed)
     */
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
        } catch (Exception _) {
            return "";
        }
    }

    /**
     * Reads the {@code name} attribute of the first annotation on the element that matches one of
     * the given names, e.g. the explicit EJB name given as {@code @Stateless(name = "...")}.
     *
     * <p>Unlike {@link #annotationValueString}, an empty string is treated the same as absent and
     * normalized to {@code null}, since EJB deployment name resolution should fall back to the bean
     * class name rather than an explicit empty name. Any exception while reading the attribute is
     * swallowed and treated as absent.
     *
     * @param element Spoon element whose annotations are searched
     * @param names fully-qualified annotation names to match, e.g. {@link #EJB_STATELESS}
     * @return the annotation's non-empty {@code name} value, or {@code null} if no annotation
     *     matches, the attribute is absent, or it is empty
     */
    private String getAnnotationNameValue(CtElement element, Set<String> names) {
        for (var ann : element.getAnnotations()) {
            if (!annMatches(ann, names)) continue;
            try {
                CtExpression<?> val = ann.getValue("name");
                if (val == null) return null;
                if (val instanceof CtLiteral<?> lit) {
                    Object v = lit.getValue();
                    return v != null && !v.toString().isEmpty() ? v.toString() : null;
                }
                String str = val.toString();
                if (str.startsWith("\"") && str.endsWith("\"")) {
                    return str.substring(1, str.length() - 1);
                }
                return str;
            } catch (Exception _) {
                return null;
            }
        }
        return null;
    }

    /**
     * Normalizes a request path to start with a single leading {@code "/"} and collapses any
     * repeated slashes.
     *
     * @param path raw path, possibly {@code null} or missing a leading slash
     * @return {@code "/"} if {@code path} is {@code null} or empty, otherwise the normalized path
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        while (path.contains("//")) path = path.replace("//", "/");
        return path;
    }

    /**
     * Joins a JAX-RS class-level base path and a method-level path segment into one normalized
     * path, inserting or collapsing the separating slash as needed so the two never end up with
     * zero or two slashes between them.
     *
     * @param base class-level {@code @Path} value, or {@code null}/empty if absent
     * @param child method-level {@code @Path} value, or {@code null}/empty if absent
     * @return the combined, normalized path (see {@link #normalizePath})
     */
    private String combinePaths(String base, String child) {
        if (base == null) base = "";
        if (child == null) child = "";
        if (base.endsWith("/") && child.startsWith("/")) return normalizePath(base + child.substring(1));
        if (!base.endsWith("/") && !child.startsWith("/") && !child.isEmpty()) return normalizePath(base + "/" + child);
        return normalizePath(base + child);
    }

    /**
     * Returns the absolute path of the source file declaring the element.
     *
     * @param el Spoon element to locate
     * @return the absolute source file path, or {@code "unknown"} if the element has no valid
     *     source position
     */
    private String getFile(CtElement el) {
        var pos = el.getPosition();
        if (pos.isValidPosition() && pos.getFile() != null) {
            return pos.getFile().getAbsolutePath();
        } else {
            return "unknown";
        }
    }

    /**
     * Returns the source line number of the element's declaration.
     *
     * @param el Spoon element to locate
     * @return the 1-based source line number, or {@code 0} if the element has no valid source
     *     position
     */
    private int getLine(CtElement el) {
        var pos = el.getPosition();
        if (pos.isValidPosition()) {
            return pos.getLine();
        } else {
            return 0;
        }
    }
}
