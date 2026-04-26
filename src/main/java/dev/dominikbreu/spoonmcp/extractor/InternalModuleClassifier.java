package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.AppEntry;
import spoon.reflect.declaration.CtType;

import java.util.Collection;
import java.util.Set;

/**
 * Classifies an embedded JAR module inside a WAR as either
 * "internal_module" (own domain logic) or "technical_library" (helpers/DTOs/adapters).
 *
 * Applies heuristics from the architecture requirements:
 * a module with architectural annotations (EJB, CDI, JAX-RS, JPA, Messaging)
 * and distinct package structure is treated as an internal_module;
 * one that is purely DTOs, utilities, or base classes is a technical_library.
 */
public class InternalModuleClassifier {

    private static final Set<String> ARCHITECTURAL_ANNOTATION_SIMPLE_NAMES = Set.of(
        "Stateless", "Stateful", "Singleton", "MessageDriven",
        "ApplicationScoped", "RequestScoped", "SessionScoped",
        "Path",
        "Entity", "MappedSuperclass",
        "Scheduled"
    );

    /** Creates a classifier using the built-in WAR child-module heuristics. */
    public InternalModuleClassifier() {}

    /**
     * Classifies a WAR child module as domain logic or technical support code.
     *
     * @param types module source types
     * @param app application entry being classified
     * @return {@code internal_module} or {@code technical_library}
     */
    public String classify(Collection<CtType<?>> types, AppEntry app) {
        int score = 0;

        // +2 for each type carrying an architectural annotation
        long archTypes = types.stream()
            .filter(t -> t.getAnnotations().stream()
                .anyMatch(a -> ARCHITECTURAL_ANNOTATION_SIMPLE_NAMES
                    .contains(a.getAnnotationType().getSimpleName())))
            .count();
        if (archTypes > 0) score += 2;
        if (archTypes >= 3) score += 1; // many annotated types → clearly domain logic

        // +1 for distinct package depth (≥3 segments suggests real structure)
        long deepPackages = types.stream()
            .map(t -> t.getPackage() != null ? t.getPackage().getQualifiedName() : "")
            .filter(p -> p.chars().filter(c -> c == '.').count() >= 2)
            .distinct()
            .count();
        if (deepPackages >= 2) score += 1;

        // -1 if name suggests utility/DTO/API nature
        String nameLower = app.name.toLowerCase();
        if (nameLower.contains("util") || nameLower.contains("dto")
                || nameLower.contains("common") || nameLower.contains("api")
                || nameLower.contains("base") || nameLower.contains("shared")) {
            score -= 1;
        }

        // +1 if name suggests domain/business module
        if (nameLower.contains("service") || nameLower.contains("domain")
                || nameLower.contains("business") || nameLower.contains("core")
                || nameLower.contains("impl")) {
            score += 1;
        }

        return score >= 2 ? "internal_module" : "technical_library";
    }
}
