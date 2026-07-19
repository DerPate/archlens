package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.AppId;

/** A non-secret configuration property key discovered in application resource files. */
public class ConfigProperty {
    /** Stable graph identifier. */
    public String id;
    /** Dotted property key, e.g. {@code billing.client.base-url}. */
    public String key;
    /** Resolved value, or {@code null} when the key is secret-like or unresolved. */
    public String value;
    /** {@code false} when the value is an unexpanded {@code ${...}} placeholder. */
    public boolean resolved = true;
    /** Application or module the property was declared in. */
    public AppId appId;
    /** Resource file the property was read from, e.g. {@code application.yml}. */
    public String sourceFile;
    /** Source evidence. */
    public SourceInfo source;

    /** Creates an empty config property for extraction and deserialization. */
    public ConfigProperty() {}
}
