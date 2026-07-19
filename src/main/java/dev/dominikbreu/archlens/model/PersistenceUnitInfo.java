package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import java.util.ArrayList;
import java.util.List;

/** Persistence-unit declaration extracted from a standard {@code persistence.xml} descriptor. */
public class PersistenceUnitInfo {
    /** Stable graph identifier. */
    public String id;
    /** Declared persistence-unit name. */
    public String name;
    /** Application or module that owns the descriptor. */
    public AppId appId;
    /** Persistence provider class, when declared. */
    public String provider;
    /** Transaction type such as {@code JTA} or {@code RESOURCE_LOCAL}. */
    public String transactionType;
    /** Declared JTA datasource JNDI name. */
    public String jtaDataSource;
    /** Declared non-JTA datasource JNDI name. */
    public String nonJtaDataSource;
    /** Explicitly managed entity class names. */
    public List<String> managedClasses = new ArrayList<>();
    /** ORM mapping files referenced by the unit. */
    public List<String> mappingFiles = new ArrayList<>();
    /** Placeholder expressions that could not be resolved safely. */
    public List<String> unresolvedPlaceholders = new ArrayList<>();
    /** Descriptor source and evidence. */
    public SourceInfo source;

    /** Creates an empty persistence-unit fact for extraction and deserialization. */
    public PersistenceUnitInfo() {}
}
