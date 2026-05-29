package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    public SourceType type(String qualifiedName) {
        return typesByQualifiedName.get(qualifiedName);
    }

    public SourceMethod method(SourceFactId methodId) {
        return methodsById.get(methodId);
    }

    public List<SourceType> types() {
        return List.copyOf(typesByQualifiedName.values());
    }

    public List<SourceMethod> methods(SourceFactId typeId) {
        return methodsByTypeId.getOrDefault(typeId, List.of());
    }

    public List<SourceField> fields(SourceFactId typeId) {
        return fieldsByTypeId.getOrDefault(typeId, List.of());
    }

    public List<SourceAnnotation> annotations(SourceFactId ownerId) {
        return annotationsByOwnerId.getOrDefault(ownerId, List.of());
    }

    public List<SourceInjectionPoint> injectionPoints(SourceFactId typeId) {
        return injectionPointsByTypeId.getOrDefault(typeId, List.of());
    }

    public List<SourceInvocation> invocations(SourceFactId methodId) {
        return invocationsByMethodId.getOrDefault(methodId, List.of());
    }

    public List<SourceAssignment> assignments(SourceFactId methodId) {
        return assignmentsByMethodId.getOrDefault(methodId, List.of());
    }

    public List<SourceReturn> returns(SourceFactId methodId) {
        return returnsByMethodId.getOrDefault(methodId, List.of());
    }

    public List<SourceType> implementations(String qualifiedName) {
        return implementationsByQualifiedName.getOrDefault(qualifiedName, List.of());
    }

    public int typeCount() {
        return typesByQualifiedName.size();
    }

    public int methodCount() {
        return methodsById.size();
    }

    public int fieldCount() {
        return fieldsByTypeId.values().stream().mapToInt(List::size).sum();
    }

    public int invocationCount() {
        return invocationsByMethodId.values().stream().mapToInt(List::size).sum();
    }

    public int assignmentCount() {
        return assignmentsByMethodId.values().stream().mapToInt(List::size).sum();
    }

    public int returnCount() {
        return returnsByMethodId.values().stream().mapToInt(List::size).sum();
    }

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
