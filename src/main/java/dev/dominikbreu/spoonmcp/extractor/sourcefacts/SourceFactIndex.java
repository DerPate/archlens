package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Read-only lookup index over extracted source facts (types, methods, fields, annotations, etc.). */
public final class SourceFactIndex {
    private final Map<String, SourceType> typesByQualifiedName;
    private final Map<SourceFactId, SourceMethod> methodsById;
    private final Map<SourceFactId, List<SourceMethod>> methodsByTypeId;
    private final Map<SourceFactId, List<SourceField>> fieldsByTypeId;
    private final Map<SourceFactId, List<SourceAnnotation>> annotationsByOwnerId;
    private final Map<SourceFactId, List<SourceInjectionPoint>> injectionPointsByTypeId;
    private final Map<SourceFactId, List<SourceInvocation>> invocationsByMethodId;
    private final Map<SourceFactId, List<SourceAssignment>> assignmentsByMethodId;
    private final Map<SourceFactId, List<SourceReturn>> returnsByMethodId;
    private final Map<String, List<SourceType>> implementationsByQualifiedName;

    /**
     * Builds a source fact index from the given fact collections.
     *
     * @param types all extracted type facts
     * @param methods all extracted method facts
     * @param fields all extracted field facts
     * @param annotations all extracted annotation facts
     * @param injectionPoints all extracted injection point facts
     * @param invocations all extracted invocation facts
     * @param assignments all extracted assignment facts
     * @param returns all extracted return facts
     * @param implementationsByQualifiedName concrete implementations keyed by interface/supertype name
     */
    public SourceFactIndex(
            List<SourceType> types,
            List<SourceMethod> methods,
            List<SourceField> fields,
            List<SourceAnnotation> annotations,
            List<SourceInjectionPoint> injectionPoints,
            List<SourceInvocation> invocations,
            List<SourceAssignment> assignments,
            List<SourceReturn> returns,
            Map<String, List<SourceType>> implementationsByQualifiedName) {
        this.typesByQualifiedName = new LinkedHashMap<>();
        this.methodsById = new LinkedHashMap<>();
        this.methodsByTypeId = groupBy(methods, SourceMethod::typeId);
        this.fieldsByTypeId = groupBy(fields, SourceField::typeId);
        this.annotationsByOwnerId = groupBy(annotations, SourceAnnotation::ownerId);
        this.injectionPointsByTypeId = groupBy(injectionPoints, SourceInjectionPoint::ownerTypeId);
        this.invocationsByMethodId = groupBy(invocations, SourceInvocation::enclosingMethodId);
        this.assignmentsByMethodId = groupBy(assignments, SourceAssignment::enclosingMethodId);
        this.returnsByMethodId = groupBy(returns, SourceReturn::enclosingMethodId);
        for (SourceType type : types) {
            typesByQualifiedName.put(type.qualifiedName(), type);
        }
        for (SourceMethod method : methods) {
            methodsById.put(method.id(), method);
        }
        this.implementationsByQualifiedName = copyMap(implementationsByQualifiedName);
    }

    /**
     * Returns the source type for the given qualified name, or {@code null} if not found.
     *
     * @param qualifiedName the fully-qualified type name
     * @return the source type, or {@code null}
     */
    public SourceType type(String qualifiedName) {
        return typesByQualifiedName.get(qualifiedName);
    }

    /**
     * Returns the source method for the given method id, or {@code null} if not found.
     *
     * @param methodId the method fact id
     * @return the source method, or {@code null}
     */
    public SourceMethod method(SourceFactId methodId) {
        return methodsById.get(methodId);
    }

    /**
     * Returns all indexed source types.
     *
     * @return an unmodifiable list of all types
     */
    public List<SourceType> types() {
        return List.copyOf(typesByQualifiedName.values());
    }

