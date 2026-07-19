package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;
import java.util.ArrayList;
import java.util.List;

/** Datasource declaration or unresolved datasource reference extracted from configuration. */
public class DataSourceInfo {
    /** Stable graph identifier. */
    public String id;
    /** Human-readable datasource name. */
    public String name;
    /** Application or module associated with the declaration. */
    public AppId appId;
    /** Primary JNDI binding, when known. */
    public String jndiName;
    /** Additional JNDI aliases. */
    public List<String> aliases = new ArrayList<>();
    /** JDBC driver or datasource class, when safely known. */
    public String driver;
    /** Sanitized database endpoint without credentials or query parameters. */
    public String endpoint;
    /** Database technology inferred from the driver or URL. */
    public String databaseKind;
    /** How the datasource was declared, such as wildfly-config or annotation. */
    public String declarationKind;
    /** Whether the binding has no matching datasource declaration. */
    public boolean unresolved;
    /** Descriptor or source-code evidence. */
    public SourceInfo source;

    /** Creates an empty datasource fact for extraction and deserialization. */
    public DataSourceInfo() {}
}
