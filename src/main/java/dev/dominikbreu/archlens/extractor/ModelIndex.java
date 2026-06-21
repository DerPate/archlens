package dev.dominikbreu.archlens.extractor;

import dev.dominikbreu.archlens.model.ArchitectureModel;

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
