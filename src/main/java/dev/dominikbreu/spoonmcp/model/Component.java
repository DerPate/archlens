package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-level architecture component such as a REST resource, service, repository, or entity.
 */
public class Component {
    /** Stable component identifier — the component's fully-qualified class name. */
    public ComponentId id;
    /** Architectural role assigned to the component. */
    public ComponentType type;
    /** Simple display name, usually the Java type simple name. */
    public String name;
    /** Fully qualified Java type name when the component comes from source. */
    public String qualifiedName;
    /** Owning application or Maven module identifier. */
    public String module;
    /** Detected technology or framework associated with the component. */
    public String technology;
    /** Additional framework or architecture labels discovered during extraction. */
    public List<String> stereotypes = new ArrayList<>();
    /** Source file and evidence metadata for this component. */
    public SourceInfo source;

    /** Creates an empty component for JSON deserialization. */
    public Component() {}
}
