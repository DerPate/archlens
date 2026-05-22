package dev.dominikbreu.spoonmcp.extractor.objectflow;

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
        Span span = tracer().spanBuilder("objectflow.build").startSpan();
        try (Scope scope = span.makeCurrent()) {
            Map<String, Component> componentByQualifiedName = componentByQualifiedName(architecture);
            Map<String, ObjectFlowIndex.TypeFact> types = new LinkedHashMap<>();
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations = new LinkedHashMap<>();
            Map<String, CtType<?>> projectTypeByQualifiedName = new LinkedHashMap<>();

            List<CtType<?>> modelTypes = ctModel.getAllTypes().stream()
                    .sorted(Comparator.comparing(CtType::getQualifiedName))
                    .toList();
            for (CtType<?> type : modelTypes) {
                projectTypeByQualifiedName.put(type.getQualifiedName(), type);
            }

            List<CtType<?>> projectTypes = modelTypes.stream()
                    .filter(type -> componentByQualifiedName.containsKey(type.getQualifiedName()))
                    .toList();

            for (CtType<?> type : projectTypes) {
                Component component = componentByQualifiedName.get(type.getQualifiedName());
                boolean abstractOrInterface = type.isInterface() || type.hasModifier(ModifierKind.ABSTRACT);
                ObjectFlowIndex.TypeFact typeFact =
                        new ObjectFlowIndex.TypeFact(type.getQualifiedName(), component.id, abstractOrInterface);
                types.put(typeFact.qualifiedName(), typeFact);
            }

            for (CtType<?> type : projectTypes) {
                ObjectFlowIndex.TypeFact concreteType = types.get(type.getQualifiedName());
                if (concreteType == null || concreteType.abstractOrInterface()) {
                    continue;
                }
                for (String supertype : supertypeClosure(type, projectTypeByQualifiedName)) {
                    registerImplementation(implementations, supertype, concreteType);
                }
            }

            ObjectFlowIndex index = new ObjectFlowIndex(
                    types,
                    implementations,
                    receiverTargets(ctModel, projectTypes, types, implementations));
            return index;
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
        ObjectFlowIndex typeIndex = new ObjectFlowIndex(types, implementations);
        Map<CtInvocation<?>, List<ReceiverTarget>> targets = new IdentityHashMap<>();
        for (CtInvocation<?> invocation : ctModel.getElements(new TypeFilter<>(CtInvocation.class))) {
            List<ReceiverTarget> resolved = resolveInvocation(invocation, projectTypes, typeIndex);
            if (!resolved.isEmpty()) {
                targets.put(invocation, resolved);
            }
        }
        return targets;
    }

    private static List<ReceiverTarget> resolveInvocation(
            CtInvocation<?> invocation, List<CtType<?>> projectTypes, ObjectFlowIndex typeIndex) {
        CtExpression<?> target = invocation.getTarget();
        if (target == null) {
            return List.of();
        }
        String methodName = invocation.getExecutable().getSimpleName();

        if (target instanceof CtArrayRead<?> arrayRead) {
            String arrayName = variableName(arrayRead.getTarget());
            if (arrayName != null) {
                return resolveArrayField(invocation, arrayName, methodName, typeIndex);
            }
        }

        if (target instanceof CtInvocation<?> targetInvocation) {
            List<ReceiverTarget> accessorTargets = resolveAccessorTarget(targetInvocation, projectTypes, methodName);
            if (!accessorTargets.isEmpty()) {
                return accessorTargets;
            }
        }

        String variableName = variableName(target);
        if (variableName == null) {
            return List.of();
        }

        List<ReceiverTarget> localTargets =
                ObjectFlowMethodAnalyzer.resolveLocalVariableTargets(invocation, variableName, methodName);
        if (!localTargets.isEmpty()) {
            return localTargets;
        }

        List<ReceiverTarget> fieldTargets = resolveField(invocation, variableName, methodName, typeIndex);
        if (!fieldTargets.isEmpty()) {
            return fieldTargets;
        }

        CtVariable<?> variable = variable(target);
        if (variable != null && variable.getType() != null) {
            return typeIndex.expandDeclaredType(elementOrDeclaredType(variable.getType()), methodName);
        }
        if (target instanceof CtVariableRead<?> variableRead
                && variableRead.getVariable() != null
                && variableRead.getVariable().getType() != null) {
            return typeIndex.expandDeclaredType(elementOrDeclaredType(variableRead.getVariable().getType()), methodName);
        }
        return List.of();
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

    private static List<ReceiverTarget> resolveAccessorTarget(
            CtInvocation<?> targetInvocation, List<CtType<?>> projectTypes, String outerMethodName) {
        String accessorName = targetInvocation.getExecutable().getSimpleName();
        return projectTypes.stream()
                .filter(type -> hasMethod(type, accessorName))
                .findFirst()
                .map(type -> targetFor(type.getQualifiedName(), outerMethodName, ObjectFlowEvidence.ACCESSOR_RETURN))
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
        return List.of(new ReceiverTarget(
                "comp:" + qualifiedName,
                methodName,
                evidence,
                evidence.confidence(),
                false));
    }

    private static String variableName(CtExpression<?> expression) {
        if (expression instanceof CtVariableRead<?> variableRead && variableRead.getVariable() != null) {
            return variableRead.getVariable().getSimpleName();
        }
        CtVariable<?> variable = variable(expression);
        return variable != null ? variable.getSimpleName() : null;
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
        Map<String, Component> components = new LinkedHashMap<>();
        for (Component component : architecture.components) {
            if (component.qualifiedName != null && !component.qualifiedName.isBlank()) {
                components.putIfAbsent(component.qualifiedName, component);
            }
        }
        return components;
    }

    private static List<String> supertypeClosure(
            CtType<?> type,
            Map<String, CtType<?>> projectTypeByQualifiedName) {
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
                declaration.getSuperclass(),
                concreteQualifiedName,
                projectTypeByQualifiedName,
                closure,
                visited);
        for (CtTypeReference<?> superInterface : sortedTypeReferences(declaration.getSuperInterfaces())) {
            collectSupertype(superInterface, concreteQualifiedName, projectTypeByQualifiedName, closure, visited);
        }
    }

    private static List<CtTypeReference<?>> sortedTypeReferences(Set<CtTypeReference<?>> typeReferences) {
        return typeReferences.stream()
                .sorted(Comparator.comparing(CtTypeReference::getQualifiedName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static void registerImplementation(
            Map<String, List<ObjectFlowIndex.TypeFact>> implementations,
            String declaredQualifiedName,
            ObjectFlowIndex.TypeFact concreteType) {
        if (declaredQualifiedName == null || declaredQualifiedName.equals(concreteType.qualifiedName())) {
            return;
        }
        List<ObjectFlowIndex.TypeFact> typeImplementations =
                implementations.computeIfAbsent(declaredQualifiedName, ignored -> new ArrayList<>());
        if (!typeImplementations.contains(concreteType)) {
            typeImplementations.add(concreteType);
        }
    }
}
