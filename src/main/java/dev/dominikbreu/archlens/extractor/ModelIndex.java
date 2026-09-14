package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.PersistenceOperation;
import dev.dominikbreu.archlens.model.ids.MethodRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pre-built lookup indices over an {@link ArchitectureModel} for efficient extraction passes. */
public final class ModelIndex {

    /** Component index keyed by component id and simple name. */
    public final ComponentIndex components;
    /** Outgoing call-edge index keyed by caller method. */
    public final CallAdjacency callAdj;
    /** Field read/write index keyed by accessor method. */
    public final FieldAccessIndex fieldAccess;
    /** Outbound sink index keyed by site. */
    public final OutboundSinkIndex outboundSinks;
    /** Entity class index keyed by base package and simple name. */
    public final EntityIndex entityIndex;
    /** Dependency adjacency index keyed by source component. */
    public final DependencyAdjacency depAdj;
    /** Persistence operations keyed by declaring method. */
    public final Map<MethodRef, List<PersistenceOperation>> persistenceOperations;

    /**
     * Builds a model index from the given architecture model.
     *
     * @param model the architecture model to index
     * @return the populated model index
     */
    public static ModelIndex build(ArchitectureModel model) {
        return new ModelIndex(
                ComponentIndex.build(model.components),
                CallAdjacency.build(model.callEdges),
                FieldAccessIndex.build(model.fieldAccesses),
                OutboundSinkIndex.build(model.outboundSinkSites),
                EntityIndex.build(model.components),
                DependencyAdjacency.build(model.dependencies),
                persistenceOperationsByMethod(model.persistenceOperations));
    }

    private ModelIndex(
            ComponentIndex components,
            CallAdjacency callAdj,
            FieldAccessIndex fieldAccess,
            OutboundSinkIndex outboundSinks,
            EntityIndex entityIndex,
            DependencyAdjacency depAdj,
            Map<MethodRef, List<PersistenceOperation>> persistenceOperations) {
        this.components = components;
        this.callAdj = callAdj;
        this.fieldAccess = fieldAccess;
        this.outboundSinks = outboundSinks;
        this.entityIndex = entityIndex;
        this.depAdj = depAdj;
        this.persistenceOperations = persistenceOperations;
    }

    private static Map<MethodRef, List<PersistenceOperation>> persistenceOperationsByMethod(
            List<PersistenceOperation> operations) {
        Map<MethodRef, List<PersistenceOperation>> result = new LinkedHashMap<>();
        for (PersistenceOperation operation : operations) {
            if (operation.componentId == null || operation.methodName == null) continue;
            result.computeIfAbsent(
                            new MethodRef(operation.componentId, operation.methodName), ignored -> new ArrayList<>())
                    .add(operation);
        }
        return result;
    }
}
