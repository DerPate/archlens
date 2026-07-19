package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;

/** Method-local invocation of a JPA {@code EntityManager} persistence API. */
public class PersistenceOperation {
    /** Stable graph identifier. */
    public String id;
    /** Owning application/module. */
    public AppId appId;
    /** Component containing the invocation. */
    public ComponentId componentId;
    /** Enclosing method name. */
    public String methodName;
    /** Enclosing method signature. */
    public String methodSignature;
    /** EntityManager operation such as persist, merge, remove, or find. */
    public String operation;
    /** Entity type inferred from a class literal or method parameter, when known. */
    public String entityType;
    /** Resolved persistence-unit name, when known. */
    public String persistenceUnitName;
    /** Invocation evidence. */
    public SourceInfo source;

    /** Creates an empty persistence operation fact. */
    public PersistenceOperation() {}
}
