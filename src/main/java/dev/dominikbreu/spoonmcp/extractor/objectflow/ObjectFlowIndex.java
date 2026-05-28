package dev.dominikbreu.spoonmcp.extractor.objectflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.reflect.code.CtInvocation;

/**
 * Index for resolving object receiver flows discovered from a Spoon model.
 */
public class ObjectFlowIndex {
    public static final int DEFAULT_POLYMORPHIC_TARGET_CAP = 25;

    record TypeFact(String qualifiedName, String componentId, boolean abstractOrInterface) {}

    private final Map<String, TypeFact> typesByQualifiedName;
    private final Map<String, List<TypeFact>> implementationsByQualifiedName;
    private final Map<CtInvocation<?>, List<ReceiverTarget>> receiverTargetsByInvocation;
    private final Set<String> diagnostics = new LinkedHashSet<>();

    public ObjectFlowIndex(
            Map<String, TypeFact> typesByQualifiedName, Map<String, List<TypeFact>> implementationsByQualifiedName) {
        this(typesByQualifiedName, implementationsByQualifiedName, Map.of());
    }

    public ObjectFlowIndex(
            Map<String, TypeFact> typesByQualifiedName,
            Map<String, List<TypeFact>> implementationsByQualifiedName,
            Map<CtInvocation<?>, List<ReceiverTarget>> receiverTargetsByInvocation) {
        this.typesByQualifiedName = Collections.unmodifiableMap(new LinkedHashMap<>(typesByQualifiedName));
        this.implementationsByQualifiedName = copyImplementationMap(implementationsByQualifiedName);
        this.receiverTargetsByInvocation = copyReceiverTargetMap(receiverTargetsByInvocation);
    }

    public static ObjectFlowIndex empty() {
        return new ObjectFlowIndex(Map.of(), Map.of());
    }

    public List<ReceiverTarget> resolveReceiver(CtInvocation<?> invocation) {
        return receiverTargetsByInvocation.getOrDefault(invocation, List.of());
    }

    public List<ReceiverTarget> expandDeclaredType(String declaredType, String methodName) {
        List<TypeFact> implementations = implementationsByQualifiedName.getOrDefault(declaredType, List.of());
        TypeFact declaredTypeFact = typesByQualifiedName.get(declaredType);
        List<ReceiverTarget> targets = new ArrayList<>();
        if (declaredTypeFact != null && !declaredTypeFact.abstractOrInterface()) {
            targets.add(new ReceiverTarget(
                    declaredTypeFact.componentId(),
                    methodName,
                    ObjectFlowEvidence.DECLARED_FIELD_TYPE,
                    ObjectFlowEvidence.DECLARED_FIELD_TYPE.confidence(),
                    false));
        }
        targets.addAll(implementations.stream()
                .map(type -> receiverTarget(type, methodName, false))
                .toList());

        if (targets.size() > DEFAULT_POLYMORPHIC_TARGET_CAP) {
            addDiagnostic(declaredType + " polymorphic expansion capped at " + DEFAULT_POLYMORPHIC_TARGET_CAP);
            return targets.stream()
                    .limit(DEFAULT_POLYMORPHIC_TARGET_CAP)
                    .map(target -> new ReceiverTarget(
                            target.componentId(), target.methodName(), target.evidence(), target.confidence(), true))
                    .toList();
        }
        if (!targets.isEmpty()) {
            return targets;
        }

        if (declaredTypeFact != null) {
            ObjectFlowEvidence evidence;
            if (declaredTypeFact.abstractOrInterface()) {
                evidence = ObjectFlowEvidence.DECLARED_INTERFACE_ONLY;
            } else {
                evidence = ObjectFlowEvidence.DECLARED_FIELD_TYPE;
            }
            return List.of(new ReceiverTarget(
                    declaredTypeFact.componentId(), methodName, evidence, evidence.confidence(), false));
        }

        return List.of();
    }

    public List<String> diagnostics() {
        return List.copyOf(diagnostics);
    }

    void addDiagnostic(String diagnostic) {
        diagnostics.add(diagnostic);
    }

    private static Map<String, List<TypeFact>> copyImplementationMap(Map<String, List<TypeFact>> source) {
        Map<String, List<TypeFact>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<TypeFact>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<CtInvocation<?>, List<ReceiverTarget>> copyReceiverTargetMap(
            Map<CtInvocation<?>, List<ReceiverTarget>> source) {
        Map<CtInvocation<?>, List<ReceiverTarget>> copy = new IdentityHashMap<>();
        for (Map.Entry<CtInvocation<?>, List<ReceiverTarget>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static ReceiverTarget receiverTarget(TypeFact type, String methodName, boolean expansionCapped) {
        return new ReceiverTarget(
                type.componentId(),
                methodName,
                ObjectFlowEvidence.SMALL_POLYMORPHIC_EXPANSION,
                ObjectFlowEvidence.SMALL_POLYMORPHIC_EXPANSION.confidence(),
                expansionCapped);
    }
}
