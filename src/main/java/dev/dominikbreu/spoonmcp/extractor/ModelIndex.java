package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;

public final class ModelIndex {

    public final ComponentIndex components;
    public final CallAdjacency callAdj;
    public final FieldAccessIndex fieldAccess;
    public final OutboundSinkIndex outboundSinks;
    public final EntityIndex entityIndex;
    public final DependencyAdjacency depAdj;

    public static ModelIndex build(ArchitectureModel model) {
        return new ModelIndex(
                ComponentIndex.build(model.components),
                CallAdjacency.build(model.callEdges),
                FieldAccessIndex.build(model.fieldAccesses),
                OutboundSinkIndex.build(model.outboundSinkSites),
                EntityIndex.build(model.components),
                DependencyAdjacency.build(model.dependencies));
    }

    private ModelIndex(
            ComponentIndex components,
            CallAdjacency callAdj,
            FieldAccessIndex fieldAccess,
            OutboundSinkIndex outboundSinks,
            EntityIndex entityIndex,
            DependencyAdjacency depAdj) {
        this.components = components;
        this.callAdj = callAdj;
        this.fieldAccess = fieldAccess;
        this.outboundSinks = outboundSinks;
        this.entityIndex = entityIndex;
        this.depAdj = depAdj;
    }
}
