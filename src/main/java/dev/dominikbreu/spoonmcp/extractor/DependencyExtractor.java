package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.*;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

/**
 * Extracts injection-based dependencies between known components from the Spoon model.
 * Runs as a second pass after all components have been identified.
 */
public class DependencyExtractor {

    private final DependencyEvidenceScorer evidenceScorer = new DependencyEvidenceScorer();

    private static final Set<String> INJECT_ANNOTATIONS = Set.of(
            "javax.inject.Inject",
            "jakarta.inject.Inject",
            "javax.ejb.EJB",
            "jakarta.ejb.EJB",
            "javax.annotation.Resource",
            "jakarta.annotation.Resource",
            "org.springframework.beans.factory.annotation.Autowired");

    private static final Set<String> PERSISTENCE_CONTEXT =
            Set.of("javax.persistence.PersistenceContext", "jakarta.persistence.PersistenceContext");

    /** Creates a dependency extractor using the default evidence scorer. */
    public DependencyExtractor() {}

    /**
     * Adds dependencies between known model components by inspecting field types and injection annotations.
     *
     * @param ctModel Spoon model to inspect
     * @param model architecture model to update
     */
    public void extract(CtModel ctModel, ArchitectureModel model) {
        Map<ComponentId, dev.dominikbreu.spoonmcp.model.Component> componentsById = new HashMap<>();
        for (var c : model.components) componentsById.put(c.id, c);

        Map<String, List<ComponentId>> implementorsByInterface = buildImplementorIndex(ctModel, componentsById);

        for (CtType<?> type : ctModel.getAllTypes()) {
            ComponentId fromId = ComponentId.of(type.getQualifiedName());
            if (!componentsById.containsKey(fromId)) continue;

            for (CtField<?> field : type.getFields()) {
                extractFieldDependency(model, componentsById, implementorsByInterface, fromId, field);
            }
            for (CtMethod<?> method : type.getMethods()) {
                extractTypeRefDeps(model, componentsById, fromId, method.getType());
                for (CtParameter<?> param : method.getParameters()) {
                    extractTypeRefDeps(model, componentsById, fromId, param.getType());
                }
            }
        }

        dedup(model);
    }

    /**
     * Builds a map from interface qualified name to the list of known component IDs that directly implement it.
     * Used to resolve interface-typed injection fields to their concrete implementations.
     */
    private Map<String, List<ComponentId>> buildImplementorIndex(
            CtModel ctModel, Map<ComponentId, dev.dominikbreu.spoonmcp.model.Component> componentsById) {
        Map<String, List<ComponentId>> index = new HashMap<>();
        for (CtType<?> type : ctModel.getAllTypes()) {
            ComponentId id = ComponentId.of(type.getQualifiedName());
            if (!componentsById.containsKey(id)) continue;
            for (CtTypeReference<?> iface : type.getSuperInterfaces()) {
                index.computeIfAbsent(iface.getQualifiedName(), k -> new ArrayList<>())
                        .add(id);
            }
        }
        return index;
    }

    /**
     * Adds a type-usage dependency for each known component referenced by {@code typeRef},
     * including generic type arguments (e.g. {@code List<Order>} → {@code Order}).
     */
    private void extractTypeRefDeps(
            ArchitectureModel model,
            Map<ComponentId, dev.dominikbreu.spoonmcp.model.Component> componentsById,
            ComponentId fromId,
            CtTypeReference<?> typeRef) {
        if (typeRef == null) return;
        ComponentId toId = ComponentId.of(typeRef.getQualifiedName());
        if (componentsById.containsKey(toId) && !fromId.equals(toId)) {
            addDep(model, fromId, toId, "type-usage", "method-signature", 0.5);
        }
        for (CtTypeReference<?> arg : typeRef.getActualTypeArguments()) {
            extractTypeRefDeps(model, componentsById, fromId, arg);
        }
    }

    private void extractFieldDependency(
            ArchitectureModel model,
            Map<ComponentId, dev.dominikbreu.spoonmcp.model.Component> componentsById,
            Map<String, List<ComponentId>> implementorsByInterface,
            ComponentId fromId,
            CtField<?> field) {
        boolean isInjected = hasAnn(field, INJECT_ANNOTATIONS) || hasAnn(field, PERSISTENCE_CONTEXT);
        String targetQN = field.getType().getQualifiedName();
        ComponentId toId = ComponentId.of(targetQN);

        if (!componentsById.containsKey(toId)) {
            // Field type is likely an interface — resolve to its single known implementor.
            List<ComponentId> implementors = implementorsByInterface.getOrDefault(targetQN, List.of());
            if (implementors.size() != 1) {
                return; // unknown type or ambiguous (multiple implementations)
            }
            toId = implementors.get(0);
            // private final without @Autowired is constructor injection — treat as injected
            if (!isInjected && field.isFinal()) {
                isInjected = true;
            }
        }

        if (fromId.equals(toId)) return;

        double confidence = evidenceScorer.score(componentsById.get(fromId), componentsById.get(toId), isInjected);
        if (isInjected) {
            addDep(model, fromId, toId, "injection", "annotation", confidence);
        } else {
            addDep(model, fromId, toId, "field-reference", "type-relation", confidence);
        }
    }

    private void addDep(
            ArchitectureModel model, ComponentId from, ComponentId to, String kind, String derived, double conf) {
        dev.dominikbreu.spoonmcp.model.ids.DependencyId id =
                dev.dominikbreu.spoonmcp.model.ids.DependencyId.of(from, to);
        for (Dependency d : model.dependencies) {
            if (id.equals(d.id)) return;
        }
        Dependency dep = new Dependency();
        dep.id = id;
        dep.fromId = from;
        dep.toId = to;
        dep.kind = kind;
        dep.derivedFrom = derived;
        dep.confidence = conf;
        model.dependencies.add(dep);
    }

    private boolean hasAnn(CtElement element, Set<String> names) {
        Set<String> sn = names.stream()
                .map(n -> n.substring(n.lastIndexOf('.') + 1))
                .collect(java.util.stream.Collectors.toSet());
        return element.getAnnotations().stream()
                .anyMatch(a -> names.contains(a.getAnnotationType().getQualifiedName())
                        || sn.contains(a.getAnnotationType().getSimpleName()));
    }

    private void dedup(ArchitectureModel model) {
        Map<dev.dominikbreu.spoonmcp.model.ids.DependencyId, Dependency> byId = new LinkedHashMap<>();
        for (Dependency d : model.dependencies) byId.put(d.id, d);
        model.dependencies.clear();
        model.dependencies.addAll(byId.values());
    }
}
