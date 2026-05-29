package dev.dominikbreu.spoonmcp.extractor.objectflow;

import java.util.ArrayList;
import java.util.List;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.TypeFilter;

final class ObjectFlowMethodAnalyzer {
    private ObjectFlowMethodAnalyzer() {}

    static List<ReceiverTarget> resolveLocalVariableTargets(
            CtInvocation<?> invocation, String variableName, String methodName) {
        CtExecutable<?> executable = invocation.getParent(CtExecutable.class);
        if (executable == null) {
            return List.of();
        }
        for (CtVariable<?> variable : executable.getElements(new TypeFilter<>(CtVariable.class))) {
            if (!(variable instanceof CtLocalVariable<?> local) || !variableName.equals(local.getSimpleName())) {
                continue;
            }
            // Case 1: Direct constructor call → LOCAL_ASSIGNMENT
            String allocatedType = allocatedType(local.getDefaultExpression());
            if (allocatedType != null) {
                return targetFor(allocatedType, methodName, ObjectFlowEvidence.LOCAL_ASSIGNMENT);
            }
            // Case 2: Collection factory call (e.g. List.of(new X(), new Y())) → COLLECTION_ELEMENT_ALLOCATION
            if (local.getDefaultExpression() instanceof CtInvocation<?> inv) {
                List<ReceiverTarget> targets = collectionElementTargets(inv.getArguments(), methodName);
                if (!targets.isEmpty()) {
                    return targets;
                }
            }
            // Case 3: For-each iteration variable — look at the iterable's constructor elements
            CtForEach foreach = local.getParent(CtForEach.class);
            if (foreach != null && foreach.getExpression() instanceof CtVariableRead<?> iterableRead) {
                CtVariable<?> iterableVar = iterableRead.getVariable().getDeclaration();
                if (iterableVar != null && iterableVar.getDefaultExpression() instanceof CtInvocation<?> inv) {
                    List<ReceiverTarget> targets = collectionElementTargets(inv.getArguments(), methodName);
                    if (!targets.isEmpty()) {
                        return targets;
                    }
                }
            }
        }
        return List.of();
    }

    private static List<ReceiverTarget> collectionElementTargets(List<CtExpression<?>> arguments, String methodName) {
        List<ReceiverTarget> targets = new ArrayList<>();
        for (CtExpression<?> arg : arguments) {
            String type = allocatedType(arg);
            if (type != null) {
                targets.addAll(targetFor(type, methodName, ObjectFlowEvidence.COLLECTION_ELEMENT_ALLOCATION));
            }
        }
        return targets;
    }

    private static String allocatedType(CtExpression<?> expression) {
        if (expression instanceof CtConstructorCall<?> ctor && ctor.getType() != null) {
            return ctor.getType().getQualifiedName();
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
}
