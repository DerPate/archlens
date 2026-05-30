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
        String lvl;
        if (level != null) {
            lvl = level.toLowerCase();
        } else {
            lvl = "component";
        }

        List<AppEntry> apps = model.applications.stream()
                .filter(a ->
                        appIdFilter == null || a.id.serialize().contains(appIdFilter) || a.name.contains(appIdFilter))
                .toList();

        return switch (lvl) {
            case "system" -> renderSystemLevel(apps, model);
            case "container" -> renderContainerLevel(apps, model);
            case "module" -> renderModuleLevel(apps, model);
            default -> renderComponentLevel(apps, model);
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
                String label =
                        app.name + "\\n" + app.packagingType + (app.technology != null ? " / " + app.technology : "");
                sb.append("    ")
                        .append(nid(app.id.serialize()))
                        .append("[\"")
                        .append(escape(label))
                        .append("\"]\n");
            } else {
                // WAR with internal modules
                sb.append("    subgraph ")
                        .append(nid(app.id.serialize()))
                        .append("[\"")
                        .append(escape(app.name))
                        .append(" (")
                        .append(app.packagingType)
                        .append(")\"]\n");

                for (AppEntry child : children) {
                    String shape;
                    if ("technical_library".equals(child.role)) {
                        shape = "([";
                    } else {
                        shape = "[";
                    }
                    String closeShape;
                    if ("technical_library".equals(child.role)) {
                        closeShape = "])";
                    } else {
                        closeShape = "]";
                    }
                    String label = child.name + "\\n" + child.role;
                    sb.append("        ")
                            .append(nid(child.id.serialize()))
                            .append(shape)
                            .append("\"")
                            .append(escape(label))
                            .append("\"")
                            .append(closeShape)
                            .append("\n");
                }
                sb.append("    end\n");
            }
        }

        // Cross-module dependency edges (component-level deps projected to module level)
        Set<String> drawn = new HashSet<>();
        Map<String, String> compToApp = buildCompToAppMap(model);
        for (Dependency dep : model.dependencies) {
            String fromApp = compToApp.get(dep.fromId.serialize());
            String toApp = compToApp.get(dep.toId.serialize());
            if (fromApp == null || toApp == null || fromApp.equals(toApp)) continue;
            String key = fromApp + "->" + toApp;
            if (drawn.add(key)) {
                sb.append("    ")
                        .append(nid(fromApp))
                        .append(" --> ")
                        .append(nid(toApp))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private Map<String, String> buildCompToAppMap(ArchitectureModel model) {
        Map<String, String> map = new HashMap<>();
        for (AppEntry app : model.applications) {
            for (dev.dominikbreu.spoonmcp.model.ids.ComponentId cid : app.componentIds)
                map.put(cid.serialize(), app.id.serialize());
        }
        return map;
    }

    // ── system level: one box per application ────────────────────────────────

    private String renderSystemLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        for (AppEntry app : apps) {
            String id = nid(app.id.serialize());
            sb.append("    ")
                    .append(id)
                    .append("[\"**")
                    .append(app.name)
                    .append("**\\n")
                    .append(app.technology)
                    .append(" / ")
                    .append(app.packagingType)
                    .append("\"]\n");
        }

        Set<String> visibleApps = apps.stream().map(a -> a.id.serialize()).collect(Collectors.toSet());
        Map<String, String> compToApp = buildCompToAppMap(model);
        Set<String> referencedExternals = new LinkedHashSet<>();
        Set<String> drawnEdges = new LinkedHashSet<>();
        for (Dependency dep : model.dependencies) {
            if (!isExternalSystem(model, dep.toId.serialize())) continue;
            String fromApp = compToApp.get(dep.fromId.serialize());
            if (fromApp == null || !visibleApps.contains(fromApp)) continue;
            referencedExternals.add(dep.toId.serialize());
            String key = fromApp + "->" + dep.toId.serialize() + ":" + dep.kind;
            if (drawnEdges.add(key)) {
                sb.append("    ")
                        .append(nid(fromApp))
                        .append(" -->|")
                        .append(escape(dep.kind == null ? "" : dep.kind))
                        .append("| ")
                        .append(nid(dep.toId.serialize()))
                        .append("\n");
            }
        }

        for (ExternalSystem ext : model.externalSystems) {
            if (!referencedExternals.contains(ext.id)) continue;
            String[] shape = externalShape(ext.kind);
            sb.append("    ")
                    .append(nid(ext.id))
                    .append(shape[0])
                    .append("\"")
                    .append(escape(ext.name))
                    .append("\\n")
                    .append(escape(ext.kind))
                    .append("\"")
                    .append(shape[1])
                    .append("\n");
        }
        return sb.toString();
    }

    private boolean isExternalSystem(ArchitectureModel model, String id) {
        if (id == null) return false;
        for (ExternalSystem s : model.externalSystems) if (id.equals(s.id)) return true;
        return false;
    }

    private String[] externalShape(String kind) {
        if (kind == null) return new String[] {"[", "]"};
        return switch (kind) {
            case "MESSAGE_BROKER" -> new String[] {"[(", ")]"};
            case "REST_API" -> new String[] {"[/", "/]"};
            default -> new String[] {"[", "]"};
        };
    }

    // ── container level: subgraph per app, box per container ─────────────────

    private String renderContainerLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        Map<String, String> compToContainer = buildCompToContainerMap(model);

        // Count entrypoints per container
        Map<String, Long> epByContainer = model.entrypoints.stream()
                .filter(ep -> ep.componentId != null)
                .collect(Collectors.groupingBy(
                        ep -> compToContainer.getOrDefault(
                                ep.componentId != null ? ep.componentId.serialize() : "", ""),
                        Collectors.counting()));

        Set<String> visibleContainers = new LinkedHashSet<>();
        for (AppEntry app : apps) {
            sb.append("    subgraph ")
                    .append(nid(app.id.serialize()))
                    .append("[\"")
                    .append(escape(app.name))
                    .append(" (")
                    .append(app.technology)
                    .append(")\"]\n");

            List<Container> appContainers = model.containers.stream()
                    .filter(c -> app.id.equals(c.appId))
                    .toList();

            for (Container container : appContainers) {
                visibleContainers.add(container.id);
                long epCount = epByContainer.getOrDefault(container.id, 0L);
                String label = escape(container.name) + "\\n"
                        + container.componentIds.size() + " component"
                        + (container.componentIds.size() != 1 ? "s" : "")
                        + (epCount > 0 ? " / " + epCount + " EP" : "");
                sb.append("        ")
                        .append(nid(container.id))
                        .append("[\"")
                        .append(label)
                        .append("\"]\n");
            }
            sb.append("    end\n");
        }

        // Aggregate cross-container edge kinds by (from, to) pair
        Map<String, Set<String>> edgeKinds = new LinkedHashMap<>();
        Set<String> referencedExternals = new LinkedHashSet<>();

        for (Dependency dep : model.dependencies) {
            String fromC = compToContainer.get(dep.fromId.serialize());

            if (fromC != null && visibleContainers.contains(fromC) && isExternalSystem(model, dep.toId.serialize())) {
                referencedExternals.add(dep.toId.serialize());
                edgeKinds
                        .computeIfAbsent(fromC + "\0" + dep.toId.serialize(), k -> new LinkedHashSet<>())
                        .add(nullToEmpty(dep.kind));
                continue;
            }

            String toC = compToContainer.get(dep.toId.serialize());
            if (fromC == null || toC == null || fromC.equals(toC)) continue;
            if (!visibleContainers.contains(fromC) || !visibleContainers.contains(toC)) continue;
            edgeKinds
                    .computeIfAbsent(fromC + "\0" + toC, k -> new LinkedHashSet<>())
                    .add(nullToEmpty(dep.kind));
        }

        // External system nodes (outside subgraphs)
        for (ExternalSystem ext : model.externalSystems) {
            if (!referencedExternals.contains(ext.id)) continue;
            String[] shape = externalShape(ext.kind);
            sb.append("    ")
                    .append(nid(ext.id))
                    .append(shape[0])
                    .append("\"")
                    .append(escape(ext.name))
                    .append("\\n")
                    .append(escape(ext.kind))
                    .append("\"")
                    .append(shape[1])
                    .append("\n");
        }

        // Labelled edges
        for (Map.Entry<String, Set<String>> entry : edgeKinds.entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            String kindLabel = String.join(", ", entry.getValue());
            sb.append("    ")
                    .append(nid(parts[0]))
                    .append(" -->|")
                    .append(escape(kindLabel))
                    .append("| ")
                    .append(nid(parts[1]))
                    .append("\n");
        }

        return sb.toString();
    }

    private String nullToEmpty(String s) {
        if (s == null || s.isBlank()) {
            return "uses";
        } else {
            return s;
        }
    }

    // ── component level: subgraph per container, box per component ───────────

    private String renderComponentLevel(List<AppEntry> apps, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        for (AppEntry app : apps) {
            sb.append("    subgraph ")
                    .append(nid(app.id.serialize()))
                    .append("[\"")
                    .append(escape(app.name))
                    .append(" (")
                    .append(app.technology)
                    .append(")\"]\n");

            List<Container> appContainers = model.containers.stream()
                    .filter(c -> app.id.equals(c.appId))
                    .toList();

            if (appContainers.isEmpty()) {
                // No containers — flat list of components
                for (dev.dominikbreu.spoonmcp.model.ids.ComponentId cid : app.componentIds) {
                    renderComponentNode(sb, cid.serialize(), model, "        ");
                }
            } else {
                for (Container container : appContainers) {
                    sb.append("        subgraph ")
                            .append(nid(container.id))
                            .append("[\"")
                            .append(escape(container.name))
                            .append("\"]\n");
                    for (dev.dominikbreu.spoonmcp.model.ids.ComponentId cid : container.componentIds) {
                        renderComponentNode(sb, cid.serialize(), model, "            ");
                    }
                    sb.append("        end\n");
                }
            }
            sb.append("    end\n");
        }

        // Draw edges only between components belonging to the filtered apps
        Set<String> visibleComps = apps.stream()
                .flatMap(a -> a.componentIds.stream())
                .map(dev.dominikbreu.spoonmcp.model.ids.ComponentId::serialize)
                .collect(Collectors.toSet());

        for (Dependency dep : model.dependencies) {
            if (visibleComps.contains(dep.fromId.serialize()) && visibleComps.contains(dep.toId.serialize())) {
                sb.append("    ")
                        .append(nid(dep.fromId.serialize()))
                        .append(" -->|")
                        .append(escape(dep.kind))
                        .append("| ")
                        .append(nid(dep.toId.serialize()))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private void renderComponentNode(StringBuilder sb, String compId, ArchitectureModel model, String indent) {
        model.components.stream()
                .filter(c -> c.id.serialize().equals(compId))
                .findFirst()
                .ifPresent(comp -> {
                    String[] shape = shapeFor(comp.type);
                    String label = comp.type.name() + "\\n" + escape(comp.name);
                    sb.append(indent)
                            .append(nid(comp.id.serialize()))
                            .append(shape[0])
                            .append("\"")
                            .append(label)
                            .append("\"")
                            .append(shape[1])
                            .append("\n");
                });
    }

    /** Returns [open, close] Mermaid shape brackets. */
    private String[] shapeFor(ComponentType type) {
        return switch (type) {
            case ENTITY -> new String[] {"[(", ")]"}; // cylinder
            case REST_RESOURCE -> new String[] {"([", "])"}; // stadium
            default -> new String[] {"[", "]"}; // rectangle
        };
    }

    private Map<String, String> buildCompToContainerMap(ArchitectureModel model) {
        Map<String, String> map = new HashMap<>();
        for (Container c : model.containers) {
            for (dev.dominikbreu.spoonmcp.model.ids.ComponentId cid : c.componentIds) map.put(cid.serialize(), c.id);
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
