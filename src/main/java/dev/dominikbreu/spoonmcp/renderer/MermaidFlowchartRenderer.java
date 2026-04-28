package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders deterministic Mermaid flowchart diagrams from the architecture model.
 * Supports three levels: system, container, component (default).
 */
public class MermaidFlowchartRenderer {

    /** Creates a flowchart renderer for architecture model views. */
    public MermaidFlowchartRenderer() {}

    /**
     * Renders a Mermaid flowchart for the requested architecture level.
     *
     * @param model architecture model to render
     * @param appIdFilter optional application id or name fragment
     * @param level system, container, module, or component
     * @return Mermaid flowchart text
     */
    public String render(ArchitectureModel model, String appIdFilter, String level) {
        String lvl = level != null ? level.toLowerCase() : "component";

        List<AppEntry> apps = model.applications.stream()
            .filter(a -> appIdFilter == null || a.id.contains(appIdFilter) || a.name.contains(appIdFilter))
            .collect(Collectors.toList());

        return switch (lvl) {
            case "system"    -> renderSystemLevel(apps, model);
            case "container" -> renderContainerLevel(apps, model);
            case "module"    -> renderModuleLevel(apps, model);
            default          -> renderComponentLevel(apps, model);
        };
    }

    // ── module level: deployment-unit (WAR) → internal_module subgraphs ─────

    private String renderModuleLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        for (AppEntry app : apps) {
            if ("internal_module".equals(app.role) || "technical_library".equals(app.role)) continue;

            // deployment_unit or standalone
            List<AppEntry> children = model.applications.stream()
                .filter(a -> app.id.equals(a.parentAppId))
                .toList();

            if (children.isEmpty()) {
                // No sub-modules — render as single box
                String label = app.name + "\\n" + app.packagingType
                    + (app.technology != null ? " / " + app.technology : "");
                sb.append("    ").append(nid(app.id))
                  .append("[\"").append(escape(label)).append("\"]\n");
            } else {
                // WAR with internal modules
                sb.append("    subgraph ").append(nid(app.id))
                  .append("[\"").append(escape(app.name))
                  .append(" (").append(app.packagingType).append(")\"]\n");

                for (AppEntry child : children) {
                    String shape = "technical_library".equals(child.role) ? "([" : "[";
                    String closeShape = "technical_library".equals(child.role) ? "])" : "]";
                    String label = child.name + "\\n" + child.role;
                    sb.append("        ").append(nid(child.id))
                      .append(shape).append("\"").append(escape(label)).append("\"").append(closeShape)
                      .append("\n");
                }
                sb.append("    end\n");
            }
        }

