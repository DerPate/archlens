package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.cache.GraphQuery;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Chain;
import dev.dominikbreu.spoonmcp.extractor.PipelineGraphBuilder.Segment;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.DataFlowStep;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final String CONDITIONAL_EDGE_LABEL_OPEN = " -.->|";
    private static final String STORE = "store";

    /** Creates a renderer with default styling. */
    public MermaidPipelineRenderer() {}

    /**
     * Renders {@code chain} as Mermaid text.
     *
     * @param chain pipeline chain to render
     * @param index tool model index for component lookups
     * @return Mermaid flowchart text
     */
    public String render(Chain chain, GraphQuery graph) {
        if (chain == null || chain.segments.isEmpty()) {
            return "flowchart TD\n    note[no pipeline chain]\n";
        }
        RenderState st = new RenderState();
        for (int segIdx = 0; segIdx < chain.segments.size(); segIdx++) {
            renderSegment(st, chain, segIdx, graph);
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

    private void renderSegment(RenderState st, Chain chain, int segIdx, GraphQuery graph) {
        Segment seg = chain.segments.get(segIdx);
        Entrypoint ep = seg.entrypoint;

        // Boundary node from previous segment (skip for first segment).
        if (seg.incomingSink != null) {
            renderBoundary(st, seg, ep, segIdx, graph);
        }
        String headerNodeId = renderHeader(st, seg, ep, segIdx, graph);
        Map<String, String> callerNodeIds = new HashMap<>();
        String previousNodeInSeg = renderSteps(st, seg, segIdx, headerNodeId, graph, callerNodeIds);
        renderTerminalSinks(st, chain, seg, segIdx, previousNodeInSeg, callerNodeIds);

        // Determine which sink (if any) links to the next segment.
        DataFlowSink linkOut =
                (segIdx + 1 < chain.segments.size()) ? chain.segments.get(segIdx + 1).incomingSink : null;
        st.previousLastNode = previousNodeInSeg;
        st.previousSinkLabel = linkOut != null && linkOut.method != null ? linkOut.method : "";
    }

    private void renderBoundary(RenderState st, Segment seg, Entrypoint ep, int segIdx, GraphQuery graph) {
        st.boundaryCounter++;
        String boundaryId = "B" + st.boundaryCounter;
        String boundaryLabel = boundaryLabel(seg.incomingSink, graph);
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

    private String renderHeader(RenderState st, Segment seg, Entrypoint ep, int segIdx, GraphQuery graph) {
        String headerNodeId = "S" + segIdx + "_0";
        GraphQuery.ComponentNode headerComp = null;
        if (ep != null && ep.componentId != null) {
            GraphQuery.GraphNode n = graph.component(ep.componentId);
            if (n instanceof GraphQuery.ComponentNode cn) headerComp = cn;
        }
        String headerComponentName;
        if (headerComp != null) {
            headerComponentName = headerComp.name();
        } else if (seg.path.steps.isEmpty()) {
            headerComponentName = "?";
        } else {
            headerComponentName = seg.path.steps.getFirst().componentName;
        }
        ComponentType headerType = headerComp != null ? headerComp.type() : null;
        String headerLabel = headerComponentName + (ep != null && ep.name != null ? "." + ep.name : "");
        st.nodes
                .append("    ")
                .append(headerNodeId)
                .append(nodeShape(headerLabel, headerType))
                .append("\n");
        return headerNodeId;
    }

    private String renderSteps(RenderState st, Segment seg, int segIdx, String headerNodeId,
            GraphQuery graph, Map<String, String> callerNodeIds) {
        List<GraphQuery.DataFlowNodeNode> topoNodes =
                graph.pathFlowNodes(GraphNodeId.of(seg.path.id.serialize()));
        if (!topoNodes.isEmpty()) {
            return renderStepsFromTopology(st, seg, segIdx, headerNodeId, graph, callerNodeIds, topoNodes);
        }
        // Fallback: flat steps list (paths without topology data).
        // Also deduplicate DFS back-tracking artifacts: DataFlowTracer records every visited
        // node including dead-end conditional branches, so the same component+method can
        // appear multiple times non-consecutively. Key = compId#method to allow different
        // methods on the same class (e.g. ingest vs processNonNullValue) to be distinct.
        String previousNodeInSeg = headerNodeId;
        String prevComponentKey = seg.path.steps.isEmpty() ? null : compKey(seg.path.steps.getFirst());
        Set<String> renderedStepKeys = new HashSet<>();
        if (!seg.path.steps.isEmpty()) {
            DataFlowStep first = seg.path.steps.getFirst();
            renderedStepKeys.add(compKey(first) + "#" + first.method);
            if (first.componentId != null) callerNodeIds.put(first.componentId.serialize(), headerNodeId);
        }
        for (int i = 1; i < seg.path.steps.size(); i++) {
            DataFlowStep step = seg.path.steps.get(i);
            String stepKey = compKey(step);
            if (stepKey != null && stepKey.equals(prevComponentKey)) continue;
            String dedupKey = stepKey + "#" + step.method;
            if (!renderedStepKeys.add(dedupKey)) continue;
            String nodeId = "S" + segIdx + "_" + i;
            GraphQuery.ComponentNode stepComp = null;
            if (step.componentId != null) {
                GraphQuery.GraphNode n = graph.component(step.componentId);
                if (n instanceof GraphQuery.ComponentNode cn) stepComp = cn;
            }
            ComponentType type = stepComp != null ? stepComp.type() : null;
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
            prevComponentKey = stepKey;
            if (step.componentId != null) callerNodeIds.put(step.componentId.serialize(), nodeId);
        }
        return previousNodeInSeg;
    }

    private String renderStepsFromTopology(
            RenderState st, Segment seg, int segIdx, String headerNodeId,
            GraphQuery graph, Map<String, String> callerNodeIds,
            List<GraphQuery.DataFlowNodeNode> nodes) {
        List<GraphQuery.GraphEdge> edges =
                graph.pathFlowEdges(GraphNodeId.of(seg.path.id.serialize()));

        Map<GraphNodeId, String> nodeIdMap = new HashMap<>();
        String lastMethodNodeId = headerNodeId;

        for (GraphQuery.DataFlowNodeNode dn : nodes) {
            switch (dn.nodeKind()) {
                case "root" -> {
                    nodeIdMap.put(dn.id(), headerNodeId);
                    if (dn.componentId() != null)
                        callerNodeIds.put(dn.componentId().serialize(), headerNodeId);
                }
                case "method" -> {
                    String mermaidId = "S" + segIdx + "_N" + dn.nodeOrder();
                    nodeIdMap.put(dn.id(), mermaidId);
                    GraphQuery.ComponentNode compNode = null;
                    if (dn.componentId() != null) {
                        GraphQuery.GraphNode n = graph.component(dn.componentId());
                        if (n instanceof GraphQuery.ComponentNode cn) compNode = cn;
                    }
                    ComponentType type = compNode != null ? compNode.type() : null;
                    String label = (dn.componentName() != null ? dn.componentName() : "?")
                            + (dn.method() != null ? "." + dn.method() : "");
                    st.nodes.append("    ").append(mermaidId)
                            .append(nodeShape(label, type)).append("\n");
                    if (dn.componentId() != null)
                        callerNodeIds.put(dn.componentId().serialize(), mermaidId);
                    lastMethodNodeId = mermaidId;
                }
                // "sink" nodes: skip — handled by renderTerminalSinks
            }
        }

        Set<GraphNodeId> sinkNodeIds = nodes.stream()
                .filter(n -> "sink".equals(n.nodeKind()))
                .map(GraphQuery.DataFlowNodeNode::id)
                .collect(Collectors.toSet());

        for (GraphQuery.GraphEdge edge : edges) {
            if (sinkNodeIds.contains(edge.toId())) continue;
            String fromMermaid = nodeIdMap.get(edge.fromId());
            String toMermaid = nodeIdMap.get(edge.toId());
            if (fromMermaid == null || toMermaid == null) continue;

            String edgeKind = (String) edge.properties().get("edgeKind");
            String edgeLabel = (String) edge.properties().get("label");
            boolean conditional = "conditional".equals(edgeKind);
            String labelStr = edgeLabel != null && !edgeLabel.isBlank() ? edgeLabel : "";

            st.edges.append("    ").append(fromMermaid);
            if (labelStr.isEmpty()) {
                st.edges.append(conditional ? " -.-> " : " --> ");
            } else {
                st.edges.append(conditional ? CONDITIONAL_EDGE_LABEL_OPEN : EDGE_LABEL_OPEN)
                        .append(escape(labelStr)).append("| ");
            }
            st.edges.append(toMermaid).append("\n");
        }

        return lastMethodNodeId;
    }

    private static String compKey(DataFlowStep step) {
        return step.componentId != null ? step.componentId.serialize() : step.componentName;
    }

    private void renderTerminalSinks(RenderState st, Chain chain, Segment seg, int segIdx,
            String previousNodeInSeg, Map<String, String> callerNodeIds) {
        // Deduplicate sinks by (componentName, method, kind, channel/topic) and group by caller
        // so each distinct sink appears once, wired from the step that actually called it.
        // Key = dedup key; value = first sink seen with that key.
        LinkedHashMap<String, DataFlowSink> unique = new LinkedHashMap<>();
        for (DataFlowSink s : seg.path.sinks) {
            boolean isLink = (segIdx + 1 < chain.segments.size()) && chain.segments.get(segIdx + 1).incomingSink == s;
            if (isLink) continue;
            String dedupKey = sinkDedupKey(s);
            unique.putIfAbsent(dedupKey, s);
        }
        int terminalCounter = 0;
        for (DataFlowSink s : unique.values()) {
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
            // Wire from the step that called this sink, falling back to the segment's last node.
            String callerNode = s.callerComponentId != null
                    ? callerNodeIds.getOrDefault(s.callerComponentId.serialize(), previousNodeInSeg)
                    : previousNodeInSeg;
            st.edges
                    .append("    ")
                    .append(callerNode)
                    .append(EDGE_LABEL_OPEN)
                    .append(escape(s.method == null ? "" : s.method))
                    .append("| ")
                    .append(termId)
                    .append("\n");
        }
    }

    private static String sinkDedupKey(DataFlowSink s) {
        return (s.componentName != null ? s.componentName : "")
                + "|" + (s.method != null ? s.method : "")
                + "|" + (s.kind != null ? s.kind.value() : "")
                + "|" + (s.channel != null ? s.channel : s.topic != null ? s.topic : "");
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

    private String boundaryLabel(DataFlowSink sink, GraphQuery graph) {
        return switch (sink.kind) {
            case STORE -> storeBoundaryLabel(sink, graph);
            case MESSAGING, EVENT_BUS -> sink.channel != null ? sink.channel : "channel";
            case PERSISTENCE -> sink.entityType != null ? sink.entityType : nonNullComponentName(sink);
            default -> sink.componentName != null ? sink.componentName : sink.kind.value();
        };
    }

    private String storeBoundaryLabel(DataFlowSink sink, GraphQuery graph) {
        GraphQuery.ComponentNode owner = null;
        if (sink.fieldOwnerComponentId != null) {
            GraphQuery.GraphNode n = graph.component(sink.fieldOwnerComponentId);
            if (n instanceof GraphQuery.ComponentNode cn) owner = cn;
        }
        String ownerName = owner != null ? owner.name() : nonNullComponentName(sink);
        return ownerName + "." + (sink.fieldName != null ? sink.fieldName : "?");
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
        return Mermaid.escapeLabel(s);
    }
}
