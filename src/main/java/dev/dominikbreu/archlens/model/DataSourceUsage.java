package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;

/** Source or descriptor reference from a component or application to a datasource binding. */
public class DataSourceUsage {
    /** Referencing component, or {@code null} for an application-level descriptor reference. */
    public ComponentId componentId;
    /** Referencing application or module. */
    public AppId appId;
    /** Referenced JNDI name or logical resource name. */
    public String dataSourceName;
    /** Source evidence for the reference. */
    public SourceInfo source;

    /** Creates an empty datasource usage fact. */
    public DataSourceUsage() {}
}