    /**
     * Returns all methods declared on the given type.
     *
     * @param typeId the type fact id
     * @return the methods, or an empty list if none
     */
    public List<SourceMethod> methods(SourceFactId typeId) {
        return methodsByTypeId.getOrDefault(typeId, List.of());
    }

    /**
     * Returns all fields declared on the given type.
     *
     * @param typeId the type fact id
     * @return the fields, or an empty list if none
     */
    public List<SourceField> fields(SourceFactId typeId) {
        return fieldsByTypeId.getOrDefault(typeId, List.of());
    }

    /**
     * Returns all annotations on the given owner (type, method, or field).
     *
     * @param ownerId the owner fact id
     * @return the annotations, or an empty list if none
     */
    public List<SourceAnnotation> annotations(SourceFactId ownerId) {
        return annotationsByOwnerId.getOrDefault(ownerId, List.of());
    }

    /**
     * Returns all injection points declared on the given type.
     *
     * @param typeId the type fact id
     * @return the injection points, or an empty list if none
     */
    public List<SourceInjectionPoint> injectionPoints(SourceFactId typeId) {
        return injectionPointsByTypeId.getOrDefault(typeId, List.of());
    }

    /**
     * Returns all invocations within the given method.
     *
     * @param methodId the method fact id
     * @return the invocations, or an empty list if none
     */
    public List<SourceInvocation> invocations(SourceFactId methodId) {
        return invocationsByMethodId.getOrDefault(methodId, List.of());
    }

    /**
     * Returns all assignments within the given method.
     *
     * @param methodId the method fact id
     * @return the assignments, or an empty list if none
     */
    public List<SourceAssignment> assignments(SourceFactId methodId) {
        return assignmentsByMethodId.getOrDefault(methodId, List.of());
    }

    /**
     * Returns all return statements within the given method.
     *
     * @param methodId the method fact id
     * @return the return statements, or an empty list if none
     */
    public List<SourceReturn> returns(SourceFactId methodId) {
        return returnsByMethodId.getOrDefault(methodId, List.of());
    }

    /**
     * Returns all concrete implementations of the given type or interface.
     *
     * @param qualifiedName the fully-qualified interface or supertype name
     * @return the implementations, or an empty list if none
     */
    public List<SourceType> implementations(String qualifiedName) {
        return implementationsByQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    /**
     * Returns the total number of indexed types.
     *
     * @return the type count
     */
    public int typeCount() {
        return typesByQualifiedName.size();
    }

    /**
     * Returns the total number of indexed methods.
     *
     * @return the method count
     */
    public int methodCount() {
        return methodsById.size();
    }

    /**
     * Returns the total number of indexed fields across all types.
     *
     * @return the field count
     */
    public int fieldCount() {
        return fieldsByTypeId.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the total number of indexed invocations across all methods.
     *
     * @return the invocation count
     */
    public int invocationCount() {
        return invocationsByMethodId.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the total number of indexed assignments across all methods.
     *
     * @return the assignment count
     */
    public int assignmentCount() {
        return assignmentsByMethodId.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the total number of indexed return statements across all methods.
     *
     * @return the return count
     */
    public int returnCount() {
        return returnsByMethodId.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Returns the total number of indexed injection points across all types.
     *
     * @return the injection point count
     */
    public int injectionPointCount() {
        return injectionPointsByTypeId.values().stream().mapToInt(List::size).sum();
    }

    private static <T> Map<SourceFactId, List<T>> groupBy(List<T> values, Function<T, SourceFactId> keyFn) {
        Map<SourceFactId, List<T>> grouped = new LinkedHashMap<>();
        for (T value : values) {
            grouped.computeIfAbsent(keyFn.apply(value), ignored -> new ArrayList<>())
                    .add(value);
        }
        return copyMap(grouped);
    }

    private static <K, T> Map<K, List<T>> copyMap(Map<K, List<T>> source) {
        Map<K, List<T>> copy = new LinkedHashMap<>();
        for (Map.Entry<K, List<T>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
