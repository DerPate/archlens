package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a single pipeline {@link Chain} as a Mermaid {@code flowchart TD}.
 *
 * <p>Each segment becomes a vertical sequence of step nodes shaped by the component's
 * architectural role. Between segments, a boundary node is emitted whose shape and
 * style reflect the linking sink kind (STORE = cylinder, MESSAGING/EVENT_BUS = rounded
 * rectangle / circle).
 */
public class MermaidPipelineRenderer {

    /** Creates a renderer with default styling. */
    public MermaidPipelineRenderer() {}

    /**
     * Renders {@code chain} as Mermaid text.
     *
     * @param chain pipeline chain to render
     * @param model architecture model used to resolve component shapes
     * @return Mermaid flowchart text
     */
    public String render(Chain chain, ArchitectureModel model) {
        if (chain == null || chain.segments.isEmpty()) {
            return "flowchart TD\n    note[no pipeline chain]\n";
        }
        Map<String, Component> compById = new HashMap<>();
        for (Component c : model.components) compById.put(c.id, c);

        StringBuilder sb = new StringBuilder("flowchart TD\n");
        StringBuilder edges = new StringBuilder();
        StringBuilder styles = new StringBuilder();

        int boundaryCounter = 0;
        String previousLastNode = null;
        String previousSinkLabel = null;

        for (int segIdx = 0; segIdx < chain.segments.size(); segIdx++) {
            Segment seg = chain.segments.get(segIdx);
            Entrypoint ep = seg.entrypoint;

            // Boundary node from previous segment (skip for first segment).
            if (seg.incomingSink != null) {
                boundaryCounter++;
                String boundaryId = "B" + boundaryCounter;
                String boundaryLabel = boundaryLabel(seg.incomingSink, compById);
                sb.append("    ")
                        .append(boundaryId)
                        .append(boundaryShape(seg.incomingSink, boundaryLabel))
                        .append("\n");
                String cls = boundaryClass(seg.incomingSink.kind);
                styles.append("    class ")
                        .append(boundaryId)
                        .append(' ')
                        .append(cls)
                        .append("\n");
                if (previousLastNode != null) {
                    edges.append("    ")
                            .append(previousLastNode)
                            .append(" -->|")
                            .append(escape(previousSinkLabel == null ? "" : previousSinkLabel))
                            .append("| ")
                            .append(boundaryId)
                            .append("\n");
                }
                String consumeLabel = ep != null && ep.name != null ? ep.name : "";
                String firstStepNode = "S" + segIdx + "_0";
                edges.append("    ")
                        .append(boundaryId)
                        .append(" -->|")
                        .append(escape(consumeLabel))
                        .append("| ")
                        .append(firstStepNode)
                        .append("\n");
            }

            // Entrypoint header step (segment header) — represents the entrypoint method itself.
            String headerNodeId = "S" + segIdx + "_0";
            String headerComponentName = ep != null && ep.componentId != null && compById.get(ep.componentId) != null
                    ? compById.get(ep.componentId).name
                    : (seg.path.steps.isEmpty() ? "?" : seg.path.steps.get(0).componentName);
            ComponentType headerType = ep != null && ep.componentId != null && compById.get(ep.componentId) != null
                    ? compById.get(ep.componentId).type
                    : null;
            String headerLabel = headerComponentName + (ep != null && ep.name != null ? "." + ep.name : "");
            sb.append("    ")
                    .append(headerNodeId)
                    .append(nodeShape(headerLabel, headerType))
                    .append("\n");

            String previousNodeInSeg = headerNodeId;
            for (int i = 1; i < seg.path.steps.size(); i++) {
                DataFlowStep step = seg.path.steps.get(i);
                String nodeId = "S" + segIdx + "_" + i;
                ComponentType type =
                        compById.containsKey(step.componentId) ? compById.get(step.componentId).type : null;
                String label = step.componentName + "." + step.method;
                sb.append("    ").append(nodeId).append(nodeShape(label, type)).append("\n");
                edges.append("    ")
                        .append(previousNodeInSeg)
                        .append(" -->|")
                        .append(escape(step.method))
                        .append("| ")
                        .append(nodeId)
                        .append("\n");
                previousNodeInSeg = nodeId;
            }

            // Emit terminal sinks of this segment that aren't the linking sink, as leaf nodes.
            int terminalCounter = 0;
            for (DataFlowSink s : seg.path.sinks) {
                boolean isLink =
                        (segIdx + 1 < chain.segments.size()) && chain.segments.get(segIdx + 1).incomingSink == s;
                if (isLink) continue;
                terminalCounter++;
                String termId = "T" + segIdx + "_" + terminalCounter;
                String termLabel =
                        (s.componentName != null ? s.componentName : "?") + (s.method != null ? "." + s.method : "");
                sb.append("    ")
                        .append(termId)
                        .append(terminalShape(s, termLabel))
                        .append("\n");
                styles.append("    class ")
                        .append(termId)
                        .append(' ')
                        .append(terminalClass(s.kind))
                        .append("\n");
                edges.append("    ")
                        .append(previousNodeInSeg)
                        .append(" -->|")
                        .append(escape(s.method == null ? "" : s.method))
                        .append("| ")
                        .append(termId)
                        .append("\n");
            }

            // Determine which sink (if any) links to the next segment.
            DataFlowSink linkOut =
                    (segIdx + 1 < chain.segments.size()) ? chain.segments.get(segIdx + 1).incomingSink : null;
            previousLastNode = previousNodeInSeg;
            previousSinkLabel = linkOut != null && linkOut.method != null ? linkOut.method : "";
        }

        sb.append("\n").append(edges);
        sb.append("\n");
        sb.append("    classDef store fill:#1b5e20,stroke:#0b3d12,color:#ffffff\n");
        sb.append("    classDef messaging fill:#0d47a1,stroke:#062f6c,color:#ffffff\n");
        sb.append("    classDef eventbus fill:#4a148c,stroke:#2c0a55,color:#ffffff\n");
        sb.append("    classDef persistence fill:#1b5e20,stroke:#0b3d12,color:#ffffff\n");
        sb.append("    classDef http fill:#b71c1c,stroke:#7a1313,color:#ffffff\n");
        sb.append("    classDef object fill:#4e342e,stroke:#2d1c19,color:#ffffff\n");
        sb.append("    classDef file fill:#37474f,stroke:#1f2a30,color:#ffffff\n");
        sb.append(styles);
        return sb.toString();
    }

