package dev.dominikbreu.archlens.extractor;

import java.util.Objects;
import java.util.Set;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

/**
 * Recognizes persistence entities from the type itself rather than from where it is packaged.
 *
 * <p>Projects name the package holding their entities {@code model}, {@code bean}, {@code entity},
 * {@code domain}, or anything else, so package matching misses entities in most codebases. A type
 * counts as an entity when it — or any ancestor — carries a JPA/Hibernate entity annotation, or
 * when it extends one of the Panache base classes, which mark an entity without an annotation.
 */
public final class PersistenceEntityTypes {

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of(
            "javax.persistence.Entity",
            "jakarta.persistence.Entity",
            "javax.persistence.MappedSuperclass",
            "jakarta.persistence.MappedSuperclass",
            "javax.persistence.Embeddable",
            "jakarta.persistence.Embeddable",
            "org.hibernate.annotations.Entity");

    private static final Set<String> ENTITY_BASE_TYPES = Set.of(
            "io.quarkus.hibernate.orm.panache.PanacheEntity",
            "io.quarkus.hibernate.orm.panache.PanacheEntityBase",
            "io.quarkus.hibernate.reactive.panache.PanacheEntity",
            "io.quarkus.hibernate.reactive.panache.PanacheEntityBase",
            "io.quarkus.mongodb.panache.PanacheMongoEntity",
            "io.quarkus.mongodb.panache.PanacheMongoEntityBase");

    /**
     * Spoon runs in no-classpath mode, where an annotation or supertype outside the analyzed
     * sources keeps only its simple name. Matching those too is what the surrounding extractors
     * already do; without it most annotated entities go unrecognized.
     */
    private static final Set<String> ENTITY_ANNOTATION_SIMPLE_NAMES = simpleNames(ENTITY_ANNOTATIONS);

    private static final Set<String> ENTITY_BASE_SIMPLE_NAMES = simpleNames(ENTITY_BASE_TYPES);

    /** Bound on the superclass walk; guards against cyclic or pathological hierarchies. */
    private static final int MAX_SUPERCLASS_DEPTH = 16;

    private PersistenceEntityTypes() {}

    /**
     * Returns true when {@code type} is a persistence entity, including subclasses of an annotated
     * or Panache base class.
     *
     * @param type the type to classify, may be null
     * @return true when the type is a persistence entity
     */
    public static boolean isEntity(CtType<?> type) {
        CtType<?> current = type;
        for (int depth = 0; current != null && depth < MAX_SUPERCLASS_DEPTH; depth++) {
            if (hasEntityAnnotation(current)) {
                return true;
            }
            CtTypeReference<?> superclass = current.getSuperclass();
            if (superclass == null) {
                return false;
            }
            if (ENTITY_BASE_TYPES.contains(superclass.getQualifiedName())
                    || ENTITY_BASE_SIMPLE_NAMES.contains(superclass.getSimpleName())) {
                return true;
            }
            // Null in no-classpath mode when the supertype is outside the analyzed sources; the
            // Panache check above already covered the framework bases we can name.
            current = superclass.getTypeDeclaration();
        }
        return false;
    }

    private static boolean hasEntityAnnotation(CtType<?> type) {
        return type.getAnnotations().stream()
                .map(annotation -> annotation.getAnnotationType())
                .filter(Objects::nonNull)
                .anyMatch(annotationType -> ENTITY_ANNOTATIONS.contains(annotationType.getQualifiedName())
                        || ENTITY_ANNOTATION_SIMPLE_NAMES.contains(annotationType.getSimpleName()));
    }

    private static Set<String> simpleNames(Set<String> qualifiedNames) {
        return qualifiedNames.stream()
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
