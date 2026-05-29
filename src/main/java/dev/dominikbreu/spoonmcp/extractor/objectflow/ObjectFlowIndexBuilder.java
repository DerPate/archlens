package dev.dominikbreu.spoonmcp.extractor.objectflow;

import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceFactIndex;
import dev.dominikbreu.spoonmcp.extractor.sourcefacts.SourceType;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Builds the object-flow index from source and architecture metadata.
 */
public class ObjectFlowIndexBuilder {

    private static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("dev.dominikbreu.spoonmcp");
    }

    public ObjectFlowIndex build(CtModel ctModel, ArchitectureModel architecture) {
        return build(ctModel, architecture, null);
    }

    public ObjectFlowIndex build(CtModel ctModel, ArchitectureModel architecture, SourceFactIndex sourceFacts) {
        Span span = tracer().spanBuilder("objectflow.build").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<String, Component> componentByQualifiedName = componentByQualifiedName(architecture);
            span.setAttribute("components", (long) componentByQualifiedName.size());

            Map<String, ObjectFlowIndex.TypeFact> types = new LinkedHashMap<>();
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations = new LinkedHashMap<>();
            Map<String, CtType<?>> projectTypeByQualifiedName = new LinkedHashMap<>();

            List<CtType<?>> modelTypes = ctModel.getAllTypes().stream()
                    .sorted(Comparator.comparing(CtType::getQualifiedName))
                    .toList();
            for (CtType<?> type : modelTypes) {
                projectTypeByQualifiedName.put(type.getQualifiedName(), type);
            }
            span.setAttribute("model-types", (long) modelTypes.size());

            List<CtType<?>> projectTypes = modelTypes.stream()
                    .filter(type -> componentByQualifiedName.containsKey(type.getQualifiedName()))
                    .toList();
            span.setAttribute("project-types", (long) projectTypes.size());

            if (sourceFacts == null) {
                indexTypes(projectTypes, componentByQualifiedName, types);
                indexImplementations(projectTypes, projectTypeByQualifiedName, types, implementations);
            } else {
                indexTypes(sourceFacts, componentByQualifiedName, types);
                indexImplementations(sourceFacts, types, implementations);
            }

            Map<CtInvocation<?>, List<ReceiverTarget>> receiverTargets =
                    receiverTargets(ctModel, projectTypes, types, implementations);
            ObjectFlowIndex index = new ObjectFlowIndex(types, implementations, receiverTargets);
            span.setAttribute("implementation-groups", (long) implementations.size());
            span.setAttribute("receiver-target-map-size", (long) receiverTargets.size());
            return index;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static void indexTypes(
            SourceFactIndex sourceFacts,
            Map<String, Component> componentByQualifiedName,
            Map<String, ObjectFlowIndex.TypeFact> types) {
        Span span = tracer().spanBuilder("objectflow.type-index").startSpan();
        try (Scope scope = span.makeCurrent()) {
            long projectTypes = 0;
            long abstractOrInterfaceCount = 0;
            for (SourceType sourceType : sourceFacts.types()) {
                Component component = componentByQualifiedName.get(sourceType.qualifiedName());
                if (component == null) continue;
                projectTypes++;
                boolean abstractOrInterface = sourceType.interfaceType() || sourceType.abstractType();
                if (abstractOrInterface) {
                    abstractOrInterfaceCount++;
                }
                ObjectFlowIndex.TypeFact typeFact = new ObjectFlowIndex.TypeFact(
                        sourceType.qualifiedName(), component.id.serialize(), abstractOrInterface);
                types.put(typeFact.qualifiedName(), typeFact);
            }
            span.setAttribute("project-types", projectTypes);
            span.setAttribute("indexed-types", (long) types.size());
            span.setAttribute("abstract-or-interface-types", abstractOrInterfaceCount);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static void indexTypes(
            List<CtType<?>> projectTypes,
            Map<String, Component> componentByQualifiedName,
            Map<String, ObjectFlowIndex.TypeFact> types) {
        Span span = tracer().spanBuilder("objectflow.type-index").startSpan();
        try (Scope scope = span.makeCurrent()) {
            long abstractOrInterfaceCount = 0;
            for (CtType<?> type : projectTypes) {
                Component component = componentByQualifiedName.get(type.getQualifiedName());
                boolean abstractOrInterface = type.isInterface() || type.hasModifier(ModifierKind.ABSTRACT);
                if (abstractOrInterface) {
                    abstractOrInterfaceCount++;
                }
                ObjectFlowIndex.TypeFact typeFact = new ObjectFlowIndex.TypeFact(
                        type.getQualifiedName(), component.id.serialize(), abstractOrInterface);
                types.put(typeFact.qualifiedName(), typeFact);
            }
            span.setAttribute("project-types", (long) projectTypes.size());
            span.setAttribute("indexed-types", (long) types.size());
            span.setAttribute("abstract-or-interface-types", abstractOrInterfaceCount);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static void indexImplementations(
            List<CtType<?>> projectTypes,
            Map<String, CtType<?>> projectTypeByQualifiedName,
            Map<String, ObjectFlowIndex.TypeFact> types,
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations) {
        Span span = tracer().spanBuilder("objectflow.implementation-index").startSpan();
        try (Scope scope = span.makeCurrent()) {
            long concreteTypes = 0;
            long supertypeEdges = 0;
            long implementationLinks = 0;
            long duplicateImplementationLinks = 0;
            for (CtType<?> type : projectTypes) {
                ObjectFlowIndex.TypeFact concreteType = types.get(type.getQualifiedName());
                if (concreteType == null || concreteType.abstractOrInterface()) {
                    continue;
                }
                concreteTypes++;
                for (String supertype : supertypeClosure(type, projectTypeByQualifiedName)) {
                    supertypeEdges++;
                    if (registerImplementation(implementations, supertype, concreteType)) {
                        implementationLinks++;
                    } else {
                        duplicateImplementationLinks++;
                    }
                }
            }
            span.setAttribute("concrete-types", concreteTypes);
            span.setAttribute("supertype-edges", supertypeEdges);
            span.setAttribute("implementation-groups", (long) implementations.size());
            span.setAttribute("implementation-links", implementationLinks);
            span.setAttribute("duplicate-implementation-links", duplicateImplementationLinks);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static void indexImplementations(
            SourceFactIndex sourceFacts,
            Map<String, ObjectFlowIndex.TypeFact> types,
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations) {
        Span span = tracer().spanBuilder("objectflow.implementation-index").startSpan();
        try (Scope scope = span.makeCurrent()) {
            long implementationLinks = 0;
            long duplicateImplementationLinks = 0;
            long supertypeEdges = 0;
            for (SourceType supertype : sourceFacts.types()) {
                for (SourceType implementation : sourceFacts.implementations(supertype.qualifiedName())) {
                    supertypeEdges++;
                    ObjectFlowIndex.TypeFact concreteType = types.get(implementation.qualifiedName());
                    if (concreteType == null) continue;
                    if (registerImplementation(implementations, supertype.qualifiedName(), concreteType)) {
                        implementationLinks++;
                    } else {
                        duplicateImplementationLinks++;
                    }
                }
            }
            span.setAttribute(
                    "concrete-types",
                    types.values().stream()
                            .filter(t -> !t.abstractOrInterface())
                            .count());
            span.setAttribute("supertype-edges", supertypeEdges);
            span.setAttribute("implementation-groups", (long) implementations.size());
            span.setAttribute("implementation-links", implementationLinks);
            span.setAttribute("duplicate-implementation-links", duplicateImplementationLinks);
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static Map<CtInvocation<?>, List<ReceiverTarget>> receiverTargets(
            CtModel ctModel,
            List<CtType<?>> projectTypes,
            Map<String, ObjectFlowIndex.TypeFact> types,
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations) {
        Span span = tracer().spanBuilder("objectflow.receiver-targets").startSpan();
        try (Scope scope = span.makeCurrent()) {
            ObjectFlowIndex typeIndex = new ObjectFlowIndex(types, implementations);
            Map<CtInvocation<?>, List<ReceiverTarget>> targets = new IdentityHashMap<>();
            ReceiverResolutionStats stats = new ReceiverResolutionStats();
            List<CtInvocation<?>> invocations = ctModel.getElements(new TypeFilter<>(CtInvocation.class));
            span.setAttribute("executable-bodies", (long)
                    ctModel.getElements(new TypeFilter<>(CtExecutable.class)).size());
            span.setAttribute("invocations", (long) invocations.size());
            for (CtInvocation<?> invocation : invocations) {
                ReceiverResolution resolved = resolveInvocation(invocation, projectTypes, typeIndex);
                stats.record(resolved);
                if (!resolved.targets().isEmpty()) {
                    targets.put(invocation, resolved.targets());
                }
            }
            stats.apply(span);
            span.setAttribute("receiver-target-map-size", (long) targets.size());
            return targets;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static ReceiverResolution resolveInvocation(
            CtInvocation<?> invocation, List<CtType<?>> projectTypes, ObjectFlowIndex typeIndex) {
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return ReceiverResolution.unresolved();
        }
        String methodName = invocation.getExecutable().getSimpleName();

        if (target instanceof CtArrayRead<?> arrayRead) {
            String arrayName = variableName(arrayRead.getTarget());
            if (arrayName != null) {
                return ReceiverResolution.of(
                        resolveArrayField(invocation, arrayName, methodName, typeIndex),
                        ReceiverResolutionPath.ARRAY_FIELD);
            }
        }

        if (target instanceof CtInvocation<?> targetInvocation) {
            List<ReceiverTarget> accessorTargets =
                    resolveAccessorTarget(targetInvocation, projectTypes, methodName, typeIndex);
            if (!accessorTargets.isEmpty()) {
                return ReceiverResolution.of(accessorTargets, ReceiverResolutionPath.ACCESSOR);
            }
        }

        String variableName = variableName(target);
        if (variableName == null) {
            return ReceiverResolution.unresolved();
        }

        List<ReceiverTarget> localTargets =
                ObjectFlowMethodAnalyzer.resolveLocalVariableTargets(invocation, variableName, methodName);
        if (!localTargets.isEmpty()) {
            return ReceiverResolution.of(localTargets, ReceiverResolutionPath.LOCAL);
        }

        List<ReceiverTarget> fieldTargets = resolveField(invocation, variableName, methodName, typeIndex);
        if (!fieldTargets.isEmpty()) {
            return ReceiverResolution.of(fieldTargets, ReceiverResolutionPath.FIELD);
        }

        CtVariable<?> variable = variable(target);
        if (variable != null && variable.getType() != null) {
            return ReceiverResolution.of(
                    typeIndex.expandDeclaredType(elementOrDeclaredType(variable.getType()), methodName),
                    ReceiverResolutionPath.DECLARED_VARIABLE);
        }
        if (target instanceof CtVariableRead<?> variableRead
                && variableRead.getVariable() != null
                && variableRead.getVariable().getType() != null) {
            return ReceiverResolution.of(
                    typeIndex.expandDeclaredType(
                            elementOrDeclaredType(variableRead.getVariable().getType()), methodName),
                    ReceiverResolutionPath.DECLARED_VARIABLE);
        }
        return ReceiverResolution.unresolved();
    }

    private static List<ReceiverTarget> resolveField(
            CtInvocation<?> invocation, String fieldName, String methodName, ObjectFlowIndex typeIndex) {
        CtType<?> owner = invocation.getParent(CtType.class);
        if (owner == null) {
            return List.of();
        }
        CtField<?> field = field(owner, fieldName);
        if (field == null) {
            return List.of();
        }
        String allocatedType = constructorType(field.getDefaultExpression());
        if (allocatedType != null) {
            return targetFor(allocatedType, methodName, ObjectFlowEvidence.CONSTRUCTOR_ASSIGNMENT);
        }
        if (field.getType() != null) {
            return typeIndex.expandDeclaredType(elementOrDeclaredType(field.getType()), methodName);
        }
        return List.of();
    }

    private static List<ReceiverTarget> resolveArrayField(
            CtInvocation<?> invocation, String fieldName, String methodName, ObjectFlowIndex typeIndex) {
        CtType<?> owner = invocation.getParent(CtType.class);
        if (owner == null) {
            return List.of();
        }
        CtField<?> field = field(owner, fieldName);
        if (field == null) {
            return List.of();
        }
        CtExpression<?> defaultExpression = field.getDefaultExpression();
        if (defaultExpression instanceof CtNewArray<?> newArray) {
            List<ReceiverTarget> targets = new ArrayList<>();
            for (CtExpression<?> element : newArray.getElements()) {
                String allocatedType = constructorType(element);
                if (allocatedType != null) {
                    targets.addAll(targetFor(allocatedType, methodName, ObjectFlowEvidence.ARRAY_ELEMENT_ALLOCATION));
                }
            }
            if (!targets.isEmpty()) {
                return targets;
            }
        }
        if (field.getType() != null) {
            return typeIndex.expandDeclaredType(elementOrDeclaredType(field.getType()), methodName);
        }
        return List.of();
    }

    // Java API method names that are too generic to reliably identify a project-component accessor.
    // When a method chain passes through one of these (e.g. Optional.get(), Stream.findFirst()),
    // the project-type name scan would pick whichever component happens to have a method by that
    // name — producing spurious accessor fallback edges (Lombok-blindness false positives).
    private static final Set<String> GENERIC_JAVA_API_METHODS = Set.of(
            // java.util.Optional
            "get",
            "orElse",
            "orElseGet",
            "orElseThrow",
            "isPresent",
            "isEmpty",
            // java.util.stream.Stream / Collectors
            "stream",
            "findFirst",
            "findAny",
            "filter",
            "map",
            "flatMap",
            "collect",
            "toList",
            "count",
            "sorted",
            "distinct",
            "limit",
            "skip",
            "anyMatch",
            "allMatch",
            "noneMatch",
            "min",
            "max",
            "reduce",
            "forEach",
            // java.util.Collection / List / Iterator
            "size",
            "iterator",
            "listIterator",
            "first",
            "last",
            "peek",
            "poll",
            // java.util.Map
            "values",
            "keySet",
            "entrySet");

    private static final Set<String> COLLECTION_STATE_ACCESS_METHODS = Set.of(
            "put",
            "putAll",
            "putIfAbsent",
            "compute",
            "computeIfAbsent",
            "computeIfPresent",
            "merge",
            "remove",
            "replace",
            "clear",
            "add",
            "addAll",
            "offer",
            "poll",
            "get",
            "containsKey",
            "containsValue",
            "values",
            "keySet",
            "entrySet",
            "size",
            "isEmpty",
            "contains",
            "iterator",
            "stream",
            "forEach");

    private static List<ReceiverTarget> resolveAccessorTarget(
            CtInvocation<?> targetInvocation,
            List<CtType<?>> projectTypes,
            String outerMethodName,
            ObjectFlowIndex typeIndex) {
        if (targetInvocation.getType() != null) {
            List<ReceiverTarget> declaredTargets =
                    typeIndex.expandDeclaredType(elementOrDeclaredType(targetInvocation.getType()), outerMethodName);
            if (!declaredTargets.isEmpty()) {
                return declaredTargets.stream()
                        .map(target -> new ReceiverTarget(
                                target.componentId(),
                                outerMethodName,
                                ObjectFlowEvidence.ACCESSOR_RETURN,
                                ObjectFlowEvidence.ACCESSOR_RETURN.confidence(),
                                target.expansionCapped()))
                        .toList();
            }
            if (!COLLECTION_STATE_ACCESS_METHODS.contains(outerMethodName)) {
                return List.of();
            }
        }
        String accessorName = targetInvocation.getExecutable().getSimpleName();
        if (GENERIC_JAVA_API_METHODS.contains(accessorName)) {
            return List.of();
        }
        ObjectFlowEvidence fallbackEvidence;
        if (COLLECTION_STATE_ACCESS_METHODS.contains(outerMethodName)) {
            fallbackEvidence = ObjectFlowEvidence.ACCESSOR_STATE_OWNER;
        } else {
            fallbackEvidence = ObjectFlowEvidence.ACCESSOR_NAME_FALLBACK;
        }
        return projectTypes.stream()
                .filter(type -> hasMethod(type, accessorName))
                .findFirst()
                .map(type -> targetFor(type.getQualifiedName(), outerMethodName, fallbackEvidence))
                .orElse(List.of());
    }

    private static String elementOrDeclaredType(CtTypeReference<?> type) {
        if (type == null) return "";
        if (type instanceof spoon.reflect.reference.CtArrayTypeReference<?> arrayType) {
            return arrayType.getComponentType().getQualifiedName();
        }
        if (!type.getActualTypeArguments().isEmpty()) {
            return type.getActualTypeArguments().get(0).getQualifiedName();
        }
        return type.getQualifiedName();
    }

    private static CtField<?> field(CtType<?> owner, String fieldName) {
        CtType<?> current = owner;
        while (current != null) {
            for (CtField<?> field : current.getFields()) {
                if (fieldName.equals(field.getSimpleName())) {
                    return field;
                }
            }
            CtTypeReference<?> superclass = current.getSuperclass();
            current = superclass != null ? superclass.getTypeDeclaration() : null;
        }
        return null;
    }

    private static boolean hasMethod(CtType<?> type, String methodName) {
        for (CtMethod<?> method : type.getMethods()) {
            if (methodName.equals(method.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static String constructorType(CtExpression<?> expression) {
        if (expression instanceof CtConstructorCall<?> constructorCall && constructorCall.getType() != null) {
            return constructorCall.getType().getQualifiedName();
        }
        return null;
    }

    private static List<ReceiverTarget> targetFor(
            String qualifiedName, String methodName, ObjectFlowEvidence evidence) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return List.of();
        }
        return List.of(new ReceiverTarget(qualifiedName, methodName, evidence, evidence.confidence(), false));
    }

    private static String variableName(CtExpression<?> expression) {
        if (expression instanceof CtVariableRead<?> variableRead && variableRead.getVariable() != null) {
            return variableRead.getVariable().getSimpleName();
        }
        CtVariable<?> variable = variable(expression);
        if (variable != null) {
            return variable.getSimpleName();
        } else {
            return null;
        }
    }

    private static CtVariable<?> variable(CtExpression<?> expression) {
        if (expression instanceof CtVariableRead<?> variableRead) {
            var reference = variableRead.getVariable();
            if (reference == null) {
                return null;
            }
            try {
                CtVariable<?> declaration = reference.getDeclaration();
                if (declaration != null) {
                    return declaration;
                }
            } catch (RuntimeException ignored) {
                // Spoon can fail declaration lookup for unresolved no-classpath locals.
            }
        }
        return null;
    }

    private static Map<String, Component> componentByQualifiedName(ArchitectureModel architecture) {
        Span span = tracer().spanBuilder("objectflow.component-index").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<String, Component> components = new LinkedHashMap<>();
            for (Component component : architecture.components) {
                if (component.qualifiedName != null && !component.qualifiedName.isBlank()) {
                    components.putIfAbsent(component.qualifiedName, component);
                }
            }
            span.setAttribute("architecture-components", (long) architecture.components.size());
            span.setAttribute("qualified-components", (long) components.size());
            return components;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private static List<String> supertypeClosure(CtType<?> type, Map<String, CtType<?>> projectTypeByQualifiedName) {
        Set<String> closure = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        collectSupertype(type.getSuperclass(), type.getQualifiedName(), projectTypeByQualifiedName, closure, visited);
        for (CtTypeReference<?> superInterface : sortedTypeReferences(type.getSuperInterfaces())) {
            collectSupertype(superInterface, type.getQualifiedName(), projectTypeByQualifiedName, closure, visited);
        }
        return List.copyOf(closure);
    }

    private static void collectSupertype(
            CtTypeReference<?> supertype,
            String concreteQualifiedName,
            Map<String, CtType<?>> projectTypeByQualifiedName,
            Set<String> closure,
            Set<String> visited) {
        if (supertype == null) {
            return;
        }
        String qualifiedName = supertype.getQualifiedName();
        if (qualifiedName == null || qualifiedName.isBlank() || !visited.add(qualifiedName)) {
            return;
        }
        if (!qualifiedName.equals(concreteQualifiedName)) {
            closure.add(qualifiedName);
        }

        CtType<?> declaration = projectTypeByQualifiedName.get(qualifiedName);
        if (declaration == null) {
            return;
        }
        collectSupertype(
                declaration.getSuperclass(), concreteQualifiedName, projectTypeByQualifiedName, closure, visited);
        for (CtTypeReference<?> superInterface : sortedTypeReferences(declaration.getSuperInterfaces())) {
            collectSupertype(superInterface, concreteQualifiedName, projectTypeByQualifiedName, closure, visited);
        }
    }

    private static List<CtTypeReference<?>> sortedTypeReferences(Set<CtTypeReference<?>> typeReferences) {
        return typeReferences.stream()
                .sorted(Comparator.comparing(
                        CtTypeReference::getQualifiedName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static boolean registerImplementation(
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations,
            String declaredQualifiedName,
            ObjectFlowIndex.TypeFact concreteType) {
        if (declaredQualifiedName == null || declaredQualifiedName.equals(concreteType.qualifiedName())) {
            return false;
        }
        List<ObjectFlowIndex.TypeFact> typeImplementations =
                implementations.computeIfAbsent(declaredQualifiedName, ignored -> new ArrayList<>());
        if (!typeImplementations.contains(concreteType)) {
            typeImplementations.add(concreteType);
            return true;
        }
        return false;
    }

    private record ReceiverResolution(List<ReceiverTarget> targets, ReceiverResolutionPath path) {
        private static ReceiverResolution of(List<ReceiverTarget> targets, ReceiverResolutionPath path) {
            if (targets.isEmpty()) {
                return unresolved();
            }
            return new ReceiverResolution(targets, path);
        }

        private static ReceiverResolution unresolved() {
            return new ReceiverResolution(List.of(), ReceiverResolutionPath.UNRESOLVED);
        }
    }

    private enum ReceiverResolutionPath {
        ARRAY_FIELD,
        ACCESSOR,
        LOCAL,
        FIELD,
        DECLARED_VARIABLE,
        UNRESOLVED
    }

    private static final class ReceiverResolutionStats {
        private long unresolvedInvocations;
        private long resolvedInvocations;
        private long receiverTargets;
        private long fieldTargets;
        private long constructorAssignmentTargets;
        private long localAssignmentTargets;
        private long collectionElementTargets;
        private long arrayElementTargets;
        private long accessorTargets;
        private long accessorNameFallbackTargets;
        private long declaredTypeTargets;
        private long declaredInterfaceOnlyTargets;
        private long polymorphicTargets;

        private void record(ReceiverResolution resolved) {
            if (resolved.targets().isEmpty()) {
                unresolvedInvocations++;
                return;
            }
            resolvedInvocations++;
            receiverTargets += resolved.targets().size();
            if (resolved.path() == ReceiverResolutionPath.FIELD
                    || resolved.path() == ReceiverResolutionPath.ARRAY_FIELD) {
                fieldTargets += resolved.targets().size();
            }
            for (ReceiverTarget target : resolved.targets()) {
                switch (target.evidence()) {
                    case CONSTRUCTOR_ASSIGNMENT -> constructorAssignmentTargets++;
                    case LOCAL_ASSIGNMENT -> localAssignmentTargets++;
                    case COLLECTION_ELEMENT_ALLOCATION -> collectionElementTargets++;
                    case ARRAY_ELEMENT_ALLOCATION -> arrayElementTargets++;
                    case ACCESSOR_RETURN, ACCESSOR_STATE_OWNER -> accessorTargets++;
                    case ACCESSOR_NAME_FALLBACK -> accessorNameFallbackTargets++;
                    case DECLARED_FIELD_TYPE, GENERIC_ELEMENT_TYPE -> declaredTypeTargets++;
                    case DECLARED_INTERFACE_ONLY -> declaredInterfaceOnlyTargets++;
                    case SMALL_POLYMORPHIC_EXPANSION -> polymorphicTargets++;
                }
            }
        }

        private void apply(Span span) {
            span.setAttribute("resolved-invocations", resolvedInvocations);
            span.setAttribute("unresolved-invocations", unresolvedInvocations);
            span.setAttribute("receiver-targets", receiverTargets);
            span.setAttribute("constructor-assignment-targets", constructorAssignmentTargets);
            span.setAttribute("field-targets", fieldTargets);
            span.setAttribute("local-assignment-targets", localAssignmentTargets);
            span.setAttribute("collection-element-targets", collectionElementTargets);
            span.setAttribute("array-element-targets", arrayElementTargets);
            span.setAttribute("accessor-targets", accessorTargets);
            span.setAttribute("accessor-name-fallback-targets", accessorNameFallbackTargets);
            span.setAttribute("declared-type-targets", declaredTypeTargets);
            span.setAttribute("declared-interface-only-targets", declaredInterfaceOnlyTargets);
            span.setAttribute("polymorphic-targets", polymorphicTargets);
        }
    }
}
