package dev.dominikbreu.archlens.extractor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

public class StringExpressionResolver {

    public static Set<String> resolve(
            CtExpression<?> expr,
            CtType<?> containingType,
            CtMethod<?> containingMethod,
            CtModel model,
            int maxDepth,
            Set<String> visited) {

        if (maxDepth <= 0 || expr == null) return Set.of();

        // ── Case 1: String literal ────────────────────────────────────────
        if (expr instanceof CtLiteral<?> lit && lit.getValue() instanceof String s) {
            return Set.of(s);
        }

        // ── Case 2: Static final field read (e.g. KafkaConfig.PUSH_NOTIFICATION_TOPIC) ──
        if (expr instanceof CtFieldRead<?> fr) {
            try {
                CtField<?> decl = fr.getVariable().getFieldDeclaration();
                if (decl != null && decl.getDefaultExpression() instanceof CtLiteral<?> lit
                        && lit.getValue() instanceof String s) {
                    return Set.of(s);
                }
                if (decl != null && decl.getDefaultExpression() != null) {
                    return resolve(decl.getDefaultExpression(), containingType, containingMethod, model, maxDepth - 1, visited);
                }
            } catch (Exception ignored) {}
        }

        // ── Case 3: Local variable with literal/field initializer ─────────
        if (expr instanceof CtVariableRead<?> vr && !(expr instanceof CtFieldRead<?>)) {
            try {
                CtVariable<?> varDecl = vr.getVariable().getDeclaration();
                if (varDecl instanceof CtLocalVariable<?> local && local.getDefaultExpression() != null) {
                    return resolve(local.getDefaultExpression(), containingType, containingMethod, model, maxDepth - 1, visited);
                }
                if (varDecl instanceof CtParameter<?> param && containingMethod != null) {
                    // Determine which param position this is
                    int paramIndex = -1;
                    List<CtParameter<?>> params = containingMethod.getParameters();
                    for (int i = 0; i < params.size(); i++) {
                        if (params.get(i).getSimpleName().equals(param.getSimpleName())) {
                            paramIndex = i;
                            break;
                        }
                    }
                    if (paramIndex < 0) return Set.of();

                    CtTypeReference<?> paramType = param.getType();
                    String typeName = paramType != null ? paramType.getSimpleName() : "";
                    if (typeName.contains("Message")) {
                        // MESSAGE_OBJECT case — handled in Task 7
                        return Set.of();
                    }
                    return resolveFromCallers(containingType, containingMethod, paramIndex, model, maxDepth - 1, visited);
                }
                // CtInvocation cases handled in Tasks 6–7
            } catch (Exception ignored) {}
        }

        // ── Case 4: Method call on a variable — walk interface implementations ──
        if (expr instanceof CtInvocation<?> inv && inv.getTarget() != null
                && inv.getExecutable() != null) {
            String calledMethod = inv.getExecutable().getSimpleName();
            CtTypeReference<?> receiverType = inv.getTarget().getType();
            if (!calledMethod.isEmpty() && receiverType != null) {
                return resolveFromImplementations(receiverType, calledMethod, model, maxDepth - 1, visited);
            }
        }

        return Set.of();
    }

    private static Set<String> resolveFromImplementations(
            CtTypeReference<?> interfaceRef,
            String methodName,
            CtModel model,
            int depth,
            Set<String> visited) {

        String frameKey = interfaceRef.getQualifiedName() + "#" + methodName;
        if (!visited.add(frameKey)) return Set.of();

        Set<String> results = new LinkedHashSet<>();
        for (CtType<?> candidate : model.getAllTypes()) {
            boolean isImpl = candidate.getSuperInterfaces().stream()
                    .anyMatch(i -> interfaceRef.getQualifiedName().equals(i.getQualifiedName()));
            if (!isImpl && candidate.getSuperclass() != null
                    && interfaceRef.getQualifiedName().equals(candidate.getSuperclass().getQualifiedName())) {
                isImpl = true;
            }
            if (!isImpl) continue;

            for (CtMethod<?> m : candidate.getMethods()) {
                if (!methodName.equals(m.getSimpleName())) continue;
                for (CtReturn<?> ret : m.getElements(new TypeFilter<>(CtReturn.class))) {
                    CtExpression<?> returned = ret.getReturnedExpression();
                    if (returned != null) {
                        results.addAll(resolve(returned, candidate, m, model, depth, visited));
                    }
                }
            }
        }
        return results;
    }

    private static Set<String> resolveFromCallers(
            CtType<?> type,
            CtMethod<?> method,
            int paramIndex,
            CtModel model,
            int depth,
            Set<String> visited) {

        String frameKey = type.getQualifiedName() + "#" + method.getSimpleName() + "#" + paramIndex;
        if (!visited.add(frameKey)) return Set.of();

        Set<String> results = new LinkedHashSet<>();
        String methodName = method.getSimpleName();

        for (CtType<?> candidate : model.getAllTypes()) {
            for (CtInvocation<?> inv : candidate.getElements(new TypeFilter<>(CtInvocation.class))) {
                if (!methodName.equals(inv.getExecutable().getSimpleName())) continue;
                if (inv.getArguments().size() <= paramIndex) continue;
                CtTypeReference<?> declType = inv.getExecutable().getDeclaringType();
                if (declType == null) continue;
                // Try exact qualified name match first; fall back to simple name in noClasspath mode
                boolean matches = type.getQualifiedName().equals(declType.getQualifiedName())
                        || type.getSimpleName().equals(declType.getSimpleName());
                if (!matches) continue;

                CtMethod<?> callerMethod = inv.getParent(CtMethod.class);
                CtType<?> callerType = inv.getParent(CtType.class);
                if (callerMethod == null || callerType == null) continue;

                results.addAll(resolve(
                        inv.getArguments().get(paramIndex),
                        callerType, callerMethod, model, depth, visited));
            }
        }
        return results;
    }
}