    private String nodeShape(String label, ComponentType type) {
        String safe = escape(label);
        if (type == null) return "[\"" + safe + "\"]";
        return switch (type) {
            case REPOSITORY -> "[(\"" + safe + "\")]";
            case HTTP_CLIENT -> "[/\"" + safe + "\"/]";
            case MESSAGE_DRIVEN_BEAN, SCHEDULER -> "([\"" + safe + "\"])";
            case CDI_EVENT_CONSUMER, CDI_EVENT_PRODUCER -> "((\"" + safe + "\"))";
            default -> "[\"" + safe + "\"]";
        };
    }

    private String boundaryShape(DataFlowSink sink, String label) {
        String safe = escape(label);
        return switch (sink.kind) {
            case STORE -> "[(\"" + safe + "\")]";
            case EVENT_BUS -> "((\"" + safe + "\"))";
            case MESSAGING -> "(\"" + safe + "\")";
            default -> "[\"" + safe + "\"]";
        };
    }

    private String boundaryLabel(DataFlowSink sink, Map<String, Component> compById) {
        if (sink.kind == DataFlowSink.Kind.STORE) {
            String owner = sink.fieldOwnerComponentId != null && compById.get(sink.fieldOwnerComponentId) != null
                    ? compById.get(sink.fieldOwnerComponentId).name
                    : (sink.componentName != null ? sink.componentName : "?");
            return owner + "." + (sink.fieldName != null ? sink.fieldName : "?");
        }
        if (sink.kind == DataFlowSink.Kind.MESSAGING || sink.kind == DataFlowSink.Kind.EVENT_BUS) {
            return sink.channel != null ? sink.channel : "channel";
        }
        return sink.componentName != null ? sink.componentName : sink.kind.value();
    }

    private String boundaryClass(DataFlowSink.Kind kind) {
        return switch (kind) {
            case STORE -> "store";
            case MESSAGING -> "messaging";
            case EVENT_BUS -> "eventbus";
            default -> "store";
        };
    }

    private String terminalShape(DataFlowSink sink, String label) {
        String safe = escape(label);
        return switch (sink.kind) {
            case PERSISTENCE -> "[(\"" + safe + "\")]";
            case HTTP_OUTBOUND -> "[/\"" + safe + "\"/]";
            case OBJECT_STORAGE -> "[(\"" + safe + "\")]";
            case FILE_OUTBOUND -> "[\"" + safe + "\"]";
            case MESSAGING -> "(\"" + safe + "\")";
            case EVENT_BUS -> "((\"" + safe + "\"))";
            case STORE -> "[(\"" + safe + "\")]";
            default -> "[\"" + safe + "\"]";
        };
    }

    private String terminalClass(DataFlowSink.Kind kind) {
        return switch (kind) {
            case PERSISTENCE -> "persistence";
            case HTTP_OUTBOUND -> "http";
            case OBJECT_STORAGE -> "object";
            case FILE_OUTBOUND -> "file";
            case MESSAGING -> "messaging";
            case EVENT_BUS -> "eventbus";
            case STORE -> "store";
            default -> "store";
        };
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("|", "-");
    }
}
