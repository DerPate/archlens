package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Container;
import java.util.*;

/**
 * Groups components into logical containers.
 * Framework components use architectural layers; plain Java components use source package responsibility.
 */
public class ContainerInferrer {

    /** Creates a container inferrer using the built-in grouping rules. */
    public ContainerInferrer() {}

    /**
     * Groups components by architectural layer or package responsibility.
     *
     * @param components components to group
     * @return inferred logical containers
     */
    public List<Container> infer(List<Component> components) {
        // Key: appId + ":" + layerName
        Map<String, Container> containers = new LinkedHashMap<>();

        for (Component comp : components) {
            String containerName = containerNameFor(comp);
            String key = comp.module + ":" + containerName;

            Container container = containers.computeIfAbsent(key, k -> {
                Container c = new Container();
                c.id = "container:" + key;
                c.name = containerName;
                c.appId = comp.module;
                c.technology = comp.technology;
                c.derivedFrom = "java".equals(comp.technology) ? "package-convention" : "stereotype-convention";
                return c;
            });
            container.componentIds.add(comp.id);
        }

        return new ArrayList<>(containers.values());
    }

    private String containerNameFor(Component component) {
        if ("java".equals(component.technology)) {
            return packageContainerFor(component);
        }
        return layerFor(component.type);
    }

    private String packageContainerFor(Component component) {
        if (component.qualifiedName == null || component.qualifiedName.isBlank()) {
            return layerFor(component.type);
        }

        String qn = component.qualifiedName;
        if (qn.contains(".mcp.tools.")) return "mcp-tools";
        if (qn.contains(".mcp.")) return "mcp-server";
        if (qn.contains(".extractor.")) return "extractor";
        if (qn.contains(".scanner.")) return "scanner";
        if (qn.contains(".renderer.")) return "renderer";
        if (qn.contains(".merger.")) return "deployment-merge";
        if (qn.contains(".model.")) return "model";
        if (qn.contains(".cache.")) return "cache";
        return layerFor(component.type);
    }

    private String layerFor(ComponentType type) {
        return switch (type) {
            case REST_RESOURCE -> "api";
            case SERVICE, EJB_STATELESS, EJB_STATEFUL, EJB_SINGLETON -> "service";
            case REPOSITORY -> "repository";
            case ENTITY -> "domain";
            case MESSAGE_DRIVEN_BEAN, CDI_EVENT_CONSUMER -> "messaging";
            case SCHEDULER -> "scheduling";
            case HTTP_CLIENT -> "client";
            case CDI_EVENT_PRODUCER, REMOTE_SERVICE -> "service";
            default -> "misc";
        };
    }
}
