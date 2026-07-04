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
                // CtParameter and CtInvocation cases handled in Tasks 5–7
            } catch (Exception ignored) {}
        }

        return Set.of();
    }
}
