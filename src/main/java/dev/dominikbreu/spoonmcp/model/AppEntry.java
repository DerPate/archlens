package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import java.util.ArrayList;
import java.util.List;

/**
 * Application, Maven module, or deployable unit discovered during workspace indexing.
 */
public class AppEntry {
    /** Stable application identifier used by components and deployments. */
    public AppId id;
    /** Human-readable application or module name. */
    public String name;
    /** Filesystem root from which the application was scanned. */
    public String rootPath;
    /** Detected runtime or framework family, for example quarkus, javaee, or unknown. */
    public String technology;
    /** Maven packaging type such as jar, war, pom, or unknown. */
    public String packagingType;
    /** Role inside a deployment graph: deployment_unit, internal_module, or technical_library. */
    public String role;
    /** Identifier of the parent WAR deployment unit, set when role is internal_module. */
    public AppId parentAppId;
    /** Component identifiers owned by this application or module. */
    public List<ComponentId> componentIds = new ArrayList<>();

    /** Creates an empty application entry for JSON deserialization. */
    public AppEntry() {}
}