        // Cross-module dependency edges (component-level deps projected to module level)
        Set<String> drawn = new HashSet<>();
        Map<String, String> compToApp = buildCompToAppMap(model);
        for (Dependency dep : model.dependencies) {
            String fromApp = compToApp.get(dep.fromId);
            String toApp = compToApp.get(dep.toId);
            if (fromApp == null || toApp == null || fromApp.equals(toApp)) continue;
            String key = fromApp + "->" + toApp;
            if (drawn.add(key)) {
                sb.append("    ").append(nid(fromApp)).append(" --> ").append(nid(toApp)).append("\n");
            }
        }
        return sb.toString();
    }

    private Map<String, String> buildCompToAppMap(ArchitectureModel model) {
        Map<String, String> map = new HashMap<>();
        for (AppEntry app : model.applications) {
            for (String cid : app.componentIds) map.put(cid, app.id);
        }
        return map;
    }

    // ── system level: one box per application ────────────────────────────────

    private String renderSystemLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        for (AppEntry app : apps) {
            String id = nid(app.id);
            sb.append("    ").append(id)
              .append("[\"**").append(app.name).append("**\\n")
              .append(app.technology).append(" / ").append(app.packagingType)
              .append("\"]\n");
        }

        Set<String> visibleApps = apps.stream().map(a -> a.id).collect(Collectors.toSet());
        Map<String, String> compToApp = buildCompToAppMap(model);
        Set<String> referencedExternals = new LinkedHashSet<>();
        Set<String> drawnEdges = new LinkedHashSet<>();
        for (Dependency dep : model.dependencies) {
            if (!isExternalSystem(model, dep.toId)) continue;
            String fromApp = compToApp.get(dep.fromId);
            if (fromApp == null || !visibleApps.contains(fromApp)) continue;
            referencedExternals.add(dep.toId);
            String key = fromApp + "->" + dep.toId + ":" + dep.kind;
            if (drawnEdges.add(key)) {
                sb.append("    ").append(nid(fromApp))
                  .append(" -->|").append(escape(dep.kind == null ? "" : dep.kind)).append("| ")
                  .append(nid(dep.toId)).append("\n");
            }
        }

        for (ExternalSystem ext : model.externalSystems) {
            if (!referencedExternals.contains(ext.id)) continue;
            String[] shape = externalShape(ext.kind);
            sb.append("    ").append(nid(ext.id))
              .append(shape[0]).append("\"").append(escape(ext.name))
              .append("\\n").append(escape(ext.kind)).append("\"")
              .append(shape[1]).append("\n");
        }
        return sb.toString();
    }

    private boolean isExternalSystem(ArchitectureModel model, String id) {
        if (id == null) return false;
        for (ExternalSystem s : model.externalSystems) if (id.equals(s.id)) return true;
        return false;
    }

    private String[] externalShape(String kind) {
        if (kind == null) return new String[]{"[", "]"};
        return switch (kind) {
            case "MESSAGE_BROKER" -> new String[]{"[(", ")]"};
            case "REST_API" -> new String[]{"[/", "/]"};
            default -> new String[]{"[", "]"};
        };
    }

    // ── container level: subgraph per app, box per container ─────────────────

    private String renderContainerLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        for (AppEntry app : apps) {
            sb.append("    subgraph ").append(nid(app.id))
              .append("[\"").append(escape(app.name))
              .append(" (").append(app.technology).append(")\"]\n");

            List<Container> appContainers = model.containers.stream()
                .filter(c -> app.id.equals(c.appId))
                .collect(Collectors.toList());

            for (Container container : appContainers) {
                sb.append("        ").append(nid(container.id))
                  .append("[\"").append(escape(container.name)).append("\"]\n");
            }
            sb.append("    end\n");
        }

        // Dependencies between containers (de-duped)
        Set<String> drawn = new HashSet<>();
        Map<String, String> compToContainer = buildCompToContainerMap(model);
        for (Dependency dep : model.dependencies) {
            String fromC = compToContainer.get(dep.fromId);
            String toC = compToContainer.get(dep.toId);
            if (fromC == null || toC == null || fromC.equals(toC)) continue;
            String edgeKey = fromC + "->" + toC;
            if (drawn.add(edgeKey)) {
                sb.append("    ").append(nid(fromC)).append(" --> ").append(nid(toC)).append("\n");
            }
        }
        return sb.toString();
    }

    // ── component level: subgraph per container, box per component ───────────

    private String renderComponentLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        Set<String> appIds = apps.stream().map(a -> a.id).collect(Collectors.toSet());

        for (AppEntry app : apps) {
            sb.append("    subgraph ").append(nid(app.id))
              .append("[\"").append(escape(app.name))
              .append(" (").append(app.technology).append(")\"]\n");

            List<Container> appContainers = model.containers.stream()
                .filter(c -> app.id.equals(c.appId))
                .collect(Collectors.toList());

            if (appContainers.isEmpty()) {
                // No containers — flat list of components
                for (String cid : app.componentIds) {
                    renderComponentNode(sb, cid, model, "        ");
                }
            } else {
                for (Container container : appContainers) {
                    sb.append("        subgraph ").append(nid(container.id))
                      .append("[\"").append(escape(container.name)).append("\"]\n");
                    for (String cid : container.componentIds) {
                        renderComponentNode(sb, cid, model, "            ");
                    }
                    sb.append("        end\n");
                }
            }
            sb.append("    end\n");
        }

        // Draw edges only between components belonging to the filtered apps
        Set<String> visibleComps = apps.stream()
            .flatMap(a -> a.componentIds.stream())
            .collect(Collectors.toSet());

        for (Dependency dep : model.dependencies) {
            if (visibleComps.contains(dep.fromId) && visibleComps.contains(dep.toId)) {
                sb.append("    ").append(nid(dep.fromId))
                  .append(" -->|").append(escape(dep.kind)).append("| ")
                  .append(nid(dep.toId)).append("\n");
            }
        }
        return sb.toString();
    }

    private void renderComponentNode(StringBuilder sb, String compId, ArchitectureModel model, String indent) {
        model.components.stream()
            .filter(c -> c.id.equals(compId))
            .findFirst()
            .ifPresent(comp -> {
                String[] shape = shapeFor(comp.type);
                String label = comp.type.name() + "\\n" + escape(comp.name);
                sb.append(indent).append(nid(comp.id))
                  .append(shape[0]).append("\"").append(label).append("\"").append(shape[1])
                  .append("\n");
            });
    }

    /** Returns [open, close] Mermaid shape brackets. */
    private String[] shapeFor(ComponentType type) {
        return switch (type) {
            case ENTITY       -> new String[]{"[(", ")]"};  // cylinder
            case REST_RESOURCE -> new String[]{"([", "])"}; // stadium
            default           -> new String[]{"[", "]"};    // rectangle
        };
    }

    private Map<String, String> buildCompToContainerMap(ArchitectureModel model) {
        Map<String, String> map = new HashMap<>();
        for (Container c : model.containers) {
            for (String cid : c.componentIds) map.put(cid, c.id);
        }
        return map;
    }

    /** Sanitise an ID for use as a Mermaid node identifier (no spaces/colons). */
    private String nid(String id) {
        return id.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", "\\n");
    }
}
