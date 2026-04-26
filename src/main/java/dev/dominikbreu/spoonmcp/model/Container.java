package dev.dominikbreu.spoonmcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Logical grouping of components used for container-level architecture views.
 */
public class Container {
    /** Stable container identifier. */
    public String id;
    /** Display name such as api, service, repository, model, or mcp-tools. */
    public String name;
    /** Owning application identifier. */
    public String appId;
    /** Dominant technology represented by the container. */
    public String technology;
    /** Component identifiers assigned to this container. */
    public List<String> componentIds = new ArrayList<>();
    /** Extraction rule or evidence category used to infer the container. */
    public String derivedFrom;

    /** Creates an empty container for JSON deserialization. */
    public Container() {}
}
