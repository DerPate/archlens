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

    private static final String EDGE_LABEL_OPEN = " -->|";
    private static final String STORE = "store";

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
        for (Component c : model.components) compById.put(c.id.serialize(), c);

        RenderState st = new RenderState();
        for (int segIdx = 0; segIdx < chain.segments.size(); segIdx++) {
            renderSegment(st, chain, segIdx, compById);
        }
        return assemble(st);
    }

    /** Mutable accumulator threaded through per-segment rendering. */
    private static final class RenderState {
        final StringBuilder nodes = new StringBuilder("flowchart TD\n");
        final StringBuilder edges = new StringBuilder();
        final StringBuilder styles = new StringBuilder();
        int boundaryCounter;
        String previousLastNode;
        String previousSinkLabel;
    }

    private void renderSegment(RenderState st, Chain chain, int segIdx, Map<String, Component> compById) {
        Segment seg = chain.segments.get(segIdx);
        Entrypoint ep = seg.entrypoint;

        // Boundary node from previous segment (skip for first segment).
        if (seg.incomingSink != null) {
            renderBoundary(st, seg, ep, segIdx, compById);
        }
        String headerNodeId = renderHeader(st, seg, ep, segIdx, compById);
        String previousNodeInSeg = renderSteps(st, seg, segIdx, headerNodeId, compById);
        renderTerminalSinks(st, chain, seg, segIdx, previousNodeInSeg);

        // Determine which sink (if any) links to the next segment.
        DataFlowSink linkOut =
                (segIdx + 1 < chain.segments.size()) ? chain.segments.get(segIdx + 1).incomingSink : null;
        st.previousLastNode = previousNodeInSeg;
        st.previousSinkLabel = linkOut != null && linkOut.method != null ? linkOut.method : "";
    }

    private void renderBoundary(
            RenderState st, Segment seg, Entrypoint ep, int segIdx, Map<String, Component> compById) {
        st.boundaryCounter++;
        String boundaryId = "B" + st.boundaryCounter;
        String boundaryLabel = boundaryLabel(seg.incomingSink, compById);
        st.nodes
                .append("    ")
                .append(boundaryId)
                .append(boundaryShape(seg.incomingSink, boundaryLabel))
                .append("\n");
        st.styles
                .append("    class ")
                .append(boundaryId)
                .append(' ')
                .append(boundaryClass(seg.incomingSink.kind))
                .append("\n");
        if (st.previousLastNode != null) {
            st.edges
                    .append("    ")
                    .append(st.previousLastNode)
                    .append(EDGE_LABEL_OPEN)
                    .append(escape(st.previousSinkLabel == null ? "" : st.previousSinkLabel))
                    .append("| ")
                    .append(boundaryId)
                    .append("\n");
        }
        String consumeLabel = (ep != null && ep.name != null) ? ep.name : "";
        st.edges
                .append("    ")
                .append(boundaryId)
                .append(EDGE_LABEL_OPEN)
                .append(escape(consumeLabel))
                .append("| ")
                .append("S" + segIdx + "_0")
                .append("\n");
    }

    private String renderHeader(
            RenderState st, Segment seg, Entrypoint ep, int segIdx, Map<String, Component> compById) {
        String headerNodeId = "S" + segIdx + "_0";
        Component headerComp = (ep != null && ep.componentId != null) ? compById.get(ep.componentId.serialize()) : null;
        String headerComponentName = headerComp != null
                ? headerComp.name
                : (seg.path.steps.isEmpty() ? "?" : seg.path.steps.get(0).componentName);
        ComponentType headerType = headerComp != null ? headerComp.type : null;
        String headerLabel = headerComponentName + (ep != null && ep.name != null ? "." + ep.name : "");
        st.nodes
                .append("    ")
                .append(headerNodeId)
                .append(nodeShape(headerLabel, headerType))
                .append("\n");
        return headerNodeId;
    }

    private String renderSteps(
            RenderState st, Segment seg, int segIdx, String headerNodeId, Map<String, Component> compById) {
        String previousNodeInSeg = headerNodeId;
        for (int i = 1; i < seg.path.steps.size(); i++) {
            DataFlowStep step = seg.path.steps.get(i);
            String nodeId = "S" + segIdx + "_" + i;
            ComponentType type = (step.componentId != null && compById.containsKey(step.componentId.serialize()))
                    ? compById.get(step.componentId.serialize()).type
                    : null;
            String label = step.componentName + "." + step.method;
            st.nodes
                    .append("    ")
                    .append(nodeId)
                    .append(nodeShape(label, type))
                    .append("\n");
            st.edges
                    .append("    ")
                    .append(previousNodeInSeg)
                    .append(EDGE_LABEL_OPEN)
                    .append(escape(step.method))
                    .append("| ")
                    .append(nodeId)
                    .append("\n");
            previousNodeInSeg = nodeId;
        }
        return previousNodeInSeg;
    }

    private void renderTerminalSinks(RenderState st, Chain chain, Segment seg, int segIdx, String previousNodeInSeg) {
        // Emit terminal sinks of this segment that aren't the linking sink, as leaf nodes.
        int terminalCounter = 0;
        for (DataFlowSink s : seg.path.sinks) {
            boolean isLink = (segIdx + 1 < chain.segments.size()) && chain.segments.get(segIdx + 1).incomingSink == s;
            if (isLink) continue;
            terminalCounter++;
            String termId = "T" + segIdx + "_" + terminalCounter;
            String termLabel =
                    (s.componentName != null ? s.componentName : "?") + (s.method != null ? "." + s.method : "");
            st.nodes
                    .append("    ")
                    .append(termId)
                    .append(terminalShape(s, termLabel))
                    .append("\n");
            st.styles
                    .append("    class ")
                    .append(termId)
                    .append(' ')
                    .append(terminalClass(s.kind))
                    .append("\n");
            st.edges
                    .append("    ")
                    .append(previousNodeInSeg)
                    .append(EDGE_LABEL_OPEN)
                    .append(escape(s.method == null ? "" : s.method))
                    .append("| ")
                    .append(termId)
                    .append("\n");
        }
    }

    private String assemble(RenderState st) {
        st.nodes.append("\n").append(st.edges);
        st.nodes.append("\n");
        st.nodes.append("    classDef store fill:#1b5e20,stroke:#0b3d12,color:#ffffff\n");
        st.nodes.append("    classDef messaging fill:#0d47a1,stroke:#062f6c,color:#ffffff\n");
        st.nodes.append("    classDef eventbus fill:#4a148c,stroke:#2c0a55,color:#ffffff\n");
        st.nodes.append("    classDef persistence fill:#1b5e20,stroke:#0b3d12,color:#ffffff\n");
        st.nodes.append("    classDef http fill:#b71c1c,stroke:#7a1313,color:#ffffff\n");
        st.nodes.append("    classDef object fill:#4e342e,stroke:#2d1c19,color:#ffffff\n");
        st.nodes.append("    classDef file fill:#37474f,stroke:#1f2a30,color:#ffffff\n");
        st.nodes.append(st.styles);
        return st.nodes.toString();
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
            case STORE, PERSISTENCE -> "[(\"" + safe + "\")]";
            case EVENT_BUS -> "((\"" + safe + "\"))";
            case MESSAGING -> "(\"" + safe + "\")";
            default -> "[\"" + safe + "\"]";
        };
    }

    private String boundaryLabel(DataFlowSink sink, Map<String, Component> compById) {
        return switch (sink.kind) {
            case STORE -> storeBoundaryLabel(sink, compById);
            case MESSAGING, EVENT_BUS -> sink.channel != null ? sink.channel : "channel";
            case PERSISTENCE -> sink.entityType != null ? sink.entityType : nonNullComponentName(sink);
            default -> sink.componentName != null ? sink.componentName : sink.kind.value();
        };
    }

    private String storeBoundaryLabel(DataFlowSink sink, Map<String, Component> compById) {
        String owner =
                (sink.fieldOwnerComponentId != null && compById.get(sink.fieldOwnerComponentId.serialize()) != null)
                        ? compById.get(sink.fieldOwnerComponentId.serialize()).name
                        : nonNullComponentName(sink);
        return owner + "." + (sink.fieldName != null ? sink.fieldName : "?");
    }

    private static String nonNullComponentName(DataFlowSink sink) {
        return sink.componentName != null ? sink.componentName : "?";
    }

    private String boundaryClass(DataFlowSink.Kind kind) {
        return switch (kind) {
            case STORE -> STORE;
            case MESSAGING -> "messaging";
            case EVENT_BUS -> "eventbus";
            case PERSISTENCE -> "persistence";
            default -> STORE;
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
            case STORE -> STORE;
            default -> STORE;
        };
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("|", "-");
    }
}
