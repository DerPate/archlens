package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;

/** Source-level reference from a component or application to a persistence unit. */
public class PersistenceUnitUsage {
    /** Referencing component, or {@code null} for an application-level descriptor reference. */
    public ComponentId componentId;
    /** Referencing application or module. */
    public AppId appId;
    /** Referenced persistence-unit name; blank means the module default. */
    public String unitName;
    /** Source evidence for the reference. */
    public SourceInfo source;

    /** Creates an empty persistence-unit usage fact. */
    public PersistenceUnitUsage() {}
}
