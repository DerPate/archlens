package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.ComponentId;

/**
 * Single component visit in an inferred runtime flow.
 */
public class RuntimeFlowStep {
    /** Zero-based or one-based display order assigned by the flow inferrer. */
    public int order;
    /** Component identifier visited at this step. */
    public ComponentId componentId;
    /** Component display name. */
    public String componentName;
    /** Component type name. */
    public String componentType;
    /** Dependency or transition evidence used to reach this step. */
    public String via;

    /** Creates an empty runtime flow step for JSON deserialization. */
    public RuntimeFlowStep() {}

    /**
     * Creates a populated runtime flow step.
     *
     * @param order display order
     * @param componentId component identifier
     * @param componentName component display name
     * @param componentType component type name
     * @param via transition evidence
     */
    public RuntimeFlowStep(int order, ComponentId componentId, String componentName, String componentType, String via) {
        this.order = order;
        this.componentId = componentId;
        this.componentName = componentName;
        this.componentType = componentType;
        this.via = via;
    }
}
