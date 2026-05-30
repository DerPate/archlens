package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Dependency;
import java.util.*;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;

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
        Map<dev.dominikbreu.spoonmcp.model.ids.ComponentId, dev.dominikbreu.spoonmcp.model.Component> componentsById =
                new HashMap<>();
        for (var c : model.components) componentsById.put(c.id, c);

        for (CtType<?> type : ctModel.getAllTypes()) {
            dev.dominikbreu.spoonmcp.model.ids.ComponentId fromId =
                    dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(type.getQualifiedName());
            if (!componentsById.containsKey(fromId)) continue;

            for (CtField<?> field : type.getFields()) {
                extractFieldDependency(model, componentsById, fromId, field);
            }
        }

        dedup(model);
    }

    private void extractFieldDependency(
            ArchitectureModel model,
            Map<dev.dominikbreu.spoonmcp.model.ids.ComponentId, dev.dominikbreu.spoonmcp.model.Component> componentsById,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId fromId,
            CtField<?> field) {
        boolean isInjected = hasAnn(field, INJECT_ANNOTATIONS) || hasAnn(field, PERSISTENCE_CONTEXT);
        String targetQN = field.getType().getQualifiedName();
        dev.dominikbreu.spoonmcp.model.ids.ComponentId toId =
                dev.dominikbreu.spoonmcp.model.ids.ComponentId.of(targetQN);
        if (componentsById.containsKey(toId) && !fromId.equals(toId)) {
            double confidence =
                    evidenceScorer.score(componentsById.get(fromId), componentsById.get(toId), isInjected);
            if (isInjected) {
                addDep(model, fromId, toId, "injection", "annotation", confidence);
            } else {
                addDep(model, fromId, toId, "field-reference", "type-relation", confidence);
            }
        }
    }

    private void addDep(
            ArchitectureModel model,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId from,
            dev.dominikbreu.spoonmcp.model.ids.ComponentId to,
            String kind,
            String derived,
            double conf) {
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
        Map<String, Dependency> byId = new LinkedHashMap<>();
        for (Dependency d : model.dependencies) byId.put(d.id.serialize(), d);
        model.dependencies.clear();
        model.dependencies.addAll(byId.values());
    }
}
